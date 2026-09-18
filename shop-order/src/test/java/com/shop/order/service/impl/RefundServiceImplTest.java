package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.model.order.dto.RefundApplyDTO;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.OrderLog;
import com.shop.model.order.entity.RefundOrder;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.enums.RefundStatusEnum;
import com.shop.model.order.vo.RefundVO;
import com.shop.order.feign.ProductFeignClient;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.mapper.RefundOrderMapper;
import com.shop.order.util.OrderNoGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款服务实现类 RefundServiceImpl 的单元测试
 * <p>
 * 小白讲解：
 * 退款是电商系统里最敏感的功能之一，涉及钱的问题必须严格测试。
 * RefundServiceImpl 负责三件事：
 * 1. applyRefund - 用户申请退款（只有"待发货"状态的订单能退）
 * 2. auditRefund - 商家审核退款（同意 → 回滚库存；拒绝 → 恢复订单状态）
 * 3. getRefundList - 查询某个订单的所有退款记录
 *
 * 测试策略：
 * - 把所有 Mapper 和 Feign 客户端都 Mock 掉，专注验证业务逻辑分支
 * - 重点测试状态机校验、乐观锁更新、金额校验、库存回滚容错
 * - 用 @Nested 按方法分组，每个方法覆盖正常流程 + 各种异常分支
 * </p>
 */
