package com.shop.payment.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.result.ErrorCode;
import com.shop.model.payment.dto.PayCallbackDTO;
import com.shop.model.payment.dto.PayCreateDTO;
import com.shop.model.payment.vo.PaymentVO;
import com.shop.model.payment.vo.PayResultVO;
import com.shop.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentController 切片测试（B-S-04）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos等中间件。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. 参数校验是否生效（@Valid校验不通过时返回400）
 * 3. 返回格式是否正确（统一Result格式，code=200表示成功）
 * 4. Service调用是否正确（参数传递、调用次数）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 * <p>
 * 注意：
 * 1. PaymentController 类上没有 @Validated 注解，所以 refund 接口的 @RequestParam
 *    上的 @NotNull/@DecimalMin 校验不会触发。只测 @RequestBody 参数的校验。
 * 2. @Idempotent 注解在切片测试中AOP不生效，所以幂等性不测，只测Controller逻辑。
 * 3. createPayment/mockPay/getPaymentByOrderNo/refund 用 StpUtil.getLoginIdAsLong() 获取用户ID。
 * 4. handleCallback 不需要登录（第三方支付平台回调）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentController 切片测试")
class PaymentControllerTest {

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 假装支付服务 */
    @Mock
    private PaymentService paymentService;

    /** Sa-Token静态方法mock（模拟登录状态） */
    private MockedStatic<StpUtil> stpUtilMock;

    /** 测试中模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 PaymentController 实例，注入 mock 依赖
        PaymentController controller = new PaymentController(paymentService);

        // 2. 用 Standalone 方式构建 MockMvc，手动注册全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 3. mock StpUtil.getLoginIdAsLong()，让Controller认为用户已登录
        stpUtilMock = org.mockito.Mockito.mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(MOCK_USER_ID);
    }

    @AfterEach
    void tearDown() {
        // 每个测试结束后关闭静态mock，避免影响其他测试
        stpUtilMock.close();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一个合法的创建支付DTO
     *
     * @return 构造好的 PayCreateDTO
     */
    private PayCreateDTO buildValidCreateDTO() {
        PayCreateDTO dto = new PayCreateDTO();
        dto.setOrderNo("ORD20240101001");
        dto.setAmount(new BigDecimal("199.00"));
        dto.setPayType(1); // 1=模拟支付
        return dto;
    }

    /**
     * 构造一个合法的支付回调DTO
     *
     * @return 构造好的 PayCallbackDTO
     */
    private PayCallbackDTO buildValidCallbackDTO() {
        PayCallbackDTO dto = new PayCallbackDTO();
        dto.setPaymentNo("PAY20240101001");
        dto.setOutTradeNo("ALI20240101001");
        dto.setChannel("alipay");
        dto.setCallbackData("{\"trade_status\":\"TRADE_SUCCESS\"}");
        return dto;
    }

    /**
     * 构造一个支付信息VO（用于Service返回值）
     *
     * @param id        支付ID
     * @param paymentNo 支付单号
     * @param payStatus 支付状态：0待支付 2已支付
     * @return 构造好的 PaymentVO
     */
    private PaymentVO buildPaymentVO(Long id, String paymentNo, Integer payStatus) {
        PaymentVO vo = new PaymentVO();
        vo.setId(id);
        vo.setPaymentNo(paymentNo);
        vo.setOrderNo("ORD20240101001");
        vo.setUserId(MOCK_USER_ID);
        vo.setAmount(new BigDecimal("199.00"));
        vo.setPayType(1);
        vo.setPayStatus(payStatus);
        vo.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return vo;
    }

    /**
     * 构造一个支付结果VO（用于Service返回值）
     *
     * @param paymentNo 支付单号
     * @param success   是否成功
     * @param message   结果描述
     * @return 构造好的 PayResultVO
     */
    private PayResultVO buildResultVO(String paymentNo, Boolean success, String message) {
        PayResultVO vo = new PayResultVO();
        vo.setPaymentNo(paymentNo);
        vo.setSuccess(success);
        vo.setMessage(message);
        return vo;
    }

