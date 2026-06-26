package com.shop.merchant.controller;

import cn.hutool.core.util.DesensitizedUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.context.UserContext;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.mapper.MerchantMapper;
import com.shop.merchant.service.MerchantService;
import com.shop.model.merchant.dto.MerchantApplyDTO;
import com.shop.model.merchant.entity.Merchant;
import com.shop.model.merchant.vo.MerchantAuditVO;
import com.shop.model.merchant.vo.MerchantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商家管理控制器
 * <p>
 * 提供商家入驻申请、信息查询、信息修改、审核状态查询等接口。
 * 商家相关的操作都在这里，审核操作在MerchantAuditController。
 * 使用SecurityUtils获取当前登录用户ID，替代直接从请求头获取。
 * </p>
 */
@Tag(name = "商家管理", description = "商家入驻、信息查询、修改等接口")
@Slf4j
@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantController {

    /** 商家服务，处理商家相关的业务逻辑 */
    private final MerchantService merchantService;

    /** 商家Mapper，管理后台直接查询商家表用 */
    private final MerchantMapper merchantMapper;

    /**
     * 商家入驻申请
     * <p>
     * 用户提交商家入驻申请，填写商家名称、联系电话、营业执照等信息。
     * 提交后状态为待审核，需要管理员审核通过才能经营。
     * </p>
     *
     * @param applyDTO 入驻申请参数
     * @return 商家信息
     */
    @Operation(summary = "商家入驻申请", description = "用户提交商家入驻申请，等待管理员审核")
    @PostMapping("/apply")
    public Result<MerchantVO> apply(@Validated @RequestBody MerchantApplyDTO applyDTO) {
        // 入驻申请接口被排除在Sa-Token拦截器之外，通过UserContext获取用户ID
        // UserContext由拦截器从Sa-Token或网关X-User-Id请求头中设置
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        MerchantVO merchant = merchantService.apply(userId, applyDTO);
        return Result.success(merchant);
    }

    /**
     * 获取当前商家信息
     * <p>
     * 商家登录后查看自己的商家信息，联系电话会脱敏显示。
     * </p>
     *
     * @return 商家信息（联系电话脱敏）
     */
    @Operation(summary = "获取当前商家信息", description = "商家登录后查看自己的信息")
    @GetMapping("/info")
    public Result<MerchantVO> getInfo() {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        return Result.success(merchant);
    }

    /**
     * 更新商家信息
     * <p>
     * 商家修改自己的基本信息和资质信息，修改后状态会重置为待审核。
     * 只有审核通过或已拒绝的商家才能修改信息。
     * </p>
     *
     * @param applyDTO 更新参数，和入驻申请参数一样
     * @return 操作结果
     */
    @Operation(summary = "更新商家信息", description = "商家修改基本信息，修改后需重新审核")
    @PutMapping("/info")
    public Result<Void> updateInfo(@Validated @RequestBody MerchantApplyDTO applyDTO) {
        Long userId = SecurityUtils.requireLogin();
        // 先通过用户ID找到商家
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        merchantService.updateMerchant(merchant.getId(), applyDTO);
        return Result.success(null);
    }

    /**
     * 获取审核状态
     * <p>
     * 商家查询自己的审核进展，看看是通过了还是被拒绝了。
     * </p>
     *
     * @return 审核状态信息
     */
    @Operation(summary = "获取审核状态", description = "商家查看自己的审核进展")
    @GetMapping("/audit-status")
    public Result<MerchantAuditVO> getAuditStatus() {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return Result.fail("商家信息不存在");
        }
        MerchantAuditVO auditStatus = merchantService.getAuditStatus(merchant.getId());
        return Result.success(auditStatus);
    }

    // ========== 管理后台专用接口（供 shop-admin 通过 Feign 调用） ==========

    /**
     * 分页查询商家列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有商家，支持按状态和关键词筛选。
     * 关键词按商家名称模糊搜索。联系电话返回时做脱敏处理。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  商家状态（可选）：0待审核 1已通过 2已拒绝 3已禁用
     * @param keyword 搜索关键词（可选）：按商家名称搜索
     * @return 分页商家列表
     */
    @GetMapping("/admin/list")
    public Result<PageResult<MerchantVO>> adminListMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        // 构造分页对象，MyBatis-Plus会自动拼接LIMIT语句
        Page<Merchant> pageParam = new Page<>(page, size);
        // 构造查询条件
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        // 按状态精确筛选
        if (status != null) {
            wrapper.eq(Merchant::getStatus, status);
        }
        // 按关键词模糊搜索商家名称
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Merchant::getName, keyword);
        }
        // 按创建时间倒序，新入驻的商家排前面
        wrapper.orderByDesc(Merchant::getCreateTime);
        // 执行分页查询
        Page<Merchant> result = merchantMapper.selectPage(pageParam, wrapper);
        // 把Merchant实体列表转成MerchantVO列表（联系电话脱敏）
        List<MerchantVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        // 用PageResult.from把MyBatis-Plus的分页结果转成项目的分页结果
        return Result.success(PageResult.from(result, voList));
    }

    /**
     * 根据商家ID查询商家详情（管理后台专用）
     * <p>
     * 直接复用MerchantService的getMerchantInfo方法，联系电话会做脱敏处理。
     * 商家不存在时会抛出BusinessException，由全局异常处理器统一处理。
     * </p>
     *
     * @param id 商家ID
     * @return 商家信息
     */
    @GetMapping("/admin/{id}")
    public Result<MerchantVO> adminGetMerchantById(@PathVariable Long id) {
        MerchantVO merchantVO = merchantService.getMerchantInfo(id);
        return Result.success(merchantVO);
    }

    /**
     * 禁用商家（管理后台专用）
     * <p>
     * 将商家状态改为3（已禁用），禁用后商家无法经营。
     * </p>
     *
     * @param id 商家ID
     * @return 操作结果
     */
    @PutMapping("/admin/{id}/disable")
    public Result<Void> adminDisableMerchant(@PathVariable Long id) {
        // 先检查商家是否存在，不存在就抛异常
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        // 只更新状态字段，其他字段不变
        Merchant updateMerchant = new Merchant();
        updateMerchant.setId(id);
        updateMerchant.setStatus(3);
        merchantMapper.updateById(updateMerchant);
        log.info("管理后台禁用商家: merchantId={}", id);
        return Result.success();
    }

    /**
     * 启用商家（管理后台专用）
     * <p>
     * 将商家状态改为1（已通过），启用后商家可以正常经营。
     * </p>
     *
     * @param id 商家ID
     * @return 操作结果
     */
    @PutMapping("/admin/{id}/enable")
    public Result<Void> adminEnableMerchant(@PathVariable Long id) {
        // 先检查商家是否存在，不存在就抛异常
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        // 只更新状态字段，其他字段不变
        Merchant updateMerchant = new Merchant();
        updateMerchant.setId(id);
        updateMerchant.setStatus(1);
        merchantMapper.updateById(updateMerchant);
        log.info("管理后台启用商家: merchantId={}", id);
        return Result.success();
    }

    /**
     * Merchant实体转MerchantVO（联系电话脱敏）
     * <p>
     * 把数据库查出来的Merchant对象转成返回给前端的MerchantVO对象。
     * 联系电话做脱敏处理，中间4位用*号替代，比如138****5678。
     * 这个方法和MerchantServiceImpl里的convertToVO逻辑一样，
     * 因为那边是private方法没法直接调用，所以这里复制了一份。
     * </p>
     *
     * @param merchant 商家实体
     * @return 商家VO（联系电话脱敏后）
     */
    private MerchantVO convertToVO(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        vo.setId(merchant.getId());
        vo.setName(merchant.getName());
        vo.setLogo(merchant.getLogo());
        vo.setDescription(merchant.getDescription());
        // 联系电话脱敏：13812345678 → 138****5678
        vo.setContactPhone(DesensitizedUtil.mobilePhone(merchant.getContactPhone()));
        vo.setStatus(merchant.getStatus());
        vo.setCreateTime(merchant.getCreateTime());
        vo.setUpdateTime(merchant.getUpdateTime());
        return vo;
    }
}
