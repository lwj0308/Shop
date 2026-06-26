/**
 * 订单状态常量
 * 对齐后端 OrderStatusEnum 状态机定义
 * 状态流转：待付款→已取消/待发货→运输中→已收货→已完成
 *           待发货→退款中→已退款/待发货
 */

/** 订单状态枚举（和后端 OrderStatusEnum 完全一致） */
export const ORDER_STATUS = {
  /** 待付款：用户刚下单，还没付钱 */
  UNPAID: 0,
  /** 已取消：用户主动取消或超时自动取消 */
  CANCELLED: 1,
  /** 待发货：用户已付款，等商家发货（后端枚举名PAID） */
  PENDING_DELIVERY: 2,
  /** 运输中：商家已发货，快递在路上 */
  SHIPPING: 3,
  /** 已收货：用户确认收到商品 */
  RECEIVED: 4,
  /** 已完成：订单流程结束 */
  COMPLETED: 5,
  /** 退款中：用户申请退款，等商家审核 */
  REFUNDING: 6,
  /** 已退款：退款成功，钱已退回 */
  REFUNDED: 7,
} as const

/** 状态描述映射，包含中文标签和展示颜色 */
export const ORDER_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待付款', color: '#E6A23C' },
  1: { label: '已取消', color: '#909399' },
  2: { label: '待发货', color: '#409EFF' },
  3: { label: '运输中', color: '#67C23A' },
  4: { label: '已收货', color: '#67C23A' },
  5: { label: '已完成', color: '#909399' },
  6: { label: '退款中', color: '#F56C6C' },
  7: { label: '已退款', color: '#909399' },
}
