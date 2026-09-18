/**
 * validate.ts 校验工具函数测试
 * <p>
 * 验证手机号、密码、验证码、邮箱、身份证、URL、中文姓名等校验函数。
 * 全部为纯函数，使用表驱动测试（test.each）批量验证。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import {
  isValidPhone,
  isValidPassword,
  isValidVerifyCode,
  isValidEmail,
  isValidIdCard,
  isValidUrl,
  isValidChineseName,
} from './validate'

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

describe('isValidVerifyCode 验证码校验', () => {
  it.each([
    ['123456', true],
    ['000000', true],
    ['12345', false],   // 5位
    ['1234567', false], // 7位
    ['abcdef', false],  // 含字母
    ['', false],
  ])('验证码 "%s" 应返回 %s', (input, expected) => {
    expect(isValidVerifyCode(input)).toBe(expected)
  })
})

describe('isValidEmail 邮箱校验', () => {
  it.each([
    ['test@example.com', true],
    ['user.name@domain.org', true],
    ['a@b.co', true],
    ['test', false],          // 无@符号
    ['test@', false],         // @后无域名
    ['@example.com', false],  // @前无用户名
    ['test@com', false],      // 无点号
    ['', false],
  ])('邮箱 "%s" 应返回 %s', (input, expected) => {
    expect(isValidEmail(input)).toBe(expected)
  })
})

describe('isValidIdCard 身份证号校验', () => {
  it('合法18位身份证号应通过', () => {
    // 这是一个通过 GB 11643-1999 校验码算法验证的合法身份证号
    // 前17位 11010119900307653 算出的校验码是 '6'
    expect(isValidIdCard('110101199003076536')).toBe(true)
  })

  it('校验码错误应不通过', () => {
    // 故意写错最后一位，校验码不匹配
    expect(isValidIdCard('110101199003076530')).toBe(false)
  })

  it('17位数字应不通过', () => {
    expect(isValidIdCard('1101011990030765')).toBe(false)
  })

  it('19位数字应不通过', () => {
    expect(isValidIdCard('110101199003076530X1')).toBe(false)
  })

  it('含字母的非法格式应不通过', () => {
    expect(isValidIdCard('abcdefghij03076530X')).toBe(false)
  })

  it('空字符串应不通过', () => {
    expect(isValidIdCard('')).toBe(false)
  })
})

describe('isValidUrl URL校验', () => {
  it.each([
    ['http://example.com', true],
    ['https://example.com', true],
    ['https://example.com/path?query=1', true],
    ['http://localhost:3000', true],
  ])('合法URL "%s" 应返回 true', (input, expected) => {
    expect(isValidUrl(input)).toBe(expected)
  })

  it.each([
    ['javascript:alert(1)', false],  // 危险协议
    ['ftp://example.com', false],    // 非http协议
    ['not-a-url', false],            // 非URL格式
    ['', false],                     // 空字符串
  ])('非法URL "%s" 应返回 false', (input, expected) => {
    expect(isValidUrl(input)).toBe(expected)
  })
})

describe('isValidChineseName 中文姓名校验', () => {
  it.each([
    ['张三', true],
    ['欧阳娜娜', true],
    ['买买提·吐尔逊', true],  // 少数民族姓名含·符号
    ['李', false],            // 只有1个字
    ['张三李四王五赵六钱七孙八周九吴十郑十一', true], // 19字，在2-20范围内
    ['张三李四王五赵六钱七孙八周九吴十郑十一二二二二', false], // 23字超过20字上限
    ['Zhang San', false],     // 英文
    ['张三1', false],         // 含数字
    ['', false],
  ])('姓名 "%s" 应返回 %s', (input, expected) => {
    expect(isValidChineseName(input)).toBe(expected)
  })
})
