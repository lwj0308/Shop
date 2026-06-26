package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.entity.UserCoupon;
import com.shop.model.coupon.enums.CouponStatusEnum;
import com.shop.model.coupon.enums.CouponTypeEnum;
import com.shop.model.coupon.enums.UserCouponStatusEnum;
import com.shop.model.coupon.vo.CouponVO;
import com.shop.model.coupon.vo.UserCouponVO;
import com.shop.user.feign.MerchantCouponFeignClient;
import com.shop.user.mapper.UserCouponMapper;
import com.shop.user.service.UserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户优惠券服务实现类
 * <p>
 * 实现用户优惠券的领取、查询、核销、回退等操作。
 * </p>
 * <p>
 * 核心流程说明：
 * 1. 领取：查模板（Feign调shop-merchant）→ 校验 → 写入user_coupon → Feign增加领取数
 * 2. 核销：查用户券 → 校验 → 计算优惠金额 → 标记已使用 → Feign增加使用数
 * 3. 回退：按订单号查券 → 恢复未使用 → Feign减少使用数
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl implements UserCouponService {

    /** 用户优惠券 Mapper */
    private final UserCouponMapper userCouponMapper;

    /** 商家优惠券 Feign 客户端（调用 shop-merchant 内部接口） */
    private final MerchantCouponFeignClient merchantCouponFeignClient;

    /**
     * 用户领取优惠券
     * <p>
     * 流程：
     * 1. 通过 Feign 调用 shop-merchant 获取优惠券模板信息
     * 2. 校验：状态是否进行中、是否在领取时间窗口内、是否已达每人限领数
     * 3. 写入 user_coupon 表（含优惠券冗余信息）
     * 4. 通过 Feign 调用 shop-merchant 增加已领取数量
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveCoupon(Long userId, Long couponId) {
        // 1. 通过 Feign 获取优惠券模板信息
        Coupon coupon = fetchCouponTemplate(couponId);

        // 2. 校验优惠券状态
        validateCouponReceivable(coupon);

        // 3. 校验每人限领数
        Long receivedCount = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId));
        if (receivedCount >= coupon.getPerLimit()) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "已达到每人限领数量" + coupon.getPerLimit() + "张");
        }

        // 4. 写入用户券表（含冗余信息）
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setMerchantId(coupon.getMerchantId());
        userCoupon.setCouponName(coupon.getName());
        userCoupon.setCouponType(coupon.getType());
        userCoupon.setAmount(coupon.getAmount());
        userCoupon.setThreshold(coupon.getThreshold());
        userCoupon.setValidStartTime(coupon.getValidStartTime());
        userCoupon.setValidEndTime(coupon.getValidEndTime());
        userCoupon.setStatus(UserCouponStatusEnum.UNUSED.getCode());
        userCoupon.setGetTime(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);

        // 5. 通过 Feign 增加模板的已领取数量
        var incrResult = merchantCouponFeignClient.incrReceivedCount(couponId);
        if (incrResult == null || !incrResult.isSuccess() || !Boolean.TRUE.equals(incrResult.getData())) {
            // 增加领取数失败（可能已被领完），回滚事务
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "优惠券已被领完");
        }

        log.info("用户领取优惠券成功: userId={}, couponId={}, userCouponId={}", userId, couponId, userCoupon.getId());
    }

    /**
     * 查询我的优惠券列表
     */
    @Override
    public PageResult<UserCouponVO> getMyCoupons(Long userId, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        if (status != null) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        wrapper.orderByDesc(UserCoupon::getGetTime);

        Page<UserCoupon> pageResult = userCouponMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserCouponVO> records = pageResult.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 用 PageResult.from 转换，避免手动构造4个参数（PageResult没有这种构造器）
        return PageResult.from(pageResult, records);
    }

    /**
     * 查询可领取的优惠券列表（领券中心用）
     * <p>通过 Feign 调用 shop-merchant 获取，然后排除用户已领取的券</p>
     */
    @Override
    public List<CouponVO> getReceivableCouponList() {
        // 1. 通过 Feign 获取所有可领取的优惠券
        var result = merchantCouponFeignClient.getReceivableCouponList();
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return List.of();
        }
        List<CouponVO> couponList = result.getData();
        if (couponList.isEmpty()) {
            return couponList;
        }

        // 2. 查询用户已领取的优惠券模板ID集合，用于排除已领的券
        // 注意：这里无法获取 userId（领券中心可能未登录），所以返回所有可领取的券
        // 前端根据用户是否已领来显示"已领取"状态
        return couponList;
    }

    /**
     * 查询用户可用优惠券（下单时用）
     * <p>返回未使用且在有效期内的券，并计算每张券在当前订单金额下的优惠金额</p>
     */
    @Override
    public List<UserCouponVO> getUsableCoupons(Long userId, BigDecimal orderAmount) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        wrapper.eq(UserCoupon::getStatus, UserCouponStatusEnum.UNUSED.getCode());
        wrapper.le(UserCoupon::getValidStartTime, LocalDateTime.now());
        wrapper.ge(UserCoupon::getValidEndTime, LocalDateTime.now());
        wrapper.orderByDesc(UserCoupon::getAmount);

        List<UserCoupon> list = userCouponMapper.selectList(wrapper);
        return list.stream()
                .map(uc -> {
                    UserCouponVO vo = convertToVO(uc);
                    // 计算优惠金额
                    vo.setDiscountAmount(calculateDiscount(uc, orderAmount));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ==================== 内部接口实现 ====================

    /**
     * 核销优惠券
     * <p>
     * 1. 查询用户券，校验状态（未使用）、有效期
     * 2. 校验门槛（满减券需要订单金额 >= threshold）
     * 3. 计算优惠金额
     * 4. 标记为已使用，记录订单号
     * 5. 通过 Feign 增加模板的已使用数量
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal useCoupon(CouponUseDTO dto) {
        // 1. 查询用户券
        UserCoupon userCoupon = userCouponMapper.selectById(dto.getUserCouponId());
        if (userCoupon == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "用户优惠券不存在");
        }
        // 校验归属权
        if (!userCoupon.getUserId().equals(dto.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权使用此优惠券");
        }
        // 校验状态
        if (!userCoupon.getStatus().equals(UserCouponStatusEnum.UNUSED.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "优惠券已使用或已过期");
        }
        // 校验有效期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(userCoupon.getValidStartTime()) || now.isAfter(userCoupon.getValidEndTime())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "优惠券不在有效期内");
        }

        // 2. 校验门槛
        if (userCoupon.getCouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
            if (dto.getOrderAmount().compareTo(userCoupon.getThreshold()) < 0) {
                throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "订单金额未满" + userCoupon.getThreshold() + "元，不满足使用条件");
            }
        }

        // 3. 计算优惠金额
        BigDecimal discountAmount = calculateDiscount(userCoupon, dto.getOrderAmount());

        // 4. 标记为已使用
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, dto.getUserCouponId())
                .eq(UserCoupon::getStatus, UserCouponStatusEnum.UNUSED.getCode())
                .set(UserCoupon::getStatus, UserCouponStatusEnum.USED.getCode())
                .set(UserCoupon::getOrderNo, dto.getOrderNo())
                .set(UserCoupon::getUseTime, now));

        // 5. 通过 Feign 增加模板的已使用数量
        try {
            merchantCouponFeignClient.incrUsedCount(userCoupon.getCouponId());
        } catch (Exception e) {
            log.warn("增加优惠券使用数失败（不影响核销）: couponId={}, error={}", userCoupon.getCouponId(), e.getMessage());
        }

        log.info("核销优惠券成功: userCouponId={}, orderNo={}, discount={}", dto.getUserCouponId(), dto.getOrderNo(), discountAmount);
        return discountAmount;
    }

    /**
     * 回退优惠券（订单取消时调用）
     * <p>按订单号查询已使用的券，恢复为未使用状态</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(String orderNo) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getOrderNo, orderNo);
        wrapper.eq(UserCoupon::getStatus, UserCouponStatusEnum.USED.getCode());
        List<UserCoupon> list = userCouponMapper.selectList(wrapper);

        for (UserCoupon uc : list) {
            // 恢复为未使用
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, uc.getId())
                    .set(UserCoupon::getStatus, UserCouponStatusEnum.UNUSED.getCode())
                    .set(UserCoupon::getOrderNo, null)
                    .set(UserCoupon::getUseTime, null));

            // 通过 Feign 减少模板的已使用数量
            try {
                merchantCouponFeignClient.decrUsedCount(uc.getCouponId());
            } catch (Exception e) {
                log.warn("减少优惠券使用数失败（不影响回退）: couponId={}, error={}", uc.getCouponId(), e.getMessage());
            }
        }
        log.info("回退优惠券成功: orderNo={}, 回退{}张", orderNo, list.size());
    }

    // ==================== 私有方法 ====================

    /**
     * 通过 Feign 获取优惠券模板，校验非空
     */
    private Coupon fetchCouponTemplate(Long couponId) {
        var result = merchantCouponFeignClient.getCouponById(couponId);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "优惠券不存在或已下架");
        }
        return result.getData();
    }

    /**
     * 校验优惠券是否可领取
     */
    private void validateCouponReceivable(Coupon coupon) {
        if (!coupon.getStatus().equals(CouponStatusEnum.ACTIVE.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "优惠券不在进行中状态");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getReceiveStartTime()) || now.isAfter(coupon.getReceiveEndTime())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "不在领取时间范围内");
        }
        // 校验余量（不限量券 totalCount=0 跳过）
        if (coupon.getTotalCount() > 0 && coupon.getReceivedCount() >= coupon.getTotalCount()) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "优惠券已被领完");
        }
    }

    /**
     * 计算优惠金额
     * <p>
     * - 满减券：减 amount 元（需满足 threshold 门槛，否则返回0）
     * - 立减券：减 amount 元
     * - 折扣券：orderAmount * (1 - amount)，如 amount=0.85 则优惠 = orderAmount * 0.15
     * </p>
     *
     * @param userCoupon  用户券
     * @param orderAmount 订单金额
     * @return 优惠金额
     */
    private BigDecimal calculateDiscount(UserCoupon userCoupon, BigDecimal orderAmount) {
        if (userCoupon.getCouponType().equals(CouponTypeEnum.FULL_REDUCTION.getCode())) {
            // 满减：未达门槛返回0，满足则减amount
            if (orderAmount.compareTo(userCoupon.getThreshold()) < 0) {
                return BigDecimal.ZERO;
            }
            return userCoupon.getAmount();
        } else if (userCoupon.getCouponType().equals(CouponTypeEnum.DIRECT_DISCOUNT.getCode())) {
            // 立减：直接减amount
            return userCoupon.getAmount();
        } else if (userCoupon.getCouponType().equals(CouponTypeEnum.DISCOUNT.getCode())) {
            // 折扣：优惠 = orderAmount * (1 - amount)
            BigDecimal discount = BigDecimal.ONE.subtract(userCoupon.getAmount());
            return orderAmount.multiply(discount).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 实体转 VO（填充类型描述和状态描述）
     */
    private UserCouponVO convertToVO(UserCoupon userCoupon) {
        UserCouponVO vo = new UserCouponVO();
        BeanUtils.copyProperties(userCoupon, vo);

        // 填充优惠券类型描述
        CouponTypeEnum typeEnum = CouponTypeEnum.getByCode(userCoupon.getCouponType());
        if (typeEnum != null) {
            vo.setCouponTypeDesc(typeEnum.getDesc());
        }
        // 填充状态描述
        UserCouponStatusEnum statusEnum = UserCouponStatusEnum.getByCode(userCoupon.getStatus());
        if (statusEnum != null) {
            vo.setStatusDesc(statusEnum.getDesc());
        }
        return vo;
    }
}
