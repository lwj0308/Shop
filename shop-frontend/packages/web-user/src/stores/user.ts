/**
 * 用户状态管理
 * 存储当前登录用户的信息，以及登录/登出操作
 * 用户信息持久化到localStorage，页面刷新后不丢失
 */

import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { getUserInfo as getUserInfoApi, userLogout as userLogoutApi } from '@shop/shared'
import { setToken, clearToken, setStorage, getStorage } from '@shop/shared'
import type { UserInfo } from '@shop/shared'

/** localStorage中用户信息的存储键 */
const USER_INFO_STORAGE_KEY = 'shop_user_info'

/**
 * 用户Store
 * 管理用户登录状态和个人信息
 * 用户信息自动持久化到localStorage
 */
export const useUserStore = defineStore('user', () => {
  /** 当前登录用户信息（初始化时从localStorage恢复） */
  const userInfo = ref<UserInfo | null>(getStorage<UserInfo>(USER_INFO_STORAGE_KEY))

  /**
   * 监听userInfo变化，自动持久化到localStorage
   * 这样不管在哪里修改了userInfo，都会自动保存
   */
  watch(userInfo, (newVal) => {
    if (newVal) {
      setStorage(USER_INFO_STORAGE_KEY, newVal)
    } else {
      localStorage.removeItem(USER_INFO_STORAGE_KEY)
    }
  }, { deep: true })

  /**
   * 获取用户信息
   * 登录后调用，从服务器获取最新的用户资料
   */
  const fetchUserInfo = async () => {
    const res = await getUserInfoApi()
    userInfo.value = res.data
  }

  /**
   * 退出登录
   * 清除本地Token和用户信息
   */
  const logout = async () => {
    try {
      await userLogoutApi()
    } finally {
      userInfo.value = null
      clearToken()
    }
  }

  /**
   * 设置Token（登录成功后调用）
   */
  const setAuth = (accessToken: string, refreshToken: string) => {
    setToken(accessToken, refreshToken)
  }

  return { userInfo, fetchUserInfo, logout, setAuth }
})
