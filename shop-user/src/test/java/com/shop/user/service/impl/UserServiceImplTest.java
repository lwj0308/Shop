package com.shop.user.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.dto.TokenRefreshDTO;
import com.shop.model.user.dto.UserLoginDTO;
import com.shop.model.user.dto.UserPasswordDTO;
import com.shop.model.user.dto.UserRegisterDTO;
import com.shop.model.user.dto.UserUpdateDTO;
import com.shop.model.user.entity.User;
import com.shop.model.user.entity.UserAccount;
import com.shop.model.user.entity.UserLoginLog;
import com.shop.model.user.vo.UserLoginVO;
import com.shop.model.user.vo.UserVO;
import com.shop.user.mapper.UserAccountMapper;
import com.shop.user.mapper.UserLoginLogMapper;
import com.shop.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务 UserServiceImpl 的单元测试
 * <p>
 * 这个测试类验证用户注册、登录、刷新Token、查询信息、修改信息、修改密码等核心功能。
 * 小白理解：我们把真正访问数据库的 Mapper 和访问 Redis 的模板都"假装"一下（Mock），
 * 这样测试不需要真的连数据库和 Redis，跑得又快又稳定。
 * </p>
 * <p>
 * 几个关键点说明：
 * - UserServiceImpl 里的 BCryptPasswordEncoder 是直接 new 出来的（不是构造注入），
 *   所以测试里我们用真实的加密器来构造测试密码，这样能验证真实的加密/校验逻辑。
 * - UserServiceImpl 用到了 Sa-Token 的 StpUtil 静态方法（登录、获取Token等），
 *   测试时用 Mockito.mockStatic 把它"假装"一下，避免真的去操作 Sa-Token。
 * - Lambda 查询条件（比如 User::getPhone）需要 MyBatis-Plus 的字段缓存，
 *   所以用 @BeforeAll 提前初始化，否则会报 "can not find lambda cache" 错误。
 * </p>
 */
