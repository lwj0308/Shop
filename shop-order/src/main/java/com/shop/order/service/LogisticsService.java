package com.shop.order.service;

import com.shop.model.order.dto.DeliveryDTO;
import com.shop.model.order.vo.OrderDetailVO;

/**
 * 物流服务接口
 * <p>
 * 定义物流相关的业务方法，包括商家发货和查看物流信息。
 * </p>
 */
public interface LogisticsService {

    /**
     * 商家发货
     * <p>
     * 只有"待发货"状态的订单才能发货。
     * 发货后订单状态变为"运输中"，并创建物流记录。
     * </p>
     *
     * @param dto 发货参数（包含快递单号和快递公司）
     */
    void delivery(DeliveryDTO dto);

    /**
     * 查看物流信息
     * <p>
     * 返回订单的物流轨迹信息。
     * </p>
     *
     * @param orderId 订单ID
     * @return 物流信息（包含在订单详情中）
     */
    OrderDetailVO.OrderLogisticsVO getLogistics(Long orderId);
}
