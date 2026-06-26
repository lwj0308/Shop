/**
 * 校验工具函数
 * 提供常用的表单校验方法
 */

/**
 * 校验手机号格式
 * 中国大陆手机号规则：1开头，第二位是3-9，共11位数字
 * @param phone - 手机号字符串
 * @returns true表示格式正确
 */
export function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

/**
 * 校验密码强度
 * 密码要求：6-20位，必须包含字母和数字
 * @param password - 密码字符串
 * @returns true表示密码符合要求
 */
export function isValidPassword(password: string): boolean {
  return /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,20}$/.test(password)
}

/**
 * 校验验证码格式
 * 验证码要求：6位纯数字
 * @param code - 验证码字符串
 * @returns true表示格式正确
 */
export function isValidVerifyCode(code: string): boolean {
  return /^\d{6}$/.test(code)
}

/**
 * 校验邮箱格式
 * @param email - 邮箱字符串
 * @returns true表示格式正确
 */
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

/**
 * 校验身份证号格式
 * 支持18位身份证号校验，包含最后一位校验码验证
 * 18位身份证 = 6位地区码 + 8位生日 + 3位顺序码 + 1位校验码
 * @param idCard - 身份证号字符串
 * @returns true表示格式正确
 */
export function isValidIdCard(idCard: string): boolean {
  // 18位身份证号基本格式校验
  if (!/^\d{17}[\dXx]$/.test(idCard)) return false

  // 校验码验证（根据GB 11643-1999标准）
  const weightFactors = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const checkCodes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']
  let sum = 0
  for (let i = 0; i < 17; i++) {
    sum += parseInt(idCard[i], 10) * weightFactors[i]
  }
  const checkCode = checkCodes[sum % 11]
  return idCard[17].toUpperCase() === checkCode
}

/**
 * 校验URL格式
 * 只允许http和https协议，防止javascript:等危险协议
 * @param url - URL字符串
 * @returns true表示格式正确且安全
 */
export function isValidUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    // 只允许http和https协议，防止XSS攻击
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

/**
 * 校验中文姓名
 * 2-20个中文字符，支持少数民族姓名中的·符号
 * @param name - 姓名字符串
 * @returns true表示格式正确
 */
export function isValidChineseName(name: string): boolean {
  return /^[\u4e00-\u9fa5·]{2,20}$/.test(name)
}
