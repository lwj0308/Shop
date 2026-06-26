package com.shop.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.order.dto.RefundApplyDTO;
import com.shop.model.order.dto.RefundAuditDTO;
import com.shop.model.order.entity.RefundOrder;
import com.shop.model.order.enums.RefundStatusEnum;
import com.shop.model.order.vo.RefundVO;
import com.shop.order.mapper.RefundOrderMapper;
import com.shop.order.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 退款控制器
 * <p>
 * 处理退款相关的接口：申请退款、审核退款、查询退款列表。
 * 申请退款需要用户登录，审核退款需要商家登录。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order/refund")
@RequiredArgsConstructor
@Tag(name = "退款管理", description = "退款申请、审核和查询")
public class RefundController {

    /** 退款服务 */
    private final RefundService refundService;

    /** 退款单Mapper（管理端直接查数据库，不走Service） */
    private final RefundOrderMapper refundOrderMapper;

    /**
     * 申请退款
     * <p>
     * 用户申请退款，需要指定订单ID、订单明细ID和退款原因。
     * 只有"待发货"状态的订单才能申请退款。
     * </p>
     *
     * @param dto 退款申请参数
     * @return 退款单信息
     */
    @PostMapping("/apply")
    @Operation(summary = "申请退款", description = "用户申请退款，订单状态变为退款中")
    public Result<RefundVO> applyRefund(@Validated @RequestBody RefundApplyDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        RefundVO refundVO = refundService.applyRefund(userId, dto);
        return Result.success("退款申请已提交", refundVO);
    }

    /**
     * 审核退款
     * <p>
     * 商家审核退款申请，可以同意或拒绝。
     * status=1同意，status=2拒绝。
     * </p>
     *
     * @param dto 退款审核参数
     * @return 操作结果
     */
    @PutMapping("/audit")
    @Operation(summary = "审核退款", description = "商家审核退款申请，同意或拒绝")
    public Result<Void> auditRefund(@Validated @RequestBody RefundAuditDTO dto) {
        refundService.auditRefund(dto);
        return Result.success("审核完成", null);
    }

    /**
     * 获取退款列表
     * <p>
     * 查询某个订单的所有退款记录。
     * </p>
     *
     * @param orderId 订单ID
     * @return 退款列表
     */
    @GetMapping("/list")
    @Operation(summary = "退款列表", description = "查询指定订单的退款记录列表")
    public Result<List<RefundVO>> getRefundList(@RequestParam Long orderId) {
        List<RefundVO> refundList = refundService.getRefundList(orderId);
        return Result.success(refundList);
    }

    /**
     * 分页查询退款列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有退款记录，支持按状态筛选。
     * 和用户端的区别：用户端只能查自己订单的退款，管理端查所有退款。
     * </p>
     *
     * @param page   页码（从1开始）
     * @param size   每页条数
     * @param status 退款状态（可选）：0待审核 1已同意 2已拒绝 3退款中 4已退款
     * @return 分页退款列表
     */
    @GetMapping("/admin/list")
    public Result<PageResult<RefundVO>> adminListRefunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        // 第1步：构建分页对象
        Page<RefundOrder> pageObj = new Page<>(page, size);

        // 第2步：构建查询条件，支持按状态筛选
        LambdaQueryWrapper<RefundOrder> wrapper = new LambdaQueryWrapper<RefundOrder>()
                .eq(status != null, RefundOrder::getStatus, status)
                .orderByDesc(RefundOrder::getCreateTime);

        // 第3步：执行分页查询
        Page<RefundOrder> result = refundOrderMapper.selectPage(pageObj, wrapper);

        // 第4步：把实体转成VO返回给前端
        List<RefundVO> voList = result.getRecords().stream().map(refund -> {
            RefundVO vo = new RefundVO();
            vo.setId(refund.getId());
            vo.setRefundNo(refund.getRefundNo());
            vo.setOrderId(refund.getOrderId());
            vo.setOrderNo(refund.getOrderNo());
            vo.setOrderItemId(refund.getOrderItemId());
            vo.setRefundAmount(refund.getRefundAmount());
            vo.setReason(refund.getReason());
            vo.setStatus(refund.getStatus());
            vo.setStatusDesc(getRefundStatusDesc(refund.getStatus()));
            vo.setAuditNote(refund.getAuditNote());
            vo.setAuditTime(refund.getAuditTime());
            vo.setRefundTime(refund.getRefundTime());
            vo.setCreateTime(refund.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        // 第5步：构建分页结果
        return Result.success(PageResult.from(result, voList));
    }

    // ==================== 私有方法 ====================

    /**
     * 获取退款状态描述
     * <p>
     * 把数字状态码转成中文描述，比如0转成"待审核"。
     * </p>
     *
     * @param status 状态码
     * @return 状态描述
     */
    private String getRefundStatusDesc(Integer status) {
        if (status == null) return "未知";
        RefundStatusEnum statusEnum = RefundStatusEnum.getByCode(status);
        return statusEnum != null ? statusEnum.getDesc() : "未知";
    }
}
