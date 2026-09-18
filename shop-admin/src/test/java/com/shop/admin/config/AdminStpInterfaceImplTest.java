package com.shop.admin.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.admin.mapper.AdminPermissionMapper;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminRolePermissionMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.entity.AdminPermission;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminRolePermission;
import com.shop.model.admin.entity.AdminUserRole;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sa-Token权限桥接 AdminStpInterfaceImpl 的单元测试
 * <p>
 * 这个测试类验证 Sa-Token 的权限获取接口实现。
 * 小白理解：Sa-Token 框架在鉴权时会调用这个类的方法来获取当前登录管理员拥有的权限和角色。
 * 我们把数据库 Mapper 和 Sa-Token 的静态方法都"假装"一下（Mock），专注验证权限查询和缓存逻辑。
 * </p>
 * <p>
 * 核心逻辑说明：
 * 1. 先从 Session 缓存取权限/角色，命中就直接返回（避免重复查库）
 * 2. 缓存未命中时查4张表联查：admin_user_role → admin_role → admin_role_permission → admin_permission
 * 3. 只返回启用状态（status=1）的角色和权限，禁用的不算数
 * 4. 查完后存入 Session 缓存，下次直接命中
 * </p>
 */
@DisplayName("AdminStpInterfaceImpl Sa-Token权限桥接测试")
@ExtendWith(MockitoExtension.class)
class AdminStpInterfaceImplTest {

    /** 假装操作 admin_user_role 表的 Mapper（查管理员有哪些角色） */
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;

    /** 假装操作 admin_role_permission 表的 Mapper（查角色有哪些权限） */
    @Mock
    private AdminRolePermissionMapper adminRolePermissionMapper;

    /** 假装操作 admin_role 表的 Mapper（查角色详情） */
    @Mock
    private AdminRoleMapper adminRoleMapper;

    /** 假装操作 admin_permission 表的 Mapper（查权限详情） */
    @Mock
    private AdminPermissionMapper adminPermissionMapper;

