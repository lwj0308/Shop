package com.shop.admin.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.OrderFeignClientFallbackFactory;
import com.shop.model.order.vo.AdminTodayStatsVO;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 订单服务Feign客户端
 * <p>
 * 通过Feign远程调用订单服务，管理后台用来管理订单查看、发货等。
 * 使用fallbackFactory实现降级：当订单服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(name = "shop-order", contextId = "adminOrder", path = "/order", fallbackFactory = OrderFeignClientFallbackFactory.class)
public interface OrderFeignClient {

    /**
     * 分页查询订单列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有订单，支持按状态和订单号筛选。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  订单状态（可选）：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款
     * @param orderNo 订单号（可选）：精确搜索
     * @return 分页订单列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<OrderVO>> listOrders(@RequestParam("page") int page,
                                            @RequestParam("size") int size,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "orderNo", required = false) String orderNo);

    /**
     * 根据订单ID查询订单详情（管理后台专用）
     * <p>
     * 管理员查看某个订单的完整信息，包含商品明细、收货地址、物流信息等。
     * </p>
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/admin/{id}")
    Result<OrderDetailVO> getOrderDetail(@PathVariable("id") Long id);

    /**
     * 管理后台发货
     * <p>
     * 管理员代替商家发货，填写快递单号和快递公司。
     * </p>
     *
     * @param id               订单ID
     * @param logisticsNo      快递单号
     * @param logisticsCompany 快递公司
     * @return 操作结果
     */
    @PutMapping("/admin/{id}/deliver")
    Result<Void> deliverOrder(@PathVariable("id") Long id,
                               @RequestParam("logisticsNo") String logisticsNo,
                               @RequestParam("logisticsCompany") String logisticsCompany);

    /**
     * 获取全平台今日统计（管理后台仪表盘用）
     * <p>
     * 调用订单服务的内部统计接口，获取今日订单数和今日销售额。
     * 给管理后台仪表盘展示用。
     * </p>
     *
     * @return 今日统计数据
     */
    @GetMapping("/admin/stats/today")
    Result<AdminTodayStatsVO> getTodayStats();
}
