/**
 * useLoading Loading状态组合式函数测试
 * <p>
 * 验证 loading 状态管理、手动控制、withLoading 自动管理。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import { useLoading } from './useLoading'

describe('useLoading 初始状态', () => {
  it('默认初始状态应为false', () => {
    const { loading } = useLoading()
    expect(loading.value).toBe(false)
  })

  it('可指定初始状态为true', () => {
    const { loading } = useLoading(true)
    expect(loading.value).toBe(true)
  })
})

describe('startLoading / stopLoading 手动控制', () => {
  it('startLoading应设置loading为true', () => {
    const { loading, startLoading } = useLoading()
    startLoading()
    expect(loading.value).toBe(true)
  })

  it('stopLoading应设置loading为false', () => {
    const { loading, startLoading, stopLoading } = useLoading()
    startLoading()
    stopLoading()
    expect(loading.value).toBe(false)
  })
})

describe('withLoading 自动管理（传入函数）', () => {
  it('执行期间loading应为true，完成后为false', async () => {
    const { loading, withLoading } = useLoading()
    const promise = withLoading(() => Promise.resolve('结果'))
    expect(loading.value).toBe(true)
    const result = await promise
    expect(result).toBe('结果')
    expect(loading.value).toBe(false)
  })

  it('任务抛错时loading也应恢复false', async () => {
    const { loading, withLoading } = useLoading()
    await expect(withLoading(() => Promise.reject(new Error('失败')))).rejects.toThrow('失败')
    expect(loading.value).toBe(false)
  })

  it('应返回任务的执行结果', async () => {
    const { withLoading } = useLoading()
    const result = await withLoading(() => Promise.resolve({ data: [1, 2, 3] }))
    expect(result).toEqual({ data: [1, 2, 3] })
  })
})

describe('withLoading 自动管理（传入Promise）', () => {
  it('传入Promise也应正常工作', async () => {
    const { loading, withLoading } = useLoading()
    const promise = withLoading(Promise.resolve(42))
    expect(loading.value).toBe(true)
    const result = await promise
    expect(result).toBe(42)
    expect(loading.value).toBe(false)
  })

  it('传入rejected Promise也应恢复loading', async () => {
    const { loading, withLoading } = useLoading()
    await expect(withLoading(Promise.reject(new Error('失败')))).rejects.toThrow('失败')
    expect(loading.value).toBe(false)
  })
})
