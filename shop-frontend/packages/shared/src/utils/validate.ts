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
