package com.shop.admin.controller;

import com.shop.admin.service.DashboardService;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.result.Result;
import com.shop.model.admin.vo.DashboardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DashboardController 切片测试（B-S-07）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * DashboardController 是数据聚合型Controller，调用DashboardService获取
 * 仪表盘概览数据（今日订单数、销售额、新增用户数、在线商家数）。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确
 * 2. Service调用是否正确
 * 3. 返回格式是否正确（统一Result格式）
 * 4. 数据字段是否正确透传
 * </p>
 * <p>
 * 注意：
 * 1. @RequirePermission 是AOP注解，在切片测试中不会触发，不需要测试权限校验。
 * 2. DashboardController 依赖 DashboardService，测试中 mock 这个Service。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashboardController 切片测试")
class DashboardControllerTest {

    /** Mock的仪表盘服务（假装的Service，不真的聚合数据） */
    @Mock
    private DashboardService dashboardService;

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 创建DashboardController实例，注入Mock的DashboardService，
     * 构建MockMvc并设置全局异常处理器。
     * </p>
     */
    @BeforeEach
    void setUp() {
        DashboardController controller = new DashboardController(dashboardService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 1. 获取仪表盘概览测试 ====================

    /**
     * 获取仪表盘概览接口测试组
     * <p>
     * GET /admin/dashboard/overview，无参数
     * </p>
     */
    @Nested
    @DisplayName("GET /admin/dashboard/overview - 获取仪表盘概览")
    class GetOverviewTest {

        /**
         * 测试正常获取仪表盘数据
         * <p>
         * 场景：各微服务正常返回数据，应聚合显示今日订单数、销售额等
         * </p>
         */
        @Test
        @DisplayName("正常获取仪表盘数据 - 返回经营概况")
        void getOverview_Success() throws Exception {
            DashboardVO vo = new DashboardVO();
            vo.setTodayOrderCount(156L);
            vo.setTodaySalesAmount(new BigDecimal("88888.88"));
            vo.setTodayNewUserCount(23L);
            vo.setOnlineMerchantCount(12L);

            when(dashboardService.getDashboardOverview()).thenReturn(vo);

            mockMvc.perform(get("/admin/dashboard/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.todayOrderCount").value(156))
                    .andExpect(jsonPath("$.data.todaySalesAmount").value(88888.88))
                    .andExpect(jsonPath("$.data.todayNewUserCount").value(23))
                    .andExpect(jsonPath("$.data.onlineMerchantCount").value(12));

            verify(dashboardService).getDashboardOverview();
        }

        /**
         * 测试部分微服务不可用（降级场景）
         * <p>
         * 小白理解：DashboardService 内部会调用多个微服务，如果某个服务挂了，
         * 对应的指标会返回0，而不是整个接口报错。这是"降级"处理。
         * 场景：所有指标都为0，表示所有微服务都不可用
         * </p>
         */
        @Test
        @DisplayName("微服务不可用降级 - 返回0值")
        void getOverview_Degraded() throws Exception {
            DashboardVO vo = new DashboardVO();
            vo.setTodayOrderCount(0L);
            vo.setTodaySalesAmount(BigDecimal.ZERO);
            vo.setTodayNewUserCount(0L);
            vo.setOnlineMerchantCount(0L);

            when(dashboardService.getDashboardOverview()).thenReturn(vo);

            mockMvc.perform(get("/admin/dashboard/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.todayOrderCount").value(0))
                    .andExpect(jsonPath("$.data.todaySalesAmount").value(0))
                    .andExpect(jsonPath("$.data.todayNewUserCount").value(0))
                    .andExpect(jsonPath("$.data.onlineMerchantCount").value(0));

            verify(dashboardService).getDashboardOverview();
        }
    }
}
