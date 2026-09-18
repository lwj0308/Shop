/**
 * security.ts 安全工具函数测试
 * <p>
 * 验证 XSS 过滤、金额校验、HTML 转义等安全函数。
 * 这是 web-merchant 端的示例测试，验证 Vitest 环境可用。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import { sanitizeHtml, sanitizeAmount, escapeHtml } from './security'

describe('sanitizeHtml XSS过滤', () => {
  it('应移除script标签', () => {
    const input = '<script>alert("xss")</script>正常文本'
    expect(sanitizeHtml(input)).toBe('正常文本')
  })

  it('应移除iframe标签', () => {
    const input = '<iframe src="evil.com"></iframe>正常文本'
    expect(sanitizeHtml(input)).toBe('正常文本')
  })

  it('应移除事件属性onclick', () => {
    const input = '<div onclick="alert(1)">文本</div>'
    const result = sanitizeHtml(input)
    expect(result).not.toContain('onclick')
  })

  it('应移除javascript:协议', () => {
    const input = '<a href="javascript:alert(1)">链接</a>'
    expect(sanitizeHtml(input)).not.toContain('javascript:')
  })

  it('空字符串应返回空', () => {
    expect(sanitizeHtml('')).toBe('')
  })
})

describe('sanitizeAmount 金额校验', () => {
  it('正常金额应原样返回', () => {
    expect(sanitizeAmount('99.50')).toBe('99.50')
  })

  it('应移除非数字字符', () => {
    expect(sanitizeAmount('abc123.45def')).toBe('123.45')
  })

  it('只保留第一个小数点', () => {
    expect(sanitizeAmount('12.34.56')).toBe('12.3456')
  })

  it('小数点后最多两位', () => {
    expect(sanitizeAmount('99.999')).toBe('99.99')
  })

  it('空字符串应返回空', () => {
    expect(sanitizeAmount('')).toBe('')
  })
})

describe('escapeHtml HTML转义', () => {
  it('应转义尖括号', () => {
    expect(escapeHtml('<div>')).toBe('&lt;div&gt;')
  })

  it('应转义引号', () => {
    expect(escapeHtml('"hello"')).toBe('&quot;hello&quot;')
  })

  it('应转义&符号', () => {
    expect(escapeHtml('a & b')).toBe('a &amp; b')
  })

  it('空字符串应返回空', () => {
    expect(escapeHtml('')).toBe('')
  })

  it('无特殊字符应原样返回', () => {
    expect(escapeHtml('普通文本123')).toBe('普通文本123')
  })
})
