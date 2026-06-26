package com.shop.merchant.service;

import com.shop.model.merchant.dto.MerchantApplyDTO;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import com.shop.model.merchant.dto.MerchantChangePasswordDTO;
import com.shop.model.merchant.dto.MerchantLoginDTO;
import com.shop.model.merchant.vo.MerchantAuditVO;
import com.shop.model.merchant.vo.MerchantVO;

import java.util.Map;

/**
 * 商家服务接口
 * <p>
 * 定义商家相关的业务方法，包括入驻申请、审核、信息查询、登录认证等。
 * 商家入驻流程：用户提交申请 → 管理员审核 → 审核通过自动创建店铺
 * </p>
 */
public interface MerchantService {

    /**
     * 商家入驻申请
     * <p>
     * 用户填写商家信息和资质信息提交申请，状态设为待审核(0)。
     * 同时创建商家记录和资质记录，一个用户只能入驻一个商家。
     * </p>
     *
     * @param userId  用户ID，从登录信息中获取
     * @param applyDTO 入驻申请参数，包含商家名称、联系电话、营业执照等
     * @return 商家信息
     */
    MerchantVO apply(Long userId, MerchantApplyDTO applyDTO);

    /**
     * 商家登录
     * <p>
     * 商家使用手机号和密码登录，登录成功后返回Token。
     * 有登录失败次数限制：连续5次失败后锁定30分钟，防止暴力破解密码。
     * </p>
     *
     * @param loginDTO 登录参数，包含手机号和密码
     * @return 登录结果，包含Token和商家ID
     */
    Map<String, Object> login(MerchantLoginDTO loginDTO);

    /**
     * 商家修改密码
     * <p>
     * 商家修改登录密码，需要验证旧密码才能修改，防止被盗号。
     * </p>
     *
     * @param merchantId 商家ID
     * @param dto        修改密码参数，包含旧密码和新密码
     */
    void changePassword(Long merchantId, MerchantChangePasswordDTO dto);

    /**
     * 审核商家
     * <p>
     * 管理员审核商家的入驻申请，可以通过(1)或拒绝(2)。
     * 审核通过时会自动创建一个默认店铺，商家就可以开始经营了。
     * </p>
     *
     * @param auditDTO 审核参数，包含商家ID、审核状态、审核备注
     */
    void audit(MerchantAuditDTO auditDTO);

    /**
     * 获取商家信息
     * <p>
     * 根据商家ID查询商家详情，联系电话做脱敏处理。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 商家信息（联系电话脱敏）
     */
    MerchantVO getMerchantInfo(Long merchantId);

    /**
     * 根据用户ID获取关联的商家信息
     * <p>
     * 一个用户只能入驻一个商家，通过用户ID查找对应的商家。
     * 用于商家登录后获取自己的商家信息。
     * </p>
     *
     * @param userId 用户ID
     * @return 商家信息（联系电话脱敏），如果该用户没有入驻商家则返回null
     */
    MerchantVO getMerchantByUserId(Long userId);

    /**
     * 更新商家信息
     * <p>
     * 商家修改自己的基本信息，只有审核通过或已拒绝的商家才能修改。
     * 修改后状态会重置为待审核，需要重新审核。
     * </p>
     *
     * @param merchantId 商家ID
     * @param applyDTO   更新参数，和入驻申请参数一样
     */
    void updateMerchant(Long merchantId, MerchantApplyDTO applyDTO);

    /**
     * 获取商家审核状态
     * <p>
     * 商家查询自己的审核进展，看看是通过了还是被拒绝了。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 审核状态信息
     */
    MerchantAuditVO getAuditStatus(Long merchantId);

    /**
     * 校验商家是否处于可操作状态
     * <p>
     * 被禁用的商家不能进行任何操作（修改信息、管理店铺等），
     * 这个方法用来统一校验商家状态，避免每个地方都写一遍判断逻辑。
     * </p>
     *
     * @param merchantId 商家ID
     */
    void checkMerchantActive(Long merchantId);
}
