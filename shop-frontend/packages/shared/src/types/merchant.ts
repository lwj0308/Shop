/**
 * 商家相关类型定义
 * 包含商家信息、入驻申请等类型
 * 对齐后端 Merchant 相关实体和 VO
 */

/** 商家入驻状态枚举 */
export type MerchantStatus = 0 | 1 | 2 | 3

/** 商家信息 */
export interface MerchantInfo {
  /** 商家ID */
  id: number
  /** 商家名称 */
  name: string
  /** 商家Logo URL */
  logo: string
  /** 商家描述 */
  description: string
  /** 联系人 */
  contactName: string
  /** 联系电话 */
  contactPhone: string
  /** 商家地址 */
  address: string
  /** 入驻状态：0-审核中 1-已通过 2-已拒绝 3-已禁用 */
  status: MerchantStatus
  /** 创建时间 */
  createTime: string
}

/** 商家入驻申请参数 */
export interface MerchantApplyParams {
  /** 商家名称 */
  name: string
  /** 联系人 */
  contactName: string
  /** 联系电话 */
  contactPhone: string
  /** 商家地址 */
  address: string
  /** 营业执照URL */
  licenseUrl: string
  /** 商家描述 */
  description: string
}

/** 商家登录请求参数 */
export interface MerchantLoginParams {
  /** 联系电话（商家登录账号） */
  contactPhone: string
  /** 密码 */
  password: string
}

/** 商家登录响应数据 */
export interface MerchantLoginResult {
  /** Sa-Token 访问令牌 */
  token: string
  /** 商家ID */
  merchantId: number
}

/** 商家设置更新参数 */
export interface MerchantSettingsParams {
  /** 商家名称 */
  name?: string
  /** 商家Logo */
  logo?: string
  /** 商家描述 */
  description?: string
  /** 联系人 */
  contactName?: string
  /** 联系电话 */
  contactPhone?: string
  /** 商家地址 */
  address?: string
}

// ==================== 结算相关类型 ====================

/** 结算状态枚举：0-待结算 1-已结算 2-已退款 */
export type SettlementStatus = 0 | 1 | 2

/** 提现状态枚举：0-待审核 1-已通过 2-已拒绝 3-已打款 */
export type WithdrawStatus = 0 | 1 | 2 | 3

/**
 * 结算账户信息
 * 对齐后端 MerchantSettlementVO，银行账号已脱敏（只显示后4位）
 */
export interface SettlementAccountInfo {
  /** 结算账户ID */
  id: number
  /** 商家ID */
  merchantId: number
  /** 银行名称，比如"中国工商银行" */
  bankName: string
  /** 银行账号（脱敏后），比如 ************7890 */
  bankAccount: string
  /** 账户名，银行卡持卡人姓名 */
  accountName: string
  /** 可用余额（元），商家可提现的金额 */
  balance: number
  /** 冻结金额（元），提现申请中的金额 */
  frozenAmount: number
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/**
 * 结算账户参数（添加/更新）
 * 对齐后端 MerchantSettlementDTO
 */
export interface SettlementAccountParams {
  /** 银行名称，比如"中国工商银行" */
  bankName: string
  /** 银行账号，商家的收款银行卡号 */
  bankAccount: string
  /** 账户名，银行卡持卡人姓名 */
  accountName: string
}

/**
 * 结算流水信息
 * 对齐后端 SettlementRecordVO，每条记录对应一笔订单的结算
 */
export interface SettlementRecordInfo {
  /** 结算流水ID */
  id: number
  /** 商家ID */
  merchantId: number
  /** 订单号 */
  orderNo: string
  /** 订单金额（元） */
  orderAmount: number
  /** 平台抽成比例（如0.05表示5%） */
  commissionRate: number
  /** 平台抽成金额（元） */
  commissionAmount: number
  /** 商家应得金额（元） */
  settlementAmount: number
  /** 结算状态：0-待结算 1-已结算 2-已退款 */
  status: SettlementStatus
  /** 结算状态描述（中文） */
  statusDesc: string
  /** 结算时间 */
  settleTime: string | null
  /** 创建时间 */
  createTime: string
}

/**
 * 提现申请信息
 * 对齐后端 WithdrawOrderVO，银行账号已脱敏
 */
export interface WithdrawOrderInfo {
  /** 提现申请ID */
  id: number
  /** 商家ID */
  merchantId: number
  /** 商家名称（管理端展示用） */
  merchantName?: string
  /** 提现金额（元） */
  amount: number
  /** 状态：0-待审核 1-已通过 2-已拒绝 3-已打款 */
  status: WithdrawStatus
  /** 状态描述（中文） */
  statusDesc: string
  /** 银行名称 */
  bankName: string
  /** 银行账号（脱敏后） */
  bankAccount: string
  /** 账户名 */
  accountName: string
  /** 审核备注 */
  auditRemark: string | null
  /** 审核时间 */
  auditTime: string | null
  /** 创建时间 */
  createTime: string
}

/**
 * 提现申请参数
 * 对齐后端 WithdrawApplyDTO，只需提现金额（银行卡信息从结算账户获取）
 */
export interface WithdrawApplyParams {
  /** 提现金额（元），必须大于1元 */
  amount: number
}
