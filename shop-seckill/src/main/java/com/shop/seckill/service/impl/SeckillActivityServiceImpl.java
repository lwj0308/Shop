package com.shop.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.seckill.mapper.SeckillActivityMapper;
import com.shop.seckill.service.SeckillActivityService;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.dto.SeckillQueryDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.enums.SeckillStatusEnum;
import com.shop.model.seckill.vo.SeckillVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀活动服务实现类
 * <p>
 * 实现秒杀活动的创建、下架、查询等操作。
 * 秒杀活动状态流转：待生效 → 进行中（到开始时间）→ 已结束（超过结束时间）
 * 商家/管理员可手动下架（变为已下架状态）。
 * </p>
 * <p>
 * 库存预热机制：创建活动时把秒杀库存写入 Redis（key=seckill:stock:{活动ID}），
 * 用户抢购时通过 Lua 脚本原子扣减 Redis 库存，避免高并发下超卖。
 * 下架活动时同步清理 Redis 库存缓存，防止脏数据。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillActivityServiceImpl implements SeckillActivityService {

    /** 秒杀活动 Mapper */
    private final SeckillActivityMapper seckillActivityMapper;

    /** Redis 操作模板，用于秒杀库存预热 */
    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 秒杀库存 key 前缀，完整 key = 前缀 + 活动ID */
    private static final String SECKILL_STOCK_KEY_PREFIX = "seckill:stock:";

    /**
     * 创建秒杀活动
     * <p>
     * 校验规则：
     * - 秒杀价 > 0
     * - 库存总数 > 0
     * - 限购数量 > 0
     * - 开始时间 < 结束时间
     * </p>
     * <p>
     * 根据当前时间判断初始状态：
     * - 当前时间 < 开始时间 → 待生效(0)
     * - 开始时间 <= 当前时间 <= 结束时间 → 进行中(1)
     * - 当前时间 > 结束时间 → 已结束(2)
     * </p>
     * <p>
     * 插入数据库后把库存总数预热到 Redis，作为抢购时的扣减来源。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSeckillActivity(Long merchantId, SeckillCreateDTO dto) {
        // 校验秒杀参数（金额、库存、限购、时间窗口）
        validateSeckillParam(dto);

        // 构建秒杀活动实体，把 DTO 中同名字段拷贝过来
        SeckillActivity activity = new SeckillActivity();
        BeanUtils.copyProperties(dto, activity);
        activity.setMerchantId(merchantId);

        // 初始可用库存 = 总库存
        activity.setAvailableCount(dto.getTotalCount());

        // 根据当前时间判断初始状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dto.getStartTime())) {
            activity.setStatus(SeckillStatusEnum.PENDING.getCode());
        } else if (now.isAfter(dto.getEndTime())) {
            activity.setStatus(SeckillStatusEnum.ENDED.getCode());
        } else {
            activity.setStatus(SeckillStatusEnum.ACTIVE.getCode());
        }

        seckillActivityMapper.insert(activity);
        log.info("创建秒杀活动成功: id={}, merchantId={}, seckillPrice={}, totalCount={}",
                activity.getId(), merchantId, activity.getSeckillPrice(), activity.getTotalCount());

        // 库存预热到 Redis：用户抢购时直接从 Redis 扣减，保证高并发下不超卖
        String stockKey = SECKILL_STOCK_KEY_PREFIX + activity.getId();
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(dto.getTotalCount()));
        log.info("秒杀库存预热到 Redis 成功: key={}, count={}", stockKey, dto.getTotalCount());

        return activity.getId();
    }

    /**
     * 下架秒杀活动
     * <p>
     * 归属权校验规则：
     * - merchantId=0 表示平台管理员，可以下架任意活动
     * - merchantId>0 表示商家，只能下架自己的活动
     * </p>
     * <p>
     * 下架后状态置为 3（已下架），并清理 Redis 库存缓存，避免用户继续抢购已下架的活动。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineSeckillActivity(Long merchantId, Long seckillId) {
        SeckillActivity activity = seckillActivityMapper.selectById(seckillId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "秒杀活动不存在");
        }

        // merchantId=0 是平台管理员，可以下架任意活动；否则校验归属权
        if (merchantId != null && merchantId > 0) {
            if (!activity.getMerchantId().equals(merchantId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此秒杀活动");
            }
        }

        // 已经下架的活动不需要重复下架
        if (activity.getStatus().equals(SeckillStatusEnum.OFFLINE.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "秒杀活动已是下架状态");
        }

        activity.setStatus(SeckillStatusEnum.OFFLINE.getCode());
        seckillActivityMapper.updateById(activity);
        log.info("下架秒杀活动成功: id={}, operator={}", seckillId, merchantId);

        // 清理 Redis 库存缓存，避免脏数据被继续使用
        String stockKey = SECKILL_STOCK_KEY_PREFIX + seckillId;
        stringRedisTemplate.delete(stockKey);
        log.info("清理秒杀库存 Redis 缓存: key={}", stockKey);
    }

    /**
     * 查询秒杀活动列表
     * <p>
     * merchantId=null 表示管理端查询所有商家和平台活动，merchantId>0 表示商家端只查自己的活动。
     * 支持按 status 筛选，按创建时间倒序排列。
     * </p>
     */
    @Override
    public PageResult<SeckillVO> getSeckillList(Long merchantId, SeckillQueryDTO query) {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        // 管理端传 null 查所有，商家端传具体 merchantId
        if (merchantId != null) {
            wrapper.eq(SeckillActivity::getMerchantId, merchantId);
        }
        // 状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(SeckillActivity::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(SeckillActivity::getCreateTime);

        Page<SeckillActivity> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<SeckillActivity> result = seckillActivityMapper.selectPage(page, wrapper);

        // 实体转 VO
        List<SeckillVO> records = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 使用 PageResult.from 从 MyBatis-Plus 的 Page 转换，自动填充分页信息
        return PageResult.from(result, records);
    }

    /**
     * 查询秒杀活动详情
     */
    @Override
    public SeckillVO getSeckillDetail(Long seckillId) {
        SeckillActivity activity = seckillActivityMapper.selectById(seckillId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "秒杀活动不存在");
        }
        return convertToVO(activity);
    }

    // ==================== 内部接口实现 ====================

    /**
     * 根据ID查询秒杀活动实体（内部接口，供 shop-order 通过 Feign 调用）
     * <p>
     * 直接返回实体，方便 shop-order 拿到秒杀价、限购数量、时间窗口等原始字段做下单校验。
     * </p>
     */
    @Override
    public SeckillActivity getSeckillById(Long seckillId) {
        SeckillActivity activity = seckillActivityMapper.selectById(seckillId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "秒杀活动不存在");
        }
        return activity;
    }

    // ==================== 私有方法 ====================

    /**
     * 校验秒杀活动参数
     * <p>校验秒杀价、库存总数、限购数量必须大于0，开始时间必须早于结束时间</p>
     */
    private void validateSeckillParam(SeckillCreateDTO dto) {
        // 秒杀价校验：必须大于0
        if (dto.getSeckillPrice() == null || dto.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "秒杀价格必须大于0");
        }
        // 库存总数校验：必须大于0
        if (dto.getTotalCount() == null || dto.getTotalCount() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "秒杀库存总数必须大于0");
        }
        // 限购数量校验：必须大于0
        if (dto.getLimitCount() == null || dto.getLimitCount() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "每人限购数量必须大于0");
        }
        // 时间窗口校验：开始时间必须早于结束时间
        if (dto.getStartTime() == null || dto.getEndTime() == null
                || dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "活动开始时间必须早于结束时间");
        }
    }

    /**
     * 实体转 VO
     * <p>填充状态描述（statusDesc）和秒杀进度（progress，已售百分比）</p>
     * <p>
     * 进度计算公式：progress = (totalCount - availableCount) * 100 / totalCount
     * 小白讲解：已售数量 = 总库存 - 剩余库存；进度 = 已售数量 / 总库存 × 100
     * </p>
     */
    private SeckillVO convertToVO(SeckillActivity activity) {
        SeckillVO vo = new SeckillVO();
        BeanUtils.copyProperties(activity, vo);

        // 填充状态描述（如"进行中"、"已结束"）
        SeckillStatusEnum statusEnum = SeckillStatusEnum.getByCode(activity.getStatus());
        if (statusEnum != null) {
            vo.setStatusDesc(statusEnum.getDesc());
        }

        // 计算秒杀进度百分比（已售/总数 * 100）
        Integer totalCount = activity.getTotalCount();
        Integer availableCount = activity.getAvailableCount();
        if (totalCount != null && totalCount > 0 && availableCount != null) {
            int soldCount = totalCount - availableCount;
            vo.setProgress(soldCount * 100 / totalCount);
        }
        return vo;
    }
}
