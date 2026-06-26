package com.shop.order.config;

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
 * 配置两样东西：
 * 1. 分页插件：让MyBatis-Plus支持分页查询，不配置的话selectPage不生效
 * 2. 自动填充处理器：新增/修改数据时自动填充createTime和updateTime字段，
 *    不用手动在代码里set时间了
 * </p>
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 分页插件配置
     * <p>
     * 不加这个插件，MyBatis-Plus的selectPage方法就不会自动拼接LIMIT语句，
     * 会查出所有数据，分页就不生效了。
     * </p>
     *
     * @return MyBatis-Plus拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件，指定数据库类型为MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器
     * <p>
     * 当实体类字段上标注了 @TableField(fill = FieldFill.INSERT) 时，
     * 新增数据会自动填充createTime；
     * 标注了 @TableField(fill = FieldFill.INSERT_UPDATE) 时，
     * 新增和修改都会自动填充updateTime。
     * 这样就不用每次手动set时间了，省事又不会忘。
     * </p>
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            /**
             * 新增时自动填充
             * <p>
             * 当执行insert操作时，自动把createTime和updateTime设为当前时间。
             * </p>
             *
             * @param metaObject MyBatis-Plus的元对象，可以获取和设置实体类字段值
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }

            /**
             * 修改时自动填充
             * <p>
             * 当执行update操作时，自动把updateTime设为当前时间。
             * createTime不会变，因为创建时间不应该被修改。
             * </p>
             *
             * @param metaObject MyBatis-Plus的元对象
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
