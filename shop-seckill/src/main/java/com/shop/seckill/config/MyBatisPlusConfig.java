package com.shop.seckill.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus配置类
 * <p>
 * 配置MyBatis-Plus的插件和自动填充功能：
 * 1. 分页插件：让分页查询变得简单，不用手写limit语句
 * 2. 自动填充：createTime和updateTime字段自动赋值，不用每次手动设置
 * </p>
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus拦截器配置
     * <p>
     * 添加分页插件，让MyBatis-Plus的分页查询生效。
     * 不加这个插件的话，selectPage方法不会自动添加limit语句。
     * </p>
     *
     * @return MyBatis-Plus拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加MySQL分页插件，告诉MyBatis-Plus我们用的是MySQL数据库
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器
     * <p>
     * 当插入或更新数据时，自动填充createTime和updateTime字段。
     * 这样就不用在业务代码里手动设置时间了，减少重复代码。
     * </p>
     *
     * @return MetaObjectHandler实现
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            /**
             * 插入数据时自动填充
             * <p>
             * 新增记录时，createTime和updateTime都设置为当前时间。
             * </p>
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }

            /**
             * 更新数据时自动填充
             * <p>
             * 修改记录时，只更新updateTime为当前时间。
             * createTime不应该被修改，所以这里不填充。
             * </p>
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
