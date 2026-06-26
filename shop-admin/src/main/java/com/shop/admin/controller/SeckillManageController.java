package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.SeckillFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.vo.SeckillVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀活动管理控制器
 * <p>
 * 管理后台对秒杀活动的管理接口，包括创建平台秒杀活动、查看全平台秒杀活动、下架秒杀活动。
 * 所有接口都需要管理员拥有对应的权限才能访问（超级管理员自动放行）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/seckill")
@Tag(name = "秒杀活动管理", description = "管理后台对秒杀活动的管理接口")
@RequiredArgsConstructor
public class SeckillManageController {

    /** 秒杀活动服务Feign客户端，远程调用商家服务 */
    private final SeckillFeignClient seckillFeignClient;

    /**
     * 创建平台秒杀活动
     * <p>
     * 管理员创建平台活动（merchantId=0），全平台用户可参与抢购。
     * 需要 seckill:create 权限。
     * </p>
     *
     * @param dto 秒杀活动参数
     * @return 秒杀活动ID
     */
    @Operation(summary = "创建平台秒杀活动", description = "管理员创建平台秒杀活动，全平台用户可参与抢购")
    @PostMapping
    @RequirePermission("seckill:create")
    @OperationLog(module = "秒杀活动管理", type = OperationType.CREATE, description = "创建平台秒杀活动：#dto.productId")
    public Result<Long> createSeckill(@RequestBody @Validated SeckillCreateDTO dto) {
        return seckillFeignClient.adminCreateSeckill(dto);
    }

    /**
     * 分页查询全平台秒杀活动
     * <p>
     * 管理员查看所有秒杀活动（含平台活动和商家活动），支持按状态筛选。
     * 需要 seckill:list 权限。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   秒杀活动状态（可选）
     * @return 分页秒杀活动列表
     */
    @Operation(summary = "查询秒杀活动列表", description = "分页查询全平台秒杀活动，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("seckill:list")
    @OperationLog(module = "秒杀活动管理", type = OperationType.QUERY, description = "查询秒杀活动列表")
    public Result<PageResult<SeckillVO>> listSeckills(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) Integer status) {
        return seckillFeignClient.adminGetSeckillList(pageNum, pageSize, status);
    }

    /**
     * 下架秒杀活动
     * <p>
     * 管理员下架任意秒杀活动（含商家活动），下架后用户不能再参与该秒杀活动。
     * 需要 seckill:offline 权限。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 操作结果
     */
    @Operation(summary = "下架秒杀活动", description = "下架后用户不能再参与该秒杀活动")
    @PutMapping("/{seckillId}/offline")
    @RequirePermission("seckill:offline")
    @OperationLog(module = "秒杀活动管理", type = OperationType.UPDATE, description = "下架秒杀活动：#seckillId")
    public Result<Void> offlineSeckill(@PathVariable Long seckillId) {
        return seckillFeignClient.adminOfflineSeckill(seckillId);
    }
}
