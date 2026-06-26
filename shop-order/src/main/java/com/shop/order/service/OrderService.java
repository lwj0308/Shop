package com.shop.order.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderVO;

/**
 * 订单服务接口
 * <p>
 * 定义订单相关的业务方法，包括创建订单、取消订单、查询订单等。
 * 创建订单是核心链路，涉及分布式事务。
 * </p>
 */
public interface OrderService {

    /**
     * 创建订单
     * <p>
     * 核心业务流程：
     * 1. 通过Feign调用商品服务获取SKU信息
     * 2. 通过Feign调用用户服务获取地址信息
     * 3. 计算订单金额
     * 4. 创建订单+订单明细+地址快照
     * 5. 通过Feign调用商品服务扣减库存（Seata AT分布式事务保证一致性）
     * 6. 发送延时消息（30分钟超时自动取消）
     * 7. 下单成功后删除购物车
     * </p>
     *
     * @param userId 用户ID
     * @param dto    创建订单参数
     * @return 订单详情
     */
    OrderDetailVO createOrder(Long userId, OrderCreateDTO dto);

    /**
     * 取消订单
     * <p>
     * 只有"待付款"状态的订单才能取消。
     * 取消后需要回滚库存。
     * </p>
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @param dto     取消原因
     */
    void cancelOrder(Long userId, Long orderId, OrderCancelDTO dto);

    /**
     * 获取订单详情
     * <p>
     * 包含订单基本信息、商品明细、收货地址、物流信息。
     * </p>
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单详情
     */
    OrderDetailVO getOrderDetail(Long userId, Long orderId);

    /**
     * 获取订单列表
     * <p>
     * 按状态筛选，支持分页。
     * </p>
     *
     * @param userId     用户ID
     * @param status     订单状态（null表示查所有状态）
     * @param pageRequest 分页参数
     * @return 分页订单列表
     */
    PageResult<OrderVO> getOrderList(Long userId, Integer status, PageRequest pageRequest);

    /**
     * 确认收货
     * <p>
     * 只有"运输中"状态的订单才能确认收货。
     * 确认收货后状态变为"已收货"。
     * </p>
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    void confirmReceive(Long userId, Long orderId);

    /**
     * 自动取消超时订单
     * <p>
     * MQ消费者调用：30分钟后检查订单是否已支付，未支付则自动取消。
     * </p>
     *
     * @param orderNo 订单号
     */
    void autoCancelOrder(String orderNo);

    /**
     * 支付成功回调
     * <p>
     * 支付服务调用：用户支付成功后，更新订单状态为"待发货"。
     * </p>
     *
     * @param orderNo 订单号
     */
    void paySuccess(String orderNo);
}
