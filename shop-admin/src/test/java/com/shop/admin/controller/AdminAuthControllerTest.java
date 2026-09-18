package com.shop.admin.controller;

import com.shop.admin.service.AdminAuthService;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.result.ErrorCode;
import com.shop.model.admin.dto.AdminLoginDTO;
import com.shop.model.admin.vo.AdminLoginVO;
import com.shop.model.admin.vo.CaptchaVO;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminAuthController 切片测试（B-S-07）
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
 * 4. 参数校验是否正确（@NotBlank注解触发校验）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 * <p>
 * 注意：
 * 1. 验证码和登录接口不需要登录（SaTokenConfig中配置了排除），所以不需要mock登录态。
 * 2. 登出接口需要登录，但登录态校验由Sa-Token拦截器处理，切片测试不涉及。
 * 3. AdminAuthController 依赖 AdminAuthService，测试中 mock 这个Service。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminAuthController 切片测试")
class AdminAuthControllerTest {

    /** Mock的管理员认证服务（假装的Service，不真的执行业务逻辑） */
    @Mock
    private AdminAuthService adminAuthService;

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 创建AdminAuthController实例，注入Mock的AdminAuthService，
     * 构建MockMvc并设置全局异常处理器。
     * </p>
     */
    @BeforeEach
    void setUp() {
        AdminAuthController controller = new AdminAuthController(adminAuthService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 1. 获取验证码测试 ====================

    /**
     * 获取验证码接口测试组
     * <p>
     * POST /admin/auth/captcha，无参数
     * </p>
     */
    @Nested
    @DisplayName("POST /admin/auth/captcha - 获取验证码")
    class GetCaptchaTest {

        /**
         * 测试正常获取验证码
         * <p>
         * 场景：调用获取验证码接口，应返回captchaKey和captchaImage
         * </p>
         */
        @Test
        @DisplayName("正常获取验证码 - 返回captchaKey和captchaImage")
        void getCaptcha_Success() throws Exception {
            // 构造验证码VO
            CaptchaVO captchaVO = new CaptchaVO();
            captchaVO.setCaptchaKey("captcha-uuid-12345");
            captchaVO.setCaptchaImage("data:image/png;base64,abcdef==");

            when(adminAuthService.getCaptcha()).thenReturn(captchaVO);

            mockMvc.perform(post("/admin/auth/captcha"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("获取验证码成功"))
                    .andExpect(jsonPath("$.data.captchaKey").value("captcha-uuid-12345"))
                    .andExpect(jsonPath("$.data.captchaImage").value("data:image/png;base64,abcdef=="));

            verify(adminAuthService).getCaptcha();
        }
    }

    // ==================== 2. 管理员登录测试 ====================

    /**
     * 管理员登录接口测试组
     * <p>
     * POST /admin/auth/login，参数：AdminLoginDTO（username、password、captchaKey、captchaCode）
     * </p>
     */
    @Nested
    @DisplayName("POST /admin/auth/login - 管理员登录")
    class LoginTest {

        /**
         * 测试正常登录
         * <p>
         * 场景：传入合法的用户名、密码、验证码，应返回Token和管理员信息
         * </p>
         */
        @Test
        @DisplayName("正常登录 - 返回Token和权限信息")
        void login_Success() throws Exception {
            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("password123");
            dto.setCaptchaKey("captcha-uuid-12345");
            dto.setCaptchaCode("ABCD");

            // 构造登录响应VO
            AdminLoginVO loginVO = new AdminLoginVO();
            loginVO.setToken("satoken-abcdef-123456");
            loginVO.setAdminUserId(1L);
            loginVO.setUsername("admin");
            loginVO.setNickname("超级管理员");
            loginVO.setAvatar("/avatar/admin.png");
            Set<String> permissions = new HashSet<>(Arrays.asList("user:list", "user:disable", "role:list"));
            Set<String> roles = new HashSet<>(Arrays.asList("admin", "super_admin"));
            loginVO.setPermissions(permissions);
            loginVO.setRoles(roles);

            when(adminAuthService.login(any(AdminLoginDTO.class))).thenReturn(loginVO);

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("登录成功"))
                    .andExpect(jsonPath("$.data.token").value("satoken-abcdef-123456"))
                    .andExpect(jsonPath("$.data.adminUserId").value(1))
                    .andExpect(jsonPath("$.data.username").value("admin"))
                    .andExpect(jsonPath("$.data.nickname").value("超级管理员"));

            verify(adminAuthService).login(any(AdminLoginDTO.class));
        }

        /**
         * 测试用户名为空
         * <p>
         * 场景：不传username，应返回400参数校验错误（@NotBlank校验）
         * </p>
         */
        @Test
        @DisplayName("用户名为空 - 返回400")
        void login_UsernameBlank() throws Exception {
            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setPassword("password123");

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试密码为空
         * <p>
         * 场景：不传password，应返回400参数校验错误（@NotBlank校验）
         * </p>
         */
        @Test
        @DisplayName("密码为空 - 返回400")
        void login_PasswordBlank() throws Exception {
            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * 测试验证码错误
         * <p>
         * 场景：Service抛出验证码错误异常，应返回业务错误码
         * </p>
         */
        @Test
        @DisplayName("验证码错误 - 返回业务错误码")
        void login_CaptchaError() throws Exception {
            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("password123");
            dto.setCaptchaKey("captcha-uuid-12345");
            dto.setCaptchaCode("WRONG");

            doThrow(new BusinessException(ErrorCode.ADMIN_CAPTCHA_ERROR))
                    .when(adminAuthService).login(any(AdminLoginDTO.class));

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ADMIN_CAPTCHA_ERROR.getCode()));
        }

        /**
         * 测试密码错误
         * <p>
         * 场景：Service抛出密码错误异常，应返回业务错误码
         * </p>
         */
        @Test
        @DisplayName("密码错误 - 返回业务错误码")
        void login_PasswordError() throws Exception {
            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("wrongpassword");
            dto.setCaptchaKey("captcha-uuid-12345");
            dto.setCaptchaCode("ABCD");

            doThrow(new BusinessException(ErrorCode.ADMIN_PASSWORD_ERROR))
                    .when(adminAuthService).login(any(AdminLoginDTO.class));

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ADMIN_PASSWORD_ERROR.getCode()));
        }

        /**
         * 测试账号被锁定
         * <p>
         * 场景：Service抛出账号锁定异常（连续5次密码错误），应返回业务错误码
         * </p>
         */
        @Test
        @DisplayName("账号被锁定 - 返回业务错误码")
        void login_AccountLocked() throws Exception {
            AdminLoginDTO dto = new AdminLoginDTO();
            dto.setUsername("admin");
            dto.setPassword("password123");
            dto.setCaptchaKey("captcha-uuid-12345");
            dto.setCaptchaCode("ABCD");

            doThrow(new BusinessException(ErrorCode.ADMIN_LOGIN_LOCKED))
                    .when(adminAuthService).login(any(AdminLoginDTO.class));

            mockMvc.perform(post("/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ADMIN_LOGIN_LOCKED.getCode()));
        }
    }

    // ==================== 3. 管理员登出测试 ====================

    /**
     * 管理员登出接口测试组
     * <p>
     * POST /admin/auth/logout，无参数
     * </p>
     */
    @Nested
    @DisplayName("POST /admin/auth/logout - 管理员登出")
    class LogoutTest {

        /**
         * 测试正常登出
         * <p>
         * 场景：调用登出接口，应返回成功
         * </p>
         */
        @Test
        @DisplayName("正常登出 - 返回成功")
        void logout_Success() throws Exception {
            mockMvc.perform(post("/admin/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("退出成功"));

            verify(adminAuthService).logout();
        }
    }
}
