package com.shop.seckill.controller;

import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.dto.SeckillQueryDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.vo.SeckillVO;
import com.shop.seckill.feign.MerchantFeignClient;
import com.shop.seckill.service.SeckillActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * SeckillController 切片测试（B-S-05）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos等中间件。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. 返回格式是否正确（统一Result格式，code=200表示成功）
 * 3. Service调用是否正确（参数传递、调用次数）
 * 4. 商家身份校验（通过Feign获取商家ID，失败时返回"商家信息不存在"）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 * <p>
 * 注意：
 * 1. 商家端接口用 SecurityUtils.requireLogin() 获取用户ID，通过 Feign 调用 shop-merchant
 *    获取商家ID。测试中 mock SecurityUtils.requireLogin() 和 MerchantFeignClient。
 * 2. 管理端接口用 merchantId=0 表示平台活动，不需要商家身份校验。
 * 3. 用户端公开接口和内部接口不需要登录。
 * 4. SeckillCreateDTO 没有校验注解，所以不测参数校验。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SeckillController 切片测试")
class SeckillControllerTest {

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用），注册Java8时间模块支持LocalDateTime */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** 假装秒杀活动服务 */
    @Mock
    private SeckillActivityService seckillActivityService;

    /** 假装商家服务Feign客户端 */
    @Mock
    private MerchantFeignClient merchantFeignClient;

    /** SecurityUtils静态方法mock（模拟登录状态） */
    private MockedStatic<SecurityUtils> securityUtilsMock;

    /** 测试中模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    /** 测试中模拟的商家ID */
    private static final Long MOCK_MERCHANT_ID = 5001L;

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 SeckillController 实例，注入 mock 依赖
        SeckillController controller = new SeckillController(seckillActivityService, merchantFeignClient);