    // ==================== 创建支付 ====================

    @Nested
    @DisplayName("创建支付 POST /payment/create")
    class CreatePaymentTest {

        @Test
        @DisplayName("正常创建支付 → 返回200和支付信息")
        void createPayment_success_returnsPaymentInfo() throws Exception {
            PayCreateDTO dto = buildValidCreateDTO();
            PaymentVO paymentVO = buildPaymentVO(3001L, "PAY20240101001", 0);

            when(paymentService.createPayment(eq(MOCK_USER_ID), any(PayCreateDTO.class)))
                    .thenReturn(paymentVO);

            mockMvc.perform(post("/payment/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(3001))
                    .andExpect(jsonPath("$.data.paymentNo").value("PAY20240101001"))
                    .andExpect(jsonPath("$.data.payStatus").value(0))
                    .andExpect(jsonPath("$.data.amount").value(199.00));

            verify(paymentService).createPayment(eq(MOCK_USER_ID), any(PayCreateDTO.class));
        }

        @Test
        @DisplayName("订单号为空 → 参数校验失败返回400")
        void createPayment_orderNoEmpty_returns400() throws Exception {
            PayCreateDTO dto = buildValidCreateDTO();
            dto.setOrderNo(""); // 订单号为空

            mockMvc.perform(post("/payment/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("支付金额为空 → 参数校验失败返回400")
        void createPayment_amountNull_returns400() throws Exception {
            PayCreateDTO dto = buildValidCreateDTO();
            dto.setAmount(null); // 支付金额为空

            mockMvc.perform(post("/payment/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("订单已支付 → Service抛业务异常 → 返回业务错误码")
        void createPayment_orderAlreadyPaid_throwsBusinessException() throws Exception {
            PayCreateDTO dto = buildValidCreateDTO();

            // mock Service抛出"订单已支付"业务异常
            when(paymentService.createPayment(anyLong(), any(PayCreateDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID));

            mockMvc.perform(post("/payment/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PAYMENT_ALREADY_PAID.getCode()));
        }
    }

    // ==================== 模拟支付 ====================

    @Nested
    @DisplayName("模拟支付 POST /payment/mock-pay/{paymentId}")
    class MockPayTest {

        @Test
        @DisplayName("正常模拟支付 → 返回200和支付结果")
        void mockPay_success_returnsPayResult() throws Exception {
            PayResultVO resultVO = buildResultVO("PAY20240101001", true, "支付成功");

            when(paymentService.mockPay(eq(MOCK_USER_ID), eq(3001L))).thenReturn(resultVO);

            mockMvc.perform(post("/payment/mock-pay/{paymentId}", 3001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.paymentNo").value("PAY20240101001"))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("支付成功"));

            verify(paymentService).mockPay(eq(MOCK_USER_ID), eq(3001L));
        }

        @Test
        @DisplayName("非自己的支付单 → Service抛业务异常 → 返回业务错误码")
        void mockPay_notYours_throwsBusinessException() throws Exception {
            // mock Service抛出"无权操作"业务异常
            when(paymentService.mockPay(anyLong(), eq(9999L)))
                    .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权操作此支付单"));

            mockMvc.perform(post("/payment/mock-pay/{paymentId}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
        }
    }

    // ==================== 支付回调 ====================

    @Nested
    @DisplayName("支付回调 POST /payment/callback")
    class HandleCallbackTest {

        @Test
        @DisplayName("正常处理回调 → 返回200和支付结果")
        void handleCallback_success_returnsPayResult() throws Exception {
            PayCallbackDTO dto = buildValidCallbackDTO();
            PayResultVO resultVO = buildResultVO("PAY20240101001", true, "回调处理成功");

            when(paymentService.handleCallback(any(PayCallbackDTO.class))).thenReturn(resultVO);

            mockMvc.perform(post("/payment/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.paymentNo").value("PAY20240101001"))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("回调处理成功"));

            verify(paymentService).handleCallback(any(PayCallbackDTO.class));
        }

        @Test
        @DisplayName("支付单号为空 → 参数校验失败返回400")
        void handleCallback_paymentNoEmpty_returns400() throws Exception {
            PayCallbackDTO dto = buildValidCallbackDTO();
            dto.setPaymentNo(""); // 支付单号为空

            mockMvc.perform(post("/payment/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("支付单不存在 → Service抛业务异常 → 返回业务错误码")
        void handleCallback_paymentNotFound_throwsBusinessException() throws Exception {
            PayCallbackDTO dto = buildValidCallbackDTO();

            when(paymentService.handleCallback(any(PayCallbackDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            mockMvc.perform(post("/payment/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PAYMENT_NOT_FOUND.getCode()));
        }
    }

    // ==================== 查询支付信息 ====================

    @Nested
    @DisplayName("查询支付信息 GET /payment/order/{orderNo}")
    class GetPaymentByOrderNoTest {

        @Test
        @DisplayName("正常查询支付信息 → 返回200和支付详情")
        void getPaymentByOrderNo_success() throws Exception {
            PaymentVO paymentVO = buildPaymentVO(3001L, "PAY20240101001", 2); // 2=已支付

            when(paymentService.getPaymentByOrderNo(eq(MOCK_USER_ID), eq("ORD20240101001")))
                    .thenReturn(paymentVO);

            mockMvc.perform(get("/payment/order/{orderNo}", "ORD20240101001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(3001))
                    .andExpect(jsonPath("$.data.paymentNo").value("PAY20240101001"))
                    .andExpect(jsonPath("$.data.payStatus").value(2));

            verify(paymentService).getPaymentByOrderNo(eq(MOCK_USER_ID), eq("ORD20240101001"));
        }

        @Test
        @DisplayName("支付单不存在 → Service抛业务异常 → 返回业务错误码")
        void getPaymentByOrderNo_notFound_throwsBusinessException() throws Exception {
            when(paymentService.getPaymentByOrderNo(anyLong(), eq("ORD9999")))
                    .thenThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            mockMvc.perform(get("/payment/order/{orderNo}", "ORD9999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PAYMENT_NOT_FOUND.getCode()));
        }
    }

    // ==================== 退款 ====================

    @Nested
    @DisplayName("退款 POST /payment/refund")
    class RefundTest {

        @Test
        @DisplayName("正常退款 → 返回200")
        void refund_success() throws Exception {
            mockMvc.perform(post("/payment/refund")
                            .param("paymentId", "3001")
                            .param("amount", "199.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退款成功"));

            verify(paymentService).refund(eq(MOCK_USER_ID), eq(3001L), eq(new BigDecimal("199.00")));
        }

        @Test
        @DisplayName("退款金额超限 → Service抛业务异常 → 返回业务错误码")
        void refund_amountExceed_throwsBusinessException() throws Exception {
            // mock Service抛出"退款金额超出限制"业务异常
            doThrow(new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEED))
                    .when(paymentService).refund(anyLong(), eq(3001L), any(BigDecimal.class));

            mockMvc.perform(post("/payment/refund")
                            .param("paymentId", "3001")
                            .param("amount", "999.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.REFUND_AMOUNT_EXCEED.getCode()));
        }

        @Test
        @DisplayName("非自己的支付单 → Service抛业务异常 → 返回业务错误码")
        void refund_notYours_throwsBusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权操作此支付单"))
                    .when(paymentService).refund(anyLong(), eq(9999L), any(BigDecimal.class));

            mockMvc.perform(post("/payment/refund")
                            .param("paymentId", "9999")
                            .param("amount", "199.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
        }
    }
}
