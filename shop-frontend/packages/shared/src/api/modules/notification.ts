/**
 * 消息通知相关API
 * 包含用户端、商家端、管理端三端的通知接口
 *
 * 三端接口路径不同：
 * - 用户端：/user/notification/*（直接调用 shop-user 服务）
 * - 商家端：/merchant/notification/*（通过 shop-merchant 转发到 shop-user）
 * - 管理端：/admin/notification/*（通过 shop-admin 转发到 shop-user）
 */

import { get, put } from '../request'
import type {
  NotificationInfo,
  NotificationQueryParams,
} from '../../types'
import type { ApiResponse, PageResult, PageParams } from '../../types/api'

// ==================== 用户端通知接口 ====================

/**
 * 查询当前用户的通知列表
 * @param params 分页参数 + 可选的类型和已读状态筛选
 */
export function getUserNotificationList(params: PageParams & { type?: number; isRead?: number }) {
  return get<PageResult<NotificationInfo>>('/user/notification/list', params)
}

/**
 * 查询当前用户的未读通知数量
 * 用于顶部铃铛徽章显示
 */
export function getUserUnreadCount() {
  return get<number>('/user/notification/unread-count')
}

/**
 * 标记单条通知为已读（用户端）
 * @param id 通知ID
 */
export function markUserNotificationRead(id: number) {
  return put<null>(`/user/notification/${id}/read`)
}

/**
 * 全部标记已读（用户端）
 */
export function markUserAllNotificationsRead() {
  return put<null>('/user/notification/read-all')
}

// ==================== 商家端通知接口 ====================

/**
 * 查询当前商家的通知列表
 * @param params 分页参数 + 可选的类型和已读状态筛选
 */
export function getMerchantNotificationList(params: PageParams & { type?: number; isRead?: number }) {
  return get<PageResult<NotificationInfo>>('/merchant/notification/list', params)
}

/**
 * 查询当前商家的未读通知数量
 */
export function getMerchantUnreadCount() {
  return get<number>('/merchant/notification/unread-count')
}

/**
 * 全部标记已读（商家端）
 */
export function markMerchantAllNotificationsRead() {
  return put<null>('/merchant/notification/read-all')
}

// ==================== 管理端通知接口 ====================

/**
 * 查询当前管理员的通知列表
 * @param params 分页参数 + 可选的类型和已读状态筛选
 */
export function getAdminNotificationList(params: PageParams & { type?: number; isRead?: number }) {
  return get<PageResult<NotificationInfo>>('/admin/notification/list', params)
}

/**
 * 查询当前管理员的未读通知数量
 */
export function getAdminUnreadCount() {
  return get<number>('/admin/notification/unread-count')
}

/**
 * 全部标记已读（管理端）
 */
export function markAdminAllNotificationsRead() {
  return put<null>('/admin/notification/read-all')
}
