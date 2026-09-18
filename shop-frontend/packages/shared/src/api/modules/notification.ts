/**
 * 消息通知相关API
 * 包含用户端、商家端、管理端三端的通知接口
 *
 * 三端接口完全同构，只有网关路径前缀不同：
 * - 用户端：/user/notification/*（直接调用 shop-user 服务）
 * - 商家端：/merchant/notification/*（通过 shop-merchant 转发到 shop-user）
 * - 管理端：/admin/notification/*（通过 shop-admin 转发到 shop-user）
 * 因此由 createNotificationApi 按端生成，避免三份复制粘贴。
 */

import { get, put } from '../request'
import type { NotificationInfo } from '../../types'
import type { PageResult, PageParams } from '../../types/api'

/** 通知列表查询参数：分页 + 可选的类型和已读状态筛选 */
type NotificationListParams = PageParams & { type?: number; isRead?: number }

/**
 * 生成某一端的通知接口集合
 * @param scope - 端标识，对应网关路径前缀
 */
function createNotificationApi(scope: 'user' | 'merchant' | 'admin') {
  const base = `/${scope}/notification`
  return {
    /** 查询通知列表 @param params 分页参数 + 可选的类型和已读状态筛选 */
    getList: (params: NotificationListParams) =>
      get<PageResult<NotificationInfo>>(`${base}/list`, params),
    /** 查询未读通知数量（用于顶部铃铛徽章显示） */
    getUnreadCount: () => get<number>(`${base}/unread-count`),
    /** 全部标记已读 */
    markAllRead: () => put<null>(`${base}/read-all`),
  }
}

const userApi = createNotificationApi('user')
const merchantApi = createNotificationApi('merchant')
const adminApi = createNotificationApi('admin')

// ==================== 用户端通知接口 ====================

export const getUserNotificationList = userApi.getList
export const getUserUnreadCount = userApi.getUnreadCount
export const markUserAllNotificationsRead = userApi.markAllRead

/**
 * 标记单条通知为已读（只有用户端提供了该接口）
 * @param id 通知ID
 */
export function markUserNotificationRead(id: number) {
  return put<null>(`/user/notification/${id}/read`)
}

// ==================== 商家端通知接口 ====================

export const getMerchantNotificationList = merchantApi.getList
export const getMerchantUnreadCount = merchantApi.getUnreadCount
export const markMerchantAllNotificationsRead = merchantApi.markAllRead

// ==================== 管理端通知接口 ====================

export const getAdminNotificationList = adminApi.getList
export const getAdminUnreadCount = adminApi.getUnreadCount
export const markAdminAllNotificationsRead = adminApi.markAllRead
