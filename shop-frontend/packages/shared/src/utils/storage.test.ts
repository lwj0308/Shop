/**
 * storage.ts localStorage封装测试
 * <p>
 * 验证 setStorage/getStorage/removeStorage 三个函数，
 * 重点测试过期机制和JSON解析容错。
 * </p>
 */
// @vitest-environment happy-dom
// 上面这行告诉 Vitest：这个测试文件需要在 happy-dom 模拟的浏览器环境运行，
// 因为 storage.ts 用到了 localStorage（只有浏览器环境才有）
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setStorage, getStorage, removeStorage } from './storage'

// 每个测试前清空 localStorage
beforeEach(() => {
  localStorage.clear()
})

// 恢复被 mock 的 Date.now
afterEach(() => {
  vi.useRealTimers()
})

describe('setStorage / getStorage 基本存取', () => {
  it('存储字符串应能读取', () => {
    setStorage('name', '张三')
    expect(getStorage<string>('name')).toBe('张三')
  })

  it('存储数字应能读取', () => {
    setStorage('age', 25)
    expect(getStorage<number>('age')).toBe(25)
  })

  it('存储对象应能读取', () => {
    const user = { id: 1, name: '李四', roles: ['admin'] }
    setStorage('user', user)
    expect(getStorage<typeof user>('user')).toEqual(user)
  })

  it('存储数组应能读取', () => {
    const list = [1, 2, 3]
    setStorage('list', list)
    expect(getStorage<number[]>('list')).toEqual(list)
  })

  it('存储布尔值应能读取', () => {
    setStorage('flag', true)
    expect(getStorage<boolean>('flag')).toBe(true)
  })

  it('存储null值应能读取', () => {
    setStorage('nullval', null)
    expect(getStorage<null>('nullval')).toBeNull()
  })
})

describe('getStorage 不存在的key', () => {
  it('key不存在时应返回null', () => {
    expect(getStorage('not-exist')).toBeNull()
  })

  it('删除后应返回null', () => {
    setStorage('temp', 'data')
    removeStorage('temp')
    expect(getStorage('temp')).toBeNull()
  })
})

describe('setStorage 过期机制', () => {
  it('未设置过期时间应永不过期', () => {
    setStorage('permanent', 'value')
    expect(getStorage('permanent')).toBe('value')
  })

  it('未过期时应正常返回数据', () => {
    // 设置1000ms后过期
    setStorage('temp', 'data', 1000)
    expect(getStorage('temp')).toBe('data')
  })

  it('过期后应返回null并自动清理', () => {
    // 使用假定时器控制时间
    vi.useFakeTimers()
    const now = Date.now()
    vi.setSystemTime(now)

    // 设置100ms后过期
    setStorage('temp', 'data', 100)

    // 时间快进200ms，数据应已过期
    vi.setSystemTime(now + 200)
    expect(getStorage('temp')).toBeNull()

    // 过期数据应被自动清理
    expect(localStorage.getItem('temp')).toBeNull()
  })
})

describe('getStorage JSON解析容错', () => {
  it('localStorage中存入非JSON字符串时应原样返回', () => {
    // 直接写入原始字符串（绕过setStorage的JSON序列化）
    localStorage.setItem('raw', '这不是JSON')
    expect(getStorage<string>('raw')).toBe('这不是JSON')
  })

  it('localStorage中存入空字符串时应返回null', () => {
    // 注意：getStorage 内部用 `if (!item) return null` 短路判断，
    // 空字符串会被 ! 转为 true，所以会直接返回 null，不会走到 JSON.parse 的 catch
    localStorage.setItem('empty', '')
    expect(getStorage<string>('empty')).toBeNull()
  })
})

describe('removeStorage 删除数据', () => {
  it('应删除指定key的数据', () => {
    setStorage('a', '1')
    setStorage('b', '2')
    removeStorage('a')
    expect(getStorage('a')).toBeNull()
    expect(getStorage('b')).toBe('2')
  })

  it('删除不存在的key不应报错', () => {
    expect(() => removeStorage('not-exist')).not.toThrow()
  })
})
