package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.OrderFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理控制器
 * <p>
 * 管理后台对订单的管理接口，包括订单列表查询、详情查询、发货。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/order")
@Tag(name = "订单管理", description = "管理后台对订单的管理接口")
@RequiredArgsConstructor
public class OrderManageController {

    /** 订单服务Feign客户端，远程调用订单服务 */
    private final OrderFeignClient orderFeignClient;

    /**
     * 分页查询订单列表
     * <p>
     * 管理员查看所有订单，支持按状态和订单号筛选。
     * 需要 order:list 权限。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  订单状态（可选）
     * @param orderNo 订单号（可选）
     * @return 分页订单列表
     */
    @Operation(summary = "查询订单列表", description = "分页查询订单列表，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("order:list")
    @OperationLog(module = "订单管理", type = OperationType.QUERY, description = "查询订单列表")
    public Result<PageResult<OrderVO>> listOrders(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) String orderNo) {
        return orderFeignClient.listOrders(page, size, status, orderNo);
    }

    /**
     * 根据订单ID查询订单详情
     * <p>
     * 管理员查看某个订单的完整信息，包含商品明细、收货地址、物流信息等。
     * 需要 order:detail 权限。
     * </p>
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @Operation(summary = "查询订单详情", description = "根据ID查询订单详细信息")
    @GetMapping("/{id}")
    @RequirePermission("order:detail")
    @OperationLog(module = "订单管理", type = OperationType.QUERY, description = "查询订单详情：#id")
    public Result<OrderDetailVO> getOrderDetail(@PathVariable Long id) {
        return orderFeignClient.getOrderDetail(id);
    }

    /**
     * 管理后台发货
     * <p>
     * 管理员代替商家发货，填写快递单号和快递公司。
     * 需要 order:delivery 权限。
     * </p>
     *
     * @param id               订单ID
     * @param logisticsNo      快递单号
     * @param logisticsCompany 快递公司
     * @return 操作结果
     */
    @Operation(summary = "订单发货", description = "管理员发货，填写快递信息")
    @PutMapping("/{id}/deliver")
    @RequirePermission("order:delivery")
    @OperationLog(module = "订单管理", type = OperationType.UPDATE, description = "订单发货：#id")
    public Result<Void> deliverOrder(@PathVariable Long id,
                                      @RequestParam String logisticsNo,
                                      @RequestParam String logisticsCompany) {
        return orderFeignClient.deliverOrder(id, logisticsNo, logisticsCompany);
    }
}
