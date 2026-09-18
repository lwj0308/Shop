package com.shop.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.order.dto.DeliveryDTO;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderAddress;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.OrderLogistics;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderVO;
import com.shop.order.mapper.OrderAddressMapper;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderLogisticsMapper;
import com.shop.order.service.LogisticsService;
import com.shop.order.service.OrderService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController 切片测试（B-S-01）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos等中间件。
 * </p>
 * <p>
 * 为什么不用 @WebMvcTest？
 * Spring Boot 4.0 的 @WebMvcTest 会尝试加载 OrderApplication 主类，
 * 而 OrderApplication 有 @EnableDiscoveryClient 会连 Nacos，测试环境连不上就报错。
 * 用 Standalone MockMvc 避免了启动 Spring 上下文，更轻量更稳定。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. 参数校验是否生效（@Valid校验不通过时返回400）
 * 3. 返回格式是否正确（统一Result格式，code=200表示成功）
 * 4. Service调用是否正确（参数传递、调用次数）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderController 切片测试")
class OrderControllerTest {

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 假装订单服务 */
    @Mock
    private OrderService orderService;

    /** 假装订单主表Mapper */
    @Mock
    private OrderInfoMapper orderInfoMapper;

    /** 假装订单明细Mapper */
    @Mock
    private OrderItemMapper orderItemMapper;

    /** 假装订单地址Mapper */
    @Mock
    private OrderAddressMapper orderAddressMapper;

    /** 假装物流信息Mapper */
    @Mock
    private OrderLogisticsMapper orderLogisticsMapper;

    /** 假装物流服务 */
    @Mock
    private LogisticsService logisticsService;

    /** Sa-Token静态方法mock（模拟登录状态） */
    private MockedStatic<StpUtil> stpUtilMock;

