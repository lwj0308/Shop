/**
 * 支付相关API
 * 包含发起支付、模拟支付、查询支付信息等接口
 * 路径对齐后端 PaymentController：/payment/create、/payment/mock-pay/{id}、/payment/order/{orderNo}
 */

import { get, post } from '../request'
import type { CreatePaymentParams, PaymentInfo, PaymentResult } from '../../types'

/** 发起支付（创建支付记录，返回支付单号） */
export function createPayment(data: CreatePaymentParams) {
  return post<PaymentResult>('/payment/create', data)
}

/** 模拟支付（直接把状态改为已支付，MVP阶段专用） */
export function mockPay(paymentId: number) {
  return post<PaymentResult>(`/payment/mock-pay/${paymentId}`)
}

/** 根据订单号查询支付信息（支付结果页用） */
export function getPaymentByOrderNo(orderNo: string) {
  return get<PaymentInfo>(`/payment/order/${orderNo}`)
}
