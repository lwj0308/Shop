/**
 * 认证逻辑组合式函数
 * 把登录、登出、获取用户信息这些跟"身份认证"相关的操作封装在一起
 * 这样在任何组件里只要调用 useAuth() 就能使用这些功能
 */

import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { userLogin as userLoginApi, userLogout as userLogoutApi, getUserInfo as getUserInfoApi } from '../api'
import { setToken, clearToken, isAuthenticated } from '../utils/auth'
import type { UserInfo } from '../types'

/**
 * 认证逻辑组合式函数
 * 提供登录、登出、获取用户信息、判断是否已登录等功能
 * 登录成功后会自动获取用户信息
 */
export function useAuth() {
  const router = useRouter()

  /** 当前用户信息（模块级共享，避免重复请求） */
  const userInfo = ref<UserInfo | null>(null)

  /** 是否正在登录中（防止重复提交） */
  const loginLoading = ref(false)

  /** 是否已登录（响应式，Token变化时会自动更新） */
  const isLoggedIn = computed(() => isAuthenticated())

  /**
   * 用户登录
   * 调用登录接口，成功后把Token存到本地，获取用户信息，然后跳转到首页或之前访问的页面
   * @param phone - 手机号
   * @param password - 密码
   * @param redirect - 登录后要跳转的路径，默认跳首页
   */
  const login = async (phone: string, password: string, redirect?: string) => {
    if (loginLoading.value) return // 防止重复提交
    loginLoading.value = true
    try {
      const res = await userLoginApi({ phone, password })
      // 登录成功，存储Token
      setToken(res.data.accessToken, res.data.refreshToken)
      // 登录后立即获取用户信息
      await fetchUserInfo()
      // 跳转到指定页面或首页
      router.push(redirect || '/')
    } finally {
      loginLoading.value = false
    }
  }

  /**
   * 用户登出
   * 通知后端使Token失效，然后清除本地Token和用户信息，跳转到登录页
   */
  const logout = async () => {
    try {
      await userLogoutApi()
    } finally {
      // 不管后端接口是否成功，都清除本地Token和用户信息
      clearToken()
      userInfo.value = null
      router.push('/login')
    }
  }

  /**
   * 获取当前登录用户信息
   * 获取成功后缓存到userInfo，避免重复请求
   * @returns 用户信息对象
   */
  const fetchUserInfo = async (): Promise<UserInfo> => {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    return res.data
  }

  return { userInfo, loginLoading, isLoggedIn, login, logout, fetchUserInfo }
}
