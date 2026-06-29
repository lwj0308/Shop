package com.shop.marketing.service;

import com.shop.common.model.PageResult;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.dto.PromotionQueryDTO;
import com.shop.model.promotion.vo.PromotionVO;

import java.math.BigDecimal;

/**
 * 满减活动服务接口
 * <p>
 * 定义满减活动的创建、修改、下架、查询等操作。
 * 商家端只能管理自己的活动，管理端可以管理所有活动（含平台活动）。
 * </p>
 */
public interface PromotionService {

    /**
     * 创建满减活动
     *
     * @param merchantId 商家ID（0表示平台活动）
     * @param dto        创建参数
     * @return 满减活动ID
     */
    Long createPromotion(Long merchantId, PromotionCreateDTO dto);

    /**
     * 修改满减活动
     * <p>仅待生效状态可修改</p>
     *
     * @param merchantId  商家ID（校验归属权，平台活动 merchantId=0 仅管理员可改）
     * @param promotionId 满减活动ID
     * @param dto         修改参数
     */
    void updatePromotion(Long merchantId, Long promotionId, PromotionCreateDTO dto);

    /**
     * 下架满减活动
     *
     * @param merchantId  商家ID（校验归属权）
     * @param promotionId 满减活动ID
     */
    void offlinePromotion(Long merchantId, Long promotionId);

    /**
     * 查询满减活动列表（商家端/管理端通用）
     *
     * @param merchantId 商家ID（null 表示管理端查询所有）
     * @param query      查询条件
     * @return 分页满减活动列表
     */
    PageResult<PromotionVO> getPromotionList(Long merchantId, PromotionQueryDTO query);

    /**
     * 查询满减活动详情
     *
     * @param promotionId 满减活动ID
     * @return 满减活动VO
     */
    PromotionVO getPromotionDetail(Long promotionId);

    // ==================== 内部接口（供其他微服务通过 Feign 调用） ====================

    /**
     * 计算满减优惠金额（内部接口）
     * <p>
     * shop-order 下单时通过 Feign 调用此接口，传入订单金额和商家ID，
     * 返回该订单能享受的最大满减优惠金额（多个活动满足时取最优惠的一个）。
     * </p>
     *
     * @param dto 计算请求参数（包含商家ID、订单金额、SKU列表）
     * @return 优惠金额，没有满足门槛的活动返回 0
     */
    BigDecimal calculatePromotion(PromotionCalculateDTO dto);
}
