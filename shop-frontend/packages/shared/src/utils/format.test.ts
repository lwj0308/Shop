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
  formatPhone,
  formatBankCard,
  formatIdCard,
  formatNumber,
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

describe('formatBankCard 银行卡号脱敏', () => {
  it('16位卡号应只保留前4后4', () => {
    const result = formatBankCard('6222021234567890')
    expect(result).toBe('6222********7890')
  })

  it('不足8位应原样返回', () => {
    expect(formatBankCard('12345')).toBe('12345')
  })
})

describe('formatIdCard 身份证号脱敏', () => {
  it('18位身份证应只保留前3后4', () => {
    const result = formatIdCard('110101199001011234')
    expect(result).toBe('110***********1234')
  })

  it('不足7位应原样返回', () => {
    expect(formatIdCard('12345')).toBe('12345')
  })
})

describe('formatNumber 数字千分位', () => {
  it('大数字应加千分位逗号', () => {
    expect(formatNumber(1234567.89)).toBe('1,234,567.89')
  })

  it('小数字不加逗号', () => {
    expect(formatNumber(999)).toBe('999')
  })
})
