/**
 * 应用全局状态
 * 存储一些跟整个应用相关的状态，比如购物车数量、页面加载状态等
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCartCount } from '@shop/shared'

/**
 * 应用Store
 * 管理全局性的应用状态
 */
export const useAppStore = defineStore('app', () => {
  /** 购物车商品数量（顶部导航栏角标） */
  const cartCount = ref(0)

  /** 全局loading状态（页面级，如路由切换时） */
  const globalLoading = ref(false)

  /**
   * 刷新购物车数量
   * 登录后、添加购物车后调用，更新角标数字
   */
  const refreshCartCount = async () => {
    try {
      const res = await getCartCount()
      cartCount.value = res.data
    } catch {
      cartCount.value = 0
    }
  }

  /**
   * 设置全局loading状态
   * @param loading - 是否显示全局loading
   */
  const setGlobalLoading = (loading: boolean): void => {
    globalLoading.value = loading
  }

  return { cartCount, globalLoading, refreshCartCount, setGlobalLoading }
})
