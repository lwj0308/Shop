/**
 * 订单相关API
 * 包含创建订单、查询订单、取消订单等接口
 */

import { get, post, put } from '../request'
import type {
  OrderInfo,
  CreateOrderParams,
  OrderListParams,
  PageParams,
  PageResult,
} from '../../types'

/** 创建订单（从购物车结算） */
export function createOrder(data: CreateOrderParams) {
  return post<OrderInfo>('/order', data)
}

/** 获取订单列表 */
export function getOrderList(params: PageParams & OrderListParams) {
  return get<PageResult<OrderInfo>>('/order/list', params)
}

/** 获取订单详情 */
export function getOrderDetail(id: number) {
  return get<OrderInfo>(`/order/${id}`)
}

/** 取消订单 */
export function cancelOrder(id: number) {
  return put<null>(`/order/${id}/cancel`)
}

/** 确认收货 */
export function confirmReceive(id: number) {
  return put<null>(`/order/${id}/confirm`)
}

/** 申请退款 */
export function applyRefund(orderId: number, orderItemId: number, reason: string) {
  return post<null>('/order/refund/apply', { orderId, orderItemId, reason })
}

/** 商家 - 获取商家订单列表 */
export function getMerchantOrderList(params: PageParams & OrderListParams) {
  return get<PageResult<OrderInfo>>('/order/list', params)
}

/** 商家 - 发货 */
export function shipOrder(orderId: number, logisticsNo: string, logisticsCompany: string) {
  return post<null>('/order/logistics/delivery', { orderId, logisticsNo, logisticsCompany })
}

/** 商家 - 同意退款 */
export function agreeRefund(refundId: number) {
  return put<null>('/order/refund/audit', { refundId, status: 1 })
}

/** 商家 - 拒绝退款 */
export function rejectRefund(refundId: number, reason: string) {
  return put<null>('/order/refund/audit', { refundId, status: 2, auditNote: reason })
}
