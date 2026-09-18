package com.shop.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.admin.annotation.DataScope;
import com.shop.admin.mapper.AdminDeptMapper;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminUserMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.model.admin.entity.AdminDept;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.enums.AdminDataScopeEnum;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据权限拦截器 DataScopeInterceptor 的单元测试
 * <p>
 * 这个测试类验证 MyBatis 拦截器能不能正确地根据当前管理员的数据权限范围，
 * 在查询 SQL 中追加过滤条件。
 * </p>
 * <p>
 * 小白理解：数据权限就是"你能看哪些数据"。比如运营只能看自己部门的数据，
 * 超级管理员可以看所有数据。拦截器在 SQL 执行前自动加 WHERE 条件，
 * 不需要在每个查询里手动写过滤条件。
 * </p>
 * <p>
 * 测试要点：
 * 1. 用 DataScopeContextHolder.set/clear 模拟 ThreadLocal 中的注解信息
 * 2. 用 mockStatic(StpUtil.class) 模拟 Sa-Token 的登录状态
 * 3. 用真实的 BoundSql 对象（不能用 Mock），因为拦截器通过反射修改 sql 字段
 * 4. @BeforeAll 初始化 MyBatis-Plus 的 Lambda 缓存
 * 5. @AfterEach 清理 ThreadLocal，防止测试间相互影响
 * </p>
 * <p>
 * 工作流程回顾：
 * 1. 检查 ThreadLocal 中是否有 @DataScope 注解 → 没有就放行
 * 2. 检查是否登录 → 未登录就跳过
 * 3. 查询管理员的最大数据权限范围
 * 4. ALL权限(1) → 跳过过滤
 * 5. DEPT(2) → 追加 dept_id = ?
 * 6. DEPT_AND_CHILD(3) → 追加 dept_id IN (...)
 * 7. SELF(4) → 追加 user_id = ?
 * 8. 异常 → 抛 SQLException 拒绝查询（防止越权）
 * </p>
 */
