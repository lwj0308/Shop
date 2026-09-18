/**
 * app.ts 应用全局状态测试
 * <p>
 * 测试购物车数量刷新和全局 loading 状态管理。
 * 需要 mock @shop/shared 的 getCartCount 接口。
 * </p>
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// mock @shop/shared 的 getCartCount
const mockGetCartCount = vi.fn()
vi.mock('@shop/shared', () => ({
  getCartCount: (...args: any[]) => mockGetCartCount(...args),
}))

import { useAppStore } from './app'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('useAppStore 初始化', () => {
  it('初始 cartCount 应为 0', () => {
    const store = useAppStore()
    expect(store.cartCount).toBe(0)
  })

  it('初始 globalLoading 应为 false', () => {
    const store = useAppStore()
    expect(store.globalLoading).toBe(false)
  })
})

describe('refreshCartCount 刷新购物车数量', () => {
  it('应调用接口并更新 cartCount', async () => {
    mockGetCartCount.mockResolvedValue({ data: 5 })
    const store = useAppStore()

    await store.refreshCartCount()

    expect(mockGetCartCount).toHaveBeenCalledTimes(1)
    expect(store.cartCount).toBe(5)
  })

  it('接口失败时 cartCount 应设为 0', async () => {
    mockGetCartCount.mockRejectedValue(new Error('未登录'))
    const store = useAppStore()

    // 先设置一个非0值
    mockGetCartCount.mockResolvedValueOnce({ data: 3 })
    await store.refreshCartCount()
    expect(store.cartCount).toBe(3)

    // 再调用失败
    await store.refreshCartCount()
    expect(store.cartCount).toBe(0)
  })
})

describe('setGlobalLoading', () => {
  it('应能设置 globalLoading 为 true', () => {
    const store = useAppStore()
    store.setGlobalLoading(true)
    expect(store.globalLoading).toBe(true)
  })

  it('应能设置 globalLoading 为 false', () => {
    const store = useAppStore()
    store.setGlobalLoading(true)
    store.setGlobalLoading(false)
    expect(store.globalLoading).toBe(false)
  })
})
