/**
 * 商家状态管理
 * 存储当前登录商家的信息，支持持久化和全局loading状态
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMerchantInfo as getMerchantInfoApi, merchantLogout as merchantLogoutApi } from '@shop/shared'
import { setToken, clearToken, setStorage, getStorage, removeStorage } from '@shop/shared'
import type { MerchantInfo } from '@shop/shared'

/** 商家信息在localStorage中的存储键名 */
const MERCHANT_INFO_KEY = 'shop_merchant_info'

/**
 * 商家Store
 * 管理商家登录状态和店铺信息
 * 商家信息会自动持久化到localStorage，刷新页面不会丢失
 */
export const useMerchantStore = defineStore('merchant', () => {
  /** 当前登录商家信息（优先从本地缓存恢复） */
  const merchantInfo = ref<MerchantInfo | null>(getStorage<MerchantInfo>(MERCHANT_INFO_KEY))

  /** 全局loading状态，用于页面级别的加载提示 */
  const globalLoading = ref(false)

  /**
   * 获取商家信息
   * 登录后调用，从服务器获取最新的商家资料，并持久化到本地
   */
  const fetchMerchantInfo = async () => {
    const res = await getMerchantInfoApi()
    merchantInfo.value = res.data
    // 持久化商家信息到localStorage
    setStorage(MERCHANT_INFO_KEY, res.data)
  }

  /**
   * 退出登录
   * 清除本地Token、商家信息和缓存
   */
  const logout = async () => {
    try {
      await merchantLogoutApi()
    } finally {
      // 不管后端接口是否成功，都清除本地数据
      merchantInfo.value = null
      clearToken()
      removeStorage(MERCHANT_INFO_KEY)
    }
  }

  /**
   * 设置Token（登录成功后调用）
   * @param accessToken - 访问令牌
   * @param refreshToken - 刷新令牌
   */
  const setAuth = (accessToken: string, refreshToken: string) => {
    setToken(accessToken, refreshToken)
  }

  /**
   * 设置全局loading状态
   * @param loading - 是否正在加载
   */
  const setGlobalLoading = (loading: boolean) => {
    globalLoading.value = loading
  }

  return { merchantInfo, globalLoading, fetchMerchantInfo, logout, setAuth, setGlobalLoading }
})
