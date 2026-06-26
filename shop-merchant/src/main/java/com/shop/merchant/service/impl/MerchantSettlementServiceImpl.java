package com.shop.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.feign.NotificationFeignClient;
import com.shop.merchant.mapper.MerchantMapper;
import com.shop.merchant.mapper.MerchantSettlementMapper;
import com.shop.merchant.mapper.SettlementRecordMapper;
import com.shop.merchant.mapper.WithdrawOrderMapper;
import com.shop.merchant.service.MerchantSettlementService;
import com.shop.model.merchant.dto.MerchantSettlementDTO;
import com.shop.model.merchant.dto.WithdrawApplyDTO;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.entity.Merchant;
import com.shop.model.merchant.entity.MerchantSettlement;
import com.shop.model.merchant.entity.SettlementRecord;
import com.shop.model.merchant.entity.WithdrawOrder;
import com.shop.model.merchant.vo.MerchantSettlementVO;
import com.shop.model.merchant.vo.SettlementRecordVO;
import com.shop.model.merchant.vo.WithdrawOrderVO;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.enums.NotificationTypeEnum;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商家结算服务实现类
 * <p>
 * 实现结算账户管理、结算流水查询、提现申请等功能。
 * </p>
 * <p>
 * 提现流程说明：
 * 1. 商家发起提现 → 金额从 balance 转入 frozen_amount（冻结）
 * 2. 管理员审核通过 → frozen_amount 扣除（钱已打款）
 * 3. 管理员审核拒绝 → frozen_amount 转回 balance（解冻）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantSettlementServiceImpl implements MerchantSettlementService {

    /** 平台默认抽成比例（5%），后续可改为从配置读取 */
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.05");

    /** 结算账户Mapper，操作merchant_settlement表 */
    private final MerchantSettlementMapper settlementMapper;

    /** 结算流水Mapper，操作settlement_record表 */
    private final SettlementRecordMapper settlementRecordMapper;

    /** 提现申请Mapper，操作withdraw_order表 */
    private final WithdrawOrderMapper withdrawOrderMapper;

    /** 商家信息Mapper，用于管理端查提现列表时关联商家名称 */
    private final MerchantMapper merchantMapper;

    /** 通知服务Feign客户端，用于提现审核结果通知商家 */
    private final NotificationFeignClient notificationFeignClient;

    // ==================== 结算账户管理 ====================

    /**
     * 添加结算账户
     */
    @Override
    public void addSettlement(Long merchantId, MerchantSettlementDTO dto) {
        // 检查是否已经配置过结算账户
        Long existCount = settlementMapper.selectCount(
                new LambdaQueryWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getMerchantId, merchantId)
        );
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.MERCHANT_SETTLEMENT_EXISTS);
        }

        MerchantSettlement settlement = new MerchantSettlement();
        settlement.setMerchantId(merchantId);
        settlement.setBankName(dto.getBankName());
        settlement.setBankAccount(dto.getBankAccount());
        settlement.setAccountName(dto.getAccountName());
        // 新账户余额为0
        settlement.setBalance(BigDecimal.ZERO);
        settlement.setFrozenAmount(BigDecimal.ZERO);
        settlementMapper.insert(settlement);

        log.info("结算账户添加成功，商家ID：{}", merchantId);
    }

    /**
     * 更新结算账户
     */
    @Override
    public void updateSettlement(Long merchantId, MerchantSettlementDTO dto) {
        MerchantSettlement settlement = settlementMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getMerchantId, merchantId)
        );

        if (settlement == null) {
            // 还没有结算账户，自动创建
            settlement = new MerchantSettlement();
            settlement.setMerchantId(merchantId);
            settlement.setBankName(dto.getBankName());
            settlement.setBankAccount(dto.getBankAccount());
            settlement.setAccountName(dto.getAccountName());
            settlement.setBalance(BigDecimal.ZERO);
            settlement.setFrozenAmount(BigDecimal.ZERO);
            settlementMapper.insert(settlement);
            log.info("结算账户不存在，已自动创建，商家ID：{}", merchantId);
        } else {
            // 更新已有账户（只更新银行卡信息，不改动余额）
            settlement.setBankName(dto.getBankName());
            settlement.setBankAccount(dto.getBankAccount());
            settlement.setAccountName(dto.getAccountName());
            settlementMapper.updateById(settlement);
            log.info("结算账户已更新，商家ID：{}", merchantId);
        }
    }

    /**
     * 获取结算账户（含余额信息）
     */
    @Override
    public MerchantSettlementVO getSettlement(Long merchantId) {
        MerchantSettlement settlement = settlementMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getMerchantId, merchantId)
        );
        if (settlement == null) {
            return null;
        }
        return convertToVO(settlement);
    }

    // ==================== 结算流水查询 ====================

    /**
     * 查询结算流水列表
     * <p>按商家ID查询结算记录，支持按状态筛选，按创建时间倒序</p>
     */
    @Override
    public PageResult<SettlementRecordVO> getSettlementRecords(Long merchantId, Integer status, PageRequest pageRequest) {
        Page<SettlementRecord> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementRecord::getMerchantId, merchantId);
        if (status != null) {
            wrapper.eq(SettlementRecord::getStatus, status);
        }
        wrapper.orderByDesc(SettlementRecord::getCreateTime);

        Page<SettlementRecord> result = settlementRecordMapper.selectPage(page, wrapper);

        // 转换为VO
        List<SettlementRecordVO> voList = result.getRecords().stream()
                .map(this::convertSettlementRecordToVO)
                .collect(Collectors.toList());

        PageResult<SettlementRecordVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    // ==================== 提现申请 ====================

    /**
     * 申请提现
     * <p>
     * 1. 校验结算账户存在
     * 2. 校验余额充足（balance >= 提现金额）
     * 3. 金额从 balance 转入 frozen_amount（冻结）
     * 4. 创建提现申请记录
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyWithdraw(Long merchantId, WithdrawApplyDTO dto) {
        // 幂等校验：商家有待审核的提现申请时，禁止再次申请
        // 小白讲解：防止商家快速连点"申请提现"按钮，导致余额被多次冻结
        Long pendingCount = withdrawOrderMapper.selectCount(
                new LambdaQueryWrapper<WithdrawOrder>()
                        .eq(WithdrawOrder::getMerchantId, merchantId)
                        .eq(WithdrawOrder::getStatus, 0) // 0=待审核
        );
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(),
                    "您有待审核的提现申请，请等待审核完成后再申请");
        }

        // 查询结算账户
        MerchantSettlement settlement = settlementMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getMerchantId, merchantId)
        );
        if (settlement == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "请先配置结算账户");
        }

        // 校验余额充足
        BigDecimal balance = settlement.getBalance() != null ? settlement.getBalance() : BigDecimal.ZERO;
        if (balance.compareTo(dto.getAmount()) < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "可用余额不足");
        }

        // 金额从 balance 转入 frozen_amount（使用乐观锁方式更新）
        BigDecimal newBalance = balance.subtract(dto.getAmount());
        BigDecimal frozenAmount = settlement.getFrozenAmount() != null ? settlement.getFrozenAmount() : BigDecimal.ZERO;
        BigDecimal newFrozen = frozenAmount.add(dto.getAmount());

        int updated = settlementMapper.update(null,
                new LambdaUpdateWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getId, settlement.getId())
                        .eq(MerchantSettlement::getBalance, balance)
                        .set(MerchantSettlement::getBalance, newBalance)
                        .set(MerchantSettlement::getFrozenAmount, newFrozen)
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "提现失败，余额可能已变更，请刷新重试");
        }

        // 创建提现申请记录（快照银行卡信息）
        WithdrawOrder withdrawOrder = new WithdrawOrder();
        withdrawOrder.setMerchantId(merchantId);
        withdrawOrder.setAmount(dto.getAmount());
        withdrawOrder.setStatus(0); // 待审核
        withdrawOrder.setBankName(settlement.getBankName());
        withdrawOrder.setBankAccount(settlement.getBankAccount());
        withdrawOrder.setAccountName(settlement.getAccountName());
        withdrawOrderMapper.insert(withdrawOrder);

        log.info("提现申请成功: merchantId={}, amount={}, withdrawId={}", merchantId, dto.getAmount(), withdrawOrder.getId());
    }

    /**
     * 查询提现申请列表
     * <p>按商家ID查询提现记录，支持按状态筛选，按创建时间倒序</p>
     */
    @Override
    public PageResult<WithdrawOrderVO> getWithdrawList(Long merchantId, Integer status, PageRequest pageRequest) {
        Page<WithdrawOrder> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        LambdaQueryWrapper<WithdrawOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WithdrawOrder::getMerchantId, merchantId);
        if (status != null) {
            wrapper.eq(WithdrawOrder::getStatus, status);
        }
        wrapper.orderByDesc(WithdrawOrder::getCreateTime);

        Page<WithdrawOrder> result = withdrawOrderMapper.selectPage(page, wrapper);

        // 转换为VO
        List<WithdrawOrderVO> voList = result.getRecords().stream()
                .map(this::convertWithdrawOrderToVO)
                .collect(Collectors.toList());

        PageResult<WithdrawOrderVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    // ==================== 订单结算（内部调用） ====================

    /**
     * 订单结算：生成结算记录并增加商家余额
     * <p>
     * 用户确认收货后由订单服务通过Feign调用。
     * 幂等设计：先查orderNo是否已结算过，已结算则直接返回（防止重复结算）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOrder(Long merchantId, String orderNo, BigDecimal orderAmount) {
        // 幂等校验：同一订单号只结算一次
        Long existCount = settlementRecordMapper.selectCount(
                new LambdaQueryWrapper<SettlementRecord>()
                        .eq(SettlementRecord::getOrderNo, orderNo)
        );
        if (existCount > 0) {
            log.warn("订单已结算过，跳过重复结算: orderNo={}", orderNo);
            return;
        }

        // 计算佣金和商家应得金额
        // commission_amount = order_amount * 0.05
        // settlement_amount = order_amount - commission_amount
        BigDecimal commissionAmount = orderAmount.multiply(DEFAULT_COMMISSION_RATE)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal settlementAmount = orderAmount.subtract(commissionAmount)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // 生成结算流水记录（status=1已结算）
        SettlementRecord record = new SettlementRecord();
        record.setMerchantId(merchantId);
        record.setOrderNo(orderNo);
        record.setOrderAmount(orderAmount);
        record.setCommissionRate(DEFAULT_COMMISSION_RATE);
        record.setCommissionAmount(commissionAmount);
        record.setSettlementAmount(settlementAmount);
        record.setStatus(1); // 已结算
        record.setSettleTime(LocalDateTime.now());
        settlementRecordMapper.insert(record);

        // 增加商家可用余额（乐观锁方式，防止并发问题）
        // 先查当前余额，再用 eq(balance, oldBalance) 条件更新
        MerchantSettlement settlement = settlementMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getMerchantId, merchantId)
        );
        if (settlement == null) {
            // 商家还没配置结算账户，先自动创建一个（余额从0开始加）
            log.warn("商家未配置结算账户，自动创建: merchantId={}", merchantId);
            settlement = new MerchantSettlement();
            settlement.setMerchantId(merchantId);
            settlement.setBankName("未配置");
            settlement.setBankAccount("未配置");
            settlement.setAccountName("未配置");
            settlement.setBalance(settlementAmount);
            settlement.setFrozenAmount(BigDecimal.ZERO);
            settlementMapper.insert(settlement);
        } else {
            // 乐观锁更新余额
            BigDecimal oldBalance = settlement.getBalance() != null ? settlement.getBalance() : BigDecimal.ZERO;
            BigDecimal newBalance = oldBalance.add(settlementAmount);
            int updated = settlementMapper.update(null,
                    new LambdaUpdateWrapper<MerchantSettlement>()
                            .eq(MerchantSettlement::getId, settlement.getId())
                            .eq(MerchantSettlement::getBalance, oldBalance)
                            .set(MerchantSettlement::getBalance, newBalance)
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "结算失败，余额可能已变更");
            }
        }

        log.info("订单结算成功: orderNo={}, merchantId={}, settlementAmount={}", orderNo, merchantId, settlementAmount);
    }

    // ==================== 提现审核（管理端） ====================

    /**
     * 管理端查询所有提现申请
     * <p>查全平台提现申请，关联查出商家名称用于展示</p>
     */
    @Override
    public PageResult<WithdrawOrderVO> adminGetWithdrawList(Integer status, PageRequest pageRequest) {
        Page<WithdrawOrder> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        LambdaQueryWrapper<WithdrawOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(WithdrawOrder::getStatus, status);
        }
        wrapper.orderByDesc(WithdrawOrder::getCreateTime);

        Page<WithdrawOrder> result = withdrawOrderMapper.selectPage(page, wrapper);

        // 批量查询商家名称（避免N+1查询）
        List<WithdrawOrder> records = result.getRecords();
        Map<Long, String> merchantNameMap = batchGetMerchantNames(records);

        // 转换为VO
        List<WithdrawOrderVO> voList = records.stream()
                .map(order -> {
                    WithdrawOrderVO vo = convertWithdrawOrderToVO(order);
                    vo.setMerchantName(merchantNameMap.getOrDefault(order.getMerchantId(), "未知商家"));
                    return vo;
                })
                .collect(Collectors.toList());

        PageResult<WithdrawOrderVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    /**
     * 审核提现申请
     * <p>
     * 审核通过：frozen_amount扣减，状态改为已通过
     * 审核拒绝：frozen_amount转回balance（解冻），状态改为已拒绝
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditWithdraw(WithdrawAuditDTO dto) {
        // 校验审核结果合法性
        if (dto.getStatus() != 1 && dto.getStatus() != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "审核结果只能是通过(1)或拒绝(2)");
        }

        // 查询提现申请
        WithdrawOrder withdrawOrder = withdrawOrderMapper.selectById(dto.getId());
        if (withdrawOrder == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "提现申请不存在");
        }

        // 校验状态：只有待审核(0)的才能审核
        if (withdrawOrder.getStatus() != 0) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "该提现申请已审核，请勿重复操作");
        }

        // 查询结算账户（用于操作余额）
        MerchantSettlement settlement = settlementMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlement>()
                        .eq(MerchantSettlement::getMerchantId, withdrawOrder.getMerchantId())
        );
        if (settlement == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "商家结算账户不存在");
        }

        BigDecimal amount = withdrawOrder.getAmount();
        BigDecimal oldFrozen = settlement.getFrozenAmount() != null ? settlement.getFrozenAmount() : BigDecimal.ZERO;

        if (dto.getStatus() == 1) {
            // 审核通过：frozen_amount 扣减（钱已打款给商家）
            BigDecimal newFrozen = oldFrozen.subtract(amount);
            int updated = settlementMapper.update(null,
                    new LambdaUpdateWrapper<MerchantSettlement>()
                            .eq(MerchantSettlement::getId, settlement.getId())
                            .eq(MerchantSettlement::getFrozenAmount, oldFrozen)
                            .set(MerchantSettlement::getFrozenAmount, newFrozen)
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "审核失败，冻结金额可能已变更");
            }
            log.info("提现审核通过: withdrawId={}, merchantId={}, amount={}", dto.getId(), withdrawOrder.getMerchantId(), amount);
        } else {
            // 审核拒绝：frozen_amount 转回 balance（解冻）
            BigDecimal oldBalance = settlement.getBalance() != null ? settlement.getBalance() : BigDecimal.ZERO;
            BigDecimal newFrozen = oldFrozen.subtract(amount);
            BigDecimal newBalance = oldBalance.add(amount);
            int updated = settlementMapper.update(null,
                    new LambdaUpdateWrapper<MerchantSettlement>()
                            .eq(MerchantSettlement::getId, settlement.getId())
                            .eq(MerchantSettlement::getFrozenAmount, oldFrozen)
                            .eq(MerchantSettlement::getBalance, oldBalance)
                            .set(MerchantSettlement::getFrozenAmount, newFrozen)
                            .set(MerchantSettlement::getBalance, newBalance)
            );
            if (updated == 0) {
                throw new BusinessException(ErrorCode.OPERATION_FAIL.getCode(), "审核失败，金额可能已变更");
            }
            log.info("提现审核拒绝: withdrawId={}, merchantId={}, amount={}", dto.getId(), withdrawOrder.getMerchantId(), amount);
        }

        // 更新提现申请状态
        withdrawOrder.setStatus(dto.getStatus());
        withdrawOrder.setAuditRemark(dto.getAuditRemark());
        withdrawOrder.setAuditTime(LocalDateTime.now());
        withdrawOrderMapper.updateById(withdrawOrder);

        // 提现审核结果通知商家（异步容忍，失败不影响审核主流程）
        sendWithdrawAuditNotification(withdrawOrder, dto.getStatus());
    }

    /**
     * 发送提现审核结果通知给商家
     * <p>
     * 提现审核完成（通过或拒绝）后调用通知服务，给商家发一条站内通知。
     * 通知发送失败不影响审核主流程（审核已经完成），通过try-catch吞掉异常。
     * </p>
     *
     * @param withdrawOrder 提现申请单
     * @param status        审核结果：1通过 2拒绝
     */
    private void sendWithdrawAuditNotification(WithdrawOrder withdrawOrder, Integer status) {
        try {
            NotificationSendDTO notification = new NotificationSendDTO();
            notification.setReceiverType(ReceiverTypeEnum.MERCHANT.getCode());
            notification.setReceiverId(withdrawOrder.getMerchantId());
            notification.setType(NotificationTypeEnum.WITHDRAW.getCode());
            notification.setBizType("withdraw");
            notification.setBizId(String.valueOf(withdrawOrder.getId()));

            if (status == 1) {
                // 审核通过
                notification.setTitle("提现申请已通过");
                notification.setContent("您的提现申请（金额：" + withdrawOrder.getAmount()
                        + "元）已审核通过，款项将打入您的银行账户。");
            } else {
                // 审核拒绝
                String remark = withdrawOrder.getAuditRemark() != null ? withdrawOrder.getAuditRemark() : "无";
                notification.setTitle("提现申请未通过");
                notification.setContent("您的提现申请（金额：" + withdrawOrder.getAmount()
                        + "元）未通过审核，原因：" + remark + "。金额已退回可用余额。");
            }

            notificationFeignClient.sendNotification(notification);
            log.info("提现审核通知发送成功: withdrawId={}, merchantId={}, status={}",
                    withdrawOrder.getId(), withdrawOrder.getMerchantId(), status);
        } catch (Exception e) {
            // 通知发送失败不影响审核主流程，记录日志即可
            log.error("提现审核通知发送异常: withdrawId={}, merchantId={}",
                    withdrawOrder.getId(), withdrawOrder.getMerchantId(), e);
        }
    }

    // ==================== 私有转换方法 ====================

    /**
     * MerchantSettlement 实体转 VO（银行账号脱敏）
     */
    private MerchantSettlementVO convertToVO(MerchantSettlement settlement) {
        MerchantSettlementVO vo = new MerchantSettlementVO();
        BeanUtils.copyProperties(settlement, vo);
        // 银行账号脱敏：只显示后4位
        vo.setBankAccount(desensitizeBankAccount(settlement.getBankAccount()));
        return vo;
    }

    /**
     * SettlementRecord 实体转 VO
     */
    private SettlementRecordVO convertSettlementRecordToVO(SettlementRecord record) {
        SettlementRecordVO vo = new SettlementRecordVO();
        BeanUtils.copyProperties(record, vo);
        // 设置状态描述
        vo.setStatusDesc(getSettlementStatusDesc(record.getStatus()));
        return vo;
    }

    /**
     * WithdrawOrder 实体转 VO（银行账号脱敏）
     */
    private WithdrawOrderVO convertWithdrawOrderToVO(WithdrawOrder order) {
        WithdrawOrderVO vo = new WithdrawOrderVO();
        BeanUtils.copyProperties(order, vo);
        // 银行账号脱敏
        vo.setBankAccount(desensitizeBankAccount(order.getBankAccount()));
        // 设置状态描述
        vo.setStatusDesc(getWithdrawStatusDesc(order.getStatus()));
        return vo;
    }

    /**
     * 银行账号脱敏处理：只显示后4位，前面用*号
     */
    private String desensitizeBankAccount(String account) {
        if (account != null && account.length() > 4) {
            return "************" + account.substring(account.length() - 4);
        }
        return account;
    }

    /**
     * 结算状态描述
     */
    private String getSettlementStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待结算";
            case 1 -> "已结算";
            case 2 -> "已退款";
            default -> "未知";
        };
    }

    /**
     * 提现状态描述
     */
    private String getWithdrawStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "已通过";
            case 2 -> "已拒绝";
            case 3 -> "已打款";
            default -> "未知";
        };
    }

    /**
     * 批量查询商家名称
     * <p>
     * 管理端查提现列表时，需要显示商家名称。
     * 收集所有merchantId，一次性查出商家信息，避免N+1查询问题。
     * </p>
     *
     * @param records 提现申请列表
     * @return merchantId → 商家名称 的映射
     */
    private Map<Long, String> batchGetMerchantNames(List<WithdrawOrder> records) {
        if (records.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        // 收集所有不重复的商家ID
        Set<Long> merchantIds = records.stream()
                .map(WithdrawOrder::getMerchantId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (merchantIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        // 批量查询商家信息
        List<Merchant> merchants = merchantMapper.selectBatchIds(merchantIds);
        return merchants.stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName, (a, b) -> a));
    }
}
