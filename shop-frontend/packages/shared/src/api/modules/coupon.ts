/**
 * 优惠券相关API
 * 包含用户端、商家端、管理端三端的优惠券接口
 *
 * 三端接口路径不同：
 * - 用户端：/user/coupon/*（直接调用 shop-user 服务）
 * - 商家端：/marketing/coupon/*（直接调用 shop-marketing 服务）
 * - 管理端：/admin/manage/coupon/*（通过 shop-admin 转发到 shop-marketing）
 */

import { get, post, put } from '../request'
import type {
  CouponInfo,
  CouponCreateParams,
  CouponQueryParams,
  UserCouponInfo,
} from '../../types'
import type { PageResult, PageParams } from '../../types/api'

// ==================== 用户端优惠券接口 ====================

/**
 * 领取优惠券
 * @param couponId 优惠券模板ID
 */
export function receiveCoupon(couponId: number) {
  return post<null>(`/user/coupon/receive/${couponId}`)
}

/**
 * 查询我的优惠券列表
 * @param params 分页参数 + 可选的状态筛选（0未使用 1已使用 2已过期）
 */
export function getMyCoupons(params: PageParams & { status?: number }) {
  return get<PageResult<UserCouponInfo>>('/user/coupon/my', params)
}

/**
 * 查询可领取的优惠券列表（领券中心）
 * 返回所有进行中且在领取时间窗口内的优惠券
 */
export function getReceivableCouponList() {
  return get<CouponInfo[]>('/user/coupon/receivable')
}

/**
 * 查询下单可用优惠券列表
 * @param orderAmount 订单金额（用于校验满减门槛）
 */
export function getUsableCoupons(orderAmount: number) {
  return get<UserCouponInfo[]>('/user/coupon/usable', { orderAmount })
}

// ==================== 商家端优惠券接口 ====================

/**
 * 商家创建优惠券
 * @param data 优惠券参数
 */
export function createMerchantCoupon(data: CouponCreateParams) {
  return post<number>('/marketing/coupon', data)
}

/**
 * 商家修改优惠券（仅待生效状态可改）
 * @param couponId 优惠券ID
 * @param data 优惠券参数
 */
export function updateMerchantCoupon(couponId: number, data: CouponCreateParams) {
  return put<null>(`/marketing/coupon/${couponId}`, data)
}

/**
 * 商家下架优惠券
 * @param couponId 优惠券ID
 */
export function offlineMerchantCoupon(couponId: number) {
  return put<null>(`/marketing/coupon/${couponId}/offline`)
}

/**
 * 查询商家自己的优惠券列表
 * @param params 查询参数（含分页、状态、类型筛选）
 */
export function getMerchantCouponList(params: CouponQueryParams) {
  return get<PageResult<CouponInfo>>('/marketing/coupon/list', params)
}

/**
 * 查询优惠券详情
 * @param couponId 优惠券ID
 */
export function getMerchantCouponDetail(couponId: number) {
  return get<CouponInfo>(`/marketing/coupon/${couponId}`)
}

// ==================== 管理端优惠券接口 ====================

/**
 * 管理员创建平台优惠券
 * @param data 优惠券参数
 */
export function createAdminCoupon(data: CouponCreateParams) {
  return post<number>('/admin/manage/coupon', data)
}

/**
 * 查询全平台优惠券列表（含平台券和商家券）
 * @param params 查询参数（含分页、状态、类型筛选）
 */
export function getAdminCouponList(params: CouponQueryParams) {
  return get<PageResult<CouponInfo>>('/admin/manage/coupon/list', params)
}

/**
 * 管理员下架优惠券
 * @param couponId 优惠券ID
 */
export function offlineAdminCoupon(couponId: number) {
  return put<null>(`/admin/manage/coupon/${couponId}/offline`)
}
