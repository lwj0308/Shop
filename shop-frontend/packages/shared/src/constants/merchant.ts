/**
 * 商家状态常量
 * 对齐后端 MerchantStatusEnum 定义
 */

/** 商家状态枚举（和后端 MerchantStatusEnum 完全一致） */
export const MERCHANT_STATUS = {
  /** 待审核：商家刚提交入驻申请，等管理员审核 */
  PENDING: 0,
  /** 已通过：管理员审核通过，商家可以正常经营 */
  APPROVED: 1,
  /** 已拒绝：管理员审核拒绝，商家需要修改后重新提交 */
  REJECTED: 2,
  /** 已禁用：商家违规被管理员封禁，不能经营 */
  DISABLED: 3,
} as const

/** 商家状态描述映射 */
export const MERCHANT_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待审核', color: '#E6A23C' },
  1: { label: '已通过', color: '#67C23A' },
  2: { label: '已拒绝', color: '#F56C6C' },
  3: { label: '已禁用', color: '#909399' },
}
