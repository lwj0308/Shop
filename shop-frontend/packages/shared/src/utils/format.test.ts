/**
 * format.ts 工具函数测试
 * <p>
 * 验证价格、日期、手机号等格式化函数的正确性。
 * 这是 shared 包的示例测试，验证 Vitest 环境可用。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import {
  formatPrice,
  formatPriceWithSymbol,
  formatDate,
  formatDateTimeShort,
  formatPhone,
} from './format'

describe('formatPrice 价格格式化（分→元）', () => {
  it('正常价格：9900分应转为99.00元', () => {
    expect(formatPrice(9900)).toBe('99.00')
  })

  it('零价格：0分应转为0.00元', () => {
    expect(formatPrice(0)).toBe('0.00')
  })

  it('小数价格：1分应转为0.01元', () => {
    expect(formatPrice(1)).toBe('0.01')
  })
})

describe('formatPriceWithSymbol 带符号价格', () => {
  it('9900分应转为¥99.00', () => {
    expect(formatPriceWithSymbol(9900)).toBe('¥99.00')
  })
})

describe('formatDate 日期格式化', () => {
  const testDate = '2024-01-15T10:30:45'

  it('full格式应返回完整日期时间', () => {
    expect(formatDate(testDate)).toBe('2024-01-15 10:30:45')
  })

  it('date格式应只返回日期部分', () => {
    expect(formatDate(testDate, 'date')).toBe('2024-01-15')
  })

  it('time格式应只返回时间部分', () => {
    expect(formatDate(testDate, 'time')).toBe('10:30:45')
  })
})

describe('formatPhone 手机号脱敏', () => {
  it('11位手机号应隐藏中间4位', () => {
    expect(formatPhone('13812345678')).toBe('138****5678')
  })

  it('非11位手机号应原样返回', () => {
    expect(formatPhone('12345')).toBe('12345')
  })
})

describe('formatDateTimeShort 日期时间截取到分钟', () => {
  it('应把 T 替换为空格并截取到分钟', () => {
    expect(formatDateTimeShort('2026-06-25T10:00:00')).toBe('2026-06-25 10:00')
  })

  it('空值应返回空字符串', () => {
    expect(formatDateTimeShort('')).toBe('')
  })
})
