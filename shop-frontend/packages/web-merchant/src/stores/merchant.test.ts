/**
 * merchant.ts 商家状态管理测试
 * <p>
 * 测试商家信息获取、登出、Token 设置、localStorage 持久化。
 * 结构和 web-user/stores/user.ts 类似，需要 mock @shop/shared。
 * </p>
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// mock @shop/shared 的所有依赖
const mockGetMerchantInfo = vi.fn()
const mockMerchantLogout = vi.fn()
const mockSetToken = vi.fn()
const mockClearToken = vi.fn()
const mockSetStorage = vi.fn()
const mockGetStorage = vi.fn()
const mockRemoveStorage = vi.fn()

vi.mock('@shop/shared', () => ({
  getMerchantInfo: (...args: any[]) => mockGetMerchantInfo(...args),
  merchantLogout: (...args: any[]) => mockMerchantLogout(...args),
  setToken: (...args: any[]) => mockSetToken(...args),
  clearToken: (...args: any[]) => mockClearToken(...args),
  setStorage: (...args: any[]) => mockSetStorage(...args),
  getStorage: (...args: any[]) => mockGetStorage(...args),
  removeStorage: (...args: any[]) => mockRemoveStorage(...args),
}))

import { useMerchantStore } from './merchant'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mockGetStorage.mockReturnValue(null)
})

/**
 * 构造假的商家信息
 */
function makeFakeMerchant() {
  return {
    id: 1,
    userId: 100,
    merchantName: '测试店铺',
    logo: 'https://example.com/logo.png',
    contactPhone: '13812345678',
    contactEmail: 'test@example.com',
    status: 1,
    statusDesc: '已通过',
    createTime: '2024-01-01 10:00:00',
  }
}

describe('useMerchantStore 初始化', () => {
  it('localStorage 无数据时 merchantInfo 应为 null', () => {
    mockGetStorage.mockReturnValue(null)
    const store = useMerchantStore()
    expect(store.merchantInfo).toBeNull()
  })

  it('localStorage 有数据时 merchantInfo 应从缓存恢复', () => {
    const fakeMerchant = makeFakeMerchant()
    mockGetStorage.mockReturnValue(fakeMerchant)
    const store = useMerchantStore()
    expect(store.merchantInfo).toEqual(fakeMerchant)
  })

  it('初始 globalLoading 应为 false', () => {
    const store = useMerchantStore()
    expect(store.globalLoading).toBe(false)
  })
})

describe('fetchMerchantInfo 获取商家信息', () => {
  it('应调用接口并更新 merchantInfo', async () => {
    const fakeMerchant = makeFakeMerchant()
    mockGetMerchantInfo.mockResolvedValue({ data: fakeMerchant })
    const store = useMerchantStore()

    await store.fetchMerchantInfo()

    expect(mockGetMerchantInfo).toHaveBeenCalledTimes(1)
    expect(store.merchantInfo).toEqual(fakeMerchant)
    // 应同时持久化到 localStorage
    expect(mockSetStorage).toHaveBeenCalledWith('shop_merchant_info', fakeMerchant)
  })

  it('接口失败时应向上抛出异常', async () => {
    mockGetMerchantInfo.mockRejectedValue(new Error('未登录'))
    const store = useMerchantStore()

    await expect(store.fetchMerchantInfo()).rejects.toThrow('未登录')
  })
})

describe('logout 退出登录', () => {
  it('登出成功应清除 merchantInfo、Token 和缓存', async () => {
    mockMerchantLogout.mockResolvedValue({})
    mockGetStorage.mockReturnValue(makeFakeMerchant())
    const store = useMerchantStore()

    await store.logout()

    expect(mockMerchantLogout).toHaveBeenCalledTimes(1)
    expect(store.merchantInfo).toBeNull()
    expect(mockClearToken).toHaveBeenCalledTimes(1)
    expect(mockRemoveStorage).toHaveBeenCalledWith('shop_merchant_info')
  })

  it('登出接口失败时也应清除本地状态', async () => {
    // logout 用 try...finally，错误会向上抛出，但 finally 块会执行
    mockMerchantLogout.mockRejectedValue(new Error('网络错误'))
    mockGetStorage.mockReturnValue(makeFakeMerchant())
    const store = useMerchantStore()

    await expect(store.logout()).rejects.toThrow('网络错误')

    expect(store.merchantInfo).toBeNull()
    expect(mockClearToken).toHaveBeenCalledTimes(1)
    expect(mockRemoveStorage).toHaveBeenCalledWith('shop_merchant_info')
  })
})

describe('setAuth 设置Token', () => {
  it('应调用 setToken 保存 Token', () => {
    const store = useMerchantStore()
    store.setAuth('access-token', 'refresh-token')
    expect(mockSetToken).toHaveBeenCalledWith('access-token', 'refresh-token')
  })
})

describe('setGlobalLoading', () => {
  it('应能设置 globalLoading', () => {
    const store = useMerchantStore()
    store.setGlobalLoading(true)
    expect(store.globalLoading).toBe(true)
    store.setGlobalLoading(false)
    expect(store.globalLoading).toBe(false)
  })
})
