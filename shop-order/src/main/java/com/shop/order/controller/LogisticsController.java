package com.shop.order.controller;

import com.shop.common.result.Result;
import com.shop.model.order.dto.DeliveryDTO;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.order.service.LogisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 物流控制器
 * <p>
 * 处理物流相关的接口：商家发货、查看物流信息。
 * 发货需要商家登录，查看物流需要用户登录。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order/logistics")
@RequiredArgsConstructor
@Tag(name = "物流管理", description = "商家发货和物流信息查询")
public class LogisticsController {

    /** 物流服务 */
    private final LogisticsService logisticsService;

    /**
     * 商家发货
     * <p>
     * 商家发货时填写快递单号和快递公司。
     * 只有"待发货"状态的订单才能发货。
     * </p>
     *
     * @param dto 发货参数
     * @return 操作结果
     */
    @PostMapping("/delivery")
    @Operation(summary = "商家发货", description = "商家填写快递信息发货，订单状态变为运输中")
    public Result<Void> delivery(@Validated @RequestBody DeliveryDTO dto) {
        logisticsService.delivery(dto);
        return Result.success("发货成功", null);
    }

    /**
     * 查看物流信息
     * <p>
     * 返回订单的物流轨迹信息。
     * </p>
     *
     * @param orderId 订单ID
     * @return 物流信息
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "查看物流", description = "查询订单的物流轨迹信息")
    public Result<OrderDetailVO.OrderLogisticsVO> getLogistics(@PathVariable Long orderId) {
        OrderDetailVO.OrderLogisticsVO logistics = logisticsService.getLogistics(orderId);
        return Result.success(logistics);
    }
}
