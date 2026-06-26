/**
 * 登录弹窗状态管理
 * 全局控制登录弹窗的显示/隐藏，以及登录后的待执行操作（pendingAction）
 *
 * 核心机制：
 * 1. 用户点击需登录的操作（如加购）但未登录
 * 2. 把操作存入 pendingAction，打开登录弹窗
 * 3. 登录成功后，自动执行 pendingAction
 * 4. 关闭弹窗时，清空 pendingAction
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 待执行操作接口
 * 登录成功后会自动执行这个操作
 */
export interface PendingAction {
  /** 操作描述（用于弹窗提示用户） */
  description: string
  /** 执行函数（登录成功后调用） */
  execute: () => Promise<void> | void
}

export const useAuthModalStore = defineStore('authModal', () => {
  /** 是否显示登录弹窗 */
  const showAuthModal = ref(false)

  /** 待执行操作（登录成功后执行） */
  const pendingAction = ref<PendingAction | null>(null)

  /** 是否有待执行操作（用于弹窗显示提示） */
  const hasPendingAction = computed(() => pendingAction.value !== null)

  /**
   * 打开登录弹窗
   * @param action - 可选，登录成功后要执行的操作
   */
  const openAuthModal = (action?: PendingAction) => {
    pendingAction.value = action || null
    showAuthModal.value = true
  }

  /**
   * 关闭登录弹窗
   * 同时清空待执行操作
   */
  const closeAuthModal = () => {
    showAuthModal.value = false
    pendingAction.value = null
  }

  /**
   * 执行待执行操作（登录成功后调用）
   * 执行完成后清空
   */
  const executePendingAction = async () => {
    if (pendingAction.value) {
      try {
        await pendingAction.value.execute()
      } catch (error) {
        console.error('执行待执行操作失败:', error)
      } finally {
        pendingAction.value = null
      }
    }
    showAuthModal.value = false
  }

  return {
    showAuthModal,
    pendingAction,
    hasPendingAction,
    openAuthModal,
    closeAuthModal,
    executePendingAction,
  }
})
