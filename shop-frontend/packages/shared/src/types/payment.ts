/**
 * 支付相关类型定义
 * 对齐后端 PaymentVO、PayCreateDTO、PayResultVO 等实体
 */

/** 支付方式枚举（对齐后端 payType 字段） */
export type PayType = 1 | 2 | 3

/** 支付状态枚举值类型（对齐后端 PayStatusEnum） */
export type PayStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6

/** 支付信息（对齐后端 PaymentVO） */
export interface PaymentInfo {
  /** 支付ID */
  id: number
  /** 支付单号 */
  paymentNo: string
  /** 订单编号 */
  orderNo: string
  /** 用户ID */
  userId: number
  /** 支付金额（单位：元） */
  amount: number
  /** 支付方式：1-模拟支付 2-微信 3-支付宝 */
  payType: PayType
  /** 支付状态：0-待支付 1-支付中 2-已支付 3-已关闭 4-已失败 5-退款中 6-已退款 */
  payStatus: PayStatus
  /** 支付时间 */
  payTime: string | null
  /** 创建时间 */
  createTime: string
}

/** 发起支付参数（对齐后端 PayCreateDTO） */
export interface CreatePaymentParams {
  /** 订单编号 */
  orderNo: string
  /** 支付金额（单位：元，必须大于0） */
  amount: number
  /** 支付方式：1-模拟支付 2-微信 3-支付宝 */
  payType: PayType
}

/** 支付结果（对齐后端 PayResultVO） */
export interface PaymentResult {
  /** 支付单号 */
  paymentNo: string
  /** 是否支付成功 */
  success: boolean
  /** 结果描述 */
  message: string
}