    /** 被测试的Sa-Token权限接口实现，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private AdminStpInterfaceImpl stpInterface;

    /** 测试用的管理员ID */
    private static final Long ADMIN_ID = 1L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：getAdminPermissions 方法里用到了 .eq(AdminUserRole::getUserId, ...) 这种写法，
     * MyBatis-Plus 需要知道 AdminUserRole::getUserId 对应数据库哪一列。
     * 单元测试没有 Spring 环境，所以要手动初始化，否则会报 "can not find lambda cache" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AdminUserRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminRolePermission.class);
        TableInfoHelper.initTableInfo(assistant, AdminPermission.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个管理员-角色关联
     *
     * @param userId 管理员ID
     * @param roleId 角色ID
     * @return 构造好的 AdminUserRole
     */
    private AdminUserRole buildUserRole(Long userId, Long roleId) {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    /**
     * 构造一个角色
     *
     * @param id       角色ID
     * @param roleKey  角色标识
     * @param status   状态：0禁用 1正常
     * @return 构造好的 AdminRole
     */
    private AdminRole buildRole(Long id, String roleKey, Integer status) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setStatus(status);
        return role;
    }

    /**
     * 构造一个角色-权限关联
     *
     * @param roleId       角色ID
     * @param permissionId 权限ID
     * @return 构造好的 AdminRolePermission
     */
    private AdminRolePermission buildRolePermission(Long roleId, Long permissionId) {
        AdminRolePermission rp = new AdminRolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        return rp;
    }

    /**
     * 构造一个权限
     *
     * @param id            权限ID
     * @param permissionKey 权限标识
     * @param status        状态：0禁用 1正常
     * @return 构造好的 AdminPermission
     */
    private AdminPermission buildPermission(Long id, String permissionKey, Integer status) {
        AdminPermission permission = new AdminPermission();
        permission.setId(id);
        permission.setPermissionKey(permissionKey);
        permission.setStatus(status);
        return permission;
    }

    // ==================== 1. getPermissionList 获取权限列表 ====================

    @Nested
    @DisplayName("getPermissionList 获取权限列表")
    class GetPermissionListTest {

        @Test
        @DisplayName("缓存命中：直接返回Session中缓存的权限列表，不查数据库")
        void cacheHit_returnsCachedPermissions() {
            Object loginId = ADMIN_ID;
            // 预先准备缓存中的权限列表
            List<String> cachedPermissions = Arrays.asList("user:list", "user:add", "role:delete");

            SaSession session = mock(SaSession.class);
            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                // 模拟 Session 中已有缓存
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("permissionList")).thenReturn(cachedPermissions);

                List<String> result = stpInterface.getPermissionList(loginId, "admin");

                // 验证：返回的是缓存中的权限列表
                assertThat(result).isEqualTo(cachedPermissions);
                // 验证：缓存命中时不查数据库
                verify(adminUserRoleMapper, never()).selectList(any());
                verify(adminRoleMapper, never()).selectList(any());
                // 验证：缓存命中时不重新写入缓存
                verify(session, never()).set(anyString(), any());
            }
        }

        @Test
        @DisplayName("缓存未命中+用户无角色：返回空列表，不查角色和权限表")
        void cacheMiss_noRoles_returnsEmpty() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                // Session缓存为null，触发数据库查询
                when(session.get("permissionList")).thenReturn(null);
                // 用户没有角色
                when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

                List<String> result = stpInterface.getPermissionList(loginId, "admin");

                // 验证：返回空列表
                assertThat(result).isEmpty();
                // 验证：用户无角色时不查角色表和权限表（提前返回）
                verify(adminRoleMapper, never()).selectList(any());
                verify(adminRolePermissionMapper, never()).selectList(any());
                verify(adminPermissionMapper, never()).selectList(any());
                // 验证：空列表也存入缓存
                verify(session).set(eq("permissionList"), eq(Collections.emptyList()));
            }
        }

        @Test
        @DisplayName("缓存未命中+角色全部禁用：返回空列表，过滤掉status!=1的角色")
        void cacheMiss_allRolesDisabled_returnsEmpty() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            // 用户有2个角色，但都是禁用状态
            AdminUserRole ur1 = buildUserRole(ADMIN_ID, 10L);
            AdminUserRole ur2 = buildUserRole(ADMIN_ID, 20L);
            // 角色全部禁用
            AdminRole disabledRole1 = buildRole(10L, "role1", 0);
            AdminRole disabledRole2 = buildRole(20L, "role2", 0);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("permissionList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Arrays.asList(ur1, ur2));
                // 查角色时加了status=1条件，返回空（全部禁用）
                when(adminRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

                List<String> result = stpInterface.getPermissionList(loginId, "admin");

                // 验证：返回空列表
                assertThat(result).isEmpty();
                // 验证：角色全部禁用时不查角色-权限关联表和权限表
                verify(adminRolePermissionMapper, never()).selectList(any());
                verify(adminPermissionMapper, never()).selectList(any());
            }
        }

        @Test
        @DisplayName("缓存未命中+角色无权限关联：返回空列表")
        void cacheMiss_noRolePermissions_returnsEmpty() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            AdminUserRole ur = buildUserRole(ADMIN_ID, 10L);
            AdminRole activeRole = buildRole(10L, "admin", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("permissionList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(ur));
                when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(activeRole));
                // 角色没有关联任何权限
                when(adminRolePermissionMapper.selectList(any())).thenReturn(Collections.emptyList());

                List<String> result = stpInterface.getPermissionList(loginId, "admin");

                // 验证：返回空列表
                assertThat(result).isEmpty();
                // 验证：无权限关联时不查权限详情表
                verify(adminPermissionMapper, never()).selectList(any());
            }
        }

        @Test
        @DisplayName("缓存未命中+权限全部禁用：返回空列表，过滤掉status!=1的权限")
        void cacheMiss_allPermissionsDisabled_returnsEmpty() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            AdminUserRole ur = buildUserRole(ADMIN_ID, 10L);
            AdminRole activeRole = buildRole(10L, "admin", 1);
            AdminRolePermission rp = buildRolePermission(10L, 100L);
            // 权限被禁用
            AdminPermission disabledPermission = buildPermission(100L, "user:list", 0);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("permissionList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(ur));
                when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(activeRole));
                when(adminRolePermissionMapper.selectList(any())).thenReturn(Collections.singletonList(rp));
                // 查权限时加了status=1条件，返回空（权限全部禁用）
                when(adminPermissionMapper.selectList(any())).thenReturn(Collections.emptyList());

                List<String> result = stpInterface.getPermissionList(loginId, "admin");

                // 验证：返回空列表（禁用的权限不算数）
                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("缓存未命中+正常查询：4表联查返回权限标识，存入Session缓存")
        void cacheMiss_normalQuery_returnsPermissions() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            // 构造测试数据：管理员有2个角色，1个启用1个禁用，共关联3个权限，其中1个禁用
            AdminUserRole ur1 = buildUserRole(ADMIN_ID, 10L);
            AdminUserRole ur2 = buildUserRole(ADMIN_ID, 20L);

            // 角色1启用，角色2禁用 → 只有角色1的权限有效
            AdminRole activeRole = buildRole(10L, "admin", 1);
            AdminRole disabledRole = buildRole(20L, "operator", 0);

            // 角色1关联2个权限，角色2关联1个权限
            AdminRolePermission rp1 = buildRolePermission(10L, 100L);
            AdminRolePermission rp2 = buildRolePermission(10L, 200L);
            AdminRolePermission rp3 = buildRolePermission(20L, 300L);

            // 权限1和3启用，权限2禁用
            AdminPermission perm1 = buildPermission(100L, "user:list", 1);
            AdminPermission perm2 = buildPermission(200L, "user:add", 0);
            AdminPermission perm3 = buildPermission(300L, "role:delete", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("permissionList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Arrays.asList(ur1, ur2));
                // 查启用角色时，status=1条件过滤掉了禁用角色
                when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(activeRole));
                // 只有启用角色(10L)的权限关联
                when(adminRolePermissionMapper.selectList(any())).thenReturn(Arrays.asList(rp1, rp2));
                // 查启用权限时，status=1条件过滤掉了禁用权限
                when(adminPermissionMapper.selectList(any())).thenReturn(Collections.singletonList(perm1));

                List<String> result = stpInterface.getPermissionList(loginId, "admin");

                // 验证：只返回启用角色中启用权限的标识
                assertThat(result).containsExactly("user:list");
                // 验证：结果存入Session缓存
                verify(session).set(eq("permissionList"), eq(result));
            }
        }
    }

    // ==================== 2. getRoleList 获取角色列表 ====================

    @Nested
    @DisplayName("getRoleList 获取角色列表")
    class GetRoleListTest {

        @Test
        @DisplayName("缓存命中：直接返回Session中缓存的角色列表，不查数据库")
        void cacheHit_returnsCachedRoles() {
            Object loginId = ADMIN_ID;
            List<String> cachedRoles = Arrays.asList("admin", "operator");

            SaSession session = mock(SaSession.class);
            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("roleList")).thenReturn(cachedRoles);

                List<String> result = stpInterface.getRoleList(loginId, "admin");

                // 验证：返回的是缓存中的角色列表
                assertThat(result).isEqualTo(cachedRoles);
                // 验证：缓存命中时不查数据库
                verify(adminUserRoleMapper, never()).selectList(any());
                verify(adminRoleMapper, never()).selectList(any());
                // 验证：缓存命中时不重新写入缓存
                verify(session, never()).set(anyString(), any());
            }
        }

        @Test
        @DisplayName("缓存未命中+用户无角色：返回空列表")
        void cacheMiss_noRoles_returnsEmpty() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("roleList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

                List<String> result = stpInterface.getRoleList(loginId, "admin");

                // 验证：返回空列表
                assertThat(result).isEmpty();
                // 验证：用户无角色时不查角色表
                verify(adminRoleMapper, never()).selectList(any());
                // 验证：空列表也存入缓存
                verify(session).set(eq("roleList"), eq(Collections.emptyList()));
            }
        }

        @Test
        @DisplayName("缓存未命中+角色全部禁用：返回空列表")
        void cacheMiss_allRolesDisabled_returnsEmpty() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            AdminUserRole ur = buildUserRole(ADMIN_ID, 10L);
            // 角色被禁用
            AdminRole disabledRole = buildRole(10L, "admin", 0);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("roleList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(ur));
                // 查角色时加了status=1条件，返回空（全部禁用）
                when(adminRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

                List<String> result = stpInterface.getRoleList(loginId, "admin");

                // 验证：返回空列表
                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("缓存未命中+正常查询：返回启用角色的roleKey，存入Session缓存")
        void cacheMiss_normalQuery_returnsRoles() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            // 构造测试数据：管理员有2个角色，1个启用1个禁用
            AdminUserRole ur1 = buildUserRole(ADMIN_ID, 10L);
            AdminUserRole ur2 = buildUserRole(ADMIN_ID, 20L);

            AdminRole activeRole1 = buildRole(10L, "admin", 1);
            AdminRole activeRole2 = buildRole(20L, "operator", 1);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("roleList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Arrays.asList(ur1, ur2));
                when(adminRoleMapper.selectList(any())).thenReturn(Arrays.asList(activeRole1, activeRole2));

                List<String> result = stpInterface.getRoleList(loginId, "admin");

                // 验证：返回启用角色的roleKey
                assertThat(result).containsExactlyInAnyOrder("admin", "operator");
                // 验证：结果存入Session缓存
                verify(session).set(eq("roleList"), eq(result));
            }
        }

        @Test
        @DisplayName("缓存未命中+混合状态角色：只返回启用角色，过滤禁用角色")
        void cacheMiss_mixedStatus_returnsOnlyActiveRoles() {
            Object loginId = ADMIN_ID;
            SaSession session = mock(SaSession.class);

            AdminUserRole ur1 = buildUserRole(ADMIN_ID, 10L);
            AdminUserRole ur2 = buildUserRole(ADMIN_ID, 20L);

            // 1个启用，1个禁用
            AdminRole activeRole = buildRole(10L, "admin", 1);
            AdminRole disabledRole = buildRole(20L, "banned", 0);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getSessionByLoginId(loginId)).thenReturn(session);
                when(session.get("roleList")).thenReturn(null);
                when(adminUserRoleMapper.selectList(any())).thenReturn(Arrays.asList(ur1, ur2));
                // 查角色时status=1过滤掉了禁用角色
                when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(activeRole));

                List<String> result = stpInterface.getRoleList(loginId, "admin");

                // 验证：只返回启用角色的roleKey
                assertThat(result).containsExactly("admin");
                assertThat(result).doesNotContain("banned");
            }
        }
    }
}
