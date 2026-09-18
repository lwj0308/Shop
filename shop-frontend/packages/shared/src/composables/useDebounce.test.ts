/**
 * useDebounce 防抖组合式函数测试
 * <p>
 * 验证 loading 状态管理、重复点击拦截、延迟重置、手动 reset。
 * 使用假定时器控制时间。
 * </p>
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useDebounce } from './useDebounce'

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useDebounce 基本功能', () => {
  it('初始loading应为false', () => {
    const { loading } = useDebounce()
    expect(loading.value).toBe(false)
  })

  it('run执行期间loading应为true', async () => {
    const { loading, run } = useDebounce(1000)
    const promise = run(() => Promise.resolve('结果'))
    expect(loading.value).toBe(true)
    await promise
    // 请求完成但延迟未到，loading仍为true
    expect(loading.value).toBe(true)
  })

  it('run执行完成且延迟过后loading应变为false', async () => {
    const { loading, run } = useDebounce(1000)
    await run(() => Promise.resolve('结果'))
    expect(loading.value).toBe(true)

    // 快进1秒
    vi.advanceTimersByTime(1000)
    expect(loading.value).toBe(false)
  })

  it('run应返回异步函数的结果', async () => {
    const { run } = useDebounce()
    const result = await run(() => Promise.resolve(42))
    expect(result).toBe(42)
  })
})

describe('useDebounce 防重复点击', () => {
  it('loading状态下再次run应返回undefined', async () => {
    const { run } = useDebounce(1000)
    // 第一个请求还在进行中
    const promise1 = run(() => Promise.resolve('第一个'))
    // 第二个请求应被拦截
    const result2 = await run(() => Promise.resolve('第二个'))
    expect(result2).toBeUndefined()

    await promise1
  })
})

describe('useDebounce 错误处理', () => {
  it('异步函数抛错时loading也应在延迟后重置', async () => {
    const { loading, run } = useDebounce(1000)
    await expect(run(() => Promise.reject(new Error('失败')))).rejects.toThrow('失败')
    // 报错了，但loading仍为true（延迟未到）
    expect(loading.value).toBe(true)

    vi.advanceTimersByTime(1000)
    expect(loading.value).toBe(false)
  })
})

describe('useDebounce reset 手动重置', () => {
  it('reset应立即解除loading', async () => {
    const { loading, run, reset } = useDebounce(1000)
    await run(() => Promise.resolve('结果'))
    expect(loading.value).toBe(true)

    reset()
    expect(loading.value).toBe(false)
  })

  it('reset后再run应能正常执行', async () => {
    const { run, reset } = useDebounce(1000)
    await run(() => Promise.resolve('第一次'))
    reset()

    const result = await run(() => Promise.resolve('第二次'))
    expect(result).toBe('第二次')
  })
})

describe('useDebounce 自定义延迟时间', () => {
  it('默认延迟应为1000ms', async () => {
    const { loading, run } = useDebounce()
    await run(() => Promise.resolve())
    expect(loading.value).toBe(true)

    vi.advanceTimersByTime(999)
    expect(loading.value).toBe(true)

    vi.advanceTimersByTime(1)
    expect(loading.value).toBe(false)
  })

  it('自定义500ms延迟', async () => {
    const { loading, run } = useDebounce(500)
    await run(() => Promise.resolve())
    expect(loading.value).toBe(true)

    vi.advanceTimersByTime(499)
    expect(loading.value).toBe(true)

    vi.advanceTimersByTime(1)
    expect(loading.value).toBe(false)
  })
})
