package com.shop.admin.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.util.SecurityUtils;
import com.shop.admin.mapper.AdminDeptMapper;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminUserMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.dto.AdminPasswordUpdateDTO;
import com.shop.model.admin.dto.AdminUserCreateDTO;
import com.shop.model.admin.dto.AdminUserQueryDTO;
import com.shop.model.admin.dto.AdminUserUpdateDTO;
import com.shop.model.admin.entity.AdminDept;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.vo.AdminUserVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员服务实现类 AdminUserServiceImpl 的单元测试
 * <p>
 * 这个测试类验证管理员账号管理的核心业务逻辑：分页查询、详情、新增、修改、删除、
 * 密码管理、状态切换、获取当前登录管理员信息。
 * </p>
 * <p>
 * 小白理解：管理员服务是后台的"用户管理"功能，超级管理员（id=1）受保护不能被删/禁用，
 * 密码用 BCrypt 加密存储。我们用 Mock 把数据库 Mapper 假装出来，
 * 用 mockStatic 把 BCrypt、Db、SecurityUtils 这些静态方法也假装出来，
 * 这样测试不依赖真实数据库和 Spring 环境，跑得快又稳定。
 * </p>
 * <p>
 * 测试要点：
 * 1. @ExtendWith(MockitoExtension.class) + @Mock + @InjectMocks 是 Mockito 三件套
 * 2. @BeforeAll 初始化 MyBatis-Plus 的 Lambda 缓存（否则 Lambda 查询会报错）
 * 3. BCrypt.hashpw/checkpw 是静态方法，用 mockStatic 模拟
 * 4. Db.saveBatch 是 MyBatis-Plus 的批量插入静态方法，用 mockStatic 模拟
 * 5. SecurityUtils.requireLogin 是登录校验静态方法，用 mockStatic 模拟
 * 6. 超级管理员（id=1）保护逻辑：deleteAdminUser 和 updateStatus 都不能操作 id=1
 * </p>
 */