@DisplayName("用户服务 UserServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    /** 假装操作 user 表的 Mapper */
    @Mock
    private UserMapper userMapper;

    /** 假装操作 user_account 表的 Mapper */
    @Mock
    private UserAccountMapper userAccountMapper;

    /** 假装操作 user_login_log 表的 Mapper */
    @Mock
    private UserLoginLogMapper userLoginLogMapper;

    /** 假装 Redis 模板，用来存登录失败次数 */
    @Mock
    private StringRedisTemplate redisTemplate;

    /** 假装 Redis 的 Value 操作对象，redisTemplate.opsForValue() 返回的就是它 */
    @Mock
    private ValueOperations<String, String> valueOps;

    /** 被测试的用户服务，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private UserServiceImpl userService;

    /** 真实的 BCrypt 加密器，用来构造测试密码（和 Service 里用的是同一种算法） */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 测试用手机号 */
    private static final String PHONE = "13812345678";

    /** 测试用用户ID */
    private static final Long USER_ID = 1L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：register 和 login 方法用到了 .eq(User::getPhone, ...) 这种写法，
     * MyBatis-Plus 需要知道 User::getPhone 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    /**
     * 每个测试前，统一设置 Redis 的 opsForValue 返回值
     * <p>
     * 小白理解：登录相关方法会调用 redisTemplate.opsForValue().get/increment，
     * 我们让 opsForValue() 返回一个 Mock 对象，这样调用 .get() 时默认返回 null（表示没有锁定记录）。
     * 用 lenient() 是因为不是所有测试都会用到 Redis（比如注册、查询信息不会用到），
     * lenient 表示"没用上也不报错"，避免严格模式下的多余 stub 报错。
     * </p>
     */
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个注册请求DTO
     *
     * @param phone           手机号
     * @param password        密码
     * @param confirmPassword 确认密码
     * @return 构造好的 UserRegisterDTO
     */
    private UserRegisterDTO buildRegisterDTO(String phone, String password, String confirmPassword) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setPhone(phone);
        dto.setPassword(password);
        dto.setConfirmPassword(confirmPassword);
        return dto;
    }

    /**
     * 构造一个登录请求DTO
     *
     * @param phone    手机号
     * @param password 密码
     * @return 构造好的 UserLoginDTO
     */
    private UserLoginDTO buildLoginDTO(String phone, String password) {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setPhone(phone);
        dto.setPassword(password);
        return dto;
    }

    /**
     * 构造一个用户实体（密码用真实 BCrypt 加密）
     *
     * @param id       用户ID
     * @param phone    手机号
     * @param password 明文密码（会被加密后塞进实体）
     * @param status   状态：0禁用 1正常
     * @return 构造好的 User
     */
    private User buildUser(Long id, String phone, String password, Integer status) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname("测试用户");
        user.setStatus(status);
        return user;
    }

    // ==================== 1. register 用户注册 ====================

    @Nested
    @DisplayName("register 用户注册")
    class RegisterTest {

        @Test
        @DisplayName("手机号已注册 → 抛出 USER_PHONE_EXISTS 异常")
        void register_phoneExists_throwsException() {
            // 场景：注册时数据库里已经存在这个手机号
            UserRegisterDTO dto = buildRegisterDTO(PHONE, "password1", "password1");
            // 假装数据库查到 count=1，表示手机号已存在
            when(userMapper.selectCount(any())).thenReturn(1L);

            // 验证：抛出"手机号已注册"异常，错误码 11003
            assertThatThrownBy(() -> userService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_PHONE_EXISTS.getCode());

            // 验证：校验失败，没有插入用户和账户
            verify(userMapper, never()).insert(any(User.class));
            verify(userAccountMapper, never()).insert(any(UserAccount.class));
        }

        @Test
        @DisplayName("正常注册 → 密码BCrypt加密并 insert 用户和账户")
        void register_normal_insertUserAndAccount() {
            // 场景：手机号没被注册，两次密码一致，正常完成注册
            UserRegisterDTO dto = buildRegisterDTO(PHONE, "password1", "password1");
            when(userMapper.selectCount(any())).thenReturn(0L);
            // 模拟 insert 时数据库自动回填主键ID（这样后面创建账户才能拿到 userId）
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(USER_ID);
                return 1;
            });

            // 执行注册
            userService.register(dto);

            // 验证：插入了用户，且密码是 BCrypt 加密后的（不是明文）
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getPhone()).isEqualTo(PHONE);
            assertThat(savedUser.getStatus()).isEqualTo(1);
            // 默认昵称取手机号后4位：13812345678 → "用户5678"
            assertThat(savedUser.getNickname()).isEqualTo("用户5678");
            // 密码不能是明文，且用真实加密器能匹配上原始密码
            assertThat(savedUser.getPassword()).isNotEqualTo("password1");
            assertThat(passwordEncoder.matches("password1", savedUser.getPassword())).isTrue();

            // 验证：同时插入了用户账户，余额0、积分0、userId 正确
            ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
            verify(userAccountMapper).insert(accountCaptor.capture());
            UserAccount savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getUserId()).isEqualTo(USER_ID);
            assertThat(savedAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(savedAccount.getPoints()).isEqualTo(0);
        }
    }

    // ==================== 2. login 用户登录 ====================

    @Nested
    @DisplayName("login 用户登录")
    class LoginTest {

        @Test
        @DisplayName("用户不存在 → 抛出 USER_NOT_FOUND 异常")
        void login_userNotFound_throwsException() {
            // 场景：用没注册过的手机号登录
            UserLoginDTO dto = buildLoginDTO(PHONE, "password1");
            when(userMapper.selectOne(any())).thenReturn(null);

            // 验证：抛出"用户不存在"异常，错误码 11001
            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_NOT_FOUND.getCode());

            // 验证：记录了登录失败日志（防止有人用登录接口探测手机号是否注册）
            verify(userLoginLogMapper).insert(any(UserLoginLog.class));
        }

        @Test
        @DisplayName("密码错误 → 抛出 USER_PASSWORD_ERROR 异常")
        void login_passwordWrong_throwsException() {
            // 场景：手机号存在，但密码输错了
            UserLoginDTO dto = buildLoginDTO(PHONE, "wrongpassword");
            // 数据库里存的正确密码是 "correctpassword"
            User user = buildUser(USER_ID, PHONE, "correctpassword", 1);
            when(userMapper.selectOne(any())).thenReturn(user);

            // 验证：抛出"密码错误"异常，错误码 11002
            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_PASSWORD_ERROR.getCode());

            // 验证：记录了登录失败日志
            verify(userLoginLogMapper).insert(any(UserLoginLog.class));
        }

        @Test
        @DisplayName("用户被禁用 → 抛出 USER_DISABLED 异常")
        void login_userDisabled_throwsException() {
            // 场景：密码正确，但账号被管理员禁用了（status=0）
            // 注意：禁用检查在密码校验之后，所以密码必须正确才能走到禁用判断
            UserLoginDTO dto = buildLoginDTO(PHONE, "password1");
            User user = buildUser(USER_ID, PHONE, "password1", 0); // status=0 表示禁用
            when(userMapper.selectOne(any())).thenReturn(user);

            // 验证：抛出"用户已被禁用"异常，错误码 11004
            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_DISABLED.getCode());

            // 验证：记录了登录失败日志
            verify(userLoginLogMapper).insert(any(UserLoginLog.class));
        }

        @Test
        @DisplayName("正常登录 → 返回 UserLoginVO（含双Token和脱敏后的用户信息）")
        void login_success_returnsLoginVO() {
            // 场景：手机号存在、密码正确、账号正常，登录成功
            UserLoginDTO dto = buildLoginDTO(PHONE, "password1");
            User user = buildUser(USER_ID, PHONE, "password1", 1);
            when(userMapper.selectOne(any())).thenReturn(user);

            // Sa-Token 是静态方法调用，用 mockStatic 假装一下
            try (MockedStatic<StpUtil> stpMock = Mockito.mockStatic(StpUtil.class)) {
                // 假装 getTokenInfo 返回一个带 tokenValue 的 Token 信息
                SaTokenInfo tokenInfo = new SaTokenInfo();
                tokenInfo.setTokenValue("test-token-value");
                stpMock.when(StpUtil::getTokenInfo).thenReturn(tokenInfo);

                // 执行登录
                UserLoginVO result = userService.login(dto);

                // 验证：调用了 Sa-Token 的登录方法（传入用户ID）
                stpMock.verify(() -> StpUtil.login(USER_ID));

                // 验证：返回的 VO 包含双Token和用户信息
                assertThat(result).isNotNull();
                assertThat(result.getAccessToken()).isEqualTo("test-token-value");
                assertThat(result.getRefreshToken()).isEqualTo("test-token-value");
                UserVO userInfo = result.getUserInfo();
                assertThat(userInfo).isNotNull();
                assertThat(userInfo.getId()).isEqualTo(USER_ID);
                // 手机号脱敏：13812345678 → 138****5678
                assertThat(userInfo.getPhone()).isEqualTo("138****5678");
                assertThat(userInfo.getNickname()).isEqualTo("测试用户");
            }

            // 验证：登录成功后清除了该手机号的失败计数
            verify(redisTemplate).delete("user:login:fail:" + PHONE);
            // 验证：记录了登录成功日志
            verify(userLoginLogMapper).insert(any(UserLoginLog.class));
        }
    }

    // ==================== 3. refreshToken 刷新Token ====================

    @Nested
    @DisplayName("refreshToken 刷新Token")
    class RefreshTokenTest {

        @Test
        @DisplayName("refreshToken 无效 → 抛出 UNAUTHORIZED 异常")
        void refreshToken_invalidToken_throwsException() {
            // 场景：用过一个无效/过期的 refreshToken 去换新 Token
            TokenRefreshDTO dto = new TokenRefreshDTO();
            dto.setRefreshToken("invalid-token");

            try (MockedStatic<StpUtil> stpMock = Mockito.mockStatic(StpUtil.class)) {
                // 假装 Sa-Token 根据 token 查不到用户ID（返回 null 表示无效）
                stpMock.when(() -> StpUtil.getLoginIdByToken("invalid-token")).thenReturn(null);

                // 验证：抛出"未登录"异常，错误码 401
                assertThatThrownBy(() -> userService.refreshToken(dto))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ErrorCode.UNAUTHORIZED.getCode());
            }

            // 验证：Token 无效时不会去查用户
            verify(userMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("正常刷新 → 返回新的 UserLoginVO（含新的 accessToken）")
        void refreshToken_normal_returnsNewLoginVO() {
            // 场景：refreshToken 有效，换到新的 accessToken
            TokenRefreshDTO dto = new TokenRefreshDTO();
            dto.setRefreshToken("valid-refresh-token");
            User user = buildUser(USER_ID, PHONE, "password1", 1);

            try (MockedStatic<StpUtil> stpMock = Mockito.mockStatic(StpUtil.class)) {
                // 假装根据 refreshToken 查到用户ID=1，并返回新的 accessToken
                stpMock.when(() -> StpUtil.getLoginIdByToken("valid-refresh-token")).thenReturn(USER_ID);
                stpMock.when(() -> StpUtil.getTokenValueByLoginId(USER_ID)).thenReturn("new-access-token");
                when(userMapper.selectById(USER_ID)).thenReturn(user);

                // 执行刷新
                UserLoginVO result = userService.refreshToken(dto);

                // 验证：返回了新的 accessToken，refreshToken 保持不变
                assertThat(result).isNotNull();
                assertThat(result.getAccessToken()).isEqualTo("new-access-token");
                assertThat(result.getRefreshToken()).isEqualTo("valid-refresh-token");
                // 验证：用户信息正确，手机号脱敏
                assertThat(result.getUserInfo().getId()).isEqualTo(USER_ID);
                assertThat(result.getUserInfo().getPhone()).isEqualTo("138****5678");
            }
        }
    }

    // ==================== 4. getUserInfo 获取用户信息 ====================

    @Nested
    @DisplayName("getUserInfo 获取用户信息")
    class GetUserInfoTest {

        @Test
        @DisplayName("用户不存在 → 抛出 USER_NOT_FOUND 异常")
        void getUserInfo_userNotFound_throwsException() {
            // 场景：查一个不存在的用户ID
            when(userMapper.selectById(999L)).thenReturn(null);

            // 验证：抛出"用户不存在"异常
            assertThatThrownBy(() -> userService.getUserInfo(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常获取 → 返回 UserVO（手机号脱敏，不含密码）")
        void getUserInfo_normal_returnsUserVO() {
            // 场景：用户存在，正常返回信息
            User user = buildUser(USER_ID, PHONE, "password1", 1);
            user.setAvatar("avatar.jpg");
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            // 执行查询
            UserVO vo = userService.getUserInfo(USER_ID);

            // 验证：返回的 VO 字段正确，手机号脱敏
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(USER_ID);
            assertThat(vo.getPhone()).isEqualTo("138****5678");
            assertThat(vo.getNickname()).isEqualTo("测试用户");
            assertThat(vo.getAvatar()).isEqualTo("avatar.jpg");
            assertThat(vo.getStatus()).isEqualTo(1);
        }
    }

    // ==================== 5. updateUserInfo 修改个人信息 ====================

    @Nested
    @DisplayName("updateUserInfo 修改个人信息")
    class UpdateUserInfoTest {

        @Test
        @DisplayName("正常更新 → 调用 updateById 且字段设置正确")
        void updateUserInfo_normal_updateById() {
            // 场景：修改昵称和头像
            // 说明：当前 updateUserInfo 实现没有先校验用户是否存在，直接调用 updateById，
            // 所以这里只验证正常更新路径（不测"用户不存在抛异常"，因为代码不会抛）。
            UserUpdateDTO dto = new UserUpdateDTO();
            dto.setNickname("新昵称");
            dto.setAvatar("new-avatar.jpg");

            // 执行修改
            userService.updateUserInfo(USER_ID, dto);

            // 验证：调用了 updateById，且传入的实体 id、昵称、头像都正确
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(captor.capture());
            User updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(USER_ID);
            assertThat(updated.getNickname()).isEqualTo("新昵称");
            assertThat(updated.getAvatar()).isEqualTo("new-avatar.jpg");
        }
    }

    // ==================== 6. updatePassword 修改密码 ====================

    @Nested
    @DisplayName("updatePassword 修改密码")
    class UpdatePasswordTest {

        @Test
        @DisplayName("旧密码错误 → 抛出异常（USER_PASSWORD_ERROR + '旧密码错误'）")
        void updatePassword_oldPasswordWrong_throwsException() {
            // 场景：修改密码时旧密码输错了
            // 说明：源码实际抛的是 USER_PASSWORD_ERROR(11002) + 消息"旧密码错误"，
            // 而非 ErrorCode 里的 USER_OLD_PASSWORD_ERROR，这里按真实行为断言。
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword("wrong-old");
            dto.setNewPassword("newpassword1");
            User user = buildUser(USER_ID, PHONE, "correct-old", 1);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            // 验证：抛出异常，错误码 11002，消息"旧密码错误"
            assertThatThrownBy(() -> userService.updatePassword(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_PASSWORD_ERROR.getCode())
                    .hasMessage("旧密码错误");

            // 验证：旧密码错误时不会更新密码
            verify(userMapper, never()).updateById(any(User.class));
        }

        @Test
        @DisplayName("正常修改密码 → 更新为新密码并强制下线")
        void updatePassword_normal_updateAndLogout() {
            // 场景：旧密码正确，改成新密码
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword("oldpassword");
            dto.setNewPassword("newpassword1");
            User user = buildUser(USER_ID, PHONE, "oldpassword", 1);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            // StpUtil.logout 是静态方法，用 mockStatic 假装
            try (MockedStatic<StpUtil> stpMock = Mockito.mockStatic(StpUtil.class)) {
                // 执行修改密码
                userService.updatePassword(USER_ID, dto);

                // 验证：调用了强制下线（logout）
                stpMock.verify(() -> StpUtil.logout(USER_ID));
            }

            // 验证：调用了 updateById，且新密码是 BCrypt 加密后的、能匹配上新密码
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(captor.capture());
            User updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(USER_ID);
            assertThat(passwordEncoder.matches("newpassword1", updated.getPassword())).isTrue();
        }
    }
}
