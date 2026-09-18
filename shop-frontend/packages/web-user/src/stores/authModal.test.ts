/**
 * authModal.ts 登录弹窗状态管理测试
 * <p>
 * 这是 web-user 最简单的 store，纯前端状态管理，不需要 mock 任何 API。
 * 测试重点：弹窗显示/隐藏、pendingAction 的设置和执行、executePendingAction 的容错。
 * </p>
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthModalStore } from './authModal'

// 每个测试前创建一个新的 Pinia 实例，避免 store 状态污染
beforeEach(() => {
  setActivePinia(createPinia())
})

describe('useAuthModalStore 初始化', () => {
  it('初始 showAuthModal 应为 false', () => {
    const store = useAuthModalStore()
    expect(store.showAuthModal).toBe(false)
  })

  it('初始 pendingAction 应为 null', () => {
    const store = useAuthModalStore()
    expect(store.pendingAction).toBeNull()
  })

  it('初始 hasPendingAction 应为 false', () => {
    const store = useAuthModalStore()
    expect(store.hasPendingAction).toBe(false)
  })
})

describe('openAuthModal / closeAuthModal', () => {
  it('openAuthModal 应将 showAuthModal 设为 true', () => {
    const store = useAuthModalStore()
    store.openAuthModal()
    expect(store.showAuthModal).toBe(true)
  })

  it('openAuthModal 不传 action 时 pendingAction 应为 null', () => {
    const store = useAuthModalStore()
    store.openAuthModal()
    expect(store.pendingAction).toBeNull()
    expect(store.hasPendingAction).toBe(false)
  })

  it('openAuthModal 传 action 时 pendingAction 应被设置', () => {
    const store = useAuthModalStore()
    const action = { description: '加购', execute: vi.fn() }
    store.openAuthModal(action)
    expect(store.pendingAction).toEqual(action)
    expect(store.hasPendingAction).toBe(true)
  })

  it('closeAuthModal 应关闭弹窗并清空 pendingAction', () => {
    const store = useAuthModalStore()
    store.openAuthModal({ description: '测试', execute: vi.fn() })
    expect(store.showAuthModal).toBe(true)
    expect(store.hasPendingAction).toBe(true)

    store.closeAuthModal()
    expect(store.showAuthModal).toBe(false)
    expect(store.pendingAction).toBeNull()
    expect(store.hasPendingAction).toBe(false)
  })
})

describe('executePendingAction 执行待执行操作', () => {
  it('有待执行操作时应执行 execute 并清空', async () => {
    const store = useAuthModalStore()
    const execute = vi.fn().mockResolvedValue(undefined)
    store.openAuthModal({ description: '加购', execute })

    await store.executePendingAction()

    expect(execute).toHaveBeenCalledTimes(1)
    expect(store.pendingAction).toBeNull()
    expect(store.showAuthModal).toBe(false)
  })

  it('无待执行操作时应直接关闭弹窗', async () => {
    const store = useAuthModalStore()
    store.openAuthModal()

    await store.executePendingAction()

    expect(store.showAuthModal).toBe(false)
  })

  it('execute 抛错时也应清空 pendingAction 并关闭弹窗', async () => {
    const store = useAuthModalStore()
    // 模拟 execute 抛错
    const execute = vi.fn().mockRejectedValue(new Error('执行失败'))
    store.openAuthModal({ description: '测试', execute })

    // executePendingAction 内部 catch 了错误，不会抛出
    await expect(store.executePendingAction()).resolves.toBeUndefined()

    // 即使出错，pendingAction 也应被清空
    expect(store.pendingAction).toBeNull()
    expect(store.showAuthModal).toBe(false)
  })

  it('execute 是同步函数时应正常执行', async () => {
    const store = useAuthModalStore()
    const execute = vi.fn() // 同步函数
    store.openAuthModal({ description: '同步操作', execute })

    await store.executePendingAction()

    expect(execute).toHaveBeenCalledTimes(1)
    expect(store.pendingAction).toBeNull()
  })
})
