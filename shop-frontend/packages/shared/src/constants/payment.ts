/**
 * 支付状态常量
 * 对齐后端 PayStatusEnum 状态机定义
 * 状态流转：待支付→支付中→已支付/已失败
 *           待支付→已关闭（超时）
 *           已支付→退款中→已退款
 */

/** 支付状态枚举（和后端 PayStatusEnum 完全一致） */
export const PAY_STATUS = {
  /** 待支付：刚创建支付记录，等用户付款 */
  WAIT: 0,
  /** 支付中：用户正在付款 */
  PAYING: 1,
  /** 已支付：付款成功 */
  PAID: 2,
  /** 已关闭：超时未支付，自动关闭 */
  CLOSED: 3,
  /** 已失败：付款失败 */
  FAILED: 4,
  /** 退款中：正在退款 */
  REFUNDING: 5,
  /** 已退款：退款成功 */
  REFUNDED: 6,
} as const

/** 支付状态描述映射 */
export const PAY_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待支付', color: '#E6A23C' },
  1: { label: '支付中', color: '#409EFF' },
  2: { label: '已支付', color: '#67C23A' },
  3: { label: '已关闭', color: '#909399' },
  4: { label: '已失败', color: '#F56C6C' },
  5: { label: '退款中', color: '#E6A23C' },
  6: { label: '已退款', color: '#909399' },
}

/** 支付方式枚举（对齐后端 payType 字段） */
export const PAY_METHOD = {
  /** 模拟支付（MVP阶段使用） */
  MOCK: 1,
  /** 微信支付 */
  WECHAT: 2,
  /** 支付宝 */
  ALIPAY: 3,
} as const

/** 支付方式描述映射 */
export const PAY_METHOD_MAP: Record<number, string> = {
  1: '模拟支付',
  2: '微信支付',
  3: '支付宝',
}