@DisplayName("DataScopeInterceptor 数据权限拦截器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataScopeInterceptorTest {

    /** 假装操作 admin_dept 表的 Mapper（查询部门及子部门） */
    @Mock
    private AdminDeptMapper adminDeptMapper;

    /** 假装操作 admin_user_role 表的 Mapper（查询管理员的角色） */
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;

    /** 假装操作 admin_role 表的 Mapper（查询角色的数据权限范围） */
    @Mock
    private AdminRoleMapper adminRoleMapper;

    /** 假装操作 admin_user 表的 Mapper（查询管理员的部门ID） */
    @Mock
    private AdminUserMapper adminUserMapper;

    /** 被测拦截器，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private DataScopeInterceptor interceptor;

    /** MyBatis 执行器（beforeQuery 参数之一，本测试不实际使用） */
    @Mock
    private Executor executor;

    /** MappedStatement（beforeQuery 参数之一，本测试不实际使用） */
    @Mock
    private MappedStatement ms;

    /** 分页参数（beforeQuery 参数之一，本测试不实际使用） */
    @Mock
    private RowBounds rowBounds;

    /** 结果处理器（beforeQuery 参数之一，本测试不实际使用） */
    @Mock
    private ResultHandler resultHandler;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：getMaxDataScope 方法里用到了 .eq(AdminUserRole::getUserId, ...) 这种写法，
     * MyBatis-Plus 需要知道 AdminUserRole::getUserId 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
        TableInfoHelper.initTableInfo(assistant, AdminUserRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminRole.class);
        TableInfoHelper.initTableInfo(assistant, AdminDept.class);
    }

    /**
     * 每个测试结束后清理 ThreadLocal
     * <p>
     * 小白理解：DataScopeContextHolder 用 ThreadLocal 存储数据权限注解信息，
     * 如果不清理，下一个测试可能会拿到上一个测试的数据，导致测试结果不可靠。
     * </p>
     */
    @AfterEach
    void cleanupContext() {
        DataScopeContextHolder.clear();
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 创建一个 mock 的 @DataScope 注解实例
     * <p>
     * 注解本质是接口，用 Mockito mock 出来，指定别名和字段名的返回值。
     * 默认使用 "d.dept_id" 和 "u.user_id" 这种常见配置。
     * </p>
     */
    private DataScope mockDataScope() {
        DataScope dataScope = mock(DataScope.class);
        when(dataScope.deptAlias()).thenReturn("d");
        when(dataScope.userAlias()).thenReturn("u");
        when(dataScope.deptField()).thenReturn("dept_id");
        when(dataScope.userField()).thenReturn("user_id");
        return dataScope;
    }

    /**
     * 创建一个真实的 BoundSql 对象
     * <p>
     * 必须用真实的 BoundSql，因为拦截器通过反射修改 sql 字段，
     * Mockito mock 出来的对象是 CGLIB 子类，getDeclaredField 找不到父类的字段会报错。
     * </p>
     *
     * @param sql 原始SQL语句
     * @return 真实的 BoundSql 实例
     */
    private BoundSql createBoundSql(String sql) {
        Configuration configuration = new Configuration();
        return new BoundSql(configuration, sql, new ArrayList<>(), null);
    }

    /**
     * 构造一个管理员实体
     *
     * @param id     管理员ID
     * @param deptId 部门ID
     * @return 构造好的 AdminUser
     */
    private AdminUser buildAdminUser(Long id, Long deptId) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername("admin");
        user.setDeptId(deptId);
        user.setStatus(1);
        return user;
    }

    /**
     * 构造一个管理员-角色关联
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
     * @param id        角色ID
     * @param dataScope 数据权限范围（1=ALL, 2=DEPT, 3=DEPT_AND_CHILD, 4=SELF）
     * @return 构造好的 AdminRole
     */
    private AdminRole buildRole(Long id, int dataScope) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setRoleName("角色" + id);
        role.setRoleKey("role" + id);
        role.setDataScope(dataScope);
        role.setStatus(1);
        return role;
    }

    // ==================== 1. 无@DataScope注解 ====================

    @Nested
    @DisplayName("无@DataScope注解")
    class NoDataScopeTest {

        @Test
        @DisplayName("ThreadLocal中没有@DataScope注解：直接放行，不修改SQL")
        void noAnnotation_shouldNotModifySql() throws SQLException {
            // 不设置 DataScopeContextHolder，get() 返回 null
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

            // 验证：SQL 没有被修改（因为没有@DataScope注解，不需要过滤）
            assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM admin_user");
            // 验证：没有调用任何 Mapper（提前返回了）
            verify(adminUserMapper, never()).selectById(anyLong());
            verify(adminUserRoleMapper, never()).selectList(any());
        }
    }

    // ==================== 2. 未登录 ====================

    @Nested
    @DisplayName("未登录场景")
    class NotLoginTest {

        @Test
        @DisplayName("有@DataScope注解但未登录：跳过过滤，不修改SQL")
        void notLogin_shouldSkipFilter() throws SQLException {
            // 设置@DataScope注解到ThreadLocal
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                // 模拟未登录
                stpMock.when(StpUtil::isLogin).thenReturn(false);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                // 验证：SQL 没有被修改
                assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM admin_user");
                // 验证：未登录时不查询管理员信息
                verify(adminUserMapper, never()).selectById(anyLong());
            }
        }
    }

    // ==================== 3. ALL权限 ====================

    @Nested
    @DisplayName("ALL权限（全部数据）")
    class AllPermissionTest {

        @Test
        @DisplayName("拥有ALL权限的管理员：跳过过滤，不修改SQL")
        void allPermission_shouldSkipFilter() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 模拟管理员1拥有角色100，角色100是ALL权限
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.ALL.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                // 验证：SQL 没有被修改（ALL权限不需要过滤）
                assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM admin_user");
                // 验证：ALL权限时不会查询管理员详情（提前返回了）
                verify(adminUserMapper, never()).selectById(anyLong());
            }
        }
    }

    // ==================== 4. DEPT权限（本部门） ====================

    @Nested
    @DisplayName("DEPT权限（本部门数据）")
    class DeptPermissionTest {

        @Test
        @DisplayName("DEPT权限：追加 WHERE d.dept_id = 部门ID 条件")
        void deptPermission_shouldAppendEqualsCondition() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 管理员1的部门是100
            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);
            // 角色配置：DEPT权限
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.DEPT.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：SQL 被修改了，追加了过滤条件
                assertThat(modifiedSql).isNotEqualTo("SELECT * FROM admin_user");
                // 验证：包含部门ID字段和部门ID值
                assertThat(modifiedSql).contains("dept_id");
                assertThat(modifiedSql).contains("100");
            }
        }

        @Test
        @DisplayName("DEPT权限 + 已有WHERE条件：用AND连接，不覆盖原条件")
        void deptPermissionWithExistingWhere_shouldUseAnd() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            // SQL已有 WHERE status = 1 条件
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user WHERE status = 1");

            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.DEPT.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：原 WHERE 条件还在
                assertThat(modifiedSql).contains("status");
                assertThat(modifiedSql).contains("1");
                // 验证：追加了部门过滤条件
                assertThat(modifiedSql).contains("dept_id");
                assertThat(modifiedSql).contains("100");
                // 验证：用了 AND 连接（而不是覆盖）
                assertThat(modifiedSql).contains("AND");
            }
        }
    }

    // ==================== 5. DEPT_AND_CHILD权限（本部门及子部门） ====================

    @Nested
    @DisplayName("DEPT_AND_CHILD权限（本部门及下级）")
    class DeptAndChildPermissionTest {

        @Test
        @DisplayName("有子部门：追加 IN (部门ID, 子部门ID1, 子部门ID2) 条件")
        void withChildDepts_shouldAppendInCondition() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 管理员1的部门是100
            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.DEPT_AND_CHILD.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            // 模拟部门树：100(技术部) -> 101(前端组) -> 102(Vue小组)
            // 还有一个不相关的部门200
            AdminDept dept100 = new AdminDept();
            dept100.setId(100L);
            dept100.setParentId(0L);
            AdminDept dept101 = new AdminDept();
            dept101.setId(101L);
            dept101.setParentId(100L);
            AdminDept dept102 = new AdminDept();
            dept102.setId(102L);
            dept102.setParentId(101L);
            AdminDept dept200 = new AdminDept();
            dept200.setId(200L);
            dept200.setParentId(0L);
            when(adminDeptMapper.selectList(any()))
                .thenReturn(Arrays.asList(dept100, dept101, dept102, dept200));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：追加了 IN 条件
                assertThat(modifiedSql).contains("IN");
                // 验证：包含部门100（自己）和子部门101、102
                assertThat(modifiedSql).contains("100");
                assertThat(modifiedSql).contains("101");
                assertThat(modifiedSql).contains("102");
            }
        }
    }

    // ==================== 6. SELF权限（仅本人） ====================

    @Nested
    @DisplayName("SELF权限（仅本人数据）")
    class SelfPermissionTest {

        @Test
        @DisplayName("SELF权限：追加 WHERE u.user_id = 管理员ID 条件")
        void selfPermission_shouldAppendUserEqualsCondition() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.SELF.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：追加了过滤条件
                assertThat(modifiedSql).isNotEqualTo("SELECT * FROM admin_user");
                // 验证：包含用户ID字段和管理员ID值
                assertThat(modifiedSql).contains("user_id");
                assertThat(modifiedSql).contains("1");
            }
        }
    }

    // ==================== 7. 管理员不存在 ====================

    @Nested
    @DisplayName("管理员不存在")
    class AdminNotFoundTest {

        @Test
        @DisplayName("管理员信息不存在：跳过过滤，不修改SQL（防止越权）")
        void adminNotFound_shouldSkipFilter() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 模拟管理员存在角色信息，但管理员本身不存在（边界场景）
            AdminUserRole userRole = buildUserRole(999L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.DEPT.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));
            // 管理员信息不存在
            when(adminUserMapper.selectById(999L)).thenReturn(null);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(999L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                // 验证：管理员不存在时跳过过滤，SQL 不被修改
                assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM admin_user");
            }
        }
    }

    // ==================== 8. 无角色默认SELF ====================

    @Nested
    @DisplayName("无角色默认SELF")
    class NoRolesDefaultSelfTest {

        @Test
        @DisplayName("管理员没有任何角色：默认只能看本人数据（最严格权限）")
        void noRoles_shouldDefaultToSelf() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 管理员没有任何角色关联
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 管理员信息存在
            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：默认SELF权限，追加了 user_id = 管理员ID 条件
                assertThat(modifiedSql).isNotEqualTo("SELECT * FROM admin_user");
                assertThat(modifiedSql).contains("user_id");
                assertThat(modifiedSql).contains("1");
            }
        }
    }

    // ==================== 9. 多角色取最大权限 ====================

    @Nested
    @DisplayName("多角色取最大权限")
    class MultipleRolesTest {

        @Test
        @DisplayName("两个角色（DEPT+SELF）：取最小code=2（DEPT权限更大）")
        void multipleRoles_shouldTakeMinCode() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 管理员1有两个角色：角色100(DEPT, code=2) 和 角色200(SELF, code=4)
            // 取最小code=2，即DEPT权限
            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);
            AdminUserRole userRole1 = buildUserRole(1L, 100L);
            AdminUserRole userRole2 = buildUserRole(1L, 200L);
            when(adminUserRoleMapper.selectList(any()))
                .thenReturn(Arrays.asList(userRole1, userRole2));
            AdminRole role1 = buildRole(100L, AdminDataScopeEnum.DEPT.getCode());
            AdminRole role2 = buildRole(200L, AdminDataScopeEnum.SELF.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Arrays.asList(role1, role2));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：取了DEPT权限，追加了 dept_id 条件（而不是 user_id 条件）
                assertThat(modifiedSql).contains("dept_id");
                assertThat(modifiedSql).contains("100");
                // 不应该包含 user_id（因为不是SELF权限）
                assertThat(modifiedSql).doesNotContain("user_id");
            }
        }

        @Test
        @DisplayName("两个角色（ALL+DEPT）：取最小code=1（ALL权限最大，跳过过滤）")
        void multipleRolesWithAll_shouldTakeAllAndSkip() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 管理员1有两个角色：角色100(ALL, code=1) 和 角色200(DEPT, code=2)
            // 取最小code=1，即ALL权限，跳过过滤
            AdminUserRole userRole1 = buildUserRole(1L, 100L);
            AdminUserRole userRole2 = buildUserRole(1L, 200L);
            when(adminUserRoleMapper.selectList(any()))
                .thenReturn(Arrays.asList(userRole1, userRole2));
            AdminRole role1 = buildRole(100L, AdminDataScopeEnum.ALL.getCode());
            AdminRole role2 = buildRole(200L, AdminDataScopeEnum.DEPT.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Arrays.asList(role1, role2));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                // 验证：ALL权限，跳过过滤，SQL 没有被修改
                assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM admin_user");
                // 验证：ALL权限时不会查询管理员详情
                verify(adminUserMapper, never()).selectById(anyLong());
            }
        }
    }

    // ==================== 10. 异常处理 ====================

    @Nested
    @DisplayName("异常处理")
    class ExceptionTest {

        @Test
        @DisplayName("SQL解析异常：抛SQLException拒绝查询（防止越权）")
        void sqlParseException_shouldThrowSQLException() {
            DataScopeContextHolder.set(mockDataScope());
            // 提供无法解析的SQL，JSqlParser 会抛异常
            BoundSql boundSql = createBoundSql("THIS IS NOT VALID SQL !!!");

            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            AdminRole role = buildRole(100L, AdminDataScopeEnum.DEPT.getCode());
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.singletonList(role));

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                // 验证：SQL 解析异常时，抛出 SQLException 拒绝查询
                assertThatThrownBy(() ->
                    interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("数据权限过滤异常");
            }
        }
    }

    // ==================== 11. 角色全部禁用 ====================

    @Nested
    @DisplayName("角色全部禁用")
    class AllRolesDisabledTest {

        @Test
        @DisplayName("管理员的角色都被禁用：视为无角色，默认SELF权限")
        void allRolesDisabled_shouldDefaultToSelf() throws SQLException {
            DataScopeContextHolder.set(mockDataScope());
            BoundSql boundSql = createBoundSql("SELECT * FROM admin_user");

            // 管理员有一个角色关联，但角色状态是禁用(status=0)
            AdminUserRole userRole = buildUserRole(1L, 100L);
            when(adminUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(userRole));
            // selectList 查不到启用的角色，返回空列表
            when(adminRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
            // 管理员信息存在
            AdminUser adminUser = buildAdminUser(1L, 100L);
            when(adminUserMapper.selectById(1L)).thenReturn(adminUser);

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(StpUtil::isLogin).thenReturn(true);
                stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

                interceptor.beforeQuery(executor, ms, null, rowBounds, resultHandler, boundSql);

                String modifiedSql = boundSql.getSql();
                // 验证：角色全部禁用视为无角色，默认SELF权限
                assertThat(modifiedSql).isNotEqualTo("SELECT * FROM admin_user");
                assertThat(modifiedSql).contains("user_id");
                assertThat(modifiedSql).contains("1");
            }
        }
    }
}
