package com.shop.model.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 物流信息实体
 * <p>
 * 对应数据库 order_logistics 表，存储订单的物流（快递）信息。
 * 商家发货后，会生成一条物流记录，包含快递单号、快递公司等。
 * detail字段用JSON格式存储物流轨迹（从哪到哪的每一步），
 * 比如 [{"time":"2024-01-01 10:00","desc":"已揽收"}, {"time":"2024-01-01 15:00","desc":"运输中"}]
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "order_logistics", autoResultMap = true)
public class OrderLogistics extends BaseEntity {

    /** 订单ID（这个物流属于哪个订单） */
    private Long orderId;

    /** 订单号（方便查询） */
    private String orderNo;

    /** 快递单号（快递公司给的追踪号） */
    private String logisticsNo;

    /** 快递公司名称（比如"顺丰速运"、"中通快递"） */
    private String logisticsCompany;

    /** 物流轨迹详情（JSON格式，存储每一步的物流信息） */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<LogisticsDetail> detail;

    /**
     * 物流轨迹详情项
     * <p>
     * 表示物流的每一步信息，比如"已揽收"、"运输中"、"派送中"等。
     * 这是一个内部类，因为只和物流信息一起使用。
     * </p>
     */
    @Data
    public static class LogisticsDetail implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 时间（这一步发生的时间） */
        private String time;

        /** 描述（这一步发生了什么，比如"已揽收"） */
        private String desc;
    }
}
