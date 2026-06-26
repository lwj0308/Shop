/**
 * 管理员状态管理
 *
 * 管理当前登录管理员的信息、Token、权限列表和动态路由。
 * Token使用shared包的auth工具统一管理，确保请求拦截器能自动携带。
 * 管理员信息持久化到localStorage，刷新页面不丢失。
 */

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getToken as getSharedToken, setToken as setSharedToken, clearToken as clearSharedToken } from '@shop/shared/utils/auth'
import { adminLogin, adminLogout, getAdminInfo } from '@shop/shared/api/modules/admin'
import type { AdminLoginParams } from '@shop/shared/api/modules/admin'

/** 管理员信息存储的key名 */
const ADMIN_INFO_KEY = 'admin_info'

/** 管理员信息接口 */
export interface AdminInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  roles: string[]
  permissions: string[]
}

export const useAdminStore = defineStore('admin', () => {
  const router = useRouter()

  // ========== 状态 ==========
  /**
   * 登录Token
   * 从shared的auth工具获取，确保和请求拦截器读取的是同一个Token
   */
  const token = ref<string>(getSharedToken())
  /** 管理员信息 */
  const adminInfo = ref<AdminInfo | null>(
    JSON.parse(localStorage.getItem(ADMIN_INFO_KEY) || 'null')
  )
  /** 动态路由是否已加载 */
  const dynamicRoutesLoaded = ref(false)

  // ========== 计算属性 ==========
  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)
  /** 管理员昵称（没有则显示用户名） */
  const displayName = computed(() => adminInfo.value?.nickname || adminInfo.value?.username || '')
  /** 权限列表 */
  const permissions = computed(() => adminInfo.value?.permissions || [])
  /** 角色列表 */
  const roles = computed(() => adminInfo.value?.roles || [])
  /** 是否超级管理员 */
  const isSuperAdmin = computed(() => roles.value.includes('admin'))

  // ========== 自动持久化 ==========
  /**
   * 监听Token变化，同步到shared的auth工具
   * shared的auth工具会把Token存到localStorage，请求拦截器会自动读取
   */
  watch(token, (val) => {
    if (val) {
      // 管理后台没有refreshToken，传空字符串占位
      setSharedToken(val, '')
    } else {
      clearSharedToken()
    }
  })

  /** 管理员信息变化时自动持久化 */
  watch(adminInfo, (val) => {
    if (val) {
      localStorage.setItem(ADMIN_INFO_KEY, JSON.stringify(val))
    } else {
      localStorage.removeItem(ADMIN_INFO_KEY)
    }
  }, { deep: true })

  // ========== 方法 ==========

  /**
   * 管理员登录
   * 调用后端登录接口，保存Token和管理员信息
   * @param params 登录参数（用户名、密码、验证码）
   */
  async function login(params: AdminLoginParams) {
    const res = await adminLogin(params)
    const data = res.data.data
    // 保存Token（会触发watch，自动同步到shared的auth工具）
    token.value = data.token
    // 登录成功后获取完整的管理员信息（含角色和权限）
    await fetchAdminInfo()
  }

  /**
   * 获取当前管理员信息
   * 调用后端接口获取管理员详情，包含角色和权限列表
   */
  async function fetchAdminInfo() {
    const res = await getAdminInfo()
    const data = res.data.data
    adminInfo.value = {
      id: data.id,
      username: data.username,
      nickname: data.nickname,
      avatar: data.avatar || '',
      roles: data.roles || [],
      permissions: data.permissions || [],
    }
  }

  /**
   * 从后端加载动态路由
   *
   * 工作流程：
   * 1. 调用后端API获取当前管理员的权限菜单树
   * 2. 将菜单树转换为Vue Router路由配置
   * 3. 通过 router.addRoute() 动态注册路由
   * 4. 标记路由已加载
   *
   * 当前阶段：使用静态路由，直接标记为已加载
   * 后续阶段：从后端获取菜单树并动态注册
   */
  async function loadDynamicRoutes() {
    // 当前阶段使用静态路由，直接标记为已加载
    // TODO: 后续实现动态路由
    // const menuList = await getMenuList()
    // const routes = transformMenuToRoutes(menuList)
    // routes.forEach(route => router.addRoute(route))
    // router.addRoute({ path: '/:pathMatch(.*)*', redirect: '/404' })
    dynamicRoutesLoaded.value = true
  }

  /**
   * 登出
   * 调用后端登出接口，清除Token、管理员信息、动态路由状态
   */
  async function logout() {
    try {
      await adminLogout()
    } catch {
      // 即使后端登出失败，也要清除本地状态
    }
    token.value = ''
    adminInfo.value = null
    dynamicRoutesLoaded.value = false
    router.push('/login')
  }

  /**
   * 检查是否拥有指定权限
   * @param permission 权限标识，如 'admin:user:list'
   * @returns true表示有权限
   */
  function hasPermission(permission: string): boolean {
    if (isSuperAdmin.value) return true
    return permissions.value.includes(permission)
  }

  /**
   * 检查是否拥有指定权限中的任意一个
   * @param perms 权限标识数组
   * @returns true表示至少有一个权限
   */
  function hasAnyPermission(perms: string[]): boolean {
    if (isSuperAdmin.value) return true
    return perms.some(p => permissions.value.includes(p))
  }

  return {
    token,
    adminInfo,
    dynamicRoutesLoaded,
    isLoggedIn,
    displayName,
    permissions,
    roles,
    isSuperAdmin,
    login,
    fetchAdminInfo,
    loadDynamicRoutes,
    logout,
    hasPermission,
    hasAnyPermission,
  }
})
