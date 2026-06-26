package com.shop.order.service;

import com.shop.model.order.dto.RefundApplyDTO;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.vo.RefundVO;

import java.util.List;

/**
 * 退款服务接口
 * <p>
 * 定义退款相关的业务方法，包括申请退款、审核退款、查询退款列表。
 * </p>
 */
public interface RefundService {

    /**
     * 申请退款
     * <p>
     * 只有"待发货"状态的订单才能申请退款。
     * 申请后订单状态变为"退款中"，等待商家审核。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    退款申请参数
     * @return 退款单信息
     */
    RefundVO applyRefund(Long userId, RefundApplyDTO dto);

    /**
     * 审核退款
     * <p>
     * 商家审核退款申请，可以同意或拒绝。
     * 同意退款后，订单状态变为"已退款"，并回滚库存。
     * </p>
     *
     * @param dto 退款审核参数
     */
    void auditRefund(RefundAuditDTO dto);

    /**
     * 获取退款列表
     * <p>
     * 查询某个订单的所有退款记录。
     * </p>
     *
     * @param orderId 订单ID
     * @return 退款列表
     */
    List<RefundVO> getRefundList(Long orderId);
}
