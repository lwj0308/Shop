package com.shop.admin.controller;

import com.shop.admin.feign.UserFeignClient;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.user.vo.UserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserManageController 切片测试（B-S-07）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 这个Controller的特点是：它不调用本地Service，而是通过Feign远程调用用户服务，
 * 把用户服务的Result原样返回给前端。所以测试中我们 mock UserFeignClient。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. Feign调用参数是否正确（page、size、status、keyword、userId）
 * 3. Feign返回的Result是否原样透传给前端
 * 4. 分页参数默认值是否正确（page默认1，size默认10）
 * </p>
 * <p>
 * 注意：
 * 1. @RequirePermission 和 @OperationLog 是AOP注解，在切片测试中不会触发，
 *    所以不需要测试权限校验和操作日志。
 * 2. UserManageController 直接返回 Feign 的 Result，不做任何转换。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserManageController 切片测试")
class UserManageControllerTest {

    /** Mock的用户服务Feign客户端（假装的远程调用，不真的发HTTP请求） */
    @Mock
    private UserFeignClient userFeignClient;

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 创建UserManageController实例，注入Mock的UserFeignClient，
     * 构建MockMvc并设置全局异常处理器。
     * </p>
     */
    @BeforeEach
    void setUp() {
        UserManageController controller = new UserManageController(userFeignClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 1. 分页查询用户列表测试 ====================

    /**
     * 分页查询用户列表接口测试组
     * <p>
     * GET /admin/manage/user/list，参数：page、size、status（可选）、keyword（可选）
     * </p>
     */
    @Nested
    @DisplayName("GET /admin/manage/user/list - 分页查询用户列表")
    class ListUsersTest {

        /**
         * 测试默认参数查询（不带任何参数）
         * <p>
         * 场景：不传page和size，应使用默认值page=1、size=10
         * </p>
         */
        @Test
        @DisplayName("默认参数查询 - page=1, size=10")
        void listUsers_DefaultParams() throws Exception {
            // 构造分页结果
            UserVO user1 = new UserVO();
            user1.setId(1L);
            user1.setNickname("用户A");
            user1.setStatus(1);

            UserVO user2 = new UserVO();
            user2.setId(2L);
            user2.setNickname("用户B");
            user2.setStatus(1);

            PageResult<UserVO> pageResult = new PageResult<>();
            pageResult.setRecords(Arrays.asList(user1, user2));
            pageResult.setTotal(2L);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);

            when(userFeignClient.listUsers(eq(1), eq(10), isNull(), isNull()))
                    .thenReturn(Result.success(pageResult));

            mockMvc.perform(get("/admin/manage/user/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.records[0].nickname").value("用户A"));

            verify(userFeignClient).listUsers(eq(1), eq(10), isNull(), isNull());
        }

        /**
         * 测试带状态筛选查询
         * <p>
         * 场景：传入status=0（只看禁用用户），应把status传给Feign
         * </p>
         */
        @Test
        @DisplayName("带状态筛选 - status=0")
        void listUsers_WithStatus() throws Exception {
            PageResult<UserVO> pageResult = new PageResult<>();
            pageResult.setRecords(Collections.emptyList());
            pageResult.setTotal(0L);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);

            when(userFeignClient.listUsers(eq(1), eq(10), eq(0), isNull()))
                    .thenReturn(Result.success(pageResult));

            mockMvc.perform(get("/admin/manage/user/list")
                            .param("status", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));

            verify(userFeignClient).listUsers(eq(1), eq(10), eq(0), isNull());
        }

        /**
         * 测试带关键词搜索
         * <p>
         * 场景：传入keyword="张三"，应把keyword传给Feign
         * </p>
         */
        @Test
        @DisplayName("带关键词搜索 - keyword=张三")
        void listUsers_WithKeyword() throws Exception {
            UserVO user = new UserVO();
            user.setId(1L);
            user.setNickname("张三");
            user.setStatus(1);

            PageResult<UserVO> pageResult = new PageResult<>();
            pageResult.setRecords(Collections.singletonList(user));
            pageResult.setTotal(1L);
            pageResult.setPageNum(1);
            pageResult.setPageSize(10);

            when(userFeignClient.listUsers(eq(1), eq(10), isNull(), eq("张三")))
                    .thenReturn(Result.success(pageResult));

            mockMvc.perform(get("/admin/manage/user/list")
                            .param("keyword", "张三"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].nickname").value("张三"));

            verify(userFeignClient).listUsers(eq(1), eq(10), isNull(), eq("张三"));
        }

        /**
         * 测试自定义分页参数
         * <p>
         * 场景：传入page=2、size=20，应把参数传给Feign
         * </p>
         */
        @Test
        @DisplayName("自定义分页参数 - page=2, size=20")
        void listUsers_CustomPaging() throws Exception {
            PageResult<UserVO> pageResult = new PageResult<>();
            pageResult.setRecords(Collections.emptyList());
            pageResult.setTotal(50L);
            pageResult.setPageNum(2);
            pageResult.setPageSize(20);

            when(userFeignClient.listUsers(eq(2), eq(20), isNull(), isNull()))
                    .thenReturn(Result.success(pageResult));

            mockMvc.perform(get("/admin/manage/user/list")
                            .param("page", "2")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(50));

            verify(userFeignClient).listUsers(eq(2), eq(20), isNull(), isNull());
        }
    }

    // ==================== 2. 查询用户详情测试 ====================

    /**
     * 查询用户详情接口测试组
     * <p>
     * GET /admin/manage/user/{userId}，参数：PathVariable userId
     * </p>
     */
    @Nested
    @DisplayName("GET /admin/manage/user/{userId} - 查询用户详情")
    class GetUserByIdTest {

        /**
         * 测试正常查询用户详情
         * <p>
         * 场景：传入合法的用户ID，应返回用户详细信息
         * </p>
         */
        @Test
        @DisplayName("正常查询用户详情 - 返回用户信息")
        void getUserById_Success() throws Exception {
            UserVO userVO = new UserVO();
            userVO.setId(1001L);
            userVO.setNickname("测试用户");
            userVO.setPhone("13800138000");
            userVO.setStatus(1);

            when(userFeignClient.getUserById(1001L)).thenReturn(Result.success(userVO));

            mockMvc.perform(get("/admin/manage/user/{userId}", 1001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1001))
                    .andExpect(jsonPath("$.data.nickname").value("测试用户"));

            verify(userFeignClient).getUserById(1001L);
        }

        /**
         * 测试用户不存在
         * <p>
         * 场景：Feign返回业务失败（用户不存在），应原样透传错误码
         * </p>
         */
        @Test
        @DisplayName("用户不存在 - 透传Feign的错误结果")
        void getUserById_NotFound() throws Exception {
            when(userFeignClient.getUserById(9999L))
                    .thenReturn(Result.fail(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get("/admin/manage/user/{userId}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));

            verify(userFeignClient).getUserById(9999L);
        }
    }

    // ==================== 3. 禁用用户测试 ====================

    /**
     * 禁用用户接口测试组
     * <p>
     * PUT /admin/manage/user/{userId}/disable，参数：PathVariable userId
     * </p>
     */
    @Nested
    @DisplayName("PUT /admin/manage/user/{userId}/disable - 禁用用户")
    class DisableUserTest {

        /**
         * 测试正常禁用用户
         * <p>
         * 场景：传入合法的用户ID，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常禁用用户 - 返回成功")
        void disableUser_Success() throws Exception {
            when(userFeignClient.disableUser(1001L)).thenReturn(Result.success(null));

            mockMvc.perform(put("/admin/manage/user/{userId}/disable", 1001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userFeignClient).disableUser(1001L);
        }
    }

    // ==================== 4. 启用用户测试 ====================

    /**
     * 启用用户接口测试组
     * <p>
     * PUT /admin/manage/user/{userId}/enable，参数：PathVariable userId
     * </p>
     */
    @Nested
    @DisplayName("PUT /admin/manage/user/{userId}/enable - 启用用户")
    class EnableUserTest {

        /**
         * 测试正常启用用户
         * <p>
         * 场景：传入合法的用户ID，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常启用用户 - 返回成功")
        void enableUser_Success() throws Exception {
            when(userFeignClient.enableUser(1001L)).thenReturn(Result.success(null));

            mockMvc.perform(put("/admin/manage/user/{userId}/enable", 1001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userFeignClient).enableUser(1001L);
        }
    }
}