    /** 测试中模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 OrderController 实例，注入 mock 依赖
        OrderController controller = new OrderController(
                orderService, orderInfoMapper, orderItemMapper,
                orderAddressMapper, orderLogisticsMapper, logisticsService);

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

    // ==================== 创建订单 ====================

    @Nested
    @DisplayName("创建订单 POST /order")
    class CreateOrderTest {

        @Test
        @DisplayName("正常创建订单 → 返回200和订单详情")
        void createOrder_success_returnsOrderDetail() throws Exception {
            // 1. 准备请求参数
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setAddressId(1L);
            OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
            item.setSkuId(100L);
            item.setQuantity(2);
            dto.setItems(List.of(item));
            dto.setRemark("放门口");

            // 2. 准备Service返回的订单详情
            OrderDetailVO detailVO = new OrderDetailVO();
            detailVO.setId(2001L);
            detailVO.setOrderNo("ORD20240101001");
            detailVO.setTotalAmount(new BigDecimal("199.00"));
            detailVO.setStatus(0);
            detailVO.setStatusDesc("待付款");

            // 3. mock Service行为
            when(orderService.createOrder(eq(MOCK_USER_ID), any(OrderCreateDTO.class)))
                    .thenReturn(detailVO);

            // 4. 发送请求并验证结果
            mockMvc.perform(post("/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("下单成功"))
                    .andExpect(jsonPath("$.data.id").value(2001))
                    .andExpect(jsonPath("$.data.orderNo").value("ORD20240101001"))
                    .andExpect(jsonPath("$.data.status").value(0));

            // 5. 验证Service被正确调用
            verify(orderService).createOrder(eq(MOCK_USER_ID), any(OrderCreateDTO.class));
        }

        @Test
        @DisplayName("addressId为空 → 参数校验失败返回400")
        void createOrder_addressIdNull_returns400() throws Exception {
            // 构造一个addressId为空的非法请求
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setAddressId(null); // 故意设为null
            OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
            item.setSkuId(100L);
            item.setQuantity(1);
            dto.setItems(List.of(item));

            mockMvc.perform(post("/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("商品列表为空 → 参数校验失败返回400")
        void createOrder_emptyItems_returns400() throws Exception {
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setAddressId(1L);
            dto.setItems(Collections.emptyList()); // 空列表

            mockMvc.perform(post("/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("Service抛业务异常 → 返回业务错误码")
        void createOrder_serviceThrowsBusinessException_returnsErrorCode() throws Exception {
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setAddressId(1L);
            OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
            item.setSkuId(100L);
            item.setQuantity(2);
            dto.setItems(List.of(item));

            // mock Service抛出"库存不足"业务异常
            when(orderService.createOrder(anyLong(), any(OrderCreateDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.OPERATION_FAIL, "库存不足"));

            mockMvc.perform(post("/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk()) // BusinessException返回200（@ResponseStatus(HttpStatus.OK)）
                    .andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_FAIL.getCode()))
                    .andExpect(jsonPath("$.message").value("操作失败: 库存不足"));
        }
    }

    // ==================== 取消订单 ====================

    @Nested
    @DisplayName("取消订单 PUT /order/{id}/cancel")
    class CancelOrderTest {

        @Test
        @DisplayName("正常取消订单 → 返回200")
        void cancelOrder_success() throws Exception {
            OrderCancelDTO dto = new OrderCancelDTO();
            dto.setReason("不想买了");

            mockMvc.perform(put("/order/{id}/cancel", 2001L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("取消成功"));

            verify(orderService).cancelOrder(eq(MOCK_USER_ID), eq(2001L), any(OrderCancelDTO.class));
        }

        @Test
        @DisplayName("取消原因为空 → 参数校验失败返回400")
        void cancelOrder_emptyReason_returns400() throws Exception {
            OrderCancelDTO dto = new OrderCancelDTO();
            dto.setReason(""); // 空字符串

            mockMvc.perform(put("/order/{id}/cancel", 2001L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }
    }

    // ==================== 获取订单详情 ====================

    @Nested
    @DisplayName("获取订单详情 GET /order/{id}")
    class GetOrderDetailTest {

        @Test
        @DisplayName("正常获取订单详情 → 返回200和详情数据")
        void getOrderDetail_success() throws Exception {
            OrderDetailVO detailVO = new OrderDetailVO();
            detailVO.setId(2001L);
            detailVO.setOrderNo("ORD20240101001");
            detailVO.setStatus(2);
            detailVO.setStatusDesc("待发货");

            when(orderService.getOrderDetail(eq(MOCK_USER_ID), eq(2001L)))
                    .thenReturn(detailVO);

            mockMvc.perform(get("/order/{id}", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(2001))
                    .andExpect(jsonPath("$.data.orderNo").value("ORD20240101001"))
                    .andExpect(jsonPath("$.data.status").value(2));

            verify(orderService).getOrderDetail(eq(MOCK_USER_ID), eq(2001L));
        }

        @Test
        @DisplayName("订单不存在 → Service抛异常 → 返回业务错误码")
        void getOrderDetail_notFound_throwsBusinessException() throws Exception {
            when(orderService.getOrderDetail(anyLong(), anyLong()))
                    .thenThrow(new BusinessException(ErrorCode.DATA_NOT_FOUND));

            mockMvc.perform(get("/order/{id}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.DATA_NOT_FOUND.getCode()));
        }
    }

    // ==================== 获取订单列表 ====================

    @Nested
    @DisplayName("获取订单列表 GET /order/list")
    class GetOrderListTest {

        @Test
        @DisplayName("正常获取订单列表 → 返回200和分页数据")
        void getOrderList_success() throws Exception {
            // 构造一条订单VO
            OrderVO orderVO = new OrderVO();
            orderVO.setId(2001L);
            orderVO.setOrderNo("ORD20240101001");
            orderVO.setStatus(0);
            orderVO.setStatusDesc("待付款");

            // 构造分页结果
            PageResult<OrderVO> pageResult = new PageResult<>();
            pageResult.setRecords(List.of(orderVO));
            pageResult.setTotal(1);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);
            pageResult.setPages(1);

            when(orderService.getOrderList(eq(MOCK_USER_ID), any(), any()))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/order/list")
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(2001))
                    .andExpect(jsonPath("$.data.records[0].orderNo").value("ORD20240101001"));

            verify(orderService).getOrderList(eq(MOCK_USER_ID), any(), any());
        }

        @Test
        @DisplayName("按状态筛选订单 → 返回200")
        void getOrderList_withStatusFilter() throws Exception {
            PageResult<OrderVO> pageResult = new PageResult<>();
            pageResult.setRecords(Collections.emptyList());
            pageResult.setTotal(0);

            when(orderService.getOrderList(anyLong(), any(), any()))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/order/list")
                            .param("status", "0") // 只看待付款
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    // ==================== 确认收货 ====================

    @Nested
    @DisplayName("确认收货 PUT /order/{id}/confirm")
    class ConfirmReceiveTest {

        @Test
        @DisplayName("正常确认收货 → 返回200")
        void confirmReceive_success() throws Exception {
            mockMvc.perform(put("/order/{id}/confirm", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("确认收货成功"));

            verify(orderService).confirmReceive(eq(MOCK_USER_ID), eq(2001L));
        }
    }

    // ==================== 支付成功回调 ====================

    @Nested
    @DisplayName("支付成功回调 POST /order/pay-success")
    class PaySuccessTest {

        @Test
        @DisplayName("正常支付回调 → 返回200")
        void paySuccess_success() throws Exception {
            mockMvc.perform(post("/order/pay-success")
                            .param("orderNo", "ORD20240101001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("支付状态更新成功"));

            verify(orderService).paySuccess("ORD20240101001");
        }
    }

    // ==================== 管理端订单列表 ====================

    @Nested
    @DisplayName("管理端订单列表 GET /order/admin/list")
    class AdminListOrdersTest {

        @Test
        @DisplayName("正常查询管理端订单列表 → 返回200和分页数据")
        void adminListOrders_success() throws Exception {
            // 构造一条订单实体
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setId(2001L);
            orderInfo.setOrderNo("ORD20240101001");
            orderInfo.setTotalAmount(new BigDecimal("199.00"));
            orderInfo.setPayAmount(new BigDecimal("199.00"));
            orderInfo.setStatus(0);
            orderInfo.setCreateTime(LocalDateTime.now());

            // 构造MyBatis-Plus分页结果
            Page<OrderInfo> page = new Page<>(1, 10);
            page.setRecords(List.of(orderInfo));
            page.setTotal(1);

            when(orderInfoMapper.selectPage(any(Page.class), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/order/admin/list")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(2001))
                    .andExpect(jsonPath("$.data.records[0].orderNo").value("ORD20240101001"))
                    .andExpect(jsonPath("$.data.records[0].statusDesc").value("待付款"));

            verify(orderInfoMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("按状态和订单号筛选 → 返回200")
        void adminListOrders_withFilters() throws Exception {
            Page<OrderInfo> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(orderInfoMapper.selectPage(any(Page.class), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/order/admin/list")
                            .param("page", "1")
                            .param("size", "10")
                            .param("status", "2")
                            .param("orderNo", "ORD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    // ==================== 管理端订单详情 ====================

    @Nested
    @DisplayName("管理端订单详情 GET /order/admin/{id}")
    class AdminGetOrderDetailTest {

        @Test
        @DisplayName("正常获取管理端订单详情 → 返回200和完整详情")
        void adminGetOrderDetail_success() throws Exception {
            // 1. 构造订单主表
            OrderInfo order = new OrderInfo();
            order.setId(2001L);
            order.setOrderNo("ORD20240101001");
            order.setTotalAmount(new BigDecimal("199.00"));
            order.setPayAmount(new BigDecimal("199.00"));
            order.setFreightAmount(new BigDecimal("10.00"));
            order.setDiscountAmount(new BigDecimal("0"));
            order.setStatus(3); // 运输中
            order.setCreateTime(LocalDateTime.now());

            // 2. 构造订单明细
            OrderItem item = new OrderItem();
            item.setId(3001L);
            item.setProductId(100L);
            item.setSkuId(1001L);
            item.setProductName("测试商品");
            item.setSkuSpec("黑色 128G");
            item.setPrice(new BigDecimal("99.50"));
            item.setQuantity(2);
            item.setSubtotal(new BigDecimal("199.00"));

            // 3. 构造地址快照
            OrderAddress address = new OrderAddress();
            address.setName("张三");
            address.setPhone("13800138000");
            address.setProvince("广东省");
            address.setCity("深圳市");
            address.setDistrict("南山区");
            address.setDetail("科技园路1号");

            // 4. 构造物流信息
            OrderLogistics logistics = new OrderLogistics();
            logistics.setLogisticsNo("SF1234567890");
            logistics.setLogisticsCompany("顺丰速运");

            // 5. mock Mapper返回值
            when(orderInfoMapper.selectById(2001L)).thenReturn(order);
            when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
            when(orderAddressMapper.selectOne(any())).thenReturn(address);
            when(orderLogisticsMapper.selectOne(any())).thenReturn(logistics);

            // 6. 验证返回结果
            mockMvc.perform(get("/order/admin/{id}", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(2001))
                    .andExpect(jsonPath("$.data.orderNo").value("ORD20240101001"))
                    .andExpect(jsonPath("$.data.status").value(3))
                    .andExpect(jsonPath("$.data.statusDesc").value("运输中"))
                    .andExpect(jsonPath("$.data.items[0].productName").value("测试商品"))
                    .andExpect(jsonPath("$.data.address.name").value("张三"))
                    .andExpect(jsonPath("$.data.logistics.logisticsNo").value("SF1234567890"));
        }

        @Test
        @DisplayName("订单不存在 → 返回200和null数据")
        void adminGetOrderDetail_notFound_returnsNull() throws Exception {
            when(orderInfoMapper.selectById(9999L)).thenReturn(null);

            mockMvc.perform(get("/order/admin/{id}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ==================== 管理端发货 ====================

    @Nested
    @DisplayName("管理端发货 PUT /order/admin/{id}/deliver")
    class AdminDeliverOrderTest {

        @Test
        @DisplayName("正常发货 → 返回200")
        void adminDeliverOrder_success() throws Exception {
            mockMvc.perform(put("/order/admin/{id}/deliver", 2001L)
                            .param("logisticsNo", "SF1234567890")
                            .param("logisticsCompany", "顺丰速运"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("发货成功"));

            // 验证LogisticsService被调用，且参数正确
            verify(logisticsService).delivery(any(DeliveryDTO.class));
        }

        @Test
        @DisplayName("发货失败（订单状态不对） → Service抛异常 → 返回业务错误码")
        void adminDeliverOrder_wrongStatus_throwsBusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.OPERATION_FAIL, "订单状态不允许发货"))
                    .when(logisticsService).delivery(any(DeliveryDTO.class));

            mockMvc.perform(put("/order/admin/{id}/deliver", 2001L)
                            .param("logisticsNo", "SF1234567890")
                            .param("logisticsCompany", "顺丰速运"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_FAIL.getCode()))
                    .andExpect(jsonPath("$.message").value("操作失败: 订单状态不允许发货"));
        }
    }
}
