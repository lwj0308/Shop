package com.shop.admin.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.admin.service.AdminLoginLogService;
import com.shop.admin.service.AdminSecurityEventService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.admin.dto.AdminLoginDTO;
import com.shop.model.admin.entity.AdminPermission;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminRolePermission;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.vo.AdminLoginVO;
import com.shop.model.admin.vo.CaptchaVO;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员认证服务 AdminAuthServiceImpl 的单元测试
 * <p>
 * 这个测试类验证管理员后台登录的完整流程：获取验证码、登录校验（验证码+用户名+密码+账号状态）、登出。
 * 小白理解：登录是系统的"大门"，必须确保只有合法管理员才能进入。
 * 我们把数据库 Mapper、Redis、Sa-Token 都"假装"一下（Mock），这样测试不依赖真实环境，跑得又快又稳定。
 * </p>
 * <p>
 * 安全要点说明：
 * 1. 用户不存在时不告诉攻击者"用户名不存在"，统一返回"用户名或密码错误"，防止枚举用户名
 * 2. 密码错误5次锁定账号30分钟，防止暴力破解
 * 3. 验证码用完即删，防止重复使用
 * 4. 密码用 BCrypt 加密存储，即使数据库泄露也无法直接看到明文
 * </p>
 */
@DisplayName("AdminAuthServiceImpl 管理员认证服务测试")
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    /** 假装操作 admin_user 表的 Mapper */
    @Mock
    private com.shop.admin.mapper.AdminUserMapper adminUserMapper;

    /** 假装操作 admin_user_role 表的 Mapper（查管理员有哪些角色） */
    @Mock
    private com.shop.admin.mapper.AdminUserRoleMapper adminUserRoleMapper;

    /** 假装操作 admin_role_permission 表的 Mapper（查角色有哪些权限） */
    @Mock
    private com.shop.admin.mapper.AdminRolePermissionMapper adminRolePermissionMapper;

    /** 假装操作 admin_role 表的 Mapper（查角色详情） */
    @Mock
    private com.shop.admin.mapper.AdminRoleMapper adminRoleMapper;

    /** 假装操作 admin_permission 表的 Mapper（查权限详情） */
    @Mock
    private com.shop.admin.mapper.AdminPermissionMapper adminPermissionMapper;

    /** 假装 Redis 模板，用来存验证码和登录失败次数 */
    @Mock
    private StringRedisTemplate redisTemplate;

    /** 假装 Redis 的 Value 操作对象，redisTemplate.opsForValue() 返回的就是它 */
    @Mock
    private ValueOperations<String, String> valueOps;

    /** 假装登录日志服务，验证每次登录是否正确记录了日志 */
    @Mock
    private AdminLoginLogService adminLoginLogService;

    /** 假装安全事件服务，验证暴力破解等安全事件是否被记录 */
    @Mock
    private AdminSecurityEventService adminSecurityEventService;

    /** 被测试的认证服务，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private AdminAuthServiceImpl authService;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：login 方法里用到了 .eq(AdminUser::getUsername, ...) 这种写法，
     * MyBatis-Plus 需要知道 AdminUser::getUsername 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        // 把所有用到 Lambda 查询的实体类都注册进去
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
        TableInfoHelper.initTableInfo(assistant, AdminUserRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminRolePermission.class);
        TableInfoHelper.initTableInfo(assistant, AdminPermission.class);
    }

    /**
     * 每个测试前，统一设置 Redis 的 opsForValue 返回值
     * <p>
     * 小白理解：认证服务里大量使用 redisTemplate.opsForValue().get/set，
     * 我们让 opsForValue() 统一返回 Mock 对象，避免每个测试都写一遍。
     * 用 lenient() 是因为有些测试（比如 logout）不会用到 Redis，stub 没被消费也不报错。
     * </p>
     */
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个登录请求DTO
     *
     * @param username    用户名
     * @param password    密码
     * @param captchaKey  验证码key
     * @param captchaCode 用户输入的验证码
     * @return 构造好的 AdminLoginDTO
     */
    private AdminLoginDTO buildLoginDTO(String username, String password, String captchaKey, String captchaCode) {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setCaptchaKey(captchaKey);
        dto.setCaptchaCode(captchaCode);
        return dto;
    }

    /**
     * 构造一个管理员实体
     *
     * @param id       管理员ID
     * @param username 用户名
     * @param password 密码（加密后的哈希值）
     * @param status   状态：0禁用 1正常
     * @return 构造好的 AdminUser
     */
    private AdminUser buildAdminUser(Long id, String username, String password, Integer status) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname("测试管理员");
        user.setAvatar("avatar.jpg");
        user.setStatus(status);
        return user;
    }

    /**
     * 设置客户端IP的Mock
     * <p>
     * getClientIp() 方法会从 RequestContextHolder 中获取 HttpServletRequest，
     * 然后从请求头 "X-Forwarded-For" 中获取真实IP。
     * 这个方法把整个链路 Mock 掉，返回指定的IP地址。
     * </p>
     *
     * @param rhMock RequestContextHolder 的静态Mock
     * @param ip     要返回的IP地址
     */
    private void setupClientIp(MockedStatic<RequestContextHolder> rhMock, String ip) {
        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        rhMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);
        when(attrs.getRequest()).thenReturn(request);
        when(request.getHeader("X-Forwarded-For")).thenReturn(ip);
    }

    // ==================== 1. getCaptcha 获取验证码 ====================

    @Nested
    @DisplayName("getCaptcha 获取验证码")
    class GetCaptchaTest {

        @Test
        @DisplayName("正常生成验证码：返回key和base64图片，Redis存储小写化、5分钟过期")
        void getCaptcha_success() {
            // 执行：获取验证码
            CaptchaVO vo = authService.getCaptcha();

            // 验证：返回的VO不为空，包含key和图片
            assertThat(vo).isNotNull();
            assertThat(vo.getCaptchaKey()).isNotNull().isNotEmpty();
            assertThat(vo.getCaptchaImage()).isNotNull().isNotEmpty();

            // 验证：验证码存入了Redis，key以"admin:captcha:"开头
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
            verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), timeoutCaptor.capture(), unitCaptor.capture());

            // key 以 "admin:captcha:" 开头
            assertThat(keyCaptor.getValue()).startsWith("admin:captcha:");
            // 验证码统一转小写存储（不区分大小写）
            String storedCode = valueCaptor.getValue();
            assertThat(storedCode).isEqualTo(storedCode.toLowerCase());
            // 有效期5分钟
            assertThat(timeoutCaptor.getValue()).isEqualTo(5L);
            assertThat(unitCaptor.getValue()).isEqualTo(TimeUnit.MINUTES);
        }
    }

    // ==================== 2. login 验证码校验 ====================

    @Nested
    @DisplayName("login 验证码校验")
    class LoginCaptchaTest {

        @Test
        @DisplayName("验证码key为空：抛ADMIN_CAPTCHA_ERROR异常")
        void captchaKeyNull_throwsException() {
            AdminLoginDTO dto = buildLoginDTO("admin", "password", null, "abcd");

            assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_CAPTCHA_ERROR.getCode());
        }

        @Test
        @DisplayName("验证码为空：抛ADMIN_CAPTCHA_ERROR异常")
        void captchaCodeEmpty_throwsException() {
            AdminLoginDTO dto = buildLoginDTO("admin", "password", "test-key", "");

            assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_CAPTCHA_ERROR.getCode());
        }

        @Test
        @DisplayName("验证码错误：抛ADMIN_CAPTCHA_ERROR异常，且验证码用完即删（防止重复使用）")
        void captchaWrong_throwsException_andDeleted() {
            AdminLoginDTO dto = buildLoginDTO("admin", "password", "test-key", "wrong");
            // Redis中存的正确验证码是"abcd"，用户输入了"wrong"
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");

            assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_CAPTCHA_ERROR.getCode());

            // 安全考虑：验证码用完即删，即使输错了也删除，防止攻击者反复尝试同一个验证码
            verify(redisTemplate).delete("admin:captcha:test-key");
        }

        @Test
        @DisplayName("验证码已过期（Redis中不存在）：抛ADMIN_CAPTCHA_ERROR异常")
        void captchaExpired_throwsException() {
            AdminLoginDTO dto = buildLoginDTO("admin", "password", "test-key", "abcd");
            // Redis返回null，表示验证码已过期或不存在
            when(valueOps.get("admin:captcha:test-key")).thenReturn(null);

            assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_CAPTCHA_ERROR.getCode());

            // 即使验证码不存在，也尝试删除（无害操作，确保万无一失）
            verify(redisTemplate).delete("admin:captcha:test-key");
        }

        @Test
        @DisplayName("验证码不区分大小写：输入大写也能通过校验（存储时统一小写）")
        void captchaCaseInsensitive_matchesLowercaseStored() {
            // Redis中存的是小写"abcd"，用户输入大写"ABCD"
            AdminLoginDTO dto = buildLoginDTO("admin", "password", "test-key", "ABCD");
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            // 用户不存在，会抛ADMIN_PASSWORD_ERROR（说明验证码校验已通过）
            when(adminUserMapper.selectOne(any())).thenReturn(null);

            try (MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {
                setupClientIp(rhMock, "127.0.0.1");

                // 验证码校验通过（大小写不敏感），走到用户查询阶段，因用户不存在抛ADMIN_PASSWORD_ERROR
                assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PASSWORD_ERROR.getCode());
            }
        }
    }

    // ==================== 3. login 用户查询 ====================

    @Nested
    @DisplayName("login 用户查询")
    class LoginUserTest {

        @Test
        @DisplayName("用户不存在：抛ADMIN_PASSWORD_ERROR（安全考虑：不透露用户名是否存在），并记录登录日志")
        void userNotFound_throwsPasswordError_andRecordsLog() {
            AdminLoginDTO dto = buildLoginDTO("admin", "password", "test-key", "abcd");
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(null);

            try (MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {
                setupClientIp(rhMock, "192.168.1.100");

                // 安全考虑：即使用户名不存在，也返回"用户名或密码错误"，不告诉攻击者具体原因
                assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PASSWORD_ERROR.getCode());

                // 验证：记录了登录失败日志（安全审计需要）
                verify(adminLoginLogService).recordLoginLog(
                    "admin", "192.168.1.100", null, null, false, "用户名或密码错误");
            }
        }
    }

    // ==================== 4. login 账号锁定 ====================

    @Nested
    @DisplayName("login 账号锁定检查")
    class LoginLockTest {

        @Test
        @DisplayName("失败次数>=5：账号已锁定，抛ADMIN_LOGIN_LOCKED异常并记录日志")
        void accountLocked_throwsException() {
            AdminLoginDTO dto = buildLoginDTO("admin", "password", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hash", 1);
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(user);
            // Redis中记录的失败次数为5（达到上限）
            when(valueOps.get("admin:login_fail:admin")).thenReturn("5");

            try (MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {
                setupClientIp(rhMock, "10.0.0.1");

                assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_LOGIN_LOCKED.getCode());

                // 验证：记录了"账号已锁定"的登录日志
                verify(adminLoginLogService).recordLoginLog(
                    "admin", "10.0.0.1", null, null, false, "账号已锁定");
            }
        }
    }

    // ==================== 5. login 密码校验 ====================

    @Nested
    @DisplayName("login 密码校验")
    class LoginPasswordTest {

        @Test
        @DisplayName("密码错误（失败次数<5）：失败次数+1，抛ADMIN_PASSWORD_ERROR，不记录安全事件")
        void passwordWrong_belowMax_incrementsFailCount() {
            AdminLoginDTO dto = buildLoginDTO("admin", "wrongPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(user);
            // valueOps.get(failKey) 被调用3次：
            // 第1次：锁定检查 → null（没有失败记录）
            // 第2次：incrementLoginFailCount内部读取 → null
            // 第3次：增加后重新读取判断是否达到上限 → "1"
            when(valueOps.get("admin:login_fail:admin")).thenReturn(null, null, "1");

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {
                // 密码校验失败
                bcryptMock.when(() -> BCrypt.checkpw("wrongPassword", "hashed_password")).thenReturn(false);
                setupClientIp(rhMock, "10.0.0.2");

                assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PASSWORD_ERROR.getCode());

                // 验证：失败次数被设为1，有效期30分钟
                verify(valueOps).set("admin:login_fail:admin", "1", 30L, TimeUnit.MINUTES);
                // 验证：失败次数没达到5次，不记录安全事件
                verify(adminSecurityEventService, never())
                    .recordSecurityEvent(anyString(), any(), anyString(), anyString(), anyString());
                // 验证：记录了登录失败日志
                verify(adminLoginLogService).recordLoginLog(
                    "admin", "10.0.0.2", null, null, false, "用户名或密码错误");
            }
        }

        @Test
        @DisplayName("密码错误达到5次：记录BRUTE_FORCE安全事件（暴力破解）")
        void passwordWrong_reachesMax_recordsSecurityEvent() {
            AdminLoginDTO dto = buildLoginDTO("admin", "wrongPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(user);
            // valueOps.get(failKey) 被调用3次：
            // 第1次：锁定检查 → "4"（4<5，还没锁定）
            // 第2次：incrementLoginFailCount内部读取 → "4"
            // 第3次：增加后重新读取判断是否达到上限 → "5"（5>=5，触发安全事件）
            when(valueOps.get("admin:login_fail:admin")).thenReturn("4", "4", "5");

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {
                bcryptMock.when(() -> BCrypt.checkpw("wrongPassword", "hashed_password")).thenReturn(false);
                setupClientIp(rhMock, "10.0.0.3");

                assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PASSWORD_ERROR.getCode());

                // 验证：记录了暴力破解安全事件
                verify(adminSecurityEventService).recordSecurityEvent(
                    "BRUTE_FORCE", 1L, "admin",
                    "连续5次登录失败，账号已锁定30分钟", "10.0.0.3");
            }
        }
    }

    // ==================== 6. login 账号状态检查 ====================

    @Nested
    @DisplayName("login 账号状态检查")
    class LoginStatusTest {

        @Test
        @DisplayName("账号禁用（status=0）：抛ADMIN_DISABLED异常并记录日志")
        void accountDisabled_throwsException() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            // status=0 表示账号被禁用
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 0);
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(user);
            when(valueOps.get("admin:login_fail:admin")).thenReturn(null);

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {
                // 密码正确（注意：禁用检查在密码校验之后，所以密码必须正确才能走到禁用判断）
                bcryptMock.when(() -> BCrypt.checkpw("correctPassword", "hashed_password")).thenReturn(true);
                setupClientIp(rhMock, "10.0.0.4");

                assertThatThrownBy(() -> authService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DISABLED.getCode());

                // 验证：记录了"账号已被禁用"的登录日志
                verify(adminLoginLogService).recordLoginLog(
                    "admin", "10.0.0.4", null, null, false, "账号已被禁用");
            }
        }
    }

    // ==================== 7. login 登录成功 ====================

    @Nested
    @DisplayName("login 登录成功")
    class LoginSuccessTest {

        /**
         * 设置登录成功所需的全部Mock
         * <p>
         * 登录成功流程涉及10步，这里把验证码校验之后的全部依赖都准备好。
         * 权限和角色查询返回空列表（简化测试，权限查询逻辑在AdminStpInterfaceImplTest中详测）。
         * </p>
         *
         * @return 创建的SaSession Mock对象，供调用方做verify验证
         */
        private SaSession setupSuccessFlow(AdminUser user, String password,
                                      MockedStatic<StpUtil> stpMock,
                                      MockedStatic<BCrypt> bcryptMock,
                                      MockedStatic<RequestContextHolder> rhMock) {
            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(user);
            when(valueOps.get("admin:login_fail:" + user.getUsername())).thenReturn(null);
            bcryptMock.when(() -> BCrypt.checkpw(password, user.getPassword())).thenReturn(true);
            // 权限和角色查询返回空列表（登录成功但无权限）
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 更新登录IP和时间（用any(AdminUser.class)避免updateById重载歧义）
            when(adminUserMapper.updateById(any(AdminUser.class))).thenReturn(1);
            // Sa-Token Session
            SaSession session = mock(SaSession.class);
            stpMock.when(StpUtil::getSession).thenReturn(session);
            stpMock.when(StpUtil::getTokenValue).thenReturn("test-token-value");
            // 客户端IP
            setupClientIp(rhMock, "192.168.1.200");
            return session;
        }

        @Test
        @DisplayName("登录成功：返回包含Token、用户信息、权限、角色的VO")
        void loginSuccess_returnsVO() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {

                setupSuccessFlow(user, "correctPassword", stpMock, bcryptMock, rhMock);

                AdminLoginVO vo = authService.login(dto);

                // 验证返回的VO包含正确信息
                assertThat(vo).isNotNull();
                assertThat(vo.getToken()).isEqualTo("test-token-value");
                assertThat(vo.getAdminUserId()).isEqualTo(1L);
                assertThat(vo.getUsername()).isEqualTo("admin");
                assertThat(vo.getNickname()).isEqualTo("测试管理员");
                assertThat(vo.getAvatar()).isEqualTo("avatar.jpg");
                // 权限和角色为空集（因为mock的mapper返回空列表）
                assertThat(vo.getPermissions()).isNotNull().isEmpty();
                assertThat(vo.getRoles()).isNotNull().isEmpty();
            }
        }

        @Test
        @DisplayName("登录成功：Sa-Token登录并写入Session（adminUserId、adminUsername）")
        void loginSuccess_setsSessionAttributes() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {

                // 用setupSuccessFlow返回的session做验证（避免session引用不一致）
                SaSession session = setupSuccessFlow(user, "correctPassword", stpMock, bcryptMock, rhMock);

                authService.login(dto);

                // 验证：调用了Sa-Token登录
                stpMock.verify(() -> StpUtil.login(1L));
                // 验证：Session中写入了管理员ID和用户名
                verify(session).set("adminUserId", 1L);
                verify(session).set("adminUsername", "admin");
            }
        }

        @Test
        @DisplayName("登录成功：更新管理员的最后登录IP和时间")
        void loginSuccess_updatesUserIpAndTime() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {

                setupSuccessFlow(user, "correctPassword", stpMock, bcryptMock, rhMock);

                authService.login(dto);

                // 验证：调用了updateById更新管理员信息
                ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
                verify(adminUserMapper).updateById(userCaptor.capture());
                AdminUser updatedUser = userCaptor.getValue();
                // 最后登录IP应该是我们mock的IP
                assertThat(updatedUser.getLastLoginIp()).isEqualTo("192.168.1.200");
                // 最后登录时间应该是刚设置的（不为null）
                assertThat(updatedUser.getLastLoginTime()).isNotNull();
            }
        }

        @Test
        @DisplayName("登录成功：密码正确后清除失败计数")
        void loginSuccess_clearsFailCount() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {

                setupSuccessFlow(user, "correctPassword", stpMock, bcryptMock, rhMock);

                authService.login(dto);

                // 验证：密码正确后清除了失败计数（防止下次登录还受影响）
                verify(redisTemplate).delete("admin:login_fail:admin");
            }
        }

        @Test
        @DisplayName("登录成功：记录成功登录日志（success=true，failReason=null）")
        void loginSuccess_recordsLoginLog() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {

                setupSuccessFlow(user, "correctPassword", stpMock, bcryptMock, rhMock);

                authService.login(dto);

                // 验证：记录了成功登录日志
                verify(adminLoginLogService).recordLoginLog(
                    "admin", "192.168.1.200", null, null, true, null);
            }
        }

        @Test
        @DisplayName("登录成功：VO中包含从数据库查询到的权限和角色")
        void loginSuccess_returnsPermissionsAndRoles() {
            AdminLoginDTO dto = buildLoginDTO("admin", "correctPassword", "test-key", "abcd");
            AdminUser user = buildAdminUser(1L, "admin", "hashed_password", 1);

            // 构造测试数据：管理员有1个角色（admin）和1个权限（user:list）
            AdminUserRole userRole = new AdminUserRole();
            userRole.setUserId(1L);
            userRole.setRoleId(10L);

            AdminRole role = new AdminRole();
            role.setId(10L);
            role.setRoleKey("admin");
            role.setStatus(1);

            AdminRolePermission rolePerm = new AdminRolePermission();
            rolePerm.setRoleId(10L);
            rolePerm.setPermissionId(100L);

            AdminPermission permission = new AdminPermission();
            permission.setId(100L);
            permission.setPermissionKey("user:list");
            permission.setStatus(1);

            when(valueOps.get("admin:captcha:test-key")).thenReturn("abcd");
            when(adminUserMapper.selectOne(any())).thenReturn(user);
            when(valueOps.get("admin:login_fail:admin")).thenReturn(null);
            // 两次调用（getAdminPermissions + getAdminRoles）都返回同样的userRole列表
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            // 两次调用（getAdminPermissions + getAdminRoles）都返回同样的role列表
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));
            when(adminRolePermissionMapper.selectList(any())).thenReturn(Collections.singletonList(rolePerm));
            when(adminPermissionMapper.selectList(any())).thenReturn(Collections.singletonList(permission));
            when(adminUserMapper.updateById(any(AdminUser.class))).thenReturn(1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
                 MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<RequestContextHolder> rhMock = mockStatic(RequestContextHolder.class)) {

                bcryptMock.when(() -> BCrypt.checkpw("correctPassword", "hashed_password")).thenReturn(true);
                SaSession session = mock(SaSession.class);
                stpMock.when(StpUtil::getSession).thenReturn(session);
                stpMock.when(StpUtil::getTokenValue).thenReturn("test-token");
                setupClientIp(rhMock, "192.168.1.200");

                AdminLoginVO vo = authService.login(dto);

                // 验证：VO中包含正确的权限和角色
                assertThat(vo.getPermissions()).containsExactly("user:list");
                assertThat(vo.getRoles()).containsExactly("admin");
            }
        }
    }

    // ==================== 8. logout 管理员登出 ====================

    @Nested
    @DisplayName("logout 管理员登出")
    class LogoutTest {

        @Test
        @DisplayName("正常登出：获取当前登录ID并调用StpUtil.logout")
        void logout_success() {
            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                authService.logout();

                // 验证：先获取登录ID，再执行登出
                stpMock.verify(StpUtil::getLoginIdAsLong);
                stpMock.verify(StpUtil::logout);
            }
        }
    }
}
