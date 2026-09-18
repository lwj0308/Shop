package com.shop.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.shop.admin.security.DataScopeInterceptor;
import com.shop.common.config.MyBatisPlusSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * MyBatis-Plus配置类
 * <p>
 * 配置MyBatis-Plus的插件和自动填充功能：
 * 1. 分页插件：让分页查询变得简单，不用手写limit语句
 * 2. 数据权限拦截器：根据管理员的数据权限范围自动追加SQL过滤条件
 * 3. 自动填充：createTime和updateTime字段自动赋值，不用每次手动设置
 * </p>
 * <p>
 * 注意：本服务的拦截器链和其他服务不一样（多了数据权限拦截器），
 * 所以 mybatisPlusInterceptor 不能直接用 {@link MyBatisPlusSupport#paginationInterceptor()}，
 * 只有自动填充处理器是共用的。
 * </p>
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 数据权限拦截器，根据管理员角色自动拼接数据权限SQL
     * <p>
     * 用@Lazy延迟注入，打破循环依赖：
     * MybatisPlusAutoConfiguration → SqlSessionFactory → MybatisPlusInterceptor
     * → 本类 → DataScopeInterceptor → Mapper → SqlSessionFactory（循环！）
     * 加@Lazy后，Spring先注入一个代理对象，等第一次真正查询时才创建DataScopeInterceptor，
     * 此时SqlSessionFactory已经创建好了，不会循环。
     * </p>
     * 注意：@Lazy必须放在构造函数参数上，放在字段上Lombok不会传递，所以这里手写构造函数。
     */
    private final DataScopeInterceptor dataScopeInterceptor;

    /**
     * 手动构造函数：@Lazy必须放在参数上才能生效
     *
     * @param dataScopeInterceptor 数据权限拦截器（延迟注入的代理对象）
     */
    public MyBatisPlusConfig(@Lazy DataScopeInterceptor dataScopeInterceptor) {
        this.dataScopeInterceptor = dataScopeInterceptor;
    }

    /**
     * MyBatis-Plus拦截器配置
     * <p>
     * 添加分页插件和数据权限拦截器。
     * 注意：数据权限拦截器要放在分页插件前面，先过滤数据再分页，
     * 这样分页的总数才是过滤后的正确数量。
     * </p>
     *
     * @return MyBatis-Plus拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 先添加数据权限拦截器，在SQL执行前追加数据权限过滤条件
        interceptor.addInnerInterceptor(dataScopeInterceptor);
        // 再添加MySQL分页插件，告诉MyBatis-Plus我们用的是MySQL数据库
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器
     * <p>
     * 当插入或更新数据时，自动填充createTime和updateTime字段。
     * 填充规则和其他服务完全一致，直接复用 {@link MyBatisPlusSupport}。
     * </p>
     *
     * @return MetaObjectHandler实现
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return MyBatisPlusSupport.createTimeUpdateTimeFillHandler();
    }
}
