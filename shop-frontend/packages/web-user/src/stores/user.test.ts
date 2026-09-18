/**
 * user.ts 用户状态管理测试
 * <p>
 * 测试用户信息获取、登出、Token 设置、localStorage 持久化。
 * 需要 mock @shop/shared 的多个方法。
 * </p>
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// mock @shop/shared 的所有依赖
const mockGetUserInfo = vi.fn()
const mockUserLogout = vi.fn()
const mockSetToken = vi.fn()
const mockClearToken = vi.fn()
const mockSetStorage = vi.fn()
const mockGetStorage = vi.fn()

vi.mock('@shop/shared', () => ({
  getUserInfo: (...args: any[]) => mockGetUserInfo(...args),
  userLogout: (...args: any[]) => mockUserLogout(...args),
  setToken: (...args: any[]) => mockSetToken(...args),
  clearToken: (...args: any[]) => mockClearToken(...args),
  setStorage: (...args: any[]) => mockSetStorage(...args),
  getStorage: (...args: any[]) => mockGetStorage(...args),
}))

import { useUserStore } from './user'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // 默认 getStorage 返回 null（未登录状态）
  mockGetStorage.mockReturnValue(null)
})

/**
 * 构造假的用户信息
 */
function makeFakeUser() {
  return {
    id: 100,
    phone: '13812345678',
    nickname: '测试用户',
    avatar: 'https://example.com/avatar.png',
    gender: 1 as 0 | 1 | 2,
    createTime: '2024-01-01 10:00:00',
  }
}

describe('useUserStore 初始化', () => {
  it('localStorage 无数据时 userInfo 应为 null', () => {
    mockGetStorage.mockReturnValue(null)
    const store = useUserStore()
    expect(store.userInfo).toBeNull()
  })

  it('localStorage 有数据时 userInfo 应从缓存恢复', () => {
    const fakeUser = makeFakeUser()
    mockGetStorage.mockReturnValue(fakeUser)
    const store = useUserStore()
    expect(store.userInfo).toEqual(fakeUser)
  })
})

describe('fetchUserInfo 获取用户信息', () => {
  it('应调用接口并更新 userInfo', async () => {
    mockGetUserInfo.mockResolvedValue({ data: makeFakeUser() })
    const store = useUserStore()

    await store.fetchUserInfo()

    expect(mockGetUserInfo).toHaveBeenCalledTimes(1)
    expect(store.userInfo).toEqual(makeFakeUser())
  })

  it('userInfo 变化时应自动持久化到 localStorage', async () => {
    const fakeUser = makeFakeUser()
    mockGetUserInfo.mockResolvedValue({ data: fakeUser })
    const store = useUserStore()

    await store.fetchUserInfo()

    // watch 会触发 setStorage 持久化
    expect(mockSetStorage).toHaveBeenCalledWith('shop_user_info', fakeUser)
  })

  it('接口失败时应向上抛出异常', async () => {
    mockGetUserInfo.mockRejectedValue(new Error('未登录'))
    const store = useUserStore()

    await expect(store.fetchUserInfo()).rejects.toThrow('未登录')
  })
})

describe('logout 退出登录', () => {
  it('登出成功应清除 userInfo 和 Token', async () => {
    mockUserLogout.mockResolvedValue({})
    mockGetStorage.mockReturnValue(makeFakeUser())
    const store = useUserStore()
    expect(store.userInfo).not.toBeNull()

    await store.logout()

    expect(mockUserLogout).toHaveBeenCalledTimes(1)
    expect(store.userInfo).toBeNull()
    expect(mockClearToken).toHaveBeenCalledTimes(1)
  })

  it('登出接口失败时也应清除本地状态', async () => {
    // logout 用 try...finally，错误会向上抛出，但 finally 块会执行
    mockUserLogout.mockRejectedValue(new Error('网络错误'))
    mockGetStorage.mockReturnValue(makeFakeUser())
    const store = useUserStore()

    await expect(store.logout()).rejects.toThrow('网络错误')

    // 即使接口失败，本地状态也应被清除
    expect(store.userInfo).toBeNull()
    expect(mockClearToken).toHaveBeenCalledTimes(1)
  })
})

describe('setAuth 设置Token', () => {
  it('应调用 setToken 保存 Token', () => {
    const store = useUserStore()
    store.setAuth('access-token', 'refresh-token')
    expect(mockSetToken).toHaveBeenCalledWith('access-token', 'refresh-token')
  })
})
