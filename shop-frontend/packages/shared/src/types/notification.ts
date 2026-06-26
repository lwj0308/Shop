/**
 * 消息通知相关类型定义
 * 对齐后端 com.shop.model.notification 包下的实体和枚举
 */

/** 通知接收人类型 */
export enum ReceiverType {
  /** 用户 */
  USER = 1,
  /** 商家 */
  MERCHANT = 2,
  /** 管理员 */
  ADMIN = 3,
}

/** 通知类型 */
export enum NotificationType {
  /** 订单相关通知 */
  ORDER = 1,
  /** 支付相关通知 */
  PAY = 2,
  /** 退款相关通知 */
  REFUND = 3,
  /** 商家审核通知 */
  MERCHANT_AUDIT = 4,
  /** 提现通知 */
  WITHDRAW = 5,
  /** 系统通知 */
  SYSTEM = 6,
}

/** 通知类型选项（用于下拉筛选） */
export const notificationTypeOptions = [
  { label: '订单', value: NotificationType.ORDER },
  { label: '支付', value: NotificationType.PAY },
  { label: '退款', value: NotificationType.REFUND },
  { label: '商家审核', value: NotificationType.MERCHANT_AUDIT },
  { label: '提现', value: NotificationType.WITHDRAW },
  { label: '系统', value: NotificationType.SYSTEM },
]

/** 通知类型对应的标签颜色（Element Plus 的 type） */
export const notificationTypeTagMap: Record<number, string> = {
  [NotificationType.ORDER]: 'primary',
  [NotificationType.PAY]: 'success',
  [NotificationType.REFUND]: 'warning',
  [NotificationType.MERCHANT_AUDIT]: 'info',
  [NotificationType.WITHDRAW]: 'success',
  [NotificationType.SYSTEM]: 'info',
}

/** 通知类型对应的中文描述 */
export const notificationTypeTextMap: Record<number, string> = {
  [NotificationType.ORDER]: '订单',
  [NotificationType.PAY]: '支付',
  [NotificationType.REFUND]: '退款',
  [NotificationType.MERCHANT_AUDIT]: '商家审核',
  [NotificationType.WITHDRAW]: '提现',
  [NotificationType.SYSTEM]: '系统',
}

/** 通知信息 */
export interface NotificationInfo {
  /** 通知ID */
  id: number
  /** 接收人类型：1用户 2商家 3管理员 */
  receiverType: number
  /** 接收人ID */
  receiverId: number
  /** 通知类型：1订单 2支付 3退款 4商家审核 5提现 6系统 */
  type: number
  /** 通知类型描述（后端翻译好的中文） */
  typeDesc: string
  /** 通知标题 */
  title: string
  /** 通知内容 */
  content: string
  /** 关联业务类型（如 order/withdraw/merchant） */
  bizType?: string
  /** 关联业务ID（如订单号、提现单号） */
  bizId?: string
  /** 是否已读：0未读 1已读 */
  isRead: number
  /** 创建时间 */
  createTime: string
}

/** 通知查询参数 */
export interface NotificationQueryParams {
  /** 通知类型筛选（可选） */
  type?: number
  /** 已读状态筛选（可选：0未读 1已读） */
  isRead?: number
  /** 页码 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}
