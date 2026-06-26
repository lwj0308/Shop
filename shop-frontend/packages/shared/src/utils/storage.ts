/**
 * localStorage封装
 * 对原生localStorage做了一层封装，自动处理JSON序列化和反序列化
 * 还支持设置过期时间，避免数据永久有效
 */

/**
 * 存储数据到localStorage
 * 自动把对象转成JSON字符串存储
 * @param key - 存储的键名
 * @param value - 存储的值，可以是任意类型
 * @param expireMs - 可选，过期时间（毫秒），不传则永不过期
 */
export function setStorage<T>(key: string, value: T, expireMs?: number): void {
  const data = {
    value,
    expire: expireMs ? Date.now() + expireMs : null,
  }
  localStorage.setItem(key, JSON.stringify(data))
}

/**
 * 从localStorage获取数据
 * 自动把JSON字符串转回对象，并检查是否过期
 * @param key - 存储的键名
 * @returns 存储的值，如果不存在或已过期则返回null
 */
export function getStorage<T>(key: string): T | null {
  const item = localStorage.getItem(key)
  if (!item) return null

  try {
    const data = JSON.parse(item)
    // 检查是否过期
    if (data.expire && Date.now() > data.expire) {
      localStorage.removeItem(key)
      return null
    }
    return data.value as T
  } catch {
    // JSON解析失败，直接返回原始值
    return item as unknown as T
  }
}

/**
 * 从localStorage删除数据
 * @param key - 要删除的键名
 */
export function removeStorage(key: string): void {
  localStorage.removeItem(key)
}

/**
 * 清空localStorage中所有数据
 * 退出登录时可以调用，清除所有本地缓存
 */
export function clearStorage(): void {
  localStorage.clear()
}
