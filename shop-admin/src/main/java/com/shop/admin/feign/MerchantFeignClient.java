package com.shop.admin.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.admin.feign.fallback.MerchantFeignClientFallbackFactory;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.merchant.vo.WithdrawOrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 商家服务Feign客户端
 * <p>
 * 通过Feign远程调用商家服务，管理后台用来管理商家入驻、审核、启禁用、提现审核等。
 * 使用fallbackFactory实现降级：当商家服务不可用时，走降级逻辑返回友好提示。
 * </p>
 */
@FeignClient(name = "shop-merchant", path = "/merchant", fallbackFactory = MerchantFeignClientFallbackFactory.class)
public interface MerchantFeignClient {

    /**
     * 分页查询商家列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有商家，支持按状态和关键词筛选。
     * </p>
     *
     * @param page    页码
     * @param size    每页条数
     * @param status  商家状态（可选）：0待审核 1已通过 2已拒绝 3已禁用
     * @param keyword 搜索关键词（可选）：按商家名称搜索
     * @return 分页商家列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<MerchantVO>> listMerchants(@RequestParam("page") int page,
                                                  @RequestParam("size") int size,
                                                  @RequestParam(value = "status", required = false) Integer status,
                                                  @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 根据商家ID查询商家详情（管理后台专用）
     * <p>
     * 管理员查看某个商家的详细信息，用于审核或管理操作。
     * </p>
     *
     * @param id 商家ID
     * @return 商家信息
     */
    @GetMapping("/admin/{id}")
    Result<MerchantVO> getMerchantById(@PathVariable("id") Long id);

    /**
     * 审核商家入驻申请
     * <p>
     * 管理员审核商家提交的入驻申请，可以同意或拒绝。
     * 审核通过后商家才能正常经营。
     * </p>
     *
     * @param dto 审核参数（包含商家ID、审核结果、备注）
     * @return 操作结果
     */
    @PutMapping("/audit")
    Result<Void> auditMerchant(@RequestBody MerchantAuditDTO dto);

    /**
     * 禁用商家
     * <p>
     * 管理员封禁违规商家，禁用后商家无法经营。
     * </p>
     *
     * @param id 商家ID
     * @return 操作结果
     */
    @PutMapping("/admin/{id}/disable")
    Result<Void> disableMerchant(@PathVariable("id") Long id);

    /**
     * 启用商家
     * <p>
     * 管理员解封商家，启用后商家可以正常经营。
     * </p>
     *
     * @param id 商家ID
     * @return 操作结果
     */
    @PutMapping("/admin/{id}/enable")
    Result<Void> enableMerchant(@PathVariable("id") Long id);

    /**
     * 管理端查询所有提现申请
     * <p>管理员查看全平台的提现申请，支持按状态筛选</p>
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 提现状态（可选）：0待审核 1已通过 2已拒绝 3已打款
     * @return 分页提现申请列表（含商家名称）
     */
    @GetMapping("/admin/withdraw/list")
    Result<PageResult<WithdrawOrderVO>> adminGetWithdrawList(@RequestParam("page") int page,
                                                              @RequestParam("size") int size,
                                                              @RequestParam(value = "status", required = false) Integer status);

    /**
     * 审核提现申请
     * <p>
     * 管理员审核商家的提现申请：
     * - 审核通过：冻结金额扣减（钱已打款）
     * - 审核拒绝：冻结金额转回可用余额（解冻）
     * </p>
     *
     * @param dto 审核参数（含提现ID、审核结果、备注）
     * @return 操作结果
     */
    @PutMapping("/admin/withdraw/audit")
    Result<Void> auditWithdraw(@RequestBody WithdrawAuditDTO dto);
}
