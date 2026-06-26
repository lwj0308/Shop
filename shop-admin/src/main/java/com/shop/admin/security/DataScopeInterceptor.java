package com.shop.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据权限MyBatis拦截器
 * <p>
 * 自动根据当前管理员的数据权限范围，在查询SQL中追加过滤条件。
 * 只有通过@DataScope注解标记的Service方法触发的查询才会被拦截。
 * </p>
 * <p>
 * 实现MyBatis-Plus的InnerInterceptor接口，可以无缝集成到MybatisPlusInterceptor拦截链中。
 * 拦截链的执行顺序：数据权限拦截器 → 分页拦截器，先过滤数据再分页。
 * </p>
 * <p>
 * 工作原理：
 * 1. 拦截MyBatis的query方法（即所有SELECT查询）
 * 2. 从DataScopeContextHolder中检查当前线程是否有@DataScope注解信息
 * 3. 如果没有注解信息，说明不需要数据权限过滤，直接放行
 * 4. 获取当前登录管理员ID，查询管理员的角色和数据权限范围
 * 5. 如果最大数据权限是ALL(1)，不需要过滤，直接放行
 * 6. 根据不同的数据范围，用JSqlParser在SQL中追加WHERE条件：
 *    - DEPT(2)：追加 WHERE dept_alias.dept_field = {管理员部门ID}
 *    - DEPT_AND_CHILD(3)：追加 WHERE dept_alias.dept_field IN ({管理员部门ID}, {子部门ID1}, ...)
 *    - SELF(4)：追加 WHERE user_alias.user_field = {管理员用户ID}
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataScopeInterceptor implements InnerInterceptor {

    /** 部门Mapper，查询部门信息（含子部门递归查询） */
    private final AdminDeptMapper adminDeptMapper;

    /** 管理员-角色关联Mapper，查询管理员拥有的角色 */
    private final AdminUserRoleMapper adminUserRoleMapper;

    /** 角色Mapper，查询角色的数据权限范围 */
    private final AdminRoleMapper adminRoleMapper;

    /** 管理员Mapper，查询管理员的部门ID */
    private final AdminUserMapper adminUserMapper;

    /**
     * 查询前拦截，追加数据权限过滤条件
     * <p>
     * MyBatis-Plus的InnerInterceptor接口方法，在执行SELECT查询前被调用。
     * 我们在这里判断是否需要追加数据权限条件，如果需要就修改SQL。
     * </p>
     *
     * @param executor      MyBatis执行器
     * @param ms            MappedStatement对象，包含SQL映射信息
     * @param parameter     查询参数
     * @param rowBounds     分页参数
     * @param resultHandler 结果处理器
     * @param boundSql      绑定的SQL对象，包含要执行的SQL语句
     * @throws SQLException SQL异常
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler,
                            org.apache.ibatis.mapping.BoundSql boundSql) throws SQLException {
        // 1. 从ThreadLocal获取@DataScope注解信息，如果没有说明不需要数据权限过滤
        DataScope dataScope = DataScopeContextHolder.get();
        if (dataScope == null) {
            // 没有@DataScope注解，直接放行，不做任何过滤
            return;
        }

        // 2. 检查当前管理员是否已登录，未登录则跳过过滤（比如公开接口）
        if (!StpUtil.isLogin()) {
            log.debug("当前未登录，跳过数据权限过滤");
            return;
        }

        // 3. 获取当前登录管理员的ID
        Long adminUserId = StpUtil.getLoginIdAsLong();

        // 4. 查询管理员的最大数据权限范围
        Integer maxDataScope = getMaxDataScope(adminUserId);
        if (maxDataScope == null) {
            // 没有角色信息，默认只能看自己的数据（最严格）
            log.warn("管理员[{}]没有角色信息，默认只能查看本人数据", adminUserId);
            maxDataScope = AdminDataScopeEnum.SELF.getCode();
        }

        // 5. 如果是ALL(1)，拥有全部数据权限，不需要过滤
        if (maxDataScope == AdminDataScopeEnum.ALL.getCode()) {
            log.debug("管理员[{}]拥有全部数据权限，跳过过滤", adminUserId);
            return;
        }

        // 6. 根据数据权限范围构建过滤条件，并追加到SQL中
        try {
            String originalSql = boundSql.getSql();
            String modifiedSql = buildDataScopeSql(originalSql, dataScope, maxDataScope, adminUserId);
            if (!modifiedSql.equals(originalSql)) {
                log.debug("数据权限过滤 - 管理员[{}] 权限范围[{}] 原始SQL已追加过滤条件", adminUserId, maxDataScope);
                // 通过反射修改BoundSql中的SQL语句
                java.lang.reflect.Field field = boundSql.getClass().getDeclaredField("sql");
                field.setAccessible(true);
                field.set(boundSql, modifiedSql);
            }
        } catch (Exception e) {
            // 数据权限过滤失败时拒绝查询，防止越权访问
            // 宁可让请求失败，也不能让用户看到不该看的数据
            log.error("数据权限过滤异常，拒绝查询以防止越权访问。管理员[{}] 权限范围[{}]", adminUserId, maxDataScope, e);
            throw new SQLException("数据权限过滤异常，拒绝查询");
        }
    }

    /**
     * 根据数据权限范围构建过滤后的SQL
     * <p>
     * 使用JSqlParser解析原始SQL，根据不同的数据权限范围追加WHERE条件。
     * 如果SQL已经有WHERE条件，就用AND连接；如果没有，就新增WHERE子句。
     * </p>
     *
     * @param originalSql   原始SQL语句
     * @param dataScope     @DataScope注解信息，包含表别名和字段名配置
     * @param dataScopeCode 数据权限范围代码（2=本部门，3=本部门及下级，4=仅本人）
     * @param adminUserId   当前管理员ID
     * @return 追加了数据权限过滤条件的SQL语句
     * @throws Exception SQL解析或修改异常
     */
    private String buildDataScopeSql(String originalSql, DataScope dataScope,
                                     int dataScopeCode, Long adminUserId) throws Exception {
        // 使用JSqlParser解析SQL
        net.sf.jsqlparser.statement.Statement statement = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(originalSql);
        if (!(statement instanceof Select)) {
            // 不是SELECT语句，不做处理
            return originalSql;
        }

        Select select = (Select) statement;
        if (!(select.getSelectBody() instanceof PlainSelect)) {
            // 目前只支持简单SELECT，不支持UNION等复杂查询
            return originalSql;
        }

        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 根据数据权限范围构建过滤表达式
        Expression dataScopeExpression = buildDataScopeExpression(dataScope, dataScopeCode, adminUserId);
        if (dataScopeExpression == null) {
            return originalSql;
        }

        // 获取原始WHERE条件
        Expression originalWhere = plainSelect.getWhere();

        if (originalWhere != null) {
            // 已有WHERE条件，用AND连接
            Parenthesis parenthesis = new Parenthesis();
            parenthesis.setExpression(dataScopeExpression);
            plainSelect.setWhere(new AndExpression(originalWhere, parenthesis));
        } else {
            // 没有WHERE条件，直接设置
            plainSelect.setWhere(dataScopeExpression);
        }

        return select.toString();
    }

    /**
     * 根据数据权限范围构建过滤表达式
     * <p>
     * 不同的数据权限范围生成不同的SQL条件：
     * - DEPT(2)：dept_alias.dept_field = 管理员部门ID
     * - DEPT_AND_CHILD(3)：dept_alias.dept_field IN (管理员部门ID, 子部门ID1, ...)
     * - SELF(4)：user_alias.user_field = 管理员用户ID
     * </p>
     *
     * @param dataScope     @DataScope注解信息
     * @param dataScopeCode 数据权限范围代码
     * @param adminUserId   当前管理员ID
     * @return JSqlParser的Expression对象，表示过滤条件
     */
    private Expression buildDataScopeExpression(DataScope dataScope, int dataScopeCode, Long adminUserId) {
        // 查询当前管理员信息，获取部门ID
        AdminUser adminUser = adminUserMapper.selectById(adminUserId);
        if (adminUser == null) {
            log.warn("管理员[{}]信息不存在，跳过数据权限过滤", adminUserId);
            return null;
        }

        switch (dataScopeCode) {
            case 2: // DEPT 本部门数据
            {
                // DEPT：本部门数据，条件为 dept_alias.dept_field = 管理员部门ID
                EqualsTo equalsTo = new EqualsTo();
                equalsTo.setLeftExpression(buildColumn(dataScope.deptAlias(), dataScope.deptField()));
                equalsTo.setRightExpression(new LongValue(adminUser.getDeptId()));
                return equalsTo;
            }
            case 3: // DEPT_AND_CHILD 本部门及下级
            {
                // DEPT_AND_CHILD：本部门及下级部门数据
                // 条件为 dept_alias.dept_field IN (管理员部门ID, 子部门ID1, 子部门ID2, ...)
                List<Long> deptIds = getChildDeptIds(adminUser.getDeptId());
                InExpression inExpression = new InExpression();
                inExpression.setLeftExpression(buildColumn(dataScope.deptAlias(), dataScope.deptField()));
                inExpression.setRightExpression(buildParenthesedExpressionList(deptIds));
                return inExpression;
            }
            case 4: // SELF 仅本人数据
            {
                // SELF：仅本人数据，条件为 user_alias.user_field = 管理员用户ID
                EqualsTo equalsTo = new EqualsTo();
                equalsTo.setLeftExpression(buildColumn(dataScope.userAlias(), dataScope.userField()));
                equalsTo.setRightExpression(new LongValue(adminUserId));
                return equalsTo;
            }
            default:
                log.warn("未知的数据权限范围代码[{}]，跳过过滤", dataScopeCode);
                return null;
        }
    }

    /**
     * 构建带表别名的列引用
     * <p>
     * 比如传入alias="d", field="dept_id"，生成 Column("d.dept_id")。
     * 这样在SQL中就是 d.dept_id，可以明确指定是哪个表的字段。
     * </p>
     *
     * @param alias 表别名
     * @param field 字段名
     * @return JSqlParser的Column对象
     */
    private Column buildColumn(String alias, String field) {
        return new Column(alias + "." + field);
    }

    /**
     * 构建IN表达式的带括号的值列表
     * <p>
     * 将部门ID列表转成JSqlParser的ParenthesedExpressionList，用于IN条件。
     * 比如传入[1, 2, 3]，生成 (1, 2, 3) 形式的表达式列表。
     * JSqlParser 5.0中InExpression的右侧需要用ParenthesedExpressionList包装。
     * </p>
     *
     * @param values ID列表
     * @return JSqlParser的ParenthesedExpressionList对象
     */
    private ParenthesedExpressionList<Expression> buildParenthesedExpressionList(List<Long> values) {
        List<Expression> expressions = new ArrayList<>();
        for (Long value : values) {
            expressions.add(new LongValue(value));
        }
        return new ParenthesedExpressionList<>(expressions);
    }

    /**
     * 获取部门及所有子部门ID（一次性查询所有部门，内存中构建树）
     * <p>
     * 优化前：每层递归查一次数据库，5层部门=5次查询（N+1问题）
     * 优化后：一次查询所有部门，在内存中递归找子部门，只需1次查询
     * </p>
     *
     * @param parentId 起始部门ID（会包含在结果中）
     * @return 包含自身及所有子部门的ID列表
     */
    private List<Long> getChildDeptIds(Long parentId) {
        // 一次性查出所有部门，避免递归查询数据库
        List<AdminDept> allDepts = adminDeptMapper.selectList(null);
        // 在内存中递归查找子部门
        List<Long> deptIds = new ArrayList<>();
        deptIds.add(parentId);
        findChildDeptIdsInMemory(parentId, allDepts, deptIds);
        return deptIds;
    }

    /**
     * 在内存中递归查找子部门ID
     * <p>
     * 从所有部门列表中找出parent_id等于指定ID的部门，
     * 然后对每个子部门继续递归查找，直到没有更下级的部门为止。
     * 因为数据已经在内存中，不需要再查数据库，性能大幅提升。
     * </p>
     *
     * @param parentId 父部门ID
     * @param allDepts 所有部门列表（从数据库一次性查出来的）
     * @param result   收集部门ID的结果列表（会被递归修改）
     */
    private void findChildDeptIdsInMemory(Long parentId, List<AdminDept> allDepts, List<Long> result) {
        for (AdminDept dept : allDepts) {
            if (parentId.equals(dept.getParentId())) {
                result.add(dept.getId());
                findChildDeptIdsInMemory(dept.getId(), allDepts, result);
            }
        }
    }

    /**
     * 获取当前管理员的最大数据权限范围
     * <p>
     * 一个管理员可能有多个角色，每个角色的数据权限范围可能不同。
     * 我们取权限范围最大的（code值最小的），因为：
     * - code=1 (ALL) 权限最大，能看到所有数据
     * - code=4 (SELF) 权限最小，只能看自己的数据
     * 所以取最小值就是取最大权限。
     * </p>
     *
     * @param adminUserId 管理员ID
     * @return 最大数据权限范围的code值，如果没有角色返回null
     */
    private Integer getMaxDataScope(Long adminUserId) {
        // 1. 查询管理员关联的角色ID列表
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminUserId)
        );
        if (userRoles.isEmpty()) {
            return null;
        }

        // 2. 取出角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toList());

        // 3. 查询角色详情，只取启用状态的角色
        List<AdminRole> roles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>()
                        .in(AdminRole::getId, roleIds)
                        .eq(AdminRole::getStatus, 1)
        );
        if (roles.isEmpty()) {
            return null;
        }

        // 4. 取所有角色中dataScope最小的值（权限最大的范围）
        return roles.stream()
                .map(AdminRole::getDataScope)
                .min(Integer::compareTo)
                .orElse(null);
    }
}
