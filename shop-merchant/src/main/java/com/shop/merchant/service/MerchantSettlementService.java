package com.shop.merchant.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.merchant.dto.MerchantSettlementDTO;
import com.shop.model.merchant.dto.WithdrawApplyDTO;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.vo.MerchantSettlementVO;
import com.shop.model.merchant.vo.SettlementRecordVO;
import com.shop.model.merchant.vo.WithdrawOrderVO;

/**
 * 商家结算服务接口
 * <p>
 * 定义结算相关的业务方法，包括：
 * 1. 结算账户管理（添加、更新、查询银行卡信息）
 * 2. 结算流水查询（查看每笔订单的结算记录）
 * 3. 提现申请（商家发起提现，金额从余额冻结）
 * 4. 提现记录查询（查看提现申请历史）
 * 5. 订单结算（订单完成时生成结算记录并增加商家余额）
 * 6. 提现审核（管理员审核提现申请）
 * </p>
 */
public interface MerchantSettlementService {

    // ==================== 结算账户管理 ====================

    /**
     * 添加结算账户
     * <p>
     * 商家首次配置银行账户信息，一个商家只能有一个结算账户。
     * 如果已经配置过，需要使用更新接口而不是重复添加。
     * 只有审核通过的商家才能配置结算账户。
     * </p>
     *
     * @param merchantId 商家ID
     * @param dto        结算账户参数，包含银行名称、银行账号、账户名
     */
    void addSettlement(Long merchantId, MerchantSettlementDTO dto);

    /**
     * 更新结算账户
     * <p>
     * 商家修改银行账户信息，比如换了银行卡。
     * 如果还没有结算账户，会自动创建一个。
     * </p>
     *
     * @param merchantId 商家ID
     * @param dto        结算账户参数
     */
    void updateSettlement(Long merchantId, MerchantSettlementDTO dto);

    /**
     * 获取结算账户
     * <p>
     * 查询商家的银行账户信息和余额，银行账号做脱敏处理。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 结算账户信息（含余额，银行账号脱敏），如果还没配置则返回null
     */
    MerchantSettlementVO getSettlement(Long merchantId);

    // ==================== 结算流水查询 ====================

    /**
     * 查询结算流水列表
     * <p>商家查看自己店铺的结算记录，每条记录对应一笔订单的结算信息</p>
     *
     * @param merchantId  商家ID
     * @param status      结算状态：0-待结算 1-已结算 2-已退款，null查全部
     * @param pageRequest 分页参数
     * @return 分页结算流水列表
     */
    PageResult<SettlementRecordVO> getSettlementRecords(Long merchantId, Integer status, PageRequest pageRequest);

    // ==================== 提现申请 ====================

    /**
     * 申请提现
     * <p>
     * 商家发起提现申请，金额从可用余额转入冻结金额。
     * 需要校验：1.已配置结算账户 2.余额充足 3.提现金额合法
     * </p>
     *
     * @param merchantId 商家ID
     * @param dto        提现参数（含提现金额）
     */
    void applyWithdraw(Long merchantId, WithdrawApplyDTO dto);

    /**
     * 查询提现申请列表
     * <p>商家查看自己的提现申请历史</p>
     *
     * @param merchantId  商家ID
     * @param status      提现状态：0-待审核 1-已通过 2-已拒绝 3-已打款，null查全部
     * @param pageRequest 分页参数
     * @return 分页提现申请列表
     */
    PageResult<WithdrawOrderVO> getWithdrawList(Long merchantId, Integer status, PageRequest pageRequest);

    // ==================== 订单结算（内部调用） ====================

    /**
     * 订单结算：生成结算记录并增加商家余额
     * <p>
     * 用户确认收货后，订单服务通过Feign调用此方法。
     * 生成一条结算流水（status=1已结算），并把商家应得金额加到balance。
     * 使用settlement_record的uk_order_no唯一索引防止重复结算。
     * </p>
     * <p>
     * 佣金计算：commission_amount = order_amount * commission_rate（默认5%）
     * 商家应得：settlement_amount = order_amount - commission_amount
     * </p>
     *
     * @param merchantId  商家ID
     * @param orderNo     订单号
     * @param orderAmount 订单金额（元）
     */
    void settleOrder(Long merchantId, String orderNo, java.math.BigDecimal orderAmount);

    // ==================== 提现审核（管理端） ====================

    /**
     * 管理端查询所有提现申请
     * <p>管理员查看全平台的提现申请，支持按状态筛选</p>
     *
     * @param status      提现状态：0-待审核 1-已通过 2-已拒绝 3-已打款，null查全部
     * @param pageRequest 分页参数
     * @return 分页提现申请列表（含商家名称）
     */
    PageResult<WithdrawOrderVO> adminGetWithdrawList(Integer status, PageRequest pageRequest);

    /**
     * 审核提现申请
     * <p>
     * 管理员审核商家的提现申请：
     * - 审核通过(status=1)：frozen_amount扣减（钱已打款），状态改为已通过
     * - 审核拒绝(status=2)：frozen_amount转回balance（解冻），状态改为已拒绝
     * </p>
     *
     * @param dto 审核参数（含提现ID、审核结果、备注）
     */
    void auditWithdraw(WithdrawAuditDTO dto);
}
