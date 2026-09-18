package com.shop.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus默认配置工厂（各业务服务共用）
 * <p>
 * 9个业务服务原本各自复制了一份 MyBatisPlusConfig，其中分页插件和
 * createTime/updateTime 自动填充的写法完全相同，统一收敛到这里；
 * 各服务的 MyBatisPlusConfig 仍然自己声明 @Bean 方法（Bean 名字
 * mybatisPlusInterceptor、metaObjectHandler 保持不变），只是把
 * "怎么构造"委托给本类，所以每个服务的容器装配方式与收敛前一致。
 * </p>
 * <p>
 * shop-admin 需要"数据权限拦截器 + 分页插件"的拦截器链，和其他服务不同，
 * 所以它自己组装 mybatisPlusInterceptor，只复用 {@link #createTimeUpdateTimeFillHandler()}。
 * </p>
 */
public final class MyBatisPlusSupport {

    /**
     * 工具类，不允许实例化
     */
    private MyBatisPlusSupport() {
    }

    /**
     * 构建只带MySQL分页插件的MyBatis-Plus拦截器
     * <p>
     * 不加这个插件，MyBatis-Plus的selectPage方法就不会自动拼接LIMIT语句，
     * 会查出所有数据，分页就不生效了。
     * </p>
     *
     * @return 已添加分页内部插件的MyBatis-Plus拦截器
     */
    public static MybatisPlusInterceptor paginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件，指定数据库类型为MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 构建自动填充处理器
     * <p>
     * 当实体类字段上标注了 @TableField(fill = FieldFill.INSERT) 时，
     * 新增数据会自动填充createTime；
     * 标注了 @TableField(fill = FieldFill.INSERT_UPDATE) 时，
     * 新增和修改都会自动填充updateTime。
     * 这样就不用每次手动set时间了，省事又不会忘。
     * </p>
     * <p>
     * 注意：BaseEntity 里只有 createTime/updateTime 标了fill，
     * deleted 走的是 @TableLogic（由MyBatis-Plus自己处理），所以这里不填其他字段。
     * </p>
     *
     * @return 填充createTime/updateTime的处理器
     */
    public static MetaObjectHandler createTimeUpdateTimeFillHandler() {
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
