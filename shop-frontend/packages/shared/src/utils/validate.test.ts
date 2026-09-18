/**
 * validate.ts 校验工具函数测试
 * <p>
 * 验证手机号、密码校验函数。
 * 全部为纯函数，使用表驱动测试（test.each）批量验证。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import { isValidPhone, isValidPassword } from './validate'

describe('isValidPhone 手机号校验', () => {
  it.each([
    ['13812345678', true],
    ['15912345678', true],
    ['18612345678', true],
    ['19912345678', true],
    ['12012345678', false],  // 第二位是2，不合法
    ['12345678901', false],  // 第二位是2，不合法
    ['1381234567', false],   // 只有10位
    ['138123456789', false], // 12位
    ['abc12345678', false],  // 含字母
    ['', false],             // 空字符串
  ])('手机号 "%s" 应返回 %s', (input, expected) => {
    expect(isValidPhone(input)).toBe(expected)
  })
})

describe('isValidPassword 密码校验', () => {
  it.each([
    ['abc123', true],             // 字母+数字，6位
    ['Password1', true],          // 字母+数字
    ['abc123456789012345', true], // 18位（abc + 15位数字）
    ['abc123456789012345678', false], // 21位超长（超过20位上限）
    ['abcde', false],             // 纯字母，无数字
    ['123456', false],            // 纯数字，无字母
    ['ab1', false],               // 不足6位
    ['abc@123', true],            // 含特殊字符
    ['', false],                  // 空字符串
  ])('密码 "%s" 应返回 %s', (input, expected) => {
    expect(isValidPassword(input)).toBe(expected)
  })
})
