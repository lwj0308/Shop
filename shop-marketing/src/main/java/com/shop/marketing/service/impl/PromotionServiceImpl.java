package com.shop.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.marketing.mapper.PromotionMapper;
import com.shop.marketing.mapper.PromotionProductMapper;
import com.shop.marketing.service.PromotionService;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.dto.PromotionQueryDTO;
import com.shop.model.promotion.entity.Promotion;
import com.shop.model.promotion.entity.PromotionProduct;
import com.shop.model.promotion.enums.PromotionScopeTypeEnum;
import com.shop.model.promotion.enums.PromotionStatusEnum;
import com.shop.model.promotion.vo.PromotionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 满减活动服务实现类
 * <p>
 * 实现满减活动的创建、修改、下架、查询等操作。
 * 满减活动状态流转：待生效 → 进行中（到开始时间）→ 已结束（超过结束时间）
 * 商家/管理员可手动下架（变为已下架状态）。
 * </p>
 * <p>
 * 满减规则：订单金额满 threshold 元，减 discountAmount 元。
 * 参与范围：全店（scopeType=1）或指定商品（scopeType=2，需关联商品）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    /** 满减活动 Mapper */
    private final PromotionMapper promotionMapper;

    /** 满减活动商品关联 Mapper */
    private final PromotionProductMapper promotionProductMapper;

    /**
     * 创建满减活动
     * <p>
     * 创建时校验金额规则（门槛>0、优惠>0、优惠<门槛）和时间窗口（开始<结束），
     * 根据当前时间判断初始状态：
     * - 当前时间 < 开始时间 → 待生效(0)
     * - 开始时间 <= 当前时间 <= 结束时间 → 进行中(1)
     * - 当前时间 > 结束时间 → 已结束(2)
     * </p>
     * <p>
     * 如果是指定商品满减（scopeType=2），插入活动后还要批量插入商品关联表。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPromotion(Long merchantId, PromotionCreateDTO dto) {
        // 校验金额规则和时间窗口
        validatePromotionRule(dto);

        // 构建满减活动实体
        Promotion promotion = new Promotion();
        BeanUtils.copyProperties(dto, promotion);
        promotion.setMerchantId(merchantId);

        // 根据当前时间判断初始状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dto.getStartTime())) {
            promotion.setStatus(PromotionStatusEnum.PENDING.getCode());
        } else if (now.isAfter(dto.getEndTime())) {
            promotion.setStatus(PromotionStatusEnum.ENDED.getCode());
        } else {
            promotion.setStatus(PromotionStatusEnum.ACTIVE.getCode());
        }

        promotionMapper.insert(promotion);
        log.info("创建满减活动成功: id={}, name={}, merchantId={}", promotion.getId(), promotion.getName(), merchantId);

        // 指定商品满减需要写入关联表
        if (PromotionScopeTypeEnum.SPECIFIED.getCode() == dto.getScopeType()) {
            savePromotionProducts(promotion.getId(), dto.getProductIds());
        }
        return promotion.getId();
    }

    /**
     * 修改满减活动
     * <p>仅待生效状态可修改，避免进行中的活动规则被篡改</p>
     * <p>如果参与范围或商品列表有变化，先删除旧的关联数据再插入新的</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePromotion(Long merchantId, Long promotionId, PromotionCreateDTO dto) {
        Promotion promotion = getPromotionAndCheckOwnership(merchantId, promotionId);

        // 只有待生效状态才能修改
        if (!promotion.getStatus().equals(PromotionStatusEnum.PENDING.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "仅待生效状态的满减活动可修改");
        }

        validatePromotionRule(dto);

        // 处理商品关联：先删除旧关联，再按新规则插入
        // 不管原来是什么范围，先清掉旧关联，避免脏数据
        promotionProductMapper.delete(new LambdaQueryWrapper<PromotionProduct>()
                .eq(PromotionProduct::getPromotionId, promotionId));
        // 指定商品满减需要重新写入关联表
        if (PromotionScopeTypeEnum.SPECIFIED.getCode() == dto.getScopeType()) {
            savePromotionProducts(promotionId, dto.getProductIds());
        }

        // 更新活动基本信息
        BeanUtils.copyProperties(dto, promotion);
        promotionMapper.updateById(promotion);
        log.info("修改满减活动成功: id={}", promotionId);
    }

    /**
     * 下架满减活动
     * <p>下架后不再生效，下单时不会计算该活动的优惠</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlinePromotion(Long merchantId, Long promotionId) {
        Promotion promotion = getPromotionAndCheckOwnership(merchantId, promotionId);

        if (promotion.getStatus().equals(PromotionStatusEnum.OFFLINE.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "满减活动已是下架状态");
        }

        promotion.setStatus(PromotionStatusEnum.OFFLINE.getCode());
        promotionMapper.updateById(promotion);
        log.info("下架满减活动成功: id={}", promotionId);
    }

    /**
     * 查询满减活动列表
     *
     * @param merchantId null 表示管理端查询所有，>0 表示查指定商家的活动
     */
    @Override
    public PageResult<PromotionVO> getPromotionList(Long merchantId, PromotionQueryDTO query) {
        LambdaQueryWrapper<Promotion> wrapper = new LambdaQueryWrapper<>();
        // 管理端传 null 查所有，商家端传具体 merchantId
        if (merchantId != null) {
            wrapper.eq(Promotion::getMerchantId, merchantId);
        }
        // 状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(Promotion::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Promotion::getCreateTime);

        Page<Promotion> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<Promotion> result = promotionMapper.selectPage(page, wrapper);

        // 实体转 VO
        List<PromotionVO> records = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 使用 PageResult.from 从 MyBatis-Plus 的 Page 转换，自动填充分页信息
        return PageResult.from(result, records);
    }

    /**
     * 查询满减活动详情
     */
    @Override
    public PromotionVO getPromotionDetail(Long promotionId) {
        Promotion promotion = promotionMapper.selectById(promotionId);
        if (promotion == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "满减活动不存在");
        }
        return convertToVO(promotion);
    }

    // ==================== 内部接口实现 ====================

    /**
     * 计算满减优惠金额（内部接口，供 shop-order 通过 Feign 调用）
     * <p>
     * 查询该商家所有进行中且在时间窗口内的满减活动，遍历计算每个活动的优惠金额，
     * 返回最大的优惠金额（多个活动都满足时取最优惠的一个）。
     * </p>
     * <p>
     * 说明：全店满减和指定商品满减都统一用 orderAmount 判断门槛。
     * 因为一个订单只包含一个商家的商品，指定商品满减时用户买的就是参与活动的商品，
     * 所以直接用订单总额判断门槛即可，无需反查每个 SKU 的金额。
     * </p>
     */
    @Override
    public BigDecimal calculatePromotion(PromotionCalculateDTO dto) {
        BigDecimal orderAmount = dto.getOrderAmount();
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 查询该商家和平台（merchantId=0）所有进行中且在时间窗口内的满减活动
        // 小白讲解：平台活动所有商家都能享受，所以同时查 merchantId=商家的ID 和 merchantId=0
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Promotion> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Promotion::getMerchantId, dto.getMerchantId(), 0L)
                .eq(Promotion::getStatus, PromotionStatusEnum.ACTIVE.getCode())
                .le(Promotion::getStartTime, now)
                .ge(Promotion::getEndTime, now);
        List<Promotion> promotions = promotionMapper.selectList(wrapper);
        if (promotions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 遍历活动，计算每个活动的优惠金额，取最大值
        BigDecimal maxDiscount = BigDecimal.ZERO;
        for (Promotion promotion : promotions) {
            // 订单金额达到门槛才能享受满减
            if (orderAmount.compareTo(promotion.getThreshold()) >= 0) {
                if (promotion.getDiscountAmount().compareTo(maxDiscount) > 0) {
                    maxDiscount = promotion.getDiscountAmount();
                }
            }
        }
        return maxDiscount;
    }

    // ==================== 私有方法 ====================

    /**
     * 校验满减活动规则
     * <p>门槛和优惠金额必须大于0，优惠必须小于门槛，开始时间必须早于结束时间</p>
     * <p>指定商品满减时商品ID列表不能为空</p>
     */
    private void validatePromotionRule(PromotionCreateDTO dto) {
        // 金额校验
        if (dto.getThreshold() == null || dto.getThreshold().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "满减门槛金额必须大于0");
        }
        if (dto.getDiscountAmount() == null || dto.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "优惠金额必须大于0");
        }
        if (dto.getDiscountAmount().compareTo(dto.getThreshold()) >= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "优惠金额必须小于满减门槛");
        }
        // 时间窗口校验
        if (dto.getStartTime() == null || dto.getEndTime() == null
                || dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "活动开始时间必须早于结束时间");
        }
        // 指定商品满减时商品ID列表不能为空
        if (PromotionScopeTypeEnum.SPECIFIED.getCode() == dto.getScopeType()) {
            if (dto.getProductIds() == null || dto.getProductIds().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "指定商品满减必须选择参与活动的商品");
            }
        }
    }

    /**
     * 批量保存满减活动商品关联
     * <p>每个商品ID创建一条关联记录，skuId 留空表示该商品所有SKU都参与</p>
     *
     * @param promotionId 满减活动ID
     * @param productIds  商品ID列表
     */
    private void savePromotionProducts(Long promotionId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        for (Long productId : productIds) {
            PromotionProduct pp = new PromotionProduct();
            pp.setPromotionId(promotionId);
            pp.setProductId(productId);
            // skuId 留空，表示该商品的所有 SKU 都参与活动
            promotionProductMapper.insert(pp);
        }
    }

    /**
     * 获取满减活动并校验归属权
     *
     * @param merchantId  商家ID（0表示平台活动，只有管理员能操作）
     * @param promotionId 满减活动ID
     */
    private Promotion getPromotionAndCheckOwnership(Long merchantId, Long promotionId) {
        Promotion promotion = promotionMapper.selectById(promotionId);
        if (promotion == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "满减活动不存在");
        }
        if (!promotion.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此满减活动");
        }
        return promotion;
    }

    /**
     * 实体转 VO（填充状态描述、范围描述，指定商品满减时查询关联的商品ID列表）
     */
    private PromotionVO convertToVO(Promotion promotion) {
        PromotionVO vo = new PromotionVO();
        BeanUtils.copyProperties(promotion, vo);

        // 填充状态描述
        PromotionStatusEnum statusEnum = PromotionStatusEnum.getByCode(promotion.getStatus());
        if (statusEnum != null) {
            vo.setStatusDesc(statusEnum.getDesc());
        }
        // 填充参与范围描述
        PromotionScopeTypeEnum scopeEnum = PromotionScopeTypeEnum.getByCode(promotion.getScopeType());
        if (scopeEnum != null) {
            vo.setScopeTypeDesc(scopeEnum.getDesc());
        }
        // 指定商品满减时，查询关联的商品ID列表（编辑活动时回显用）
        if (PromotionScopeTypeEnum.SPECIFIED.getCode() == promotion.getScopeType()) {
            List<PromotionProduct> list = promotionProductMapper.selectList(
                    new LambdaQueryWrapper<PromotionProduct>()
                            .eq(PromotionProduct::getPromotionId, promotion.getId()));
            List<Long> productIds = new ArrayList<>();
            for (PromotionProduct pp : list) {
                productIds.add(pp.getProductId());
            }
            vo.setProductIds(productIds);
        }
        return vo;
    }
}
