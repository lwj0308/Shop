package com.shop.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.order.dto.DeliveryDTO;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.OrderAddress;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderItem;
import com.shop.model.order.entity.OrderLogistics;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderItemVO;
import com.shop.model.order.vo.OrderVO;
import com.shop.order.mapper.OrderAddressMapper;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderLogisticsMapper;
import com.shop.order.service.LogisticsService;
import com.shop.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单控制器（用户端）
 * <p>
 * 处理用户端的订单接口：创建订单、取消订单、查询订单、确认收货等。
 * 这些接口都需要用户登录后才能访问。
 * 商家端的接口（发货、审核退款等）在LogisticsController和RefundController中。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "订单的创建、取消、查询、确认收货等操作")
public class OrderController {

    /** 订单服务 */
    private final OrderService orderService;

    /** 订单主表Mapper（管理端直接查数据库，不走Service） */
    private final OrderInfoMapper orderInfoMapper;

    /** 订单明细Mapper */
    private final OrderItemMapper orderItemMapper;

    /** 订单地址快照Mapper */
    private final OrderAddressMapper orderAddressMapper;

    /** 物流信息Mapper */
    private final OrderLogisticsMapper orderLogisticsMapper;

    /** 物流服务（管理端发货时复用已有发货逻辑） */
    private final LogisticsService logisticsService;

    /**
     * 创建订单
     * <p>
     * 用户点击"提交订单"按钮时调用这个接口。
     * 需要传入收货地址ID、商品列表和备注。
     * 支持幂等Token防止重复提交。
     * </p>
     *
     * @param dto 创建订单参数
     * @return 订单详情
     */
    @PostMapping
    @Operation(summary = "创建订单", description = "提交订单，包含商品列表和收货地址，支持幂等Token防重复提交")
    public Result<OrderDetailVO> createOrder(@Validated @RequestBody OrderCreateDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        OrderDetailVO orderDetail = orderService.createOrder(userId, dto);
        return Result.success("下单成功", orderDetail);
    }

