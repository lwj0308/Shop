/**
 * 商家相关API
 * 包含商家登录、入驻申请、店铺管理、结算管理等接口
 */

import { get, post, put } from '../request'
import type {
  MerchantInfo,
  MerchantApplyParams,
  MerchantLoginParams,
  MerchantLoginResult,
  MerchantSettingsParams,
  SettlementAccountInfo,
  SettlementAccountParams,
  SettlementRecordInfo,
  SettlementStatus,
  WithdrawApplyParams,
  WithdrawOrderInfo,
  WithdrawStatus,
  PageParams,
  PageResult,
} from '../../types'

/** 商家登录 */
export function merchantLogin(data: MerchantLoginParams) {
  return post<MerchantLoginResult>('/merchant/auth/login', data)
}

/** 商家入驻申请 */
export function merchantApply(data: MerchantApplyParams) {
  return post<null>('/merchant/apply', data)
}

/** 获取商家信息 */
export function getMerchantInfo() {
  return get<MerchantInfo>('/merchant/info')
}

/** 更新商家设置 */
export function updateMerchantSettings(data: MerchantSettingsParams) {
  return put<MerchantInfo>('/merchant/info', data)
}

/** 商家退出登录 */
export function merchantLogout() {
  return post<null>('/merchant/auth/logout')
}

// ==================== 结算管理 ====================

/**
 * 获取结算账户
 * 查询商家的银行账户信息和余额，银行账号已脱敏
 */
export function getSettlementAccount() {
  return get<SettlementAccountInfo | null>('/merchant/settlement')
}

/**
 * 添加结算账户
 * 商家首次配置银行账户信息，一个商家只能有一个结算账户
 */
export function addSettlementAccount(data: SettlementAccountParams) {
  return post<null>('/merchant/settlement', data)
}

/**
 * 更新结算账户
 * 商家修改银行账户信息，如果还没有结算账户会自动创建
 */
export function updateSettlementAccount(data: SettlementAccountParams) {
  return put<null>('/merchant/settlement', data)
}

/**
 * 结算流水列表
 * 商家查看自己店铺的结算记录，每条记录对应一笔订单的结算
 * @param params 分页参数 + 可选的结算状态筛选
 */
export function getSettlementRecords(params: PageParams & { status?: SettlementStatus }) {
  return get<PageResult<SettlementRecordInfo>>('/merchant/settlement/records', params)
}

/**
 * 申请提现
 * 金额从可用余额转入冻结金额，等待管理员审核
 */
export function applyWithdraw(data: WithdrawApplyParams) {
  return post<null>('/merchant/settlement/withdraw', data)
}

/**
 * 提现申请列表
 * 商家查看自己的提现申请历史
 * @param params 分页参数 + 可选的提现状态筛选
 */
export function getWithdrawList(params: PageParams & { status?: WithdrawStatus }) {
  return get<PageResult<WithdrawOrderInfo>>('/merchant/settlement/withdraw/list', params)
}
