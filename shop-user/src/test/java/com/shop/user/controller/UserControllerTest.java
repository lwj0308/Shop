package com.shop.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.dto.UserPasswordDTO;
import com.shop.model.user.dto.UserUpdateDTO;
import com.shop.model.user.entity.User;
import com.shop.model.user.vo.UserVO;
import com.shop.user.mapper.UserMapper;
import com.shop.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 切片测试（B-S-02）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos等中间件。
 * </p>
 * <p>
 * 为什么不用 @WebMvcTest？
 * Spring Boot 4.0 的 @WebMvcTest 会尝试加载 UserApplication 主类，
 * 而 UserApplication 有 @EnableDiscoveryClient 会连 Nacos，测试环境连不上就报错。
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
 * <p>
 * 注意：UserController 通过 UserContext.getUserId() 获取当前登录用户ID，
 * 而 UserContext.getUserId() 内部调用 StpUtil.getLoginIdAsLong()，
 * 所以我们仍然 mock StpUtil 即可。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserController 切片测试")
class UserControllerTest {

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 假装用户服务 */
    @Mock
    private UserService userService;

    /** 假装用户Mapper，管理后台直接查询用 */
    @Mock
    private UserMapper userMapper;

    /** Sa-Token静态方法mock（模拟登录状态） */
    private MockedStatic<StpUtil> stpUtilMock;

    /** 测试中模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：adminListUsers 方法用到了 .eq(User::getStatus, ...) 这种写法，
     * MyBatis-Plus 需要知道 User::getStatus 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，切片测试没有完整 Spring 环境，所以要手动初始化。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 UserController 实例，注入 mock 依赖
        UserController controller = new UserController(userService, userMapper);

        // 2. 用 Standalone 方式构建 MockMvc，手动注册全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 3. mock StpUtil.getLoginIdAsLong()，让 UserContext.getUserId() 返回模拟的用户ID
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
     * 构造一个用户VO（用于Service返回值）
     *
     * @param id       用户ID
     * @param phone    手机号
     * @param nickname 昵称
     * @param status   状态：0禁用 1正常
     * @return 构造好的 UserVO
     */
    private UserVO buildUserVO(Long id, String phone, String nickname, Integer status) {
        UserVO vo = new UserVO();
        vo.setId(id);
        vo.setPhone(phone);
        vo.setNickname(nickname);
        vo.setAvatar("https://example.com/avatar.png");
        vo.setStatus(status);
        vo.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return vo;
    }

