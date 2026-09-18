/**
 * 格式化工具函数
 * 提供价格、日期等常用格式化方法
 */

/**
 * 格式化价格（分 → 元）
 * 后端存储的价格单位是"分"（整数，避免浮点数精度问题），
 * 前端展示时需要转换成"元"，保留两位小数
 * @param price - 价格，单位：分
 * @returns 格式化后的价格字符串，如 "99.00"
 */
export function formatPrice(price: number): string {
  return (price / 100).toFixed(2)
}

/**
 * 格式化价格（带人民币符号）
 * 在价格前面加上"¥"符号，用于页面展示
 * @param price - 价格，单位：分
 * @returns 带符号的价格字符串，如 "¥99.00"
 */
export function formatPriceWithSymbol(price: number): string {
  return `¥${formatPrice(price)}`
}

/**
 * 格式化日期
 * 把日期字符串转成更友好的中文格式
 * @param dateStr - 日期字符串，如 "2024-01-15T10:30:00"
 * @param format - 格式类型，默认完整格式
 * @returns 格式化后的日期字符串
 */
export function formatDate(
  dateStr: string,
  format: 'full' | 'date' | 'time' = 'full',
): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  switch (format) {
    case 'date':
      return `${year}-${month}-${day}`
    case 'time':
      return `${hours}:${minutes}:${seconds}`
    default:
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  }
}

/**
 * 格式化日期时间（简短格式，纯字符串截取）
 * 后端返回 "2026-06-25T10:00:00" 这类 ISO 字串，列表页只展示到分钟
 * 注意：不做时区换算，只是把 T 换成空格并截断，和原 formatDate 语义不同
 * @param time - 后端时间字符串，如 "2026-06-25T10:00:00"
 * @returns 形如 "2026-06-25 10:00" 的字符串，入参为空时返回空串
 */
export function formatDateTimeShort(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

/**
 * 格式化手机号
 * 把手机号中间4位用星号替换，保护隐私
 * @param phone - 手机号，如 "13812345678"
 * @returns 脱敏后的手机号，如 "138****5678"
 */
export function formatPhone(phone: string): string {
  if (phone.length !== 11) return phone
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}
