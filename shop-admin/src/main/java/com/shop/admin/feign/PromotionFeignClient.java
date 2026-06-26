package com.shop.admin.feign;

import com.shop.admin.feign.fallback.PromotionFeignClientFallbackFactory;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.vo.PromotionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 满减活动服务Feign客户端
 * <p>
 * shop-admin（管理后台）通过此客户端调用 shop-merchant 的满减活动管理端接口。
 * 管理员可以创建平台满减活动、查看全平台满减活动、下架违规满减活动。
 * </p>
 * <p>
 * 鉴权说明：FeignAuthConfig 会自动把管理员的 Sa-Token 透传给 shop-merchant，
 * shop-merchant 校验登录态后放行（管理端接口不需要商家身份，只要登录即可）。
 * </p>
 */
@FeignClient(name = "shop-merchant", path = "/merchant/promotion", fallbackFactory = PromotionFeignClientFallbackFactory.class)
public interface PromotionFeignClient {

    /**
     * 创建平台满减活动
     * <p>
     * 管理员创建平台活动（merchantId=0），全平台用户可享受。
     * </p>
     *
     * @param dto 满减活动参数
     * @return 满减活动ID
     */
    @PostMapping("/admin/create")
    Result<Long> adminCreatePromotion(@RequestBody PromotionCreateDTO dto);

    /**
     * 分页查询全平台满减活动
     * <p>
     * 管理员查看所有满减活动（含平台活动和商家活动），支持按状态筛选。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   满减活动状态（可选）：0待生效 1进行中 2已结束 3已下架
     * @return 分页满减活动列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<PromotionVO>> adminGetPromotionList(@RequestParam("pageNum") int pageNum,
                                                          @RequestParam("pageSize") int pageSize,
                                                          @RequestParam(value = "status", required = false) Integer status);

    /**
     * 下架满减活动
     * <p>
     * 管理员下架任意满减活动（含商家活动），下架后用户不能再享受该活动的优惠。
     * </p>
     *
     * @param promotionId 满减活动ID
     * @return 操作结果
     */
    @PutMapping("/admin/{promotionId}/offline")
    Result<Void> adminOfflinePromotion(@PathVariable("promotionId") Long promotionId);
}
