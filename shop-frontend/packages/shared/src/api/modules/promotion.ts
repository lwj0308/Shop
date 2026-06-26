/**
 * 满减活动相关API
 * 包含商家端和管理端两端的满减活动接口
 *
 * 两端接口路径不同：
 * - 商家端：/merchant/promotion/*（直接调用 shop-merchant 服务）
 * - 管理端：/admin/manage/promotion/*（通过 shop-admin 转发到 shop-merchant）
 *
 * 注意：满减是自动计算的，用户端没有主动操作的接口（下单时后端自动计算优惠）
 */

import { get, post, put } from '../request'
import type {
  PromotionInfo,
  PromotionCreateParams,
  PromotionQueryParams,
} from '../../types'
import type { PageResult } from '../../types/api'

// ==================== 商家端满减活动接口 ====================

/**
 * 商家创建满减活动
 * @param data 满减活动参数
 */
export function createMerchantPromotion(data: PromotionCreateParams) {
  return post<number>('/merchant/promotion', data)
}

/**
 * 商家修改满减活动（仅待生效状态可改）
 * @param promotionId 满减活动ID
 * @param data 满减活动参数
 */
export function updateMerchantPromotion(promotionId: number, data: PromotionCreateParams) {
  return put<null>(`/merchant/promotion/${promotionId}`, data)
}

/**
 * 商家下架满减活动
 * @param promotionId 满减活动ID
 */
export function offlineMerchantPromotion(promotionId: number) {
  return put<null>(`/merchant/promotion/${promotionId}/offline`)
}

/**
 * 查询商家自己的满减活动列表
 * @param params 查询参数（含分页、状态筛选）
 */
export function getMerchantPromotionList(params: PromotionQueryParams) {
  return get<PageResult<PromotionInfo>>('/merchant/promotion/list', params)
}

/**
 * 查询满减活动详情
 * @param promotionId 满减活动ID
 */
export function getMerchantPromotionDetail(promotionId: number) {
  return get<PromotionInfo>(`/merchant/promotion/${promotionId}`)
}

// ==================== 管理端满减活动接口 ====================

/**
 * 管理员创建平台满减活动
 * @param data 满减活动参数
 */
export function createAdminPromotion(data: PromotionCreateParams) {
  return post<number>('/admin/manage/promotion', data)
}

/**
 * 查询全平台满减活动列表（含平台活动和商家活动）
 * @param params 查询参数（含分页、状态筛选）
 */
export function getAdminPromotionList(params: PromotionQueryParams) {
  return get<PageResult<PromotionInfo>>('/admin/manage/promotion/list', params)
}

/**
 * 管理员下架满减活动
 * @param promotionId 满减活动ID
 */
export function offlineAdminPromotion(promotionId: number) {
  return put<null>(`/admin/manage/promotion/${promotionId}/offline`)
}
