/**
 * 按钮防抖组合式函数（RL-08 引入）
 * <p>
 * 作用：防止用户因为网络卡顿或手抖，连续点击"提交"按钮导致重复下单。
 * 原理：按钮点击后立即进入 loading 状态（禁用按钮），请求完成后延迟一段时间再解除 loading，
 *       这样即使请求返回很快，用户也无法在短时间内连续点击第二次。
 * </p>
 * <p>
 * 小白理解：就像电梯按钮，按下后灯亮一会儿，期间再按也没用，等灯灭了才能再按。
 * </p>
 */

import { ref } from 'vue'

/**
 * 创建一个防抖的异步执行器
 * @param delay - 请求完成后的冷却时间（毫秒），默认 1000ms = 1秒
 * @returns loading 状态、run 执行器、reset 手动重置函数
 */
export function useDebounce(delay = 1000) {
  /** 是否正在执行中（true 时按钮应禁用） */
  const loading = ref(false)

  /** 延迟重置定时器ID（用于手动 reset 时清理） */
  let resetTimer: ReturnType<typeof setTimeout> | null = null

  /**
   * 执行一个异步操作，自动管理 loading 状态
   * <p>
   * 1. 如果当前正在 loading，直接返回 undefined（拦截重复点击）
   * 2. 设置 loading=true，执行传入的异步函数
   * 3. 无论成功还是失败，都延迟 delay 毫秒后才设置 loading=false
   * </p>
   *
   * @param fn - 要执行的异步函数（可以有返回值）
   * @returns 异步函数的返回值；如果被防抖拦截则返回 undefined
   */
  const run = async <T>(fn: () => Promise<T>): Promise<T | undefined> => {
    // 正在执行中，拦截重复点击
    if (loading.value) return undefined

    // 清理之前可能存在的延迟重置定时器
    if (resetTimer) {
      clearTimeout(resetTimer)
      resetTimer = null
    }

    loading.value = true
    try {
      return await fn()
    } finally {
      // 延迟重置 loading，防止请求返回后用户立刻又点一次
      resetTimer = setTimeout(() => {
        loading.value = false
        resetTimer = null
      }, delay)
    }
  }

  /**
   * 手动立即重置 loading 状态（跳过延迟）
   * <p>
   * 用于特殊场景：比如秒杀排队时，不希望等 1 秒延迟，需要立即解除 loading
   * 让按钮显示"排队中"而不是"抢购中"。
   * </p>
   */
  const reset = (): void => {
    if (resetTimer) {
      clearTimeout(resetTimer)
      resetTimer = null
    }
    loading.value = false
  }

  return { loading, run, reset }
}
