package com.shop.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.mapper.CouponMapper;
import com.shop.merchant.service.CouponService;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.dto.CouponQueryDTO;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.enums.CouponStatusEnum;
import com.shop.model.coupon.enums.CouponTypeEnum;
import com.shop.model.coupon.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 优惠券服务实现类
 * <p>
 * 实现优惠券模板的创建、查询、上下架等操作。
 * 优惠券状态流转：待生效 → 进行中（到领取开始时间）→ 已结束（超过领取结束时间）
 * 商家/管理员可手动下架（变为已下架状态）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    /** 优惠券 Mapper */
    private final CouponMapper couponMapper;

    /**
     * 创建优惠券
     * <p>
     * 创建时校验时间窗口（领取时间在使用时间之前），根据当前时间判断初始状态：
     * - 当前时间 < 领取开始时间 → 待生效(0)
     * - 领取开始时间 <= 当前时间 <= 领取结束时间 → 进行中(1)
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCoupon(Long merchantId, CouponCreateDTO dto) {
        // 校验时间窗口
        validateTimeWindow(dto);

        // 构建优惠券实体
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(dto, coupon);
        coupon.setMerchantId(merchantId);
        coupon.setReceivedCount(0);
        coupon.setUsedCount(0);

        // 根据当前时间判断初始状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dto.getReceiveStartTime())) {
            coupon.setStatus(CouponStatusEnum.PENDING.getCode());
        } else if (now.isAfter(dto.getReceiveEndTime())) {
            coupon.setStatus(CouponStatusEnum.ENDED.getCode());
        } else {
            coupon.setStatus(CouponStatusEnum.ACTIVE.getCode());
        }

        couponMapper.insert(coupon);
        log.info("创建优惠券成功: id={}, name={}, merchantId={}", coupon.getId(), coupon.getName(), merchantId);
        return coupon.getId();
    }

    /**
     * 修改优惠券
     * <p>仅待生效状态可修改，避免已领取的券规则被篡改</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCoupon(Long merchantId, Long couponId, CouponCreateDTO dto) {
        Coupon coupon = getCouponAndCheckOwnership(merchantId, couponId);

        // 只有待生效状态才能修改
        if (!coupon.getStatus().equals(CouponStatusEnum.PENDING.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "仅待生效状态的优惠券可修改");
        }

        validateTimeWindow(dto);
        BeanUtils.copyProperties(dto, coupon);
        couponMapper.updateById(coupon);
        log.info("修改优惠券成功: id={}", couponId);
    }

    /**
     * 下架优惠券
     * <p>下架后用户不能再领取，已领取的券仍可使用</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineCoupon(Long merchantId, Long couponId) {
        Coupon coupon = getCouponAndCheckOwnership(merchantId, couponId);

        if (coupon.getStatus().equals(CouponStatusEnum.OFFLINE.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "优惠券已是下架状态");
        }

        coupon.setStatus(CouponStatusEnum.OFFLINE.getCode());
        couponMapper.updateById(coupon);
        log.info("下架优惠券成功: id={}", couponId);
    }

    /**
     * 查询优惠券列表
     *
     * @param merchantId null 表示管理端查询所有，>0 表示查指定商家的券
     */
    @Override
    public PageResult<CouponVO> getCouponList(Long merchantId, CouponQueryDTO query) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        // 管理端传 null 查所有，商家端传具体 merchantId
        if (merchantId != null) {
            wrapper.eq(Coupon::getMerchantId, merchantId);
        }
        // 状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(Coupon::getStatus, query.getStatus());
        }
        // 类型筛选
        if (query.getType() != null) {
            wrapper.eq(Coupon::getType, query.getType());
        }
        wrapper.orderByDesc(Coupon::getCreateTime);

        Page<Coupon> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<Coupon> result = couponMapper.selectPage(page, wrapper);

        List<CouponVO> records = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.from(result, records);
    }

    /**
     * 查询优惠券详情
     */
    @Override
    public CouponVO getCouponDetail(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "优惠券不存在");
        }
        return convertToVO(coupon);
    }

    /**
     * 查询可领取的优惠券列表
     * <p>只返回进行中状态、在领取时间窗口内、还有余量的券</p>
     */
    @Override
    public List<CouponVO> getReceivableCouponList(Long merchantId) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(Coupon::getMerchantId, merchantId);
        }
        wrapper.eq(Coupon::getStatus, CouponStatusEnum.ACTIVE.getCode());
        LocalDateTime now = LocalDateTime.now();
        wrapper.le(Coupon::getReceiveStartTime, now);
        wrapper.ge(Coupon::getReceiveEndTime, now);
        wrapper.orderByDesc(Coupon::getCreateTime);

        List<Coupon> list = couponMapper.selectList(wrapper);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    // ==================== 内部接口实现 ====================

    @Override
    public Coupon getCouponById(Long couponId) {
        return couponMapper.selectById(couponId);
    }

    /**
     * 增加已领取数量
     * <p>使用乐观锁方式更新，避免并发领取超发</p>
     */
    @Override
    public boolean incrReceivedCount(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            return false;
        }
        // 不限量的券直接增加
        if (coupon.getTotalCount() == 0) {
            couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                    .eq(Coupon::getId, couponId)
                    .setSql("received_count = received_count + 1"));
            return true;
        }
        // 限量券需要校验是否还有余量
        if (coupon.getReceivedCount() >= coupon.getTotalCount()) {
            return false;
        }
        // 使用 SQL 条件更新（乐观锁思想：where received_count < total_count）
        int rows = couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .lt(Coupon::getReceivedCount, coupon.getTotalCount())
                .setSql("received_count = received_count + 1"));
        return rows > 0;
    }

    @Override
    public void incrUsedCount(Long couponId) {
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .setSql("used_count = used_count + 1"));
    }

    @Override
    public void decrUsedCount(Long couponId) {
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .setSql("used_count = used_count - 1"));
    }

    // ==================== 私有方法 ====================

    /**
     * 校验时间窗口
     * 领取时间必须在使用时间之前，且结束时间必须大于开始时间
     */
    private void validateTimeWindow(CouponCreateDTO dto) {
        if (dto.getReceiveStartTime().isAfter(dto.getReceiveEndTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "领取开始时间不能晚于结束时间");
        }
        if (dto.getValidStartTime().isAfter(dto.getValidEndTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "有效期开始时间不能晚于结束时间");
        }
        if (dto.getReceiveEndTime().isAfter(dto.getValidStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "领取结束时间不能晚于有效期开始时间");
        }
        // 折扣券校验折扣率范围 (0, 1)
        if (dto.getType().equals(CouponTypeEnum.DISCOUNT.getCode())) {
            if (dto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0
                    || dto.getAmount().compareTo(java.math.BigDecimal.ONE) >= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "折扣率必须在0~1之间（如0.85表示85折）");
            }
        }
    }

    /**
     * 获取优惠券并校验归属权
     *
     * @param merchantId 商家ID（0表示平台券，只有管理员能操作）
     * @param couponId   优惠券ID
     */
    private Coupon getCouponAndCheckOwnership(Long merchantId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "优惠券不存在");
        }
        if (!coupon.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此优惠券");
        }
        return coupon;
    }

    /**
     * 实体转 VO（填充类型描述、状态描述、剩余数量）
     */
    private CouponVO convertToVO(Coupon coupon) {
        CouponVO vo = new CouponVO();
        BeanUtils.copyProperties(coupon, vo);

        // 填充类型描述
        CouponTypeEnum typeEnum = CouponTypeEnum.getByCode(coupon.getType());
        if (typeEnum != null) {
            vo.setTypeDesc(typeEnum.getDesc());
        }
        // 填充状态描述
        CouponStatusEnum statusEnum = CouponStatusEnum.getByCode(coupon.getStatus());
        if (statusEnum != null) {
            vo.setStatusDesc(statusEnum.getDesc());
        }
        // 计算剩余可领取数量
        if (coupon.getTotalCount() == 0) {
            vo.setRemainCount(-1); // -1 表示不限量
        } else {
            vo.setRemainCount(coupon.getTotalCount() - coupon.getReceivedCount());
        }
        return vo;
    }
}
