package com.shop.payment.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.shop.common.config.MyBatisPlusSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus配置类
 * <p>
 * 分页插件、createTime/updateTime自动填充的写法各服务完全一致，
 * 已统一收敛到 {@link MyBatisPlusSupport}；这里保留本服务的 @Bean 声明，
 * Bean 名字（mybatisPlusInterceptor、metaObjectHandler）与收敛前一致。
 * </p>
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 分页插件配置
     * <p>
     * 不加这个插件，MyBatis-Plus的selectPage方法不会自动拼接LIMIT语句，分页不生效。
     * </p>
     *
     * @return MyBatis-Plus拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return MyBatisPlusSupport.paginationInterceptor();
    }

    /**
     * 自动填充处理器
     * <p>
     * 新增填createTime+updateTime，修改只填updateTime，业务代码不用再手动set时间。
     * </p>
     *
     * @return MetaObjectHandler实现
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return MyBatisPlusSupport.createTimeUpdateTimeFillHandler();
    }
}
