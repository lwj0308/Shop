package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminRolePermissionMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.dto.AdminRoleCreateDTO;
import com.shop.model.admin.dto.AdminRoleQueryDTO;
import com.shop.model.admin.dto.AdminRoleUpdateDTO;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminRolePermission;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.vo.AdminRoleVO;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminRoleServiceImpl 角色服务实现类的单元测试
 * <p>
 * 验证角色CRUD、权限分配等核心业务逻辑：
 * 1. createAdminRole - roleKey 查重
 * 2. updateAdminRole - 超级管理员角色(id=1)不可改
 * 3. deleteAdminRole - 超级管理员不可删 / 有用户使用不可删
 * 4. getAdminRoleList / getAllRoles / getAdminRoleById（含权限列表填充）
 * </p>
 * <p>
 * 小白理解：角色管理是 RBAC 权限模型的核心，一个角色拥有多个权限，一个管理员拥有多个角色。
 * 超级管理员角色(id=1)是系统内置角色，不能被修改或删除，否则可能把所有人锁死在系统外。
 * 我们用 Mock 把数据库 Mapper 假装出来，用 mockStatic 把 Db.saveBatch 批量保存的静态方法也假装出来，
 * 这样测试不依赖真实数据库，跑得快又稳定。
 * </p>
 */
