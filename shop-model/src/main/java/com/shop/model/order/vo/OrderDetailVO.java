package com.shop.model.order.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情响应VO
 * <p>
 * 返回给前端的订单完整信息，包含订单基本信息、商品明细、收货地址、物流信息。
 * 用于订单详情页展示。
 * </p>
 */
@Data
public class OrderDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 运费金额 */
    private BigDecimal freightAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 订单状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款 */
    private Integer status;

    /** 订单状态描述（中文） */
    private String statusDesc;

    /** 订单备注 */
    private String remark;

    /** 取消原因 */
    private String cancelReason;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 订单商品明细列表 */
    private List<OrderItemVO> items;

    /** 收货地址信息 */
    private OrderAddressVO address;

    /** 物流信息（未发货时为null） */
    private OrderLogisticsVO logistics;

    /**
     * 收货地址VO（嵌套在订单详情中）
     * 手机号在Service层做脱敏处理（138****1234格式）
     */
    @Data
    public static class OrderAddressVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 收货人姓名 */
        private String name;

        /** 收货人手机号（脱敏后的，比如138****1234） */
        private String phone;

        /** 省 */
        private String province;

        /** 市 */
        private String city;

        /** 区 */
        private String district;

        /** 详细地址 */
        private String detail;
    }

    /**
     * 物流信息VO（嵌套在订单详情中）
     */
    @Data
    public static class OrderLogisticsVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 快递单号 */
        private String logisticsNo;

        /** 快递公司 */
        private String logisticsCompany;

        /** 物流轨迹 */
        private List<LogisticsDetailVO> detail;
    }

    /**
     * 物流轨迹详情VO
     */
    @Data
    public static class LogisticsDetailVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 时间 */
        private String time;

        /** 描述 */
        private String desc;
    }
}
