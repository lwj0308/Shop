package com.shop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.user.entity.UserLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作user_login_log表。
 * </p>
 */
@Mapper
public interface UserLoginLogMapper extends BaseMapper<UserLoginLog> {
}
