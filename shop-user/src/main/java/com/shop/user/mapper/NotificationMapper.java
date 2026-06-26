package com.shop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.model.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息通知Mapper接口
 * <p>
 * 继承MyBatis-Plus的BaseMapper，自动拥有增删改查能力，
 * 不用写一行SQL就能操作notification表。
 * </p>
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
