/**
 * 需登录操作的统一守卫
 *
 * 页面里大量按钮都是同一个模式：
 * - 未登录：弹出登录弹窗，并把当前操作登记为 pendingAction，登录成功后自动继续
 * - 已登录：直接执行该操作
 * 抽成 withAuth 一个函数，避免每处都写一遍 if + return。
 */

import { isAuthenticated } from '@shop/shared'
import { useAuthModalStore, type PendingAction } from '@/stores/authModal'

/**
 * 执行需要登录的操作
 * @param description - 弹窗里提示给用户的操作说明（如"登录后查看购物车"）
 * @param action - 需要登录才能执行的操作，登录成功后会被自动重放
 */
export function withAuth(description: string, action: PendingAction['execute']): void {
  if (!isAuthenticated()) {
    useAuthModalStore().openAuthModal({ description, execute: action })
    return
  }
  action()
}