@DisplayName("AdminUserServiceImpl 管理员服务测试")
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    /** 假装操作 admin_user 表的 Mapper */
    @Mock
    private AdminUserMapper adminUserMapper;

    /** 假装操作 admin_user_role 表的 Mapper（管理员-角色关联） */
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;

    /** 假装操作 admin_role 表的 Mapper（查询角色信息） */
    @Mock
    private AdminRoleMapper adminRoleMapper;

    /** 假装操作 admin_dept 表的 Mapper（查询部门信息） */
    @Mock
    private AdminDeptMapper adminDeptMapper;

    /** 被测服务，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：源码里用到了 .eq(AdminUser::getUsername, ...) 这种写法，
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
        TableInfoHelper.initTableInfo(assistant, AdminDept.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个管理员实体
     *
     * @param id       管理员ID
     * @param username 用户名
     * @param status   状态：0禁用 1正常
     * @return 构造好的 AdminUser
     */
    private AdminUser buildAdminUser(Long id, String username, Integer status) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("测试管理员");
        user.setPassword("hashed_password");
        user.setDeptId(10L);
        user.setStatus(status);
        return user;
    }

    /**
     * 构造一个查询条件DTO（分页参数默认1页10条）
     *
     * @return 构造好的 AdminUserQueryDTO
     */
    private AdminUserQueryDTO buildQueryDTO() {
        AdminUserQueryDTO dto = new AdminUserQueryDTO();
        // PageRequest 默认 pageNum=1, pageSize=10，这里不显式设置
        return dto;
    }

    /**
     * 构造一个新增管理员DTO
     *
     * @param username 用户名
     * @param password 密码明文
     * @param roleIds  角色ID列表
     * @return 构造好的 AdminUserCreateDTO
     */
    private AdminUserCreateDTO buildCreateDTO(String username, String password, List<Long> roleIds) {
        AdminUserCreateDTO dto = new AdminUserCreateDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setNickname("新管理员");
        dto.setEmail("test@example.com");
        dto.setPhone("13800138000");
        dto.setDeptId(10L);
        dto.setRoleIds(roleIds);
        return dto;
    }

    // ==================== 1. getAdminUserList 分页查询 ====================

    @Nested
    @DisplayName("getAdminUserList 分页查询管理员列表")
    class GetAdminUserListTest {

        @Test
        @DisplayName("空列表：返回空PageResult，不调用角色/部门批量查询")
        void emptyList_shouldNotQueryRolesAndDepts() {
            AdminUserQueryDTO dto = buildQueryDTO();
            // 模拟分页查询返回空列表
            Page<AdminUser> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);
            when(adminUserMapper.selectPage(any(), any())).thenReturn(emptyPage);

            var result = adminUserService.getAdminUserList(dto);

            // 验证：返回的PageResult不为空，records为空列表
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
            // 验证：因为userId列表为空，不应该再查角色和部门
            verify(adminUserRoleMapper, never()).selectList(any());
            verify(adminRoleMapper, never()).selectList(any());
            verify(adminDeptMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("有数据：批量预查询角色和部门，正确填充VO（验证N+1优化生效）")
        void withRecords_shouldBatchQueryRolesAndDepts() {
            AdminUserQueryDTO dto = buildQueryDTO();
            // 构造1个管理员记录
            AdminUser user = buildAdminUser(1L, "admin1", 1);
            Page<AdminUser> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(user));
            page.setTotal(1);
            when(adminUserMapper.selectPage(any(), any())).thenReturn(page);

            // 模拟用户-角色关联查询：管理员1拥有角色100
            AdminUserRole userRole = new AdminUserRole();
            userRole.setUserId(1L);
            userRole.setRoleId(100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));

            // 模拟角色查询：角色100
            AdminRole role = new AdminRole();
            role.setId(100L);
            role.setRoleName("运营");
            role.setRoleKey("operator");
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            // 模拟部门查询：部门10 = 技术部
            AdminDept dept = new AdminDept();
            dept.setId(10L);
            dept.setName("技术部");
            when(adminDeptMapper.selectList(any())).thenReturn(Collections.singletonList(dept));

            var result = adminUserService.getAdminUserList(dto);

            // 验证：返回的PageResult有1条数据
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);

            AdminUserVO vo = result.getRecords().get(0);
            // 验证基本信息正确填充
            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getUsername()).isEqualTo("admin1");
            // 验证部门名称正确填充
            assertThat(vo.getDeptName()).isEqualTo("技术部");
            // 验证角色列表正确填充
            assertThat(vo.getRoles()).hasSize(1);
            assertThat(vo.getRoles().get(0).getId()).isEqualTo(100L);
            assertThat(vo.getRoles().get(0).getRoleName()).isEqualTo("运营");
        }

        @Test
        @DisplayName("管理员deptId为null：跳过部门查询，deptName为null")
        void deptIdNull_shouldSkipDeptQuery() {
            AdminUserQueryDTO dto = buildQueryDTO();
            // 构造一个没有部门的管理员
            AdminUser user = new AdminUser();
            user.setId(1L);
            user.setUsername("user1");
            user.setDeptId(null); // 没有部门
            user.setStatus(1);
            Page<AdminUser> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(user));
            page.setTotal(1);
            when(adminUserMapper.selectPage(any(), any())).thenReturn(page);

            // 用户没有任何角色关联
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

            var result = adminUserService.getAdminUserList(dto);

            // 验证：返回的VO部门名称为null（因为deptId为null）
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getDeptName()).isNull();
            // 验证：deptIds为空，不会调用部门查询
            verify(adminDeptMapper, never()).selectList(any());
            // 验证：allRoleIds为空，不会调用角色查询
            verify(adminRoleMapper, never()).selectList(any());
        }
    }

    // ==================== 2. getAdminUserById 查询详情 ====================

    @Nested
    @DisplayName("getAdminUserById 查询管理员详情")
    class GetAdminUserByIdTest {

        @Test
        @DisplayName("管理员不存在：抛ADMIN_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.getAdminUserById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("查询成功：返回VO，填充角色列表和部门名称")
        void found_shouldReturnVOWithRolesAndDept() {
            AdminUser user = buildAdminUser(1L, "admin", 1);
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            // 模拟角色查询
            AdminUserRole userRole = new AdminUserRole();
            userRole.setUserId(1L);
            userRole.setRoleId(100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));

            AdminRole role = new AdminRole();
            role.setId(100L);
            role.setRoleName("管理员");
            role.setRoleKey("admin");
            when(adminRoleMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(role));

            // 模拟部门查询
            AdminDept dept = new AdminDept();
            dept.setId(10L);
            dept.setName("技术部");
            when(adminDeptMapper.selectById(10L)).thenReturn(dept);

            AdminUserVO vo = adminUserService.getAdminUserById(1L);

            // 验证：VO包含正确信息
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getUsername()).isEqualTo("admin");
            assertThat(vo.getDeptName()).isEqualTo("技术部");
            assertThat(vo.getRoles()).hasSize(1);
            assertThat(vo.getRoles().get(0).getId()).isEqualTo(100L);
            assertThat(vo.getRoles().get(0).getRoleName()).isEqualTo("管理员");
        }
    }

    // ==================== 3. createAdminUser 新增管理员 ====================

    @Nested
    @DisplayName("createAdminUser 新增管理员")
    class CreateAdminUserTest {

        @Test
        @DisplayName("用户名已存在：抛ADMIN_USERNAME_EXISTS异常，不执行insert")
        void usernameExists_shouldThrowException() {
            AdminUserCreateDTO dto = buildCreateDTO("admin", "password123", Collections.emptyList());
            // 模拟用户名已存在（count > 0）
            when(adminUserMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminUserService.createAdminUser(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_USERNAME_EXISTS.getCode());

            // 验证：因为用户名重复，不应该执行insert
            verify(adminUserMapper, never()).insert(any(AdminUser.class));
        }

        @Test
        @DisplayName("新增成功（带角色）：密码BCrypt加密后保存，Db.saveBatch保存角色关联")
        void successWithRoles_shouldEncryptPasswordAndSaveRoles() {
            AdminUserCreateDTO dto = buildCreateDTO("newuser", "password123", Arrays.asList(100L, 200L));
            // 模拟用户名不重复
            when(adminUserMapper.selectCount(any())).thenReturn(0L);
            // 模拟insert，顺便设置ID（模拟MyBatis自动回填主键）
            when(adminUserMapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
                AdminUser user = invocation.getArgument(0);
                user.setId(1L);
                return 1;
            });

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<Db> dbMock = mockStatic(Db.class)) {
                // 模拟BCrypt加密返回固定值
                bcryptMock.when(() -> BCrypt.hashpw("password123")).thenReturn("encrypted_hash");
                // 模拟批量保存返回true
                dbMock.when(() -> Db.saveBatch(anyList())).thenReturn(true);

                adminUserService.createAdminUser(dto);

                // 验证：密码用BCrypt加密了
                ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
                verify(adminUserMapper).insert(userCaptor.capture());
                AdminUser savedUser = userCaptor.getValue();
                assertThat(savedUser.getUsername()).isEqualTo("newuser");
                assertThat(savedUser.getPassword()).isEqualTo("encrypted_hash");
                // 新增管理员默认状态为正常(1)
                assertThat(savedUser.getStatus()).isEqualTo(1);

                // 验证：调用了Db.saveBatch批量保存角色关联
                dbMock.verify(() -> Db.saveBatch(anyList()), times(1));
            }
        }

        @Test
        @DisplayName("新增成功（无角色）：不调用Db.saveBatch")
        void successWithoutRoles_shouldNotCallSaveBatch() {
            AdminUserCreateDTO dto = buildCreateDTO("newuser", "password123", null);
            when(adminUserMapper.selectCount(any())).thenReturn(0L);
            when(adminUserMapper.insert(any(AdminUser.class))).thenReturn(1);

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class);
                 MockedStatic<Db> dbMock = mockStatic(Db.class)) {
                bcryptMock.when(() -> BCrypt.hashpw("password123")).thenReturn("encrypted_hash");

                adminUserService.createAdminUser(dto);

                // 验证：roleIds为空，不会调用Db.saveBatch
                dbMock.verify(() -> Db.saveBatch(anyList()), never());
            }
        }
    }

    // ==================== 4. updateAdminUser 修改管理员 ====================

    @Nested
    @DisplayName("updateAdminUser 修改管理员信息")
    class UpdateAdminUserTest {

        @Test
        @DisplayName("管理员不存在：抛ADMIN_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.updateAdminUser(999L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());

            // 验证：不存在时不更新
            verify(adminUserMapper, never()).updateById(any(AdminUser.class));
        }

        @Test
        @DisplayName("修改成功（带角色）：先删旧角色关联，再插新角色关联")
        void successWithRoles_shouldDeleteOldAndInsertNew() {
            Long userId = 2L;
            AdminUser existingUser = buildAdminUser(userId, "user2", 1);
            when(adminUserMapper.selectById(userId)).thenReturn(existingUser);

            AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
            dto.setNickname("新昵称");
            dto.setRoleIds(Arrays.asList(100L, 200L));

            try (MockedStatic<Db> dbMock = mockStatic(Db.class)) {
                dbMock.when(() -> Db.saveBatch(anyList())).thenReturn(true);

                adminUserService.updateAdminUser(userId, dto);

                // 验证：调用了updateById更新基本信息
                ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
                verify(adminUserMapper).updateById(userCaptor.capture());
                assertThat(userCaptor.getValue().getNickname()).isEqualTo("新昵称");

                // 验证：先删除了旧的角色关联
                verify(adminUserRoleMapper).delete(any());
                // 验证：再插入了新的角色关联（Db.saveBatch被调用一次）
                dbMock.verify(() -> Db.saveBatch(anyList()), times(1));
            }
        }

        @Test
        @DisplayName("修改成功（roleIds为null）：不更新角色关联")
        void successWithoutRoleIds_shouldNotUpdateRoles() {
            Long userId = 2L;
            AdminUser existingUser = buildAdminUser(userId, "user2", 1);
            when(adminUserMapper.selectById(userId)).thenReturn(existingUser);

            AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
            dto.setNickname("新昵称");
            // roleIds为null，不更新角色
            dto.setRoleIds(null);

            adminUserService.updateAdminUser(userId, dto);

            // 验证：调用了updateById更新基本信息
            verify(adminUserMapper).updateById(any(AdminUser.class));
            // 验证：roleIds为null时不会删除旧的角色关联
            verify(adminUserRoleMapper, never()).delete(any());
        }
    }

    // ==================== 5. deleteAdminUser 删除管理员 ====================

    @Nested
    @DisplayName("deleteAdminUser 删除管理员")
    class DeleteAdminUserTest {

        @Test
        @DisplayName("管理员不存在：抛ADMIN_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.deleteAdminUser(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());

            // 验证：不存在时不执行删除
            verify(adminUserMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("超级管理员(id=1)不可删除：抛ADMIN_ROLE_IMMUTABLE异常")
        void superAdmin_shouldThrowException() {
            AdminUser superAdmin = buildAdminUser(1L, "admin", 1);
            when(adminUserMapper.selectById(1L)).thenReturn(superAdmin);

            assertThatThrownBy(() -> adminUserService.deleteAdminUser(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_IMMUTABLE.getCode());

            // 验证：超级管理员受保护，不执行删除
            verify(adminUserMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除成功：调用deleteById执行逻辑删除")
        void success_shouldCallDeleteById() {
            AdminUser user = buildAdminUser(2L, "user2", 1);
            when(adminUserMapper.selectById(2L)).thenReturn(user);

            adminUserService.deleteAdminUser(2L);

            // 验证：调用了deleteById（MyBatis-Plus的逻辑删除，会把deleted字段改为1）
            verify(adminUserMapper).deleteById(2L);
        }
    }

    // ==================== 6. updatePassword 修改密码 ====================

    @Nested
    @DisplayName("updatePassword 修改密码")
    class UpdatePasswordTest {

        @Test
        @DisplayName("管理员不存在：抛ADMIN_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            AdminPasswordUpdateDTO dto = new AdminPasswordUpdateDTO();
            dto.setOldPassword("old");
            dto.setNewPassword("newpassword");
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.updatePassword(999L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("旧密码错误：抛ADMIN_OLD_PASSWORD_ERROR异常")
        void wrongOldPassword_shouldThrowException() {
            AdminUser user = buildAdminUser(1L, "admin", 1);
            user.setPassword("old_hash");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            AdminPasswordUpdateDTO dto = new AdminPasswordUpdateDTO();
            dto.setOldPassword("wrong_old");
            dto.setNewPassword("newpassword");

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class)) {
                // 模拟旧密码校验失败
                bcryptMock.when(() -> BCrypt.checkpw("wrong_old", "old_hash")).thenReturn(false);

                assertThatThrownBy(() -> adminUserService.updatePassword(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_OLD_PASSWORD_ERROR.getCode());

                // 验证：旧密码错误时不更新密码
                verify(adminUserMapper, never()).updateById(any(AdminUser.class));
            }
        }

        @Test
        @DisplayName("修改密码成功：新密码BCrypt加密后更新到数据库")
        void success_shouldEncryptNewPasswordAndUpdate() {
            AdminUser user = buildAdminUser(1L, "admin", 1);
            user.setPassword("old_hash");
            when(adminUserMapper.selectById(1L)).thenReturn(user);

            AdminPasswordUpdateDTO dto = new AdminPasswordUpdateDTO();
            dto.setOldPassword("correct_old");
            dto.setNewPassword("newpassword");

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class)) {
                // 模拟旧密码校验通过
                bcryptMock.when(() -> BCrypt.checkpw("correct_old", "old_hash")).thenReturn(true);
                // 模拟新密码加密
                bcryptMock.when(() -> BCrypt.hashpw("newpassword")).thenReturn("new_hash");

                adminUserService.updatePassword(1L, dto);

                // 验证：调用了updateById，且密码字段已更新为新哈希
                ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
                verify(adminUserMapper).updateById(userCaptor.capture());
                assertThat(userCaptor.getValue().getPassword()).isEqualTo("new_hash");
            }
        }
    }

    // ==================== 7. resetPassword 重置密码 ====================

    @Nested
    @DisplayName("resetPassword 重置密码")
    class ResetPasswordTest {

        @Test
        @DisplayName("管理员不存在：抛ADMIN_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.resetPassword(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("重置成功：密码重置为默认密码'123456'并用BCrypt加密")
        void success_shouldResetToDefaultPassword() {
            AdminUser user = buildAdminUser(2L, "user2", 1);
            when(adminUserMapper.selectById(2L)).thenReturn(user);

            try (MockedStatic<BCrypt> bcryptMock = mockStatic(BCrypt.class)) {
                // 模拟BCrypt加密"123456"返回固定哈希
                bcryptMock.when(() -> BCrypt.hashpw("123456")).thenReturn("reset_hash");

                adminUserService.resetPassword(2L);

                // 验证：调用了updateById，密码字段被重置为加密后的默认密码
                ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
                verify(adminUserMapper).updateById(userCaptor.capture());
                assertThat(userCaptor.getValue().getPassword()).isEqualTo("reset_hash");
            }
        }
    }

    // ==================== 8. updateStatus 修改状态 ====================

    @Nested
    @DisplayName("updateStatus 修改管理员状态")
    class UpdateStatusTest {

        @Test
        @DisplayName("管理员不存在：抛ADMIN_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.updateStatus(999L, 0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("超级管理员(id=1)不可禁用：抛ADMIN_ROLE_IMMUTABLE异常")
        void superAdmin_shouldThrowException() {
            AdminUser superAdmin = buildAdminUser(1L, "admin", 1);
            when(adminUserMapper.selectById(1L)).thenReturn(superAdmin);

            assertThatThrownBy(() -> adminUserService.updateStatus(1L, 0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_IMMUTABLE.getCode());

            // 验证：超级管理员受保护，不更新状态
            verify(adminUserMapper, never()).updateById(any(AdminUser.class));
        }

        @Test
        @DisplayName("修改状态成功：调用updateById更新状态")
        void success_shouldUpdateStatus() {
            AdminUser user = buildAdminUser(2L, "user2", 1);
            when(adminUserMapper.selectById(2L)).thenReturn(user);

            adminUserService.updateStatus(2L, 0);

            // 验证：调用了updateById，状态已更新为0（禁用）
            ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
            verify(adminUserMapper).updateById(userCaptor.capture());
            assertThat(userCaptor.getValue().getStatus()).isEqualTo(0);
        }
    }

    // ==================== 9. getCurrentAdminInfo 获取当前登录管理员 ====================

    @Nested
    @DisplayName("getCurrentAdminInfo 获取当前登录管理员信息")
    class GetCurrentAdminInfoTest {

        @Test
        @DisplayName("未登录：SecurityUtils.requireLogin抛UNAUTHORIZED异常")
        void notLogin_shouldThrowException() {
            try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
                // 模拟未登录：requireLogin抛UNAUTHORIZED异常
                securityMock.when(SecurityUtils::requireLogin)
                    .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

                assertThatThrownBy(() -> adminUserService.getCurrentAdminInfo())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.UNAUTHORIZED.getCode());

                // 验证：未登录时不查数据库
                verify(adminUserMapper, never()).selectById(anyLong());
            }
        }

        @Test
        @DisplayName("登录ID对应管理员不存在：抛ADMIN_NOT_FOUND异常")
        void userNotFound_shouldThrowException() {
            try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
                // 模拟已登录，登录ID是999
                securityMock.when(SecurityUtils::requireLogin).thenReturn(999L);
                when(adminUserMapper.selectById(999L)).thenReturn(null);

                assertThatThrownBy(() -> adminUserService.getCurrentAdminInfo())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_NOT_FOUND.getCode());
            }
        }

        @Test
        @DisplayName("获取成功：返回当前登录管理员的VO")
        void success_shouldReturnCurrentAdminVO() {
            AdminUser user = buildAdminUser(1L, "admin", 1);
            user.setDeptId(null); // 简化测试，不查部门

            try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
                securityMock.when(SecurityUtils::requireLogin).thenReturn(1L);
                when(adminUserMapper.selectById(1L)).thenReturn(user);
                // 模拟无角色关联
                when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

                AdminUserVO vo = adminUserService.getCurrentAdminInfo();

                // 验证：返回的VO包含当前登录管理员的信息
                assertThat(vo).isNotNull();
                assertThat(vo.getId()).isEqualTo(1L);
                assertThat(vo.getUsername()).isEqualTo("admin");
                // 没有角色时返回空列表
                assertThat(vo.getRoles()).isEmpty();
            }
        }
    }
}