@DisplayName("AdminRoleServiceImpl 角色服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRoleServiceImplTest {

    /** 假装操作 admin_role 表的 Mapper */
    @Mock
    private AdminRoleMapper adminRoleMapper;

    /** 假装操作 admin_role_permission 表的 Mapper（角色-权限关联） */
    @Mock
    private AdminRolePermissionMapper adminRolePermissionMapper;

    /** 假装操作 admin_user_role 表的 Mapper（用来检查角色是否被管理员使用） */
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;

    /** 被测服务，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private AdminRoleServiceImpl adminRoleService;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：源码里用到了 .eq(AdminRole::getRoleKey, ...) 这种写法，
     * MyBatis-Plus 需要知道 AdminRole::getRoleKey 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        // 把所有用到 Lambda 查询的实体类都注册进去
        TableInfoHelper.initTableInfo(assistant, AdminRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminRolePermission.class);
        TableInfoHelper.initTableInfo(assistant, AdminUserRole.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个角色实体
     *
     * @param id      角色ID
     * @param roleKey 角色标识
     * @param status  状态：0禁用 1正常
     * @return 构造好的 AdminRole
     */
    private AdminRole buildRole(Long id, String roleKey, Integer status) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleName("测试角色" + id);
        role.setRoleKey(roleKey);
        role.setStatus(status);
        return role;
    }

    /**
     * 构造一个新增角色DTO
     *
     * @param roleKey       角色标识
     * @param permissionIds 权限ID列表
     * @return 构造好的 AdminRoleCreateDTO
     */
    private AdminRoleCreateDTO buildCreateDTO(String roleKey, List<Long> permissionIds) {
        AdminRoleCreateDTO dto = new AdminRoleCreateDTO();
        dto.setRoleName("测试角色");
        dto.setRoleKey(roleKey);
        dto.setDataScope(1);
        dto.setRemark("测试备注");
        dto.setPermissionIds(permissionIds);
        return dto;
    }

    // ==================== 1. createAdminRole 新增角色 ====================

    @Nested
    @DisplayName("createAdminRole 新增角色")
    class CreateAdminRoleTest {

        @Test
        @DisplayName("角色标识已存在：抛ADMIN_ROLE_KEY_EXISTS异常，不执行insert")
        void roleKeyExists_shouldThrowException() {
            AdminRoleCreateDTO dto = buildCreateDTO("admin", Collections.emptyList());
            // 模拟角色标识已存在（count > 0）
            when(adminRoleMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminRoleService.createAdminRole(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_KEY_EXISTS.getCode());

            // 验证：因为标识重复，不应该执行insert
            verify(adminRoleMapper, never()).insert(any(AdminRole.class));
        }

        @Test
        @DisplayName("新增成功（带权限）：保存角色，Db.saveBatch保存权限关联，状态默认为1")
        void successWithPermissions_shouldSaveRoleAndPermissions() {
            AdminRoleCreateDTO dto = buildCreateDTO("operator", Arrays.asList(100L, 200L));
            // 模拟角色标识不重复
            when(adminRoleMapper.selectCount(any())).thenReturn(0L);
            // 模拟insert，顺便设置ID（模拟MyBatis自动回填主键）
            when(adminRoleMapper.insert(any(AdminRole.class))).thenAnswer(invocation -> {
                AdminRole role = invocation.getArgument(0);
                role.setId(1L);
                return 1;
            });

            try (MockedStatic<Db> dbMock = mockStatic(Db.class)) {
                // 模拟批量保存返回true
                dbMock.when(() -> Db.saveBatch(anyList())).thenReturn(true);

                adminRoleService.createAdminRole(dto);

                // 验证：调用了insert，捕获角色对象检查字段
                ArgumentCaptor<AdminRole> roleCaptor = ArgumentCaptor.forClass(AdminRole.class);
                verify(adminRoleMapper).insert(roleCaptor.capture());
                AdminRole savedRole = roleCaptor.getValue();
                assertThat(savedRole.getRoleKey()).isEqualTo("operator");
                // 新增角色默认状态为正常(1)
                assertThat(savedRole.getStatus()).isEqualTo(1);

                // 验证：调用了Db.saveBatch批量保存权限关联
                dbMock.verify(() -> Db.saveBatch(anyList()), times(1));
            }
        }

        @Test
        @DisplayName("新增成功（无权限）：不调用Db.saveBatch")
        void successWithoutPermissions_shouldNotCallSaveBatch() {
            AdminRoleCreateDTO dto = buildCreateDTO("operator", null);
            when(adminRoleMapper.selectCount(any())).thenReturn(0L);
            when(adminRoleMapper.insert(any(AdminRole.class))).thenReturn(1);

            try (MockedStatic<Db> dbMock = mockStatic(Db.class)) {
                adminRoleService.createAdminRole(dto);

                // 验证：permissionIds为null，不会调用Db.saveBatch
                dbMock.verify(() -> Db.saveBatch(anyList()), never());
            }
        }
    }

    // ==================== 2. updateAdminRole 修改角色 ====================

    @Nested
    @DisplayName("updateAdminRole 修改角色信息")
    class UpdateAdminRoleTest {

        @Test
        @DisplayName("角色不存在：抛ADMIN_ROLE_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            AdminRoleUpdateDTO dto = new AdminRoleUpdateDTO();
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminRoleService.updateAdminRole(999L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_NOT_FOUND.getCode());

            // 验证：不存在时不更新
            verify(adminRoleMapper, never()).updateById(any(AdminRole.class));
        }

        @Test
        @DisplayName("超级管理员(id=1)不可修改：抛ADMIN_ROLE_IMMUTABLE异常")
        void superAdmin_shouldThrowException() {
            AdminRoleUpdateDTO dto = new AdminRoleUpdateDTO();
            dto.setRoleName("新名字");
            AdminRole superAdmin = buildRole(1L, "admin", 1);
            when(adminRoleMapper.selectById(1L)).thenReturn(superAdmin);

            assertThatThrownBy(() -> adminRoleService.updateAdminRole(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_IMMUTABLE.getCode());

            // 验证：超级管理员受保护，不更新
            verify(adminRoleMapper, never()).updateById(any(AdminRole.class));
        }

        @Test
        @DisplayName("修改成功（带权限）：先删旧权限关联，再插新权限关联")
        void successWithPermissions_shouldDeleteOldAndInsertNew() {
            AdminRole role = buildRole(2L, "operator", 1);
            when(adminRoleMapper.selectById(2L)).thenReturn(role);

            AdminRoleUpdateDTO dto = new AdminRoleUpdateDTO();
            dto.setRoleName("新角色名");
            dto.setPermissionIds(Arrays.asList(100L, 200L));

            try (MockedStatic<Db> dbMock = mockStatic(Db.class)) {
                dbMock.when(() -> Db.saveBatch(anyList())).thenReturn(true);

                adminRoleService.updateAdminRole(2L, dto);

                // 验证：调用了updateById更新基本信息
                ArgumentCaptor<AdminRole> roleCaptor = ArgumentCaptor.forClass(AdminRole.class);
                verify(adminRoleMapper).updateById(roleCaptor.capture());
                assertThat(roleCaptor.getValue().getRoleName()).isEqualTo("新角色名");

                // 验证：先删除了旧的权限关联
                verify(adminRolePermissionMapper).delete(any());
                // 验证：再插入了新的权限关联（Db.saveBatch被调用一次）
                dbMock.verify(() -> Db.saveBatch(anyList()), times(1));
            }
        }

        @Test
        @DisplayName("修改成功（permissionIds为null）：不更新权限关联")
        void successWithoutPermissionIds_shouldNotUpdatePermissions() {
            AdminRole role = buildRole(2L, "operator", 1);
            when(adminRoleMapper.selectById(2L)).thenReturn(role);

            AdminRoleUpdateDTO dto = new AdminRoleUpdateDTO();
            dto.setRoleName("新角色名");
            // permissionIds为null，不更新权限
            dto.setPermissionIds(null);

            adminRoleService.updateAdminRole(2L, dto);

            // 验证：调用了updateById更新基本信息
            verify(adminRoleMapper).updateById(any(AdminRole.class));
            // 验证：permissionIds为null时不会删除旧的权限关联
            verify(adminRolePermissionMapper, never()).delete(any());
        }
    }

    // ==================== 3. deleteAdminRole 删除角色 ====================

    @Nested
    @DisplayName("deleteAdminRole 删除角色")
    class DeleteAdminRoleTest {

        @Test
        @DisplayName("超级管理员(id=1)不可删除：抛ADMIN_ROLE_IMMUTABLE异常")
        void superAdmin_shouldThrowException() {
            AdminRole superAdmin = buildRole(1L, "admin", 1);
            when(adminRoleMapper.selectById(1L)).thenReturn(superAdmin);

            assertThatThrownBy(() -> adminRoleService.deleteAdminRole(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_IMMUTABLE.getCode());

            // 验证：超级管理员受保护，不执行删除
            verify(adminRoleMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("有用户使用该角色：抛ADMIN_ROLE_IMMUTABLE异常")
        void roleInUse_shouldThrowException() {
            AdminRole role = buildRole(2L, "operator", 1);
            when(adminRoleMapper.selectById(2L)).thenReturn(role);
            // 模拟有1个管理员使用该角色
            when(adminUserRoleMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminRoleService.deleteAdminRole(2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_IMMUTABLE.getCode());

            // 验证：有用户使用时不执行删除
            verify(adminRoleMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除成功：调用deleteById执行逻辑删除")
        void success_shouldCallDeleteById() {
            AdminRole role = buildRole(2L, "operator", 1);
            when(adminRoleMapper.selectById(2L)).thenReturn(role);
            // 模拟没有管理员使用该角色
            when(adminUserRoleMapper.selectCount(any())).thenReturn(0L);

            adminRoleService.deleteAdminRole(2L);

            // 验证：调用了deleteById（MyBatis-Plus的逻辑删除，会把deleted字段改为1）
            verify(adminRoleMapper).deleteById(2L);
        }
    }

    // ==================== 4. getAllRoles 查询所有正常角色 ====================

    @Nested
    @DisplayName("getAllRoles 查询所有正常状态的角色")
    class GetAllRolesTest {

        @Test
        @DisplayName("查询成功：返回状态为1的角色列表")
        void shouldReturnActiveRoles() {
            AdminRole role1 = buildRole(1L, "admin", 1);
            AdminRole role2 = buildRole(2L, "operator", 1);
            when(adminRoleMapper.selectList(any())).thenReturn(Arrays.asList(role1, role2));

            List<AdminRoleVO> result = adminRoleService.getAllRoles();

            // 验证：返回2个VO，且id和roleKey正确
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getRoleKey()).isEqualTo("admin");
            assertThat(result.get(1).getId()).isEqualTo(2L);
            assertThat(result.get(1).getRoleKey()).isEqualTo("operator");
        }
    }

    // ==================== 5. getAdminRoleList 分页查询 ====================

    @Nested
    @DisplayName("getAdminRoleList 分页查询角色列表")
    class GetAdminRoleListTest {

        @Test
        @DisplayName("分页查询：返回正确分页数据和VO列表")
        void shouldReturnPagedResult() {
            AdminRole role = buildRole(1L, "admin", 1);
            Page<AdminRole> page = new Page<>(1, 10);
            page.setRecords(Collections.singletonList(role));
            page.setTotal(1);
            when(adminRoleMapper.selectPage(any(), any())).thenReturn(page);

            AdminRoleQueryDTO queryDTO = new AdminRoleQueryDTO();
            var result = adminRoleService.getAdminRoleList(queryDTO);

            // 验证：返回1条数据，total=1
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords().get(0).getId()).isEqualTo(1L);
            assertThat(result.getRecords().get(0).getRoleKey()).isEqualTo("admin");
        }
    }

    // ==================== 6. getAdminRoleById 查询角色详情 ====================

    @Nested
    @DisplayName("getAdminRoleById 查询角色详情")
    class GetAdminRoleByIdTest {

        @Test
        @DisplayName("角色不存在：抛ADMIN_ROLE_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminRoleMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminRoleService.getAdminRoleById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_ROLE_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("查询成功：返回VO，填充权限列表")
        void found_shouldReturnVOWithPermissions() {
            AdminRole role = buildRole(1L, "admin", 1);
            when(adminRoleMapper.selectById(1L)).thenReturn(role);

            // 模拟角色-权限关联查询：角色1拥有权限100
            AdminRolePermission rp = new AdminRolePermission();
            rp.setRoleId(1L);
            rp.setPermissionId(100L);
            when(adminRolePermissionMapper.selectList(any())).thenReturn(Collections.singletonList(rp));

            AdminRoleVO vo = adminRoleService.getAdminRoleById(1L);

            // 验证：VO包含正确的基本信息和权限列表
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getRoleKey()).isEqualTo("admin");
            assertThat(vo.getPermissions()).hasSize(1);
            assertThat(vo.getPermissions().get(0).getId()).isEqualTo(100L);
        }
    }
}
