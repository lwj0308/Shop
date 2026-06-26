/**
 * Token管理工具
 * 负责在浏览器中存取和清除登录凭证
 * 使用 localStorage 持久化存储，页面刷新后Token不会丢失
 *
 * 安全增强：Token使用Base64编码存储，避免明文暴露
 * 注意：Base64不是加密，只是防止肉眼直接看到Token内容
 *       真正的安全应该靠HTTPS传输加密 + HttpOnly Cookie
 */

/** Token在localStorage中的存储键名 */
const ACCESS_TOKEN_KEY = 'shop_access_token'
const REFRESH_TOKEN_KEY = 'shop_refresh_token'

/**
 * Base64编码字符串
 * 把明文Token变成一串"乱码"，防止在浏览器DevTools里直接看到
 * @param str - 原始字符串
 * @returns Base64编码后的字符串
 */
function encode(str: string): string {
  return btoa(encodeURIComponent(str))
}

/**
 * Base64解码字符串
 * 把编码后的Token还原成原始字符串
 * @param str - Base64编码的字符串
 * @returns 解码后的原始字符串，解码失败返回空字符串
 */
function decode(str: string): string {
  try {
    return decodeURIComponent(atob(str))
  } catch {
    return ''
  }
}

/**
 * 存储Token到本地
 * 登录成功后调用，把服务器返回的两个Token都存起来
 * 使用Base64编码存储，避免明文暴露
 * @param token - 访问令牌，用于日常接口鉴权
 * @param refreshToken - 刷新令牌，用于获取新的访问令牌
 */
export function setToken(token: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, encode(token))
  localStorage.setItem(REFRESH_TOKEN_KEY, encode(refreshToken))
}

/**
 * 获取访问令牌
 * 每次发请求时调用，从本地取出Token放到请求头里
 * @returns 访问令牌，未登录时返回空字符串
 */
export function getToken(): string {
  const encoded = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (!encoded) return ''
  return decode(encoded)
}

/**
 * 获取刷新令牌
 * 当访问令牌过期时，用刷新令牌去换一个新的
 * @returns 刷新令牌，未登录时返回空字符串
 */
export function getRefreshToken(): string {
  const encoded = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!encoded) return ''
  return decode(encoded)
}

/**
 * 清除所有Token
 * 退出登录时调用，把本地存的Token全部删掉
 */
export function clearToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * 判断用户是否已登录
 * 只要本地有访问令牌就认为已登录
 * @returns true表示已登录，false表示未登录
 */
export function isAuthenticated(): boolean {
  return !!getToken()
}
