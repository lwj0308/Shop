package com.shop.admin.feign;

import com.shop.admin.feign.fallback.SeckillFeignClientFallbackFactory;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.vo.SeckillVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀活动服务Feign客户端
 * <p>
 * shop-admin（管理后台）通过此客户端调用 shop-seckill 的秒杀活动管理端接口。
 * 管理员可以创建平台秒杀活动、查看全平台秒杀活动、下架违规秒杀活动。
 * </p>
 * <p>
 * 鉴权说明：FeignAuthConfig 会自动把管理员的 Sa-Token 透传给 shop-seckill，
 * shop-seckill 校验登录态后放行（管理端接口不需要商家身份，只要登录即可）。
 * </p>
 */
@FeignClient(name = "shop-seckill", path = "/seckill", fallbackFactory = SeckillFeignClientFallbackFactory.class)
public interface SeckillFeignClient {

    /**
     * 创建平台秒杀活动
     * <p>
     * 管理员创建平台活动（merchantId=0），全平台用户可参与抢购。
     * </p>
     *
     * @param dto 秒杀活动参数
     * @return 秒杀活动ID
     */
    @PostMapping("/admin/create")
    Result<Long> adminCreateSeckill(@RequestBody SeckillCreateDTO dto);

    /**
     * 分页查询全平台秒杀活动
     * <p>
     * 管理员查看所有秒杀活动（含平台活动和商家活动），支持按状态筛选。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   秒杀活动状态（可选）：0待生效 1进行中 2已结束 3已下架
     * @return 分页秒杀活动列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<SeckillVO>> adminGetSeckillList(@RequestParam("pageNum") int pageNum,
                                                      @RequestParam("pageSize") int pageSize,
                                                      @RequestParam(value = "status", required = false) Integer status);

    /**
     * 下架秒杀活动
     * <p>
     * 管理员下架任意秒杀活动（含商家活动），下架后用户不能再参与该秒杀活动。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 操作结果
     */
    @PutMapping("/admin/{seckillId}/offline")
    Result<Void> adminOfflineSeckill(@PathVariable("seckillId") Long seckillId);
}
