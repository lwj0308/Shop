/**
 * 安全相关工具函数
 * 提供XSS过滤、金额输入校验等安全方法
 */

/**
 * XSS过滤：去除HTML危险标签
 * 把用户输入中可能包含的恶意脚本标签（script、iframe等）过滤掉
 * 防止跨站脚本攻击（XSS）
 * @param str - 需要过滤的字符串
 * @returns 过滤后的安全字符串
 */
export function sanitizeHtml(str: string): string {
  if (!str) return ''
  return str
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '') // 移除script标签
    .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '') // 移除iframe标签
    .replace(/<object\b[^<]*(?:(?!<\/object>)<[^<]*)*<\/object>/gi, '') // 移除object标签
    .replace(/<embed\b[^>]*>/gi, '') // 移除embed标签
    .replace(/on\w+\s*=\s*"[^"]*"/gi, '') // 移除事件属性 onclick="..."
    .replace(/on\w+\s*=\s*'[^']*'/gi, '') // 移除事件属性 onclick='...'
    .replace(/javascript:/gi, '') // 移除javascript:协议
}

/**
 * 富文本内容安全过滤
 * 只允许安全的HTML标签和属性通过
 * @param html - 富文本HTML内容
 * @returns 过滤后的安全HTML
 */
export function sanitizeRichText(html: string): string {
  if (!html) return ''
  // 允许的安全标签
  const allowedTags = ['p', 'br', 'b', 'i', 'em', 'strong', 'u', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'span', 'div', 'img', 'a']
  // 允许的安全属性
  const allowedAttrs = ['href', 'src', 'alt', 'title', 'class', 'style', 'target']

  // 先做基础XSS过滤
  let result = sanitizeHtml(html)

  // 移除不允许的标签（保留内容）
  const tagRegex = /<\/?([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>/g
  result = result.replace(tagRegex, (match, tagName) => {
    if (allowedTags.includes(tagName.toLowerCase())) {
      // 对于允许的标签，过滤不允许的属性
      return match.replace(/\s([a-zA-Z][a-zA-Z0-9-]*)\s*=\s*["'][^"']*["']/g, (attrMatch, attrName) => {
        return allowedAttrs.includes(attrName.toLowerCase()) ? attrMatch : ''
      })
    }
    return '' // 不允许的标签直接移除
  })

  return result
}

/**
 * 金额输入校验
 * 只允许输入数字和小数点，且小数点后最多两位
 * @param value - 输入的金额字符串
 * @returns 校验后的合法金额字符串
 */
export function sanitizeAmount(value: string): string {
  if (!value) return ''
  // 移除非数字和小数点
  let result = value.replace(/[^\d.]/g, '')
  // 只保留第一个小数点
  const parts = result.split('.')
  if (parts.length > 2) {
    result = parts[0] + '.' + parts.slice(1).join('')
  }
  // 小数点后最多两位
  if (parts.length === 2 && parts[1].length > 2) {
    result = parts[0] + '.' + parts[1].slice(0, 2)
  }
  return result
}

/**
 * 转义HTML特殊字符
 * 把 < > & " ' 这些特殊字符转义为HTML实体
 * 防止在页面上渲染用户输入时执行恶意代码
 * @param str - 需要转义的字符串
 * @returns 转义后的安全字符串
 */
export function escapeHtml(str: string): string {
  if (!str) return ''
  const escapeMap: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }
  return str.replace(/[&<>"']/g, (char) => escapeMap[char] || char)
}
