package com.shop.model.merchant.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算流水响应VO
 * <p>
 * 返回给前端的结算流水信息，每条记录对应一笔订单的结算。
 * 商家在结算流水页面可以看到：哪个订单、多少钱、平台抽了多少、自己得了多少。
 * </p>
 */
@Data
public class SettlementRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 结算流水ID */
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 订单号 */
    private String orderNo;

    /** 订单金额（元） */
    private BigDecimal orderAmount;

    /** 平台抽成比例（如0.05表示5%） */
    private BigDecimal commissionRate;

    /** 平台抽成金额（元） */
    private BigDecimal commissionAmount;

    /** 商家应得金额（元） */
    private BigDecimal settlementAmount;

    /** 结算状态：0-待结算 1-已结算 2-已退款 */
    private Integer status;

    /** 结算状态描述（中文） */
    private String statusDesc;

    /** 结算时间 */
    private LocalDateTime settleTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
