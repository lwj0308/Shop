package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.enums.NotificationTypeEnum;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.order.dto.DeliveryDTO;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderLog;
import com.shop.model.order.entity.OrderLogistics;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.order.feign.NotificationFeignClient;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.mapper.OrderLogisticsMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物流服务实现类 LogisticsServiceImpl 的单元测试
 * <p>
 * 小白讲解：
 * 物流服务负责电商里"商家发货"和"用户查物流"两件事，是订单从"待发货"走向"运输中"的关键环节。
 * LogisticsServiceImpl 做两件事：
 * 1. delivery - 商家发货：校验订单状态、写物流记录、乐观锁更新订单状态、记日志、通知用户
 * 2. getLogistics - 查看物流：根据订单ID查物流记录，转成 VO 返回给前端
 *
 * 测试策略：
 * - 把所有 Mapper 和 Feign 客户端都 Mock 掉，专注验证业务逻辑分支
 * - 重点测试状态机校验、乐观锁更新、通知容错（通知失败不影响发货主流程）
 * - 用 @Nested 按方法分组，每个方法覆盖正常流程 + 各种异常分支
 * </p>
 */
@DisplayName("物流服务 LogisticsServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class LogisticsServiceImplTest {

    // ==================== 依赖的 Mock 对象 ====================

    /** 假装物流信息 Mapper */
    @Mock
    private OrderLogisticsMapper orderLogisticsMapper;

    /** 假装订单主表 Mapper */
    @Mock
    private OrderInfoMapper orderInfoMapper;

    /** 假装订单状态日志 Mapper */
    @Mock
    private OrderLogMapper orderLogMapper;

    /** 假装通知服务 Feign 客户端（用于发货后通知用户） */
    @Mock
    private NotificationFeignClient notificationFeignClient;

    /** 被测试的物流服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private LogisticsServiceImpl logisticsService;

    // ==================== 测试常量 ====================

    private static final Long ORDER_ID = 5001L;                     // 订单ID
    private static final Long USER_ID = 1001L;                      // 下单用户ID
    private static final String ORDER_NO = "1829384756102345678";   // 订单号
    private static final String LOGISTICS_NO = "SF1234567890";      // 快递单号
    private static final String LOGISTICS_COMPANY = "顺丰速运";       // 快递公司

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：LogisticsServiceImpl 里用了 LambdaUpdateWrapper.eq(OrderInfo::getId, ...) 和
     * LambdaQueryWrapper.eq(OrderLogistics::getOrderId, ...)，
     * 这些 Lambda 表达式需要 MyBatis-Plus 解析"实体字段对应数据库哪一列"。
     * 单元测试没有 Spring 环境，需要手动初始化，否则会报 "can not find lambda cache" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrderLogistics.class);
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, OrderLog.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个"待发货"状态的订单（只有这个状态商家才能发货）
     *
     * @return 构造好的 OrderInfo
     */
    private OrderInfo buildPaidOrder() {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatusEnum.PAID.getCode()); // 待发货状态
        return order;
    }

    /**
     * 构造一个发货请求参数
     *
     * @return 构造好的 DeliveryDTO
     */
    private DeliveryDTO buildDeliveryDTO() {
        DeliveryDTO dto = new DeliveryDTO();
        dto.setOrderId(ORDER_ID);
        dto.setLogisticsNo(LOGISTICS_NO);
        dto.setLogisticsCompany(LOGISTICS_COMPANY);
        return dto;
    }

    /**
     * 构造一条物流记录（带轨迹详情）
     *
     * @param details 物流轨迹列表（传 null 表示没有轨迹）
     * @return 构造好的 OrderLogistics
     */
    private OrderLogistics buildLogistics(List<OrderLogistics.LogisticsDetail> details) {
        OrderLogistics logistics = new OrderLogistics();
        logistics.setOrderId(ORDER_ID);
        logistics.setOrderNo(ORDER_NO);
        logistics.setLogisticsNo(LOGISTICS_NO);
        logistics.setLogisticsCompany(LOGISTICS_COMPANY);
        logistics.setDetail(details);
        return logistics;
    }

    // ==================== 1. delivery 商家发货测试 ====================

    @Nested
    @DisplayName("delivery - 商家发货")
    class DeliveryTest {

        @Test
        @DisplayName("订单不存在：抛 ORDER_NOT_FOUND 异常")
        void delivery_orderNotFound_throwException() {
            // 场景：商家传了一个不存在的订单ID
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> logisticsService.delivery(buildDeliveryDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单不存在");

            // 验证：后续步骤都没执行（没创建物流、没改订单状态、没记日志、没发通知）
            verify(orderLogisticsMapper, never()).insert(any(OrderLogistics.class));
            verify(orderInfoMapper, never()).update(any(), any(Wrapper.class));
            verify(orderLogMapper, never()).insert(any(OrderLog.class));
            verify(notificationFeignClient, never()).sendNotification(any(NotificationSendDTO.class));
        }

        @Test
        @DisplayName("订单状态非待发货（如待付款 UNPAID）：状态机校验失败抛 IllegalArgumentException")
        void delivery_orderStatusNotPaid_throwIllegalArgument() {
            // 场景：订单还没付款（UNPAID状态），商家就想发货，状态机不允许
            OrderInfo order = buildPaidOrder();
            order.setStatus(OrderStatusEnum.UNPAID.getCode()); // 待付款状态
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            assertThatThrownBy(() -> logisticsService.delivery(buildDeliveryDTO()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("订单状态不允许");

            // 验证：状态校验失败后，物流记录、订单状态更新、日志都没执行
            verify(orderLogisticsMapper, never()).insert(any(OrderLogistics.class));
            verify(orderInfoMapper, never()).update(any(), any(Wrapper.class));
            verify(orderLogMapper, never()).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("正常发货：物流记录写入、订单状态更新、订单日志记录均执行")
        void delivery_normal_success() {
            // 场景：订单是"待发货"状态，商家正常发货
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder());
            // 乐观锁更新订单状态成功（返回1表示影响1行）
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);

            logisticsService.delivery(buildDeliveryDTO());

            // 验证：物流记录被写入
            verify(orderLogisticsMapper).insert(any(OrderLogistics.class));
            // 验证：订单状态从 PAID 更新为 SHIPPING
            verify(orderInfoMapper).update(any(), any(Wrapper.class));
            // 验证：记录了订单状态变更日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("乐观锁更新失败（updated==0，订单状态已被并发修改）：抛 ORDER_STATUS_ERROR 异常")
        void delivery_optimisticLockFail_throwStatusError() {
            // 场景：商家发货瞬间，订单状态被其他请求改了（比如用户申请退款），乐观锁更新返回0
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder());
            // 乐观锁更新返回0，说明订单状态已经不是 PAID 了
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> logisticsService.delivery(buildDeliveryDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单状态已变更");

            // 验证：物流记录已经写入（在乐观锁更新之前），但订单日志和通知都没执行
            verify(orderLogisticsMapper).insert(any(OrderLogistics.class));
            verify(orderLogMapper, never()).insert(any(OrderLog.class));
            verify(notificationFeignClient, never()).sendNotification(any(NotificationSendDTO.class));
        }

        @Test
        @DisplayName("通知发送成功：发货后调用通知服务，通知内容正确")
        void delivery_notificationSent_success() {
            // 场景：正常发货，验证通知服务被调用且通知内容正确
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder());
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);

            logisticsService.delivery(buildDeliveryDTO());

            // 用 ArgumentCaptor 抓取传给通知服务的参数，验证通知内容
            ArgumentCaptor<NotificationSendDTO> captor = ArgumentCaptor.forClass(NotificationSendDTO.class);
            verify(notificationFeignClient).sendNotification(captor.capture());

            NotificationSendDTO sent = captor.getValue();
            // 通知发给下单用户
            assertThat(sent.getReceiverType()).isEqualTo(ReceiverTypeEnum.USER.getCode());
            assertThat(sent.getReceiverId()).isEqualTo(USER_ID);
            // 通知类型是"订单"
            assertThat(sent.getType()).isEqualTo(NotificationTypeEnum.ORDER.getCode());
            // 标题和业务关联信息
            assertThat(sent.getTitle()).isEqualTo("您的订单已发货");
            assertThat(sent.getBizType()).isEqualTo("order");
            assertThat(sent.getBizId()).isEqualTo(ORDER_NO);
            // 内容里包含订单号和快递公司、快递单号
            assertThat(sent.getContent()).contains(ORDER_NO, LOGISTICS_COMPANY, LOGISTICS_NO);
        }

        @Test
        @DisplayName("通知发送失败（Feign异常）：不影响发货主流程（try-catch吞异常）")
        void delivery_notificationFail_notAffectMain() {
            // 场景：通知服务挂了抛异常，但发货已经成功，不应该影响主流程
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder());
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            // 通知服务抛异常（模拟服务不可用）
            doThrow(new RuntimeException("通知服务不可用"))
                    .when(notificationFeignClient).sendNotification(any(NotificationSendDTO.class));

            // 发货应该正常完成，不抛异常（通知失败被 try-catch 吞掉）
            logisticsService.delivery(buildDeliveryDTO());

            // 验证：发货主流程的三个步骤都执行了
            verify(orderLogisticsMapper).insert(any(OrderLogistics.class));
            verify(orderInfoMapper).update(any(), any(Wrapper.class));
            verify(orderLogMapper).insert(any(OrderLog.class));
            // 验证：通知服务确实被调用了（虽然失败了）
            verify(notificationFeignClient).sendNotification(any(NotificationSendDTO.class));
        }
    }

    // ==================== 2. getLogistics 查看物流测试 ====================

    @Nested
    @DisplayName("getLogistics - 查看物流")
    class GetLogisticsTest {

        @Test
        @DisplayName("物流不存在（订单未发货）：返回 null")
        void getLogistics_notExist_returnNull() {
            // 场景：订单还没发货，查不到物流记录
            when(orderLogisticsMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            OrderDetailVO.OrderLogisticsVO result = logisticsService.getLogistics(ORDER_ID);

            // 验证：返回 null
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("物流存在但 detail 为 null：返回 VO，detail 字段为 null")
        void getLogistics_detailNull_returnVoWithNullDetail() {
            // 场景：物流记录存在，但没有轨迹详情（detail字段为null）
            when(orderLogisticsMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(buildLogistics(null));

            OrderDetailVO.OrderLogisticsVO result = logisticsService.getLogistics(ORDER_ID);

            // 验证：返回的 VO 有快递单号和公司，但 detail 是 null（不处理null的detail）
            assertThat(result).isNotNull();
            assertThat(result.getLogisticsNo()).isEqualTo(LOGISTICS_NO);
            assertThat(result.getLogisticsCompany()).isEqualTo(LOGISTICS_COMPANY);
            assertThat(result.getDetail()).isNull();
        }

        @Test
        @DisplayName("物流存在且有 detail：返回 VO，detail 正确转换为 LogisticsDetailVO 列表")
        void getLogistics_hasDetail_returnVoWithDetailList() {
            // 场景：物流记录存在，且有2条轨迹（已揽收 + 运输中）
            OrderLogistics.LogisticsDetail d1 = new OrderLogistics.LogisticsDetail();
            d1.setTime("2024-01-01 10:00:00");
            d1.setDesc("快递已揽收");

            OrderLogistics.LogisticsDetail d2 = new OrderLogistics.LogisticsDetail();
            d2.setTime("2024-01-01 15:00:00");
            d2.setDesc("运输中");

            when(orderLogisticsMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(buildLogistics(Arrays.asList(d1, d2)));

            OrderDetailVO.OrderLogisticsVO result = logisticsService.getLogistics(ORDER_ID);

            // 验证：返回的 VO 字段正确
            assertThat(result).isNotNull();
            assertThat(result.getLogisticsNo()).isEqualTo(LOGISTICS_NO);
            assertThat(result.getLogisticsCompany()).isEqualTo(LOGISTICS_COMPANY);

            // 验证：detail 被正确转换成 VO 列表，2条记录，内容和顺序一致
            assertThat(result.getDetail()).hasSize(2);
            assertThat(result.getDetail().get(0).getTime()).isEqualTo("2024-01-01 10:00:00");
            assertThat(result.getDetail().get(0).getDesc()).isEqualTo("快递已揽收");
            assertThat(result.getDetail().get(1).getTime()).isEqualTo("2024-01-01 15:00:00");
            assertThat(result.getDetail().get(1).getDesc()).isEqualTo("运输中");
        }
    }
}
