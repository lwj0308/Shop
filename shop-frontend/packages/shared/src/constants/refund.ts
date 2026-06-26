/**
 * 退款状态常量
 * 对齐后端 RefundStatusEnum 状态机定义
 * 状态流转：待审核→已同意/已拒绝
 *           已同意→退款中→已退款
 */

/** 退款状态枚举（和后端 RefundStatusEnum 完全一致） */
export const REFUND_STATUS = {
  /** 待审核：用户刚申请退款，等商家审核 */
  PENDING: 0,
  /** 已同意：商家同意退款 */
  APPROVED: 1,
  /** 已拒绝：商家拒绝退款 */
  REJECTED: 2,
  /** 退款中：正在调用支付平台退款接口 */
  REFUNDING: 3,
  /** 已退款：退款成功，钱已退回用户 */
  REFUNDED: 4,
} as const

/** 退款状态描述映射 */
export const REFUND_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待审核', color: '#E6A23C' },
  1: { label: '已同意', color: '#67C23A' },
  2: { label: '已拒绝', color: '#F56C6C' },
  3: { label: '退款中', color: '#409EFF' },
  4: { label: '已退款', color: '#909399' },
}