    /**
     * 取消订单
     * <p>
     * 只有"待付款"状态的订单才能取消。
     * </p>
     *
     * @param id  订单ID
     * @param dto 取消原因
     * @return 操作结果
     */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "取消待付款的订单，需要填写取消原因")
    public Result<Void> cancelOrder(@PathVariable Long id, @Validated @RequestBody OrderCancelDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        orderService.cancelOrder(userId, id, dto);
        return Result.success("取消成功", null);
    }

    /**
     * 获取订单详情
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "订单详情", description = "获取订单的完整信息，包含商品明细、地址、物流")
    public Result<OrderDetailVO> getOrderDetail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        OrderDetailVO orderDetail = orderService.getOrderDetail(userId, id);
        return Result.success(orderDetail);
    }

    /**
     * 获取订单列表
     *
     * @param status      订单状态（null表示查所有状态）
     * @param pageRequest 分页参数
     * @return 分页订单列表
     */
    @GetMapping("/list")
    @Operation(summary = "订单列表", description = "获取当前用户的订单列表，可按状态筛选")
    public Result<PageResult<OrderVO>> getOrderList(
            @RequestParam(required = false) Integer status,
            PageRequest pageRequest) {
        Long userId = StpUtil.getLoginIdAsLong();
        PageResult<OrderVO> pageResult = orderService.getOrderList(userId, status, pageRequest);
        return Result.success(pageResult);
    }

    /**
     * 确认收货
     * <p>
     * 只有"运输中"状态的订单才能确认收货。
     * </p>
     *
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/{id}/confirm")
    @Operation(summary = "确认收货", description = "确认收到商品，订单状态变为已收货")
    public Result<Void> confirmReceive(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        orderService.confirmReceive(userId, id);
        return Result.success("确认收货成功", null);
    }

    /**
     * 支付成功回调（内部调用）
     * <p>
     * 支付服务通过MQ或Feign调用此接口，通知订单服务支付成功。
     * 这个接口不对外暴露，只允许内部服务调用。
     * </p>
     *
     * @param orderNo 订单号
     * @return 操作结果
     */
    @PostMapping("/pay-success")
    @Operation(summary = "支付成功回调", description = "支付服务内部调用，通知订单支付成功")
    public Result<Void> paySuccess(@RequestParam String orderNo) {
        orderService.paySuccess(orderNo);
        return Result.success("支付状态更新成功", null);
    }

    // ========== 管理后台专用接口（供 shop-admin 通过 Feign 调用） ==========

    /**
     * 分页查询订单列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有用户的订单，支持按状态和订单号筛选。
     * 和用户端列表的区别：用户端只查自己的订单，管理端查所有订单。
     * </p>
     *
     * @param page    页码（从1开始）
     * @param size    每页条数
     * @param status  订单状态（可选）：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款
     * @param orderNo 订单号（可选，模糊搜索）
     * @return 分页订单列表
     */
    @GetMapping("/admin/list")
    public Result<PageResult<OrderVO>> adminListOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo) {
        // 第1步：构建分页对象
        Page<OrderInfo> pageObj = new Page<>(page, size);

        // 第2步：构建查询条件，支持按状态和订单号筛选
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(status != null, OrderInfo::getStatus, status)
                .like(orderNo != null && !orderNo.isEmpty(), OrderInfo::getOrderNo, orderNo)
                .orderByDesc(OrderInfo::getCreateTime);

        // 第3步：执行分页查询
        Page<OrderInfo> result = orderInfoMapper.selectPage(pageObj, wrapper);

        // 第4步：把实体转成VO返回给前端
        List<OrderVO> voList = result.getRecords().stream().map(order -> {
            OrderVO vo = new OrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusDesc(getOrderStatusDesc(order.getStatus()));
            vo.setCreateTime(order.getCreateTime());
            vo.setPayTime(order.getPayTime());
            return vo;
        }).collect(Collectors.toList());

        // 第5步：构建分页结果
        return Result.success(PageResult.from(result, voList));
    }

    /**
     * 根据订单ID查询订单详情（管理后台专用）
     * <p>
     * 管理员查看订单完整信息，包含订单基本信息、商品明细、收货地址、物流信息。
     * 和用户端的区别：用户端会校验订单归属，管理端不校验。
     * </p>
     *
     * @param id 订单ID
     * @return 订单详情（包含明细、地址、物流）
     */
    @GetMapping("/admin/{id}")
    public Result<OrderDetailVO> adminGetOrderDetail(@PathVariable Long id) {
        // 第1步：查询订单主表
        OrderInfo order = orderInfoMapper.selectById(id);
        if (order == null) {
            return Result.success(null);
        }

        // 第2步：查询订单明细列表
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id)
        );

        // 第3步：查询地址快照
        OrderAddress address = orderAddressMapper.selectOne(
                new LambdaQueryWrapper<OrderAddress>().eq(OrderAddress::getOrderId, id)
        );

        // 第4步：查询物流信息（未发货时为null）
        OrderLogistics logistics = orderLogisticsMapper.selectOne(
                new LambdaQueryWrapper<OrderLogistics>().eq(OrderLogistics::getOrderId, id)
        );

        // 第5步：组装详情VO返回
        return Result.success(buildOrderDetailVO(order, items, address, logistics));
    }

    /**
     * 管理后台发货
     * <p>
     * 管理员在后台为订单发货，填写快递单号和快递公司。
     * 复用LogisticsService的发货逻辑，保证状态校验、物流记录、状态日志一致。
     * </p>
     *
     * @param id               订单ID
     * @param logisticsNo      快递单号
     * @param logisticsCompany 快递公司
     * @return 操作结果
     */
    @PutMapping("/admin/{id}/deliver")
    public Result<Void> adminDeliverOrder(
            @PathVariable Long id,
            @RequestParam String logisticsNo,
            @RequestParam String logisticsCompany) {
        // 构造发货DTO，复用LogisticsService的发货逻辑
        // LogisticsService.delivery()会：校验订单状态→创建物流记录→更新订单状态为运输中→记录状态日志
        DeliveryDTO dto = new DeliveryDTO();
        dto.setOrderId(id);
        dto.setLogisticsNo(logisticsNo);
        dto.setLogisticsCompany(logisticsCompany);

        logisticsService.delivery(dto);
        return Result.success("发货成功", null);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取订单状态描述
     * <p>
     * 把数字状态码转成中文描述，比如0转成"待付款"。
     * </p>
     *
     * @param status 状态码
     * @return 状态描述
     */
    private String getOrderStatusDesc(Integer status) {
        if (status == null) return "未知";
        OrderStatusEnum statusEnum = OrderStatusEnum.getByCode(status);
        return statusEnum != null ? statusEnum.getDesc() : "未知";
    }

    /**
     * 组装订单详情VO
     * <p>
     * 把订单主表、明细列表、地址快照、物流信息组装成一个完整的详情VO。
     * </p>
     *
     * @param order     订单主表
     * @param items     订单明细列表
     * @param address   地址快照
     * @param logistics 物流信息
     * @return 订单详情VO
     */
    private OrderDetailVO buildOrderDetailVO(OrderInfo order, List<OrderItem> items,
                                             OrderAddress address, OrderLogistics logistics) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(getOrderStatusDesc(order.getStatus()));
        vo.setRemark(order.getRemark());
        vo.setCancelReason(order.getCancelReason());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setFinishTime(order.getFinishTime());

        // 转换明细列表
        List<OrderItemVO> itemVOs = items.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            itemVO.setId(item.getId());
            itemVO.setProductId(item.getProductId());
            itemVO.setSkuId(item.getSkuId());
            itemVO.setProductName(item.getProductName());
            itemVO.setSkuSpec(item.getSkuSpec());
            itemVO.setProductImage(item.getProductImage());
            itemVO.setPrice(item.getPrice());
            itemVO.setQuantity(item.getQuantity());
            itemVO.setSubtotal(item.getSubtotal());
            return itemVO;
        }).collect(Collectors.toList());
        vo.setItems(itemVOs);

        // 转换地址
        if (address != null) {
            OrderDetailVO.OrderAddressVO addressVO = new OrderDetailVO.OrderAddressVO();
            addressVO.setName(address.getName());
            addressVO.setPhone(address.getPhone());
            addressVO.setProvince(address.getProvince());
            addressVO.setCity(address.getCity());
            addressVO.setDistrict(address.getDistrict());
            addressVO.setDetail(address.getDetail());
            vo.setAddress(addressVO);
        }

        // 转换物流
        if (logistics != null) {
            OrderDetailVO.OrderLogisticsVO logisticsVO = new OrderDetailVO.OrderLogisticsVO();
            logisticsVO.setLogisticsNo(logistics.getLogisticsNo());
            logisticsVO.setLogisticsCompany(logistics.getLogisticsCompany());
            if (logistics.getDetail() != null) {
                List<OrderDetailVO.LogisticsDetailVO> detailVOs = logistics.getDetail().stream()
                        .map(d -> {
                            OrderDetailVO.LogisticsDetailVO detailVO = new OrderDetailVO.LogisticsDetailVO();
                            detailVO.setTime(d.getTime());
                            detailVO.setDesc(d.getDesc());
                            return detailVO;
                        }).collect(Collectors.toList());
                logisticsVO.setDetail(detailVOs);
            }
            vo.setLogistics(logisticsVO);
        }

        return vo;
    }
}
