package com.shop.seckill.service;

import com.shop.common.model.PageResult;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.dto.SeckillQueryDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.vo.SeckillVO;

/**
 * 秒杀活动服务接口
 * <p>
 * 定义秒杀活动的创建、下架、查询等操作。
 * 商家端只能管理自己的活动，管理端可以管理所有活动（含平台活动）。
 * </p>
 */
public interface SeckillActivityService {

    /**
     * 创建秒杀活动
     * <p>
     * 创建时会校验秒杀价、库存、限购数量和时间窗口，根据当前时间判断初始状态。
     * 插入数据库后将秒杀库存预热到 Redis（key 为 seckill:stock:{活动ID}），
     * 用户抢购时通过 Lua 脚本原子扣减 Redis 库存，防止超卖。
     * </p>
     *
     * @param merchantId 商家ID（0表示平台活动）
     * @param dto        创建参数
     * @return 秒杀活动ID
     */
    Long createSeckillActivity(Long merchantId, SeckillCreateDTO dto);

    /**
     * 下架秒杀活动
     * <p>下架后用户无法继续抢购，同时清理 Redis 中的库存缓存</p>
     *
     * @param merchantId 商家ID（校验归属权；merchantId=0 表示平台活动管理员可下架任意活动）
     * @param seckillId 秒杀活动ID
     */
    void offlineSeckillActivity(Long merchantId, Long seckillId);

    /**
     * 查询秒杀活动列表（商家端/管理端通用）
     *
     * @param merchantId 商家ID（null 表示管理端查询所有，>0 表示查指定商家的活动）
     * @param query      查询条件（支持状态筛选、分页）
     * @return 分页秒杀活动列表
     */
    PageResult<SeckillVO> getSeckillList(Long merchantId, SeckillQueryDTO query);

    /**
     * 查询秒杀活动详情
     *
     * @param seckillId 秒杀活动ID
     * @return 秒杀活动VO
     */
    SeckillVO getSeckillDetail(Long seckillId);

    // ==================== 内部接口（供其他微服务通过 Feign 调用） ====================

    /**
     * 根据ID查询秒杀活动实体（内部接口）
     * <p>
     * 供 shop-order 通过 Feign 调用，下单时查询秒杀活动信息（价格、限购、时间窗口等）。
     * 直接返回实体而不是 VO，方便其他服务使用原始字段。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 秒杀活动实体
     */
    SeckillActivity getSeckillById(Long seckillId);
}