    /**
     * 构造一个用户实体（用于Mapper返回值）
     *
     * @param id       用户ID
     * @param phone    手机号
     * @param nickname 昵称
     * @param status   状态：0禁用 1正常
     * @return 构造好的 User
     */
    private User buildUser(Long id, String phone, String nickname, Integer status) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setNickname(nickname);
        user.setAvatar("https://example.com/avatar.png");
        user.setStatus(status);
        user.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return user;
    }

    // ==================== 获取用户信息 ====================

    @Nested
    @DisplayName("获取用户信息 GET /user/info")
    class GetUserInfoTest {

        @Test
        @DisplayName("正常获取用户信息 → 返回200和用户信息")
        void getUserInfo_success_returnsUserInfo() throws Exception {
            // 1. 准备Service返回的用户信息
            UserVO userVO = buildUserVO(MOCK_USER_ID, "13812345678", "小明", 1);

            // 2. mock Service行为
            when(userService.getUserInfo(eq(MOCK_USER_ID))).thenReturn(userVO);

            // 3. 发送请求并验证结果
            mockMvc.perform(get("/user/info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(MOCK_USER_ID.intValue()))
                    .andExpect(jsonPath("$.data.nickname").value("小明"))
                    .andExpect(jsonPath("$.data.status").value(1));

            // 4. 验证Service被正确调用
            verify(userService).getUserInfo(eq(MOCK_USER_ID));
        }

        @Test
        @DisplayName("用户不存在 → Service抛业务异常 → 返回业务错误码")
        void getUserInfo_notFound_throwsBusinessException() throws Exception {
            // mock Service抛出"用户不存在"业务异常
            when(userService.getUserInfo(anyLong()))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get("/user/info"))
                    .andExpect(status().isOk()) // BusinessException返回200
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
        }
    }

    // ==================== 修改个人信息 ====================

    @Nested
    @DisplayName("修改个人信息 PUT /user/info")
    class UpdateUserInfoTest {

        @Test
        @DisplayName("正常修改个人信息 → 返回200")
        void updateUserInfo_success() throws Exception {
            UserUpdateDTO dto = new UserUpdateDTO();
            dto.setNickname("新昵称");
            dto.setAvatar("https://example.com/new.png");

            mockMvc.perform(put("/user/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"));

            // 验证Service被正确调用，参数中的用户ID是mock的登录用户
            verify(userService).updateUserInfo(eq(MOCK_USER_ID), any(UserUpdateDTO.class));
        }

        @Test
        @DisplayName("昵称超长 → 参数校验失败返回400")
        void updateUserInfo_nicknameTooLong_returns400() throws Exception {
            UserUpdateDTO dto = new UserUpdateDTO();
            // 构造一个超过50个字符的昵称
            dto.setNickname("a".repeat(51));

            mockMvc.perform(put("/user/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));

            // 校验失败时不应调用Service
            verify(userService, never()).updateUserInfo(anyLong(), any(UserUpdateDTO.class));
        }

        @Test
        @DisplayName("Service抛业务异常 → 返回业务错误码")
        void updateUserInfo_serviceThrowsBusinessException_returnsErrorCode() throws Exception {
            UserUpdateDTO dto = new UserUpdateDTO();
            dto.setNickname("已存在的昵称");

            // mock Service抛出"昵称已被使用"业务异常
            doThrow(new BusinessException(ErrorCode.USER_NICKNAME_EXISTS))
                    .when(userService).updateUserInfo(anyLong(), any(UserUpdateDTO.class));

            mockMvc.perform(put("/user/info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NICKNAME_EXISTS.getCode()));
        }
    }

    // ==================== 修改密码 ====================

    @Nested
    @DisplayName("修改密码 PUT /user/password")
    class UpdatePasswordTest {

        @Test
        @DisplayName("正常修改密码 → 返回200")
        void updatePassword_success() throws Exception {
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword("old123456");
            dto.setNewPassword("new123456");

            mockMvc.perform(put("/user/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("密码修改成功，请重新登录"));

            verify(userService).updatePassword(eq(MOCK_USER_ID), any(UserPasswordDTO.class));
        }

        @Test
        @DisplayName("旧密码为空 → 参数校验失败返回400")
        void updatePassword_oldPasswordEmpty_returns400() throws Exception {
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword(""); // 空字符串
            dto.setNewPassword("new123456");

            mockMvc.perform(put("/user/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));

            verify(userService, never()).updatePassword(anyLong(), any(UserPasswordDTO.class));
        }

        @Test
        @DisplayName("新密码太短 → 参数校验失败返回400")
        void updatePassword_newPasswordTooShort_returns400() throws Exception {
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword("old123456");
            dto.setNewPassword("ab123"); // 只有6位，不满足8-20位要求

            mockMvc.perform(put("/user/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));

            verify(userService, never()).updatePassword(anyLong(), any(UserPasswordDTO.class));
        }

        @Test
        @DisplayName("新密码无数字 → 参数校验失败返回400")
        void updatePassword_newPasswordNoDigit_returns400() throws Exception {
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword("old123456");
            dto.setNewPassword("abcdefgh"); // 只有字母没有数字，不满足"必须包含字母和数字"

            mockMvc.perform(put("/user/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));

            verify(userService, never()).updatePassword(anyLong(), any(UserPasswordDTO.class));
        }

        @Test
        @DisplayName("旧密码错误 → Service抛业务异常 → 返回业务错误码")
        void updatePassword_oldPasswordError_throwsBusinessException() throws Exception {
            UserPasswordDTO dto = new UserPasswordDTO();
            dto.setOldPassword("wrong123");
            dto.setNewPassword("new123456");

            // mock Service抛出"旧密码错误"业务异常
            doThrow(new BusinessException(ErrorCode.USER_OLD_PASSWORD_ERROR))
                    .when(userService).updatePassword(anyLong(), any(UserPasswordDTO.class));

            mockMvc.perform(put("/user/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_OLD_PASSWORD_ERROR.getCode()));
        }
    }

    // ==================== 管理后台：分页查询用户列表 ====================

    @Nested
    @DisplayName("管理后台：分页查询用户列表 GET /user/admin/list")
    class AdminListUsersTest {

        @Test
        @DisplayName("默认查询 → 返回200和分页数据")
        void adminListUsers_default_returnsPageResult() throws Exception {
            // 1. 准备分页查询结果
            User user1 = buildUser(1L, "13800000001", "用户A", 1);
            User user2 = buildUser(2L, "13800000002", "用户B", 0);
            Page<User> page = new Page<>(1, 10);
            page.setRecords(List.of(user1, user2));
            page.setTotal(2);

            // 2. mock Mapper行为（注意：LambdaQueryWrapper用any()匹配）
            when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

            // 3. 发送请求并验证结果
            mockMvc.perform(get("/user/admin/list")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.records[0].nickname").value("用户A"))
                    .andExpect(jsonPath("$.data.records[1].id").value(2));

            verify(userMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("按状态和关键词筛选 → 返回200和筛选结果")
        void adminListUsers_withStatusAndKeyword_returnsFilteredResult() throws Exception {
            User user = buildUser(1L, "13800000001", "小明", 1);
            Page<User> page = new Page<>(1, 10);
            page.setRecords(List.of(user));
            page.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

            mockMvc.perform(get("/user/admin/list")
                            .param("page", "1")
                            .param("size", "10")
                            .param("status", "1")
                            .param("keyword", "小明"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].nickname").value("小明"));

            verify(userMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("查询结果为空 → 返回200和空列表")
        void adminListUsers_emptyResult_returnsEmptyPage() throws Exception {
            Page<User> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

            mockMvc.perform(get("/user/admin/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.records").isArray());
        }
    }

    // ==================== 管理后台：查询用户详情 ====================

    @Nested
    @DisplayName("管理后台：查询用户详情 GET /user/admin/{userId}")
    class AdminGetUserByIdTest {

        @Test
        @DisplayName("正常获取用户详情 → 返回200和用户信息")
        void adminGetUserById_success() throws Exception {
            UserVO userVO = buildUserVO(2001L, "13800002001", "用户2001", 1);

            when(userService.getUserInfo(eq(2001L))).thenReturn(userVO);

            mockMvc.perform(get("/user/admin/{userId}", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(2001))
                    .andExpect(jsonPath("$.data.nickname").value("用户2001"));

            verify(userService).getUserInfo(eq(2001L));
        }

        @Test
        @DisplayName("用户不存在 → Service抛业务异常 → 返回业务错误码")
        void adminGetUserById_notFound_throwsBusinessException() throws Exception {
            when(userService.getUserInfo(eq(9999L)))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get("/user/admin/{userId}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
        }
    }

    // ==================== 管理后台：禁用用户 ====================

    @Nested
    @DisplayName("管理后台：禁用用户 PUT /user/admin/{userId}/disable")
    class AdminDisableUserTest {

        @Test
        @DisplayName("正常禁用用户 → 返回200")
        void adminDisableUser_success() throws Exception {
            // mock 用户存在
            User user = buildUser(2001L, "13800002001", "用户2001", 1);
            when(userMapper.selectById(eq(2001L))).thenReturn(user);

            mockMvc.perform(put("/user/admin/{userId}/disable", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 验证先查询用户存在，再更新状态为0（禁用）
            verify(userMapper).selectById(eq(2001L));
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("用户不存在 → 抛业务异常 → 返回业务错误码")
        void adminDisableUser_notFound_throwsBusinessException() throws Exception {
            // mock 用户不存在
            when(userMapper.selectById(eq(9999L))).thenReturn(null);

            mockMvc.perform(put("/user/admin/{userId}/disable", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));

            // 用户不存在时不应执行更新
            verify(userMapper, never()).updateById(any(User.class));
        }
    }

    // ==================== 管理后台：启用用户 ====================

    @Nested
    @DisplayName("管理后台：启用用户 PUT /user/admin/{userId}/enable")
    class AdminEnableUserTest {

        @Test
        @DisplayName("正常启用用户 → 返回200")
        void adminEnableUser_success() throws Exception {
            User user = buildUser(2001L, "13800002001", "用户2001", 0);
            when(userMapper.selectById(eq(2001L))).thenReturn(user);

            mockMvc.perform(put("/user/admin/{userId}/enable", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userMapper).selectById(eq(2001L));
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("用户不存在 → 抛业务异常 → 返回业务错误码")
        void adminEnableUser_notFound_throwsBusinessException() throws Exception {
            when(userMapper.selectById(eq(9999L))).thenReturn(null);

            mockMvc.perform(put("/user/admin/{userId}/enable", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));

            verify(userMapper, never()).updateById(any(User.class));
        }
    }
}
