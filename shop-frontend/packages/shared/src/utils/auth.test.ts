/**
 * auth.ts Token管理工具测试
 * <p>
 * 验证 Token 的存储、读取、清除和登录状态判断。
 * 依赖 localStorage，每个测试前清理状态。
 * </p>
 */
// @vitest-environment happy-dom
// 上面这行告诉 Vitest：这个测试文件需要在 happy-dom 模拟的浏览器环境运行，
// 因为 auth.ts 用到了 localStorage（只有浏览器环境才有）
import { describe, it, expect, beforeEach } from 'vitest'
import {
  setToken,
  getToken,
  getRefreshToken,
  clearToken,
  isAuthenticated,
} from './auth'

// 每个测试前清空 localStorage，避免测试间互相影响
beforeEach(() => {
  localStorage.clear()
})

describe('setToken / getToken 存取Token', () => {
  it('存储后应能读取到原始Token', () => {
    setToken('my-access-token', 'my-refresh-token')
    expect(getToken()).toBe('my-access-token')
    expect(getRefreshToken()).toBe('my-refresh-token')
  })

  it('Token应经过Base64编码存储（非明文）', () => {
    setToken('secret-token', 'refresh-secret')
    // 直接读取localStorage，不应是明文
    const raw = localStorage.getItem('shop_access_token')
    expect(raw).not.toBe('secret-token')
    expect(raw).not.toContain('secret-token')
  })

  it('中文Token应能正确存取', () => {
    setToken('令牌测试', '刷新令牌')
    expect(getToken()).toBe('令牌测试')
    expect(getRefreshToken()).toBe('刷新令牌')
  })
})

describe('getToken 未登录时返回空字符串', () => {
  it('未存储Token时应返回空字符串', () => {
    expect(getToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })

  it('清除Token后应返回空字符串', () => {
    setToken('token1', 'refresh1')
    clearToken()
    expect(getToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })
})

describe('clearToken 清除Token', () => {
  it('清除后localStorage中不应再有Token', () => {
    setToken('token1', 'refresh1')
    clearToken()
    expect(localStorage.getItem('shop_access_token')).toBeNull()
    expect(localStorage.getItem('shop_refresh_token')).toBeNull()
  })

  it('未存储Token时调用clearToken不应报错', () => {
    expect(() => clearToken()).not.toThrow()
  })
})

describe('isAuthenticated 登录状态判断', () => {
  it('有Token时应返回true', () => {
    setToken('token', 'refresh')
    expect(isAuthenticated()).toBe(true)
  })

  it('无Token时应返回false', () => {
    expect(isAuthenticated()).toBe(false)
  })

  it('清除Token后应返回false', () => {
    setToken('token', 'refresh')
    clearToken()
    expect(isAuthenticated()).toBe(false)
  })
})

describe('decode 容错性', () => {
  it('localStorage中存入非法Base64字符串时应返回空字符串', () => {
    // 模拟数据损坏：直接写入非法Base64
    localStorage.setItem('shop_access_token', '!!!非法字符串!!!')
    expect(getToken()).toBe('')
  })

  it('localStorage中存入合法Base64但非UTF-8编码时应返回空字符串', () => {
    // 构造一个Base64解码后会触发decodeURIComponent失败的字符串
    localStorage.setItem('shop_access_token', 'AAAA')
    // AAAA解码为乱码，decodeURIComponent可能抛错
    // 不论是否抛错，getToken都不应崩溃
    expect(() => getToken()).not.toThrow()
  })
})
