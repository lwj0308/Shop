package com.shop.admin.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.RefundFeignClientFallbackFactory;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.vo.RefundVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 退款服务Feign客户端
 * <p>
 * 通过Feign远程调用订单服务中的退款模块，管理后台用来管理退款审核。
 * 使用fallbackFactory实现降级：当订单服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(name = "shop-order", contextId = "adminRefund", path = "/order/refund", fallbackFactory = RefundFeignClientFallbackFactory.class)
public interface RefundFeignClient {

    /**
     * 分页查询退款列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有退款申请，支持按状态筛选。
     * </p>
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 退款状态（可选）：0待审核 1已同意 2已拒绝 3已退款
     * @return 分页退款列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<RefundVO>> listRefunds(@RequestParam("page") int page,
                                              @RequestParam("size") int size,
                                              @RequestParam(value = "status", required = false) Integer status);

    /**
     * 审核退款申请
     * <p>
     * 管理员审核退款申请，可以同意或拒绝。
     * 同意后系统会自动发起退款，拒绝时需要填写拒绝原因。
     * </p>
     *
     * @param dto 审核参数（包含退款单ID、审核结果、备注）
     * @return 操作结果
     */
    @PutMapping("/audit")
    Result<Void> auditRefund(@RequestBody RefundAuditDTO dto);
}
