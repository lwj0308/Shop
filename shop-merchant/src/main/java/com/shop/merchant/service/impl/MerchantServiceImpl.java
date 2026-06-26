package com.shop.merchant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.mapper.MerchantMapper;
import com.shop.merchant.mapper.MerchantQualificationMapper;
import com.shop.merchant.service.MerchantService;
import com.shop.merchant.service.ShopService;
import com.shop.model.merchant.dto.MerchantApplyDTO;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import com.shop.model.merchant.dto.MerchantChangePasswordDTO;
import com.shop.model.merchant.dto.MerchantLoginDTO;
import com.shop.model.merchant.dto.ShopDTO;
import com.shop.model.merchant.entity.Merchant;
import com.shop.model.merchant.entity.MerchantQualification;
import com.shop.model.merchant.enums.MerchantStatusEnum;
import com.shop.model.merchant.vo.MerchantAuditVO;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.merchant.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 商家服务实现类
 * <p>
 * 实现商家入驻申请、审核、信息查询、登录认证等核心业务逻辑。
 * 商家入驻流程：用户提交申请 → 管理员审核 → 审核通过自动创建店铺
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    /** 商家信息Mapper，操作merchant表 */
    private final MerchantMapper merchantMapper;

    /** 商家资质Mapper，操作merchant_qualification表 */
    private final MerchantQualificationMapper qualificationMapper;

    /** 店铺服务，审核通过时自动创建店铺 */
    private final ShopService shopService;

    /** Redis操作工具，用于登录失败次数限制 */
    private final StringRedisTemplate redisTemplate;

    /** 登录失败次数的Redis Key前缀 */
    private static final String LOGIN_FAIL_KEY = "merchant:login:fail:";

    /** 最大登录失败次数，超过这个数就锁定账号 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /** 账号锁定时间（分钟），登录失败太多次后要等这么久才能再试 */
    private static final int LOCK_DURATION_MINUTES = 30;

    /**
     * 商家入驻申请
     * <p>
     * 1. 检查该用户是否已经入驻过商家（一个用户只能入驻一个商家）
     * 2. 检查商家名称是否重复（两个商家不能叫同一个名字）
     * 3. 检查手机号是否已被其他商家使用
     * 4. 创建商家记录，状态设为待审核(0)
     * 5. 创建资质记录，状态也设为待审核(0)
     * 6. 密码用BCrypt加密存储
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantVO apply(Long userId, MerchantApplyDTO applyDTO) {
        // 检查该用户是否已经入驻过商家
        Long existCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId)
        );
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.MERCHANT_ALREADY_EXISTS);
        }

        // 检查商家名称是否重复（两个商家不能叫同一个名字，避免混淆）
        Long nameCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getName, applyDTO.getName())
        );
        if (nameCount > 0) {
            throw new BusinessException(ErrorCode.MERCHANT_NAME_EXISTS);
        }

        // 检查手机号是否已被其他商家使用
        Long phoneCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getContactPhone, applyDTO.getContactPhone())
        );
        if (phoneCount > 0) {
            throw new BusinessException(ErrorCode.MERCHANT_PHONE_EXISTS);
        }

        // 创建商家记录
        Merchant merchant = new Merchant();
        merchant.setName(applyDTO.getName());
        merchant.setContactPhone(applyDTO.getContactPhone());
        merchant.setUserId(userId);
        merchant.setPassword(BCrypt.hashpw(applyDTO.getPassword()));
        merchant.setStatus(MerchantStatusEnum.PENDING.getCode());
        merchantMapper.insert(merchant);

        // 创建资质记录
        MerchantQualification qualification = new MerchantQualification();
        qualification.setMerchantId(merchant.getId());
        qualification.setLicenseNo(applyDTO.getLicenseNo());
        qualification.setLicenseImg(applyDTO.getLicenseImg());
        qualification.setLegalPerson(applyDTO.getLegalPerson());
        qualification.setStatus(MerchantStatusEnum.PENDING.getCode());
        qualificationMapper.insert(qualification);

        log.info("商家入驻申请成功，商家ID：{}，用户ID：{}", merchant.getId(), userId);

        // 转换为VO返回
        return convertToVO(merchant);
    }

    /**
     * 商家登录
     * <p>
     * 1. 检查是否被登录锁定（连续失败5次锁定30分钟）
     * 2. 根据手机号查找商家
     * 3. 校验商家状态（被禁用的不能登录）
     * 4. 校验密码（BCrypt比对）
     * 5. 登录成功清除失败计数，登录失败增加计数
     * 6. 使用Sa-Token生成Token
     * </p>
     */
    @Override
    public Map<String, Object> login(MerchantLoginDTO loginDTO) {
        String failKey = LOGIN_FAIL_KEY + loginDTO.getContactPhone();

        // 检查是否被登录锁定（防止暴力破解密码）
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_FAIL_COUNT) {
            throw new BusinessException(ErrorCode.USER_LOGIN_LOCKED);
        }

        // 根据手机号查找商家
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getContactPhone, loginDTO.getContactPhone())
        );

        // 商家不存在
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }

        // 商家被禁用，不允许登录
        if (MerchantStatusEnum.DISABLED.getCode() == merchant.getStatus()) {
            throw new BusinessException(ErrorCode.MERCHANT_DISABLED);
        }

        // 密码错误（使用BCrypt验证密码）
        if (!BCrypt.checkpw(loginDTO.getPassword(), merchant.getPassword())) {
            // 登录失败，增加失败计数
            incrementLoginFailCount(failKey);
            throw new BusinessException(ErrorCode.MERCHANT_PASSWORD_ERROR);
        }

        // 登录成功，清除失败计数
        redisTemplate.delete(failKey);

        // 使用Sa-Token进行登录，以"merchant"作为登录类型区分用户和商家
        StpUtil.login(merchant.getId(), "merchant");

        // 在Session中存储角色和关联信息，方便后续权限判断
        StpUtil.getSession().set("role", "merchant");
        StpUtil.getSession().set("userId", merchant.getUserId());
        StpUtil.getSession().set("merchantId", merchant.getId());

        // 查询商家的店铺ID并存入Session，Feign拦截器会取出它注入X-Shop-Id传递给下游服务
        // 小白讲解：登录时就把shopId存好，后面调用商品服务时就不用每次都查数据库了
        ShopVO shop = shopService.getShopByMerchantId(merchant.getId());
        if (shop != null) {
            StpUtil.getSession().set("shopId", shop.getId());
        }

        // 返回Token和商家ID
        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("merchantId", merchant.getId());

        log.info("商家登录成功，商家ID：{}", merchant.getId());
        return result;
    }

    /**
     * 商家修改密码
     * <p>
     * 需要验证旧密码才能修改新密码，防止被盗号。
     * 修改密码后不会自动退出登录，商家可以继续使用当前Token。
     * </p>
     */
    @Override
    public void changePassword(Long merchantId, MerchantChangePasswordDTO dto) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }

        // 验证旧密码是否正确
        if (!BCrypt.checkpw(dto.getOldPassword(), merchant.getPassword())) {
            throw new BusinessException(ErrorCode.USER_OLD_PASSWORD_ERROR);
        }

        // 新密码用BCrypt加密后存储
        merchant.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        merchantMapper.updateById(merchant);

        log.info("商家修改密码成功，商家ID：{}", merchantId);
    }

    /**
     * 审核商家
     * <p>
     * 1. 查找商家和资质信息
     * 2. 更新商家状态和资质状态
     * 3. 如果审核通过，自动创建默认店铺
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(MerchantAuditDTO auditDTO) {
        // 查找商家
        Merchant merchant = merchantMapper.selectById(auditDTO.getMerchantId());
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }

        // 只有待审核状态的商家才能审核
        if (MerchantStatusEnum.PENDING.getCode() != merchant.getStatus()) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_ERROR);
        }

        // 校验审核状态值，只能是通过(1)或拒绝(2)
        if (MerchantStatusEnum.APPROVED.getCode() != auditDTO.getStatus()
                && MerchantStatusEnum.REJECTED.getCode() != auditDTO.getStatus()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 更新商家状态
        merchant.setStatus(auditDTO.getStatus());
        merchantMapper.updateById(merchant);

        // 更新资质审核状态
        MerchantQualification qualification = qualificationMapper.selectOne(
                new LambdaQueryWrapper<MerchantQualification>()
                        .eq(MerchantQualification::getMerchantId, merchant.getId())
                        .orderByDesc(MerchantQualification::getCreateTime)
                        .last("LIMIT 1")
        );
        if (qualification != null) {
            qualification.setStatus(auditDTO.getStatus());
            qualification.setAuditNote(auditDTO.getAuditNote());
            qualificationMapper.updateById(qualification);
        }

        // 审核通过时自动创建默认店铺
        if (MerchantStatusEnum.APPROVED.getCode() == auditDTO.getStatus()) {
            ShopDTO shopDTO = new ShopDTO();
            shopDTO.setName(merchant.getName() + "旗舰店");
            shopService.createShop(merchant.getId(), shopDTO);
            log.info("商家审核通过，已自动创建店铺，商家ID：{}", merchant.getId());
        } else {
            log.info("商家审核拒绝，商家ID：{}，原因：{}", merchant.getId(), auditDTO.getAuditNote());
        }
    }

    /**
     * 获取商家信息
     * <p>
     * 根据商家ID查询，联系电话做脱敏处理。
     * </p>
     */
    @Override
    public MerchantVO getMerchantInfo(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return convertToVO(merchant);
    }

    /**
     * 根据用户ID获取关联的商家信息
     * <p>
     * 商家登录后，通过用户ID查找自己关联的商家。
     * </p>
     */
    @Override
    public MerchantVO getMerchantByUserId(Long userId) {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId)
        );
        if (merchant == null) {
            return null;
        }
        return convertToVO(merchant);
    }

    /**
     * 更新商家信息
     * <p>
     * 商家修改基本信息和资质信息，修改后状态重置为待审核。
     * 只有审核通过或已拒绝的商家才能修改信息。
     * 被禁用的商家不能修改信息。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMerchant(Long merchantId, MerchantApplyDTO applyDTO) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }

        // 被禁用的商家不能修改信息
        if (MerchantStatusEnum.DISABLED.getCode() == merchant.getStatus()) {
            throw new BusinessException(ErrorCode.MERCHANT_DISABLED);
        }

        // 只有审核通过或已拒绝的商家才能修改信息
        if (MerchantStatusEnum.APPROVED.getCode() != merchant.getStatus()
                && MerchantStatusEnum.REJECTED.getCode() != merchant.getStatus()) {
            throw new BusinessException(ErrorCode.MERCHANT_PENDING);
        }

        // 如果修改了商家名称，需要检查名称是否重复
        if (!merchant.getName().equals(applyDTO.getName())) {
            Long nameCount = merchantMapper.selectCount(
                    new LambdaQueryWrapper<Merchant>()
                            .eq(Merchant::getName, applyDTO.getName())
                            .ne(Merchant::getId, merchantId)
            );
            if (nameCount > 0) {
                throw new BusinessException(20011, "商家名称已存在，请更换名称");
            }
        }

        // 如果修改了手机号，需要检查手机号是否已被其他商家使用
        if (!merchant.getContactPhone().equals(applyDTO.getContactPhone())) {
            Long phoneCount = merchantMapper.selectCount(
                    new LambdaQueryWrapper<Merchant>()
                            .eq(Merchant::getContactPhone, applyDTO.getContactPhone())
                            .ne(Merchant::getId, merchantId)
            );
            if (phoneCount > 0) {
                throw new BusinessException(20012, "该手机号已被其他商家使用");
            }
        }

        // 更新商家基本信息
        merchant.setName(applyDTO.getName());
        merchant.setContactPhone(applyDTO.getContactPhone());
        merchant.setStatus(MerchantStatusEnum.PENDING.getCode()); // 修改后重新审核
        merchantMapper.updateById(merchant);

        // 更新资质信息
        MerchantQualification qualification = qualificationMapper.selectOne(
                new LambdaQueryWrapper<MerchantQualification>()
                        .eq(MerchantQualification::getMerchantId, merchantId)
                        .orderByDesc(MerchantQualification::getCreateTime)
                        .last("LIMIT 1")
        );
        if (qualification != null) {
            qualification.setLicenseNo(applyDTO.getLicenseNo());
            qualification.setLicenseImg(applyDTO.getLicenseImg());
            qualification.setLegalPerson(applyDTO.getLegalPerson());
            qualification.setStatus(MerchantStatusEnum.PENDING.getCode()); // 修改后重新审核
            qualification.setAuditNote(null);
            qualificationMapper.updateById(qualification);
        }

        log.info("商家信息已更新，等待重新审核，商家ID：{}", merchantId);
    }

    /**
     * 获取商家审核状态
     * <p>
     * 查询商家的审核状态和管理员的审核意见。
     * 使用枚举获取状态描述，避免魔法数字。
     * </p>
     */
    @Override
    public MerchantAuditVO getAuditStatus(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }

        MerchantAuditVO vo = new MerchantAuditVO();
        vo.setMerchantId(merchantId);
        vo.setStatus(merchant.getStatus());
        // 使用枚举获取状态描述，比switch更优雅
        vo.setStatusDesc(MerchantStatusEnum.getDescByCode(merchant.getStatus()));

        // 查询最新的资质审核备注
        MerchantQualification qualification = qualificationMapper.selectOne(
                new LambdaQueryWrapper<MerchantQualification>()
                        .eq(MerchantQualification::getMerchantId, merchantId)
                        .orderByDesc(MerchantQualification::getCreateTime)
                        .last("LIMIT 1")
        );
        if (qualification != null) {
            vo.setAuditNote(qualification.getAuditNote());
        }

        return vo;
    }

    /**
     * 校验商家是否处于可操作状态
     * <p>
     * 被禁用的商家不能进行修改信息、管理店铺等操作。
     * 各个Controller在执行操作前调用此方法统一校验。
     * </p>
     *
     * @param merchantId 商家ID
     */
    @Override
    public void checkMerchantActive(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        if (MerchantStatusEnum.DISABLED.getCode() == merchant.getStatus()) {
            throw new BusinessException(ErrorCode.MERCHANT_DISABLED);
        }
    }

    /**
     * 增加登录失败次数
     * <p>
     * 每次登录失败后，在Redis中增加计数。
     * 第一次失败时设置30分钟过期时间，之后累加计数。
     * </p>
     *
     * @param failKey Redis Key
     */
    private void incrementLoginFailCount(String failKey) {
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = (failCountStr == null) ? 1 : Integer.parseInt(failCountStr) + 1;
        redisTemplate.opsForValue().set(failKey, String.valueOf(failCount), LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        log.warn("商家登录失败，当前失败次数：{}，手机号Key：{}", failCount, failKey);
    }

    /**
     * 将Merchant实体转换为MerchantVO
     * <p>
     * 转换时对联系电话做脱敏处理，中间4位用*号代替。
     * 比如 13812345678 → 138****5678
     * </p>
     *
     * @param merchant 商家实体
     * @return 商家VO（联系电话脱敏）
     */
    private MerchantVO convertToVO(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        BeanUtils.copyProperties(merchant, vo);
        // 联系电话脱敏：保留前3位和后4位，中间用****代替
        vo.setContactPhone(desensitizePhone(merchant.getContactPhone()));
        return vo;
    }

    /**
     * 手机号脱敏处理
     * <p>
     * 把手机号中间4位替换成*号，保护用户隐私。
     * 比如 13812345678 → 138****5678
     * 如果手机号长度不够11位，就不做处理，直接返回。
     * </p>
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    private String desensitizePhone(String phone) {
        if (phone != null && phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return phone;
    }
}
