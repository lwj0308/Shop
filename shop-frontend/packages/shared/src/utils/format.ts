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
 * 格式化手机号
 * 把手机号中间4位用星号替换，保护隐私
 * @param phone - 手机号，如 "13812345678"
 * @returns 脱敏后的手机号，如 "138****5678"
 */
export function formatPhone(phone: string): string {
  if (phone.length !== 11) return phone
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/**
 * 格式化银行卡号
 * 只保留前4位和后4位，中间用星号替换，保护用户财产安全
 * @param cardNo - 银行卡号，如 "6222021234567890123"
 * @returns 脱敏后的银行卡号，如 "6222********0123"
 */
export function formatBankCard(cardNo: string): string {
  if (cardNo.length < 8) return cardNo
  const firstFour = cardNo.slice(0, 4)
  const lastFour = cardNo.slice(-4)
  const middleStars = '*'.repeat(cardNo.length - 8)
  return `${firstFour}${middleStars}${lastFour}`
}

/**
 * 格式化身份证号
 * 只保留前3位和后4位，中间用星号替换，保护个人隐私
 * @param idCard - 身份证号，如 "110101199001011234"
 * @returns 脱敏后的身份证号，如 "110***********1234"
 */
export function formatIdCard(idCard: string): string {
  if (idCard.length < 7) return idCard
  const firstThree = idCard.slice(0, 3)
  const lastFour = idCard.slice(-4)
  const middleStars = '*'.repeat(idCard.length - 7)
  return `${firstThree}${middleStars}${lastFour}`
}

/**
 * 格式化数字（千分位分隔）
 * 大数字加上逗号分隔，方便阅读
 * @param num - 数字，如 1234567.89
 * @returns 格式化后的字符串，如 "1,234,567.89"
 */
export function formatNumber(num: number): string {
  return num.toLocaleString('zh-CN')
}