@DisplayName("退款服务 RefundServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    // ==================== 依赖的 Mock 对象 ====================

    /** 假装退款单 Mapper */
    @Mock
    private RefundOrderMapper refundOrderMapper;

    /** 假装订单主表 Mapper */
    @Mock
    private OrderInfoMapper orderInfoMapper;

    /** 假装订单明细 Mapper */
    @Mock
    private OrderItemMapper orderItemMapper;

    /** 假装订单状态日志 Mapper */
    @Mock
    private OrderLogMapper orderLogMapper;

    /** 假装商品服务 Feign 客户端（用于回滚库存） */
    @Mock
    private ProductFeignClient productFeignClient;

    /** 假装订单号生成器（也用来生成退款单号） */
    @Mock
    private OrderNoGenerator orderNoGenerator;

    /** 被测试的退款服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private RefundServiceImpl refundService;

    // ==================== 测试常量 ====================

    private static final Long USER_ID = 1001L;          // 当前用户ID
    private static final Long OTHER_USER_ID = 1002L;    // 其他用户ID（测试越权）
    private static final Long ORDER_ID = 5001L;         // 订单ID
    private static final String ORDER_NO = "1829384756102345678"; // 订单号
    private static final Long ORDER_ITEM_ID = 6001L;    // 订单明细ID
    private static final Long SKU_ID = 3001L;           // SKU ID
    private static final Long REFUND_ID = 7001L;        // 退款单ID
    private static final String REFUND_NO = "RF1829384756102345"; // 退款单号
    private static final Long MERCHANT_ID = 8001L;      // 商家ID

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：RefundServiceImpl 里用了 LambdaUpdateWrapper.eq(OrderInfo::getId, ...)，
     * 这些 Lambda 表达式需要 MyBatis-Plus 解析"实体字段对应数据库哪一列"。
     * 单元测试没有 Spring 环境，需要手动初始化，否则会报 "can not find lambda cache" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RefundOrder.class);
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, OrderLog.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个"待发货"状态的订单（只有这个状态能申请退款）
     *
     * @param userId 订单归属用户ID
     * @return 构造好的 OrderInfo
     */
    private OrderInfo buildPaidOrder(Long userId) {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(userId);
        order.setMerchantId(MERCHANT_ID);
        order.setStatus(OrderStatusEnum.PAID.getCode()); // 待发货状态
        order.setPayAmount(new BigDecimal("100.00"));
        return order;
    }

    /**
     * 构造一条订单明细（用户退的就是这个商品）
     *
     * @param subtotal 小计金额（退款金额上限）
     * @return 构造好的 OrderItem
     */
    private OrderItem buildOrderItem(BigDecimal subtotal) {
        OrderItem item = new OrderItem();
        item.setId(ORDER_ITEM_ID);
        item.setOrderId(ORDER_ID);
        item.setSkuId(SKU_ID);
        item.setQuantity(2);
        item.setSubtotal(subtotal);
        return item;
    }

    /**
     * 构造一个退款申请参数
     *
     * @param refundAmount 退款金额（null 表示不传，默认全额退）
     * @return 构造好的 RefundApplyDTO
     */
    private RefundApplyDTO buildApplyDTO(BigDecimal refundAmount) {
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderId(ORDER_ID);
        dto.setOrderItemId(ORDER_ITEM_ID);
        dto.setReason("商品有质量问题");
        dto.setRefundAmount(refundAmount);
        return dto;
    }

    /**
     * 构造一个"待审核"状态的退款单（商家审核时用）
     *
     * @return 构造好的 RefundOrder
     */
    private RefundOrder buildPendingRefundOrder() {
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setId(REFUND_ID);
        refundOrder.setRefundNo(REFUND_NO);
        refundOrder.setOrderId(ORDER_ID);
        refundOrder.setOrderNo(ORDER_NO);
        refundOrder.setOrderItemId(ORDER_ITEM_ID);
        refundOrder.setUserId(USER_ID);
        refundOrder.setMerchantId(MERCHANT_ID);
        refundOrder.setRefundAmount(new BigDecimal("50.00"));
        refundOrder.setReason("商品有质量问题");
        refundOrder.setStatus(RefundStatusEnum.PENDING.getCode()); // 待审核
        return refundOrder;
    }

    /**
     * 构造一个退款审核参数
     *
     * @param status    审核结果：1同意 2拒绝
     * @param auditNote 审核备注
     * @return 构造好的 RefundAuditDTO
     */
    private RefundAuditDTO buildAuditDTO(int status, String auditNote) {
        RefundAuditDTO dto = new RefundAuditDTO();
        dto.setRefundId(REFUND_ID);
        dto.setStatus(status);
        dto.setAuditNote(auditNote);
        return dto;
    }

    // ==================== 1. applyRefund 申请退款测试 ====================

    @Nested
    @DisplayName("applyRefund - 用户申请退款")
    class ApplyRefundTest {

        @Test
        @DisplayName("订单不存在：抛 ORDER_NOT_FOUND 异常")
        void applyRefund_orderNotFound_throwException() {
            // 场景：用户传了一个不存在的订单ID
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单不存在");

            // 验证：后续步骤都没执行
            verify(orderItemMapper, never()).selectById(anyLong());
            verify(refundOrderMapper, never()).insert(any(RefundOrder.class));
        }

        @Test
        @DisplayName("订单不属于当前用户：抛 FORBIDDEN 异常（防越权）")
        void applyRefund_orderNotBelongToUser_throwForbidden() {
            // 场景：订单属于用户1002，但用户1001想退款（越权）
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(OTHER_USER_ID));

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权操作");

            verify(orderItemMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("订单状态非待发货（如运输中）：状态机校验失败抛异常")
        void applyRefund_orderStatusNotPaid_throwStatusError() {
            // 场景：订单已经发货了（SHIPPING状态），不能再申请退款
            OrderInfo order = buildPaidOrder(USER_ID);
            order.setStatus(OrderStatusEnum.SHIPPING.getCode());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(order);

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("订单状态不允许");

            verify(orderItemMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("订单明细不存在：抛 PARAM_ERROR 异常")
        void applyRefund_orderItemNotFound_throwParamError() {
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单明细不存在");

            verify(refundOrderMapper, never()).insert(any(RefundOrder.class));
        }

        @Test
        @DisplayName("订单明细 orderId 不匹配：抛 PARAM_ERROR 异常")
        void applyRefund_orderItemOrderIdMismatch_throwParamError() {
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            // 明细的 orderId 不是当前订单的 ID
            OrderItem item = buildOrderItem(new BigDecimal("100.00"));
            item.setOrderId(9999L);
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(item);

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单明细不存在");

            verify(refundOrderMapper, never()).insert(any(RefundOrder.class));
        }

        @Test
        @DisplayName("退款金额超过实付金额：抛 PARAM_ERROR 异常")
        void applyRefund_amountExceedSubtotal_throwParamError() {
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            // 明细小计100元，但用户想退150元
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(buildOrderItem(new BigDecimal("100.00")));

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("150.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("退款金额不能超过实付金额");

            verify(refundOrderMapper, never()).insert(any(RefundOrder.class));
        }

        @Test
        @DisplayName("不传退款金额：默认退该商品全额")
        void applyRefund_noAmount_defaultFullRefund() {
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(buildOrderItem(new BigDecimal("100.00")));
            when(orderNoGenerator.generateRefundNo()).thenReturn(REFUND_NO);
            when(refundOrderMapper.insert(any(RefundOrder.class))).thenAnswer(invocation -> {
                RefundOrder r = invocation.getArgument(0);
                r.setId(REFUND_ID); // 模拟主键回填
                return 1;
            });
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1); // 乐观锁更新成功

            // refundAmount 传 null
            RefundVO result = refundService.applyRefund(USER_ID, buildApplyDTO(null));

            assertThat(result).isNotNull();
            assertThat(result.getRefundAmount()).isEqualByComparingTo(new BigDecimal("100.00")); // 默认全额
            verify(refundOrderMapper).insert(any(RefundOrder.class));
            verify(orderLogMapper).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("正常申请退款：退款单创建成功 + 订单状态变更为退款中")
        void applyRefund_normal_success() {
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(buildOrderItem(new BigDecimal("100.00")));
            when(orderNoGenerator.generateRefundNo()).thenReturn(REFUND_NO);
            when(refundOrderMapper.insert(any(RefundOrder.class))).thenAnswer(invocation -> {
                RefundOrder r = invocation.getArgument(0);
                r.setId(REFUND_ID);
                return 1;
            });
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);

            RefundVO result = refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00")));

            assertThat(result).isNotNull();
            assertThat(result.getRefundNo()).isEqualTo(REFUND_NO);
            assertThat(result.getRefundAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(result.getStatus()).isEqualTo(RefundStatusEnum.PENDING.getCode());
            assertThat(result.getStatusDesc()).isEqualTo("待审核");
            verify(refundOrderMapper).insert(any(RefundOrder.class));
            verify(orderInfoMapper).update(any(), any(Wrapper.class)); // 乐观锁更新订单状态
            verify(orderLogMapper).insert(any(OrderLog.class)); // 记录状态日志
        }

        @Test
        @DisplayName("乐观锁更新失败（订单状态已被并发修改）：抛 ORDER_STATUS_ERROR 异常")
        void applyRefund_optimisticLockFail_throwStatusError() {
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(buildOrderItem(new BigDecimal("100.00")));
            when(orderNoGenerator.generateRefundNo()).thenReturn(REFUND_NO);
            when(refundOrderMapper.insert(any(RefundOrder.class))).thenReturn(1);
            // 乐观锁更新返回 0，说明订单状态已被其他请求改了
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> refundService.applyRefund(USER_ID, buildApplyDTO(new BigDecimal("50.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单状态已变更");
        }
    }

    // ==================== 2. auditRefund 审核退款测试 ====================

    @Nested
    @DisplayName("auditRefund - 商家审核退款")
    class AuditRefundTest {

        @Test
        @DisplayName("退款单不存在：抛 PARAM_ERROR 异常")
        void auditRefund_refundNotFound_throwParamError() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(null);

            assertThatThrownBy(() -> refundService.auditRefund(buildAuditDTO(1, "同意退款")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("退款单不存在");

            verify(orderInfoMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("关联订单不存在：抛 ORDER_NOT_FOUND 异常")
        void auditRefund_orderNotFound_throwOrderNotFound() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(buildPendingRefundOrder());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> refundService.auditRefund(buildAuditDTO(1, "同意退款")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("订单不存在");
        }

        @Test
        @DisplayName("审核状态参数错误（既不是1也不是2）：抛 PARAM_ERROR 异常")
        void auditRefund_invalidStatus_throwParamError() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(buildPendingRefundOrder());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));

            // status=99 既不是同意也不是拒绝
            assertThatThrownBy(() -> refundService.auditRefund(buildAuditDTO(99, "无效操作")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("审核状态参数错误");
        }

        @Test
        @DisplayName("同意退款：退款单状态 PENDING→APPROVED→REFUNDED，订单状态→REFUNDED，回滚库存")
        void auditRefund_approve_success() {
            RefundOrder refundOrder = buildPendingRefundOrder();
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(refundOrder);
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            // 退款单乐观锁更新成功
            when(refundOrderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            // 订单状态乐观锁更新成功
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            // 订单明细存在（用于回滚库存）
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(buildOrderItem(new BigDecimal("100.00")));

            refundService.auditRefund(buildAuditDTO(RefundStatusEnum.APPROVED.getCode(), "同意退款"));

            // 验证：退款单更新了2次（第一次 PENDING→APPROVED，第二次 →REFUNDED）
            verify(refundOrderMapper, times(2)).update(any(), any(Wrapper.class));
            // 验证：订单状态更新1次（REFUNDING→REFUNDED）
            verify(orderInfoMapper).update(any(), any(Wrapper.class));
            // 验证：回滚库存被调用
            verify(productFeignClient).addStock(eq(SKU_ID), eq(2));
            // 验证：记录了状态日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("同意退款：退款单乐观锁更新失败（已审核过）：抛 ORDER_STATUS_ERROR")
        void auditRefund_approve_refundLockFail_throwStatusError() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(buildPendingRefundOrder());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            // 退款单第一次更新返回0（说明状态已被并发修改）
            when(refundOrderMapper.update(any(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> refundService.auditRefund(buildAuditDTO(RefundStatusEnum.APPROVED.getCode(), "同意")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("退款单状态已变更");

            // 验证：后续步骤未执行
            verify(orderInfoMapper, never()).update(any(), any(Wrapper.class));
            verify(productFeignClient, never()).addStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("同意退款：订单状态乐观锁更新失败：抛 ORDER_STATUS_ERROR")
        void auditRefund_approve_orderLockFail_throwStatusError() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(buildPendingRefundOrder());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            // 退款单更新成功，订单状态更新失败
            when(refundOrderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> refundService.auditRefund(buildAuditDTO(RefundStatusEnum.APPROVED.getCode(), "同意")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("更新订单状态失败");

            // 验证：库存未回滚（因为订单状态更新失败了）
            verify(productFeignClient, never()).addStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("同意退款：库存回滚失败（Feign异常）不影响主流程（最终一致性兜底）")
        void auditRefund_approve_stockRollbackFail_notAffectMain() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(buildPendingRefundOrder());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(refundOrderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            when(orderItemMapper.selectById(ORDER_ITEM_ID)).thenReturn(buildOrderItem(new BigDecimal("100.00")));
            // 库存回滚抛异常
            org.mockito.Mockito.doThrow(new RuntimeException("商品服务不可用"))
                    .when(productFeignClient).addStock(anyLong(), anyInt());

            // 库存回滚失败不应该导致整个审核失败（try-catch吞异常）
            refundService.auditRefund(buildAuditDTO(RefundStatusEnum.APPROVED.getCode(), "同意退款"));

            // 验证：退款单仍然更新为 REFUNDED（第二次update仍执行）
            verify(refundOrderMapper, times(2)).update(any(), any(Wrapper.class));
            verify(orderLogMapper).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("拒绝退款：退款单 PENDING→REJECTED，订单状态恢复为 PAID")
        void auditRefund_reject_success() {
            RefundOrder refundOrder = buildPendingRefundOrder();
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(refundOrder);
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(refundOrderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
            when(orderInfoMapper.update(any(), any(Wrapper.class))).thenReturn(1);

            refundService.auditRefund(buildAuditDTO(RefundStatusEnum.REJECTED.getCode(), "不符合退款条件"));

            // 验证：退款单更新1次（PENDING→REJECTED）
            verify(refundOrderMapper).update(any(), any(Wrapper.class));
            // 验证：订单状态恢复为 PAID
            verify(orderInfoMapper).update(any(), any(Wrapper.class));
            // 验证：不回滚库存
            verify(productFeignClient, never()).addStock(anyLong(), anyInt());
            // 验证：记录状态日志
            verify(orderLogMapper).insert(any(OrderLog.class));
        }

        @Test
        @DisplayName("拒绝退款：退款单乐观锁更新失败：抛 ORDER_STATUS_ERROR")
        void auditRefund_reject_lockFail_throwStatusError() {
            when(refundOrderMapper.selectById(REFUND_ID)).thenReturn(buildPendingRefundOrder());
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(buildPaidOrder(USER_ID));
            when(refundOrderMapper.update(any(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> refundService.auditRefund(buildAuditDTO(RefundStatusEnum.REJECTED.getCode(), "拒绝")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("退款单状态已变更");

            verify(orderInfoMapper, never()).update(any(), any(Wrapper.class));
        }
    }

    // ==================== 3. getRefundList 查询退款列表测试 ====================

    @Nested
    @DisplayName("getRefundList - 查询订单退款列表")
    class GetRefundListTest {

        @Test
        @DisplayName("订单没有退款记录：返回空列表")
        void getRefundList_empty_returnEmptyList() {
            when(refundOrderMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

            List<RefundVO> result = refundService.getRefundList(ORDER_ID);

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("订单有多条退款记录：返回VO列表（验证状态描述转换）")
        void getRefundList_hasRecords_returnVoList() {
            RefundOrder r1 = buildPendingRefundOrder();
            r1.setStatus(RefundStatusEnum.REFUNDED.getCode()); // 已退款

            RefundOrder r2 = buildPendingRefundOrder();
            r2.setId(7002L);
            r2.setRefundNo("RF9999888877776666");
            r2.setStatus(RefundStatusEnum.PENDING.getCode()); // 待审核

            when(refundOrderMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(r1, r2));

            List<RefundVO> result = refundService.getRefundList(ORDER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatusDesc()).isEqualTo("已退款");
            assertThat(result.get(1).getStatusDesc()).isEqualTo("待审核");
        }
    }
}