        // 2. 用 Standalone 方式构建 MockMvc，手动注册全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 3. mock SecurityUtils.requireLogin()，让Controller认为用户已登录
        securityUtilsMock = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::requireLogin).thenReturn(MOCK_USER_ID);
    }

    @AfterEach
    void tearDown() {
        // 每个测试结束后关闭静态mock，避免影响其他测试
        securityUtilsMock.close();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一个合法的创建秒杀活动DTO
     *
     * @return 构造好的 SeckillCreateDTO
     */
    private SeckillCreateDTO buildValidCreateDTO() {
        SeckillCreateDTO dto = new SeckillCreateDTO();
        dto.setProductId(8001L);
        dto.setSkuId(9001L);
        dto.setSeckillPrice(new BigDecimal("4999.00"));
        dto.setOriginalPrice(new BigDecimal("9999.00"));
        dto.setTotalCount(100);
        dto.setLimitCount(1);
        dto.setStartTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        dto.setEndTime(LocalDateTime.of(2024, 1, 1, 12, 0));
        dto.setDescription("iPhone 15 秒杀活动");
        return dto;
    }

    /**
     * 构造一个秒杀活动VO
     *
     * @param id     秒杀活动ID
     * @param status 状态：0待生效 1进行中 2已结束 3已下架
     * @return 构造好的 SeckillVO
     */
    private SeckillVO buildSeckillVO(Long id, Integer status) {
        SeckillVO vo = new SeckillVO();
        vo.setId(id);
        vo.setMerchantId(MOCK_MERCHANT_ID);
        vo.setProductId(8001L);
        vo.setSkuId(9001L);
        vo.setSeckillPrice(new BigDecimal("4999.00"));
        vo.setOriginalPrice(new BigDecimal("9999.00"));
        vo.setTotalCount(100);
        vo.setAvailableCount(80);
        vo.setLimitCount(1);
        vo.setStartTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        vo.setEndTime(LocalDateTime.of(2024, 1, 1, 12, 0));
        vo.setStatus(status);
        vo.setStatusDesc("进行中");
        vo.setDescription("iPhone 15 秒杀活动");
        vo.setCreateTime(LocalDateTime.of(2024, 1, 1, 9, 0));
        vo.setProgress(20);
        return vo;
    }

    /**
     * 构造一个秒杀活动实体
     *
     * @param id 秒杀活动ID
     * @return 构造好的 SeckillActivity
     */
    private SeckillActivity buildSeckillActivity(Long id) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setMerchantId(MOCK_MERCHANT_ID);
        activity.setProductId(8001L);
        activity.setSkuId(9001L);
        activity.setSeckillPrice(new BigDecimal("4999.00"));
        activity.setOriginalPrice(new BigDecimal("9999.00"));
        activity.setTotalCount(100);
        activity.setAvailableCount(80);
        activity.setLimitCount(1);
        activity.setStartTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        activity.setEndTime(LocalDateTime.of(2024, 1, 1, 12, 0));
        activity.setStatus(1);
        activity.setDescription("iPhone 15 秒杀活动");
        return activity;
    }

    // ==================== 商家端：创建秒杀活动 ====================

    @Nested
    @DisplayName("商家端：创建秒杀活动 POST /seckill")
    class CreateSeckillActivityTest {

        @Test
        @DisplayName("正常创建秒杀活动 → 返回200和活动ID")
        void createSeckillActivity_success_returnsActivityId() throws Exception {
            SeckillCreateDTO dto = buildValidCreateDTO();

            // mock Feign调用返回商家ID
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(MOCK_MERCHANT_ID));

            // mock Service返回活动ID
            when(seckillActivityService.createSeckillActivity(eq(MOCK_MERCHANT_ID), any(SeckillCreateDTO.class)))
                    .thenReturn(7001L);

            mockMvc.perform(post("/seckill")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(7001));

            verify(merchantFeignClient).getMerchantIdByUserId(eq(MOCK_USER_ID));
            verify(seckillActivityService).createSeckillActivity(eq(MOCK_MERCHANT_ID), any(SeckillCreateDTO.class));
        }

        @Test
        @DisplayName("商家信息不存在 → 返回500和错误提示")
        void createSeckillActivity_merchantNotFound_returnsFail() throws Exception {
            SeckillCreateDTO dto = buildValidCreateDTO();

            // mock Feign调用返回null（用户不是商家）
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(null));

            mockMvc.perform(post("/seckill")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("商家信息不存在"));

            // 商家信息不存在时不应调用Service
            verify(seckillActivityService, org.mockito.Mockito.never())
                    .createSeckillActivity(anyLong(), any(SeckillCreateDTO.class));
        }

        @Test
        @DisplayName("Feign调用失败 → 返回500和错误提示")
        void createSeckillActivity_feignFailed_returnsFail() throws Exception {
            SeckillCreateDTO dto = buildValidCreateDTO();

            // mock Feign调用返回失败响应
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.fail("服务调用失败"));

            mockMvc.perform(post("/seckill")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("商家信息不存在"));
        }

        @Test
        @DisplayName("Service抛业务异常 → 返回业务错误码")
        void createSeckillActivity_serviceThrowsBusinessException_returnsErrorCode() throws Exception {
            SeckillCreateDTO dto = buildValidCreateDTO();

            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(MOCK_MERCHANT_ID));

            // mock Service抛出"操作失败"业务异常（比如秒杀价>=原价）
            when(seckillActivityService.createSeckillActivity(anyLong(), any(SeckillCreateDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.OPERATION_FAIL, "秒杀价必须小于原价"));

            mockMvc.perform(post("/seckill")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_FAIL.getCode()))
                    .andExpect(jsonPath("$.message").value("操作失败: 秒杀价必须小于原价"));
        }
    }

    // ==================== 商家端：下架秒杀活动 ====================

    @Nested
    @DisplayName("商家端：下架秒杀活动 PUT /seckill/{seckillId}/offline")
    class OfflineSeckillActivityTest {

        @Test
        @DisplayName("正常下架秒杀活动 → 返回200")
        void offlineSeckillActivity_success() throws Exception {
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(MOCK_MERCHANT_ID));

            mockMvc.perform(put("/seckill/{seckillId}/offline", 7001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(seckillActivityService).offlineSeckillActivity(eq(MOCK_MERCHANT_ID), eq(7001L));
        }

        @Test
        @DisplayName("商家信息不存在 → 返回500和错误提示")
        void offlineSeckillActivity_merchantNotFound_returnsFail() throws Exception {
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(null));

            mockMvc.perform(put("/seckill/{seckillId}/offline", 7001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("商家信息不存在"));

            verify(seckillActivityService, org.mockito.Mockito.never())
                    .offlineSeckillActivity(anyLong(), anyLong());
        }

        @Test
        @DisplayName("非自己的秒杀活动 → Service抛业务异常 → 返回业务错误码")
        void offlineSeckillActivity_notYours_throwsBusinessException() throws Exception {
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(MOCK_MERCHANT_ID));

            // mock Service抛出"无权操作"业务异常
            doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权操作此秒杀活动"))
                    .when(seckillActivityService).offlineSeckillActivity(anyLong(), eq(9999L));

            mockMvc.perform(put("/seckill/{seckillId}/offline", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
        }
    }

    // ==================== 商家端：查询秒杀活动列表 ====================

    @Nested
    @DisplayName("商家端：查询秒杀活动列表 GET /seckill/list")
    class GetSeckillListTest {

        @Test
        @DisplayName("正常查询商家自己的秒杀活动列表 → 返回200和分页数据")
        void getSeckillList_success_returnsPageResult() throws Exception {
            List<SeckillVO> voList = List.of(
                    buildSeckillVO(7001L, 1),
                    buildSeckillVO(7002L, 0));
            PageResult<SeckillVO> pageResult = new PageResult<>();
            pageResult.setRecords(voList);
            pageResult.setTotal(2);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);

            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(MOCK_MERCHANT_ID));
            when(seckillActivityService.getSeckillList(eq(MOCK_MERCHANT_ID), any(SeckillQueryDTO.class)))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/seckill/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records[0].id").value(7001))
                    .andExpect(jsonPath("$.data.records[1].id").value(7002));

            verify(seckillActivityService).getSeckillList(eq(MOCK_MERCHANT_ID), any(SeckillQueryDTO.class));
        }

        @Test
        @DisplayName("商家信息不存在 → 返回500和错误提示")
        void getSeckillList_merchantNotFound_returnsFail() throws Exception {
            when(merchantFeignClient.getMerchantIdByUserId(eq(MOCK_USER_ID)))
                    .thenReturn(Result.success(null));

            mockMvc.perform(get("/seckill/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("商家信息不存在"));
        }
    }

    // ==================== 商家端：查询秒杀活动详情 ====================

    @Nested
    @DisplayName("商家端：查询秒杀活动详情 GET /seckill/{seckillId}")
    class GetSeckillDetailTest {

        @Test
        @DisplayName("正常查询秒杀活动详情 → 返回200和详情数据")
        void getSeckillDetail_success() throws Exception {
            SeckillVO vo = buildSeckillVO(7001L, 1);

            when(seckillActivityService.getSeckillDetail(eq(7001L))).thenReturn(vo);

            mockMvc.perform(get("/seckill/{seckillId}", 7001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(7001))
                    .andExpect(jsonPath("$.data.seckillPrice").value(4999.00))
                    .andExpect(jsonPath("$.data.status").value(1));

            verify(seckillActivityService).getSeckillDetail(eq(7001L));
        }
    }

    // ==================== 管理端：创建平台秒杀活动 ====================

    @Nested
    @DisplayName("管理端：创建平台秒杀活动 POST /seckill/admin/create")
    class AdminCreateSeckillActivityTest {

        @Test
        @DisplayName("正常创建平台秒杀活动 → 返回200和活动ID")
        void adminCreateSeckillActivity_success() throws Exception {
            SeckillCreateDTO dto = buildValidCreateDTO();

            // 管理端用 merchantId=0 表示平台活动
            when(seckillActivityService.createSeckillActivity(eq(0L), any(SeckillCreateDTO.class)))
                    .thenReturn(7001L);

            mockMvc.perform(post("/seckill/admin/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(7001));

            verify(seckillActivityService).createSeckillActivity(eq(0L), any(SeckillCreateDTO.class));
        }
    }

    // ==================== 管理端：查询所有秒杀活动 ====================

    @Nested
    @DisplayName("管理端：查询所有秒杀活动 GET /seckill/admin/list")
    class AdminGetSeckillListTest {

        @Test
        @DisplayName("正常查询所有秒杀活动 → 返回200和分页数据")
        void adminGetSeckillList_success() throws Exception {
            List<SeckillVO> voList = List.of(buildSeckillVO(7001L, 1), buildSeckillVO(7002L, 0));
            PageResult<SeckillVO> pageResult = new PageResult<>();
            pageResult.setRecords(voList);
            pageResult.setTotal(2);

            // 管理端传 merchantId=null 表示查询所有
            when(seckillActivityService.getSeckillList(eq(null), any(SeckillQueryDTO.class)))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/seckill/admin/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2));

            verify(seckillActivityService).getSeckillList(eq(null), any(SeckillQueryDTO.class));
        }
    }

    // ==================== 管理端：下架秒杀活动 ====================

    @Nested
    @DisplayName("管理端：下架秒杀活动 PUT /seckill/admin/{seckillId}/offline")
    class AdminOfflineSeckillActivityTest {

        @Test
        @DisplayName("正常下架秒杀活动 → 返回200")
        void adminOfflineSeckillActivity_success() throws Exception {
            // 管理端用 merchantId=0
            mockMvc.perform(put("/seckill/admin/{seckillId}/offline", 7001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(seckillActivityService).offlineSeckillActivity(eq(0L), eq(7001L));
        }
    }

    // ==================== 用户端：查询进行中的秒杀活动列表 ====================

    @Nested
    @DisplayName("用户端：查询进行中的秒杀活动列表 GET /seckill/public/list")
    class GetPublicSeckillListTest {

        @Test
        @DisplayName("正常查询进行中的秒杀活动 → 返回200和列表数据")
        void getPublicSeckillList_success() throws Exception {
            List<SeckillVO> voList = List.of(buildSeckillVO(7001L, 1), buildSeckillVO(7002L, 1));
            PageResult<SeckillVO> pageResult = new PageResult<>();
            pageResult.setRecords(voList);
            pageResult.setTotal(2);

            // 用户端查询传 merchantId=null，status=1（进行中）
            when(seckillActivityService.getSeckillList(eq(null), any(SeckillQueryDTO.class)))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/seckill/public/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(7001));

            verify(seckillActivityService).getSeckillList(eq(null), any(SeckillQueryDTO.class));
        }
    }

    // ==================== 用户端：查询秒杀活动详情 ====================

    @Nested
    @DisplayName("用户端：查询秒杀活动详情 GET /seckill/public/{seckillId}")
    class GetPublicSeckillDetailTest {

        @Test
        @DisplayName("正常查询秒杀活动详情 → 返回200和详情数据")
        void getPublicSeckillDetail_success() throws Exception {
            SeckillVO vo = buildSeckillVO(7001L, 1);

            when(seckillActivityService.getSeckillDetail(eq(7001L))).thenReturn(vo);

            mockMvc.perform(get("/seckill/public/{seckillId}", 7001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(7001))
                    .andExpect(jsonPath("$.data.status").value(1));

            verify(seckillActivityService).getSeckillDetail(eq(7001L));
        }
    }

    // ==================== 内部接口：查询秒杀活动实体 ====================

    @Nested
    @DisplayName("内部接口：查询秒杀活动实体 GET /seckill/inner/{seckillId}")
    class GetSeckillByIdTest {

        @Test
        @DisplayName("正常查询秒杀活动实体 → 返回200和实体数据")
        void getSeckillById_success() throws Exception {
            SeckillActivity activity = buildSeckillActivity(7001L);

            when(seckillActivityService.getSeckillById(eq(7001L))).thenReturn(activity);

            mockMvc.perform(get("/seckill/inner/{seckillId}", 7001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(7001))
                    .andExpect(jsonPath("$.data.seckillPrice").value(4999.00))
                    .andExpect(jsonPath("$.data.totalCount").value(100));

            verify(seckillActivityService).getSeckillById(eq(7001L));
        }
    }
}
