package com.shop.payment.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.payment.dto.PayCallbackDTO;
import com.shop.model.payment.dto.PayCreateDTO;
import com.shop.model.payment.entity.PaymentCallback;
import com.shop.model.payment.entity.PaymentInfo;
import com.shop.model.payment.enums.PayStatusEnum;
import com.shop.model.payment.vo.PaymentVO;
import com.shop.model.payment.vo.PayResultVO;
import com.shop.payment.feign.OrderFeignClient;
import com.shop.payment.mapper.PaymentCallbackMapper;
import com.shop.payment.mapper.PaymentInfoMapper;
import com.shop.payment.mq.PaymentMQProducer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付服务实现类（PaymentServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证支付服务的5个核心方法能不能正常工作：
 * 创建支付、模拟支付、处理回调、查询支付、退款。
 * 简单理解：我们把真正访问数据库的 Mapper、远程调用订单服务的 FeignClient、
 * 发消息的 MQProducer、分布式锁的 Redis 全部"假装"一下（Mock），
 * 这样测试就不需要真的连数据库、网络和消息队列，跑得又快又稳定。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * </p>
 * <p>
 * 测试覆盖的5个方法：createPayment、mockPay、handleCallback、getPaymentByOrderNo、refund
 * </p>
 */
@DisplayName("支付服务 PaymentServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    /** 假装数据库操作的 Mapper（支付记录表），不真的连数据库 */
    @Mock
    private PaymentInfoMapper paymentInfoMapper;

    /** 假装数据库操作的 Mapper（回调日志表），不真的连数据库 */
    @Mock
    private PaymentCallbackMapper paymentCallbackMapper;

    /** 假装发消息的 MQ 生产者，不真的发消息到 RocketMQ */
    @Mock
    private PaymentMQProducer paymentMQProducer;

    /** 假装远程调用订单服务的 Feign 客户端，不真的发 HTTP 请求 */
    @Mock
    private OrderFeignClient orderFeignClient;

    /** 假装 Redis 模板，不真的连 Redis（handleCallback 用它做分布式锁） */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 假装 Redis 的 ValueOperations，配合 stringRedisTemplate 做分布式锁 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 被测试的支付服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private PaymentServiceImpl paymentService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long USER_ID = 1001L;              // 当前用户ID
    private static final Long OTHER_USER_ID = 1002L;        // 另一个用户ID（测试越权用）
    private static final Long PAYMENT_ID = 5001L;           // 支付记录ID
    private static final String ORDER_NO = "ORDER20240101001"; // 订单号
    private static final String PAYMENT_NO = "PAY1234567890123456789"; // 支付单号

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：PaymentServiceImpl 大量使用了 LambdaQueryWrapper 和 LambdaUpdateWrapper，
     * 比如 .eq(PaymentInfo::getOrderNo, ...)。这些代码会让 MyBatis-Plus 去查
     * "orderNo 字段对应数据库哪一列"。正常启动 Spring 时框架会自动做这件事，
     * 但单元测试没有 Spring 环境，所以需要我们手动告诉 MyBatis-Plus：
     * PaymentInfo 这个实体有哪些字段、对应哪些列。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PaymentInfo.class
        );
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个创建支付请求DTO
     * 小白理解：用户点"去支付"时前端传过来的参数
     *
     * @param orderNo 订单号
     * @param amount  支付金额
     * @param payType 支付方式：1模拟 2微信 3支付宝
     * @return 构造好的PayCreateDTO
     */
    private PayCreateDTO buildPayCreateDTO(String orderNo, BigDecimal amount, Integer payType) {
        PayCreateDTO dto = new PayCreateDTO();
        dto.setOrderNo(orderNo);
        dto.setAmount(amount);
        dto.setPayType(payType);
        return dto;
    }

    /**
     * 构造一个支付记录实体
     * 小白理解：数据库 payment_info 表里的一条记录
     *
     * @param id         支付记录ID
     * @param userId     用户ID
     * @param orderNo    订单号
     * @param paymentNo  支付单号
     * @param amount     支付金额
     * @param payType    支付方式
     * @param payStatus  支付状态（见 PayStatusEnum）
     * @return 构造好的PaymentInfo
     */
    private PaymentInfo buildPaymentInfo(Long id, Long userId, String orderNo, String paymentNo,
                                         BigDecimal amount, Integer payType, Integer payStatus) {
        PaymentInfo info = new PaymentInfo();
        info.setId(id);
        info.setUserId(userId);
        info.setOrderNo(orderNo);
        info.setPaymentNo(paymentNo);
        info.setAmount(amount);
        info.setPayType(payType);
        info.setPayStatus(payStatus);
        return info;
    }

    /**
     * 构造一个支付回调DTO
     * 小白理解：第三方支付平台通知我们支付结果时传过来的参数
     *
     * @param paymentNo   支付单号
     * @param outTradeNo  第三方交易号
     * @param channel     回调渠道（wechat/alipay/mock）
     * @param callbackData 回调数据（JSON格式）
     * @return 构造好的PayCallbackDTO
     */
    private PayCallbackDTO buildPayCallbackDTO(String paymentNo, String outTradeNo,
                                                String channel, String callbackData) {
        PayCallbackDTO dto = new PayCallbackDTO();
        dto.setPaymentNo(paymentNo);
        dto.setOutTradeNo(outTradeNo);
        dto.setChannel(channel);
        dto.setCallbackData(callbackData);
        return dto;
    }

    /**
     * 模拟 Redis 分布式锁获取成功
     * <p>
     * 小白理解：handleCallback 方法一开始会尝试获取 Redis 分布式锁防止并发回调。
     * 我们让假的 Redis 返回"锁获取成功"（true），这样代码才能继续往下走处理回调。
     * </p>
     */
    private void mockRedisLockSuccess() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
    }

    // ==================== 1. createPayment 创建支付记录 ====================

    @Nested
    @DisplayName("createPayment 创建支付记录")
    class CreatePaymentTest {

        @Test
        @DisplayName("订单不存在 → 反查订单金额失败，抛出BusinessException")
        void createPayment_orderNotExists_throwsException() {
            // 场景：订单服务返回失败（订单不存在），不能创建支付记录
            PayCreateDTO dto = buildPayCreateDTO(ORDER_NO, new BigDecimal("100.00"), 1);

            // 模拟：没有已存在的待支付记录 + 订单服务返回失败
            when(paymentInfoMapper.selectOne(any())).thenReturn(null);
            when(orderFeignClient.getPayAmount(anyString(), anyLong()))
                    .thenReturn(Result.fail(ErrorCode.ORDER_NOT_FOUND));

            // 验证：抛出业务异常，错误码是 PAYMENT_FAIL
            assertThatThrownBy(() -> paymentService.createPayment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_FAIL.getCode());

            // 验证：没有创建支付记录
            verify(paymentInfoMapper, never()).insert(any(PaymentInfo.class));
        }

        @Test
        @DisplayName("订单已支付/不可支付 → 反查金额为null，抛出BusinessException")
        void createPayment_orderAlreadyPaid_throwsException() {
            // 场景：订单已支付，订单服务虽然返回成功但金额为null（已支付订单拿不到金额）
            // 小白理解：已支付的订单不能再付一次钱，所以订单服务返回的金额是null，
            // 支付服务检测到金额为null就拒绝创建支付记录
            PayCreateDTO dto = buildPayCreateDTO(ORDER_NO, new BigDecimal("100.00"), 1);

            // 模拟：没有已存在的待支付记录 + 订单服务返回成功但金额为null
            when(paymentInfoMapper.selectOne(any())).thenReturn(null);
            when(orderFeignClient.getPayAmount(anyString(), anyLong()))
                    .thenReturn(Result.success(null));

            // 验证：抛出业务异常，错误码是 PAYMENT_FAIL
            assertThatThrownBy(() -> paymentService.createPayment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_FAIL.getCode());

            // 验证：没有创建支付记录
            verify(paymentInfoMapper, never()).insert(any(PaymentInfo.class));
        }

        @Test
        @DisplayName("正常创建支付单 → 调用insert并返回PaymentVO")
        void createPayment_normal_insertAndReturnVO() {
            // 场景：订单存在且未支付，反查金额成功，正常创建支付记录
            PayCreateDTO dto = buildPayCreateDTO(ORDER_NO, new BigDecimal("100.00"), 1);
            BigDecimal realAmount = new BigDecimal("100.00");

            // 模拟：没有已存在的待支付记录 + 订单服务返回真实金额
            when(paymentInfoMapper.selectOne(any())).thenReturn(null);
            when(orderFeignClient.getPayAmount(anyString(), anyLong()))
                    .thenReturn(Result.success(realAmount));

            // 执行创建支付
            PaymentVO vo = paymentService.createPayment(USER_ID, dto);

            // 验证：调用了insert新增支付记录
            verify(paymentInfoMapper).insert(any(PaymentInfo.class));
            // 验证：返回的VO字段正确
            assertThat(vo).isNotNull();
            assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(vo.getUserId()).isEqualTo(USER_ID);
            assertThat(vo.getAmount()).isEqualByComparingTo(realAmount);
            assertThat(vo.getPayType()).isEqualTo(1);
            assertThat(vo.getPayStatus()).isEqualTo(PayStatusEnum.WAIT.getCode());
            // 验证：支付单号以PAY开头（由PaymentNoGenerator生成）
            assertThat(vo.getPaymentNo()).startsWith("PAY");
        }
    }

    // ==================== 2. mockPay 模拟支付 ====================

    @Nested
    @DisplayName("mockPay 模拟支付")
    class MockPayTest {

        @Test
        @DisplayName("支付单不存在 → 抛出BusinessException")
        void mockPay_paymentNotFound_throwsException() {
            // 场景：传了一个不存在的支付记录ID
            when(paymentInfoMapper.selectById(PAYMENT_ID)).thenReturn(null);

            // 验证：抛出业务异常
            assertThatThrownBy(() -> paymentService.mockPay(USER_ID, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_FAIL.getCode());

            // 验证：没有更新支付状态，没有发MQ消息
            verify(paymentInfoMapper, never()).update(any(), any());
            verify(paymentMQProducer, never()).sendPaySuccessMessage(anyString(), anyString());
        }

        @Test
        @DisplayName("支付单已支付 → 状态机校验阻止重复支付，抛出异常")
        void mockPay_alreadyPaid_throwsException() {
            // 场景：支付单已经是"已支付"状态，不能再付一次
            // 小白理解：状态机校验会发现"已支付→已支付"是非法转换，抛出异常阻止重复支付
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 1, PayStatusEnum.PAID.getCode());
            when(paymentInfoMapper.selectById(PAYMENT_ID)).thenReturn(paymentInfo);

            // 验证：抛出IllegalArgumentException（状态机校验失败）
            assertThatThrownBy(() -> paymentService.mockPay(USER_ID, PAYMENT_ID))
                    .isInstanceOf(IllegalArgumentException.class);

            // 验证：没有更新支付状态，没有发MQ消息
            verify(paymentInfoMapper, never()).update(any(), any());
            verify(paymentMQProducer, never()).sendPaySuccessMessage(anyString(), anyString());
        }

        @Test
        @DisplayName("正常支付 → 更新状态为已支付、发送MQ消息、返回PayResultVO")
        void mockPay_normal_updateAndSendMQ() {
            // 场景：支付单处于"支付中"状态，用户点支付，正常改为"已支付"
            // 小白理解：状态机要求"支付中→已支付"是合法转换，
            // 所以这里用PAYING状态才能通过校验走到更新逻辑
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 1, PayStatusEnum.PAYING.getCode());
            when(paymentInfoMapper.selectById(PAYMENT_ID)).thenReturn(paymentInfo);
            // 模拟更新成功（返回1表示影响了1行）
            when(paymentInfoMapper.update(any(), any())).thenReturn(1);

            // 执行模拟支付
            PayResultVO result = paymentService.mockPay(USER_ID, PAYMENT_ID);

            // 验证：调用了update更新支付状态
            verify(paymentInfoMapper).update(any(), any());
            // 验证：记录了回调日志
            verify(paymentCallbackMapper).insert(any(PaymentCallback.class));
            // 验证：发送了MQ消息通知订单服务
            verify(paymentMQProducer).sendPaySuccessMessage(ORDER_NO, PAYMENT_NO);
            // 验证：返回结果正确
            assertThat(result).isNotNull();
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getPaymentNo()).isEqualTo(PAYMENT_NO);
            assertThat(result.getMessage()).isEqualTo("支付成功");
        }
    }

    // ==================== 3. handleCallback 处理支付回调 ====================

    @Nested
    @DisplayName("handleCallback 处理支付回调")
    class HandleCallbackTest {

        @Test
        @DisplayName("支付单不存在 → 抛出BusinessException")
        void handleCallback_paymentNotFound_throwsException() {
            // 场景：第三方回调通知一个不存在的支付单号
            PayCallbackDTO dto = buildPayCallbackDTO(PAYMENT_NO, "WX_123", "wechat", "");

            // 模拟：Redis锁获取成功 + 数据库查不到该支付单
            mockRedisLockSuccess();
            when(paymentInfoMapper.selectOne(any())).thenReturn(null);

            // 验证：抛出业务异常，错误码是 PAYMENT_CALLBACK_ERROR
            assertThatThrownBy(() -> paymentService.handleCallback(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_CALLBACK_ERROR.getCode());

            // 验证：没有更新支付状态，没有发MQ消息
            verify(paymentInfoMapper, never()).update(any(), any());
            verify(paymentMQProducer, never()).sendPaySuccessMessage(anyString(), anyString());
        }

        @Test
        @DisplayName("回调金额不匹配 → 抛出BusinessException")
        void handleCallback_amountMismatch_throwsException() {
            // 场景：支付记录金额是100元，但回调数据里金额是50元，金额对不上
            // 小白理解：这可能是黑客篡改了回调金额，必须拦截
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 1, PayStatusEnum.WAIT.getCode());
            // 回调数据里的金额是50（和订单金额100不一致）
            PayCallbackDTO dto = buildPayCallbackDTO(PAYMENT_NO, "WX_123", "wechat",
                    "{\"amount\":50}");

            // 模拟：Redis锁获取成功 + 查到支付记录（待支付状态）
            mockRedisLockSuccess();
            when(paymentInfoMapper.selectOne(any())).thenReturn(paymentInfo);

            // 验证：抛出业务异常，错误码是 PAYMENT_CALLBACK_ERROR
            assertThatThrownBy(() -> paymentService.handleCallback(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_CALLBACK_ERROR.getCode());

            // 验证：没有更新支付状态，没有发MQ消息
            verify(paymentInfoMapper, never()).update(any(), any());
            verify(paymentMQProducer, never()).sendPaySuccessMessage(anyString(), anyString());
        }

        @Test
        @DisplayName("正常回调 → 更新状态为已支付、发送MQ消息通知订单服务")
        void handleCallback_normal_updateAndNotifyOrder() {
            // 场景：第三方支付平台通知支付成功，金额校验通过，正常更新状态
            // 小白理解：状态机要求"支付中→已支付"是合法转换，
            // 所以这里用PAYING状态才能通过校验走到更新逻辑
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 2, PayStatusEnum.PAYING.getCode());
            PayCallbackDTO dto = buildPayCallbackDTO(PAYMENT_NO, "WX_123", "wechat", "");

            // 模拟：Redis锁获取成功 + 查到支付记录 + 更新成功
            mockRedisLockSuccess();
            when(paymentInfoMapper.selectOne(any())).thenReturn(paymentInfo);
            when(paymentInfoMapper.update(any(), any())).thenReturn(1);

            // 执行回调处理
            PayResultVO result = paymentService.handleCallback(dto);

            // 验证：调用了update更新支付状态
            verify(paymentInfoMapper).update(any(), any());
            // 验证：记录了回调日志
            verify(paymentCallbackMapper).insert(any(PaymentCallback.class));
            // 验证：发送了MQ消息通知订单服务
            verify(paymentMQProducer).sendPaySuccessMessage(ORDER_NO, PAYMENT_NO);
            // 验证：返回结果正确
            assertThat(result).isNotNull();
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getPaymentNo()).isEqualTo(PAYMENT_NO);
            assertThat(result.getMessage()).isEqualTo("支付成功");
        }
    }

    // ==================== 4. getPaymentByOrderNo 根据订单号查询支付 ====================

    @Nested
    @DisplayName("getPaymentByOrderNo 根据订单号查询支付信息")
    class GetPaymentByOrderNoTest {

        @Test
        @DisplayName("支付记录不存在 → 抛出BusinessException")
        void getPaymentByOrderNo_notFound_throwsException() {
            // 场景：根据订单号查不到支付记录
            when(paymentInfoMapper.selectOne(any())).thenReturn(null);

            // 验证：抛出业务异常，错误码是 NOT_FOUND
            assertThatThrownBy(() -> paymentService.getPaymentByOrderNo(USER_ID, ORDER_NO))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常查询 → 返回PaymentVO")
        void getPaymentByOrderNo_normal_returnVO() {
            // 场景：根据订单号查到支付记录，正常返回
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 1, PayStatusEnum.PAID.getCode());
            when(paymentInfoMapper.selectOne(any())).thenReturn(paymentInfo);

            // 执行查询
            PaymentVO vo = paymentService.getPaymentByOrderNo(USER_ID, ORDER_NO);

            // 验证：返回的VO字段正确
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(PAYMENT_ID);
            assertThat(vo.getPaymentNo()).isEqualTo(PAYMENT_NO);
            assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(vo.getUserId()).isEqualTo(USER_ID);
            assertThat(vo.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(vo.getPayStatus()).isEqualTo(PayStatusEnum.PAID.getCode());
        }
    }

    // ==================== 5. refund 退款 ====================

    @Nested
    @DisplayName("refund 退款处理")
    class RefundTest {

        @Test
        @DisplayName("支付单不存在 → 抛出BusinessException")
        void refund_paymentNotFound_throwsException() {
            // 场景：退款时传了一个不存在的支付记录ID
            when(paymentInfoMapper.selectById(PAYMENT_ID)).thenReturn(null);

            // 验证：抛出业务异常
            assertThatThrownBy(() -> paymentService.refund(USER_ID, PAYMENT_ID, new BigDecimal("100.00")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_FAIL.getCode());

            // 验证：没有更新支付状态，没有发MQ消息
            verify(paymentInfoMapper, never()).update(any(), any());
            verify(paymentMQProducer, never()).sendRefundSuccessMessage(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("退款金额超过支付金额 → 抛出BusinessException")
        void refund_amountExceed_throwsException() {
            // 场景：支付了100元，但退款金额传了200元，超过限制了
            // 小白理解：退款金额不能超过实付金额，否则商家亏钱
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 1, PayStatusEnum.PAID.getCode());
            when(paymentInfoMapper.selectById(PAYMENT_ID)).thenReturn(paymentInfo);

            // 验证：抛出业务异常，错误码是 PAYMENT_FAIL
            assertThatThrownBy(() -> paymentService.refund(USER_ID, PAYMENT_ID, new BigDecimal("200.00")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PAYMENT_FAIL.getCode());

            // 验证：没有更新支付状态，没有发MQ消息
            verify(paymentInfoMapper, never()).update(any(), any());
            verify(paymentMQProducer, never()).sendRefundSuccessMessage(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("正常退款 → 更新状态为已退款、发送MQ消息通知订单服务")
        void refund_normal_updateAndNotifyOrder() {
            // 场景：支付了100元，退款100元，正常退款成功
            // 小白理解：状态机要求"已支付→退款中"是合法转换，
            // 所以支付单必须是PAID状态才能退款
            PaymentInfo paymentInfo = buildPaymentInfo(PAYMENT_ID, USER_ID, ORDER_NO, PAYMENT_NO,
                    new BigDecimal("100.00"), 1, PayStatusEnum.PAID.getCode());
            when(paymentInfoMapper.selectById(PAYMENT_ID)).thenReturn(paymentInfo);
            // 模拟更新成功
            when(paymentInfoMapper.update(any(), any())).thenReturn(1);

            // 执行退款
            paymentService.refund(USER_ID, PAYMENT_ID, new BigDecimal("100.00"));

            // 验证：调用了update更新支付状态为已退款
            verify(paymentInfoMapper).update(any(), any());
            // 验证：发送了退款成功的MQ消息通知订单服务
            verify(paymentMQProducer).sendRefundSuccessMessage(ORDER_NO, PAYMENT_NO, new BigDecimal("100.00"));
        }
    }
}
