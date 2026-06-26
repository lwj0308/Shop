/**
 * Loading状态组合式函数
 * 提供统一的loading状态管理，避免每个组件都手动写loading变量
 * 支持异步操作自动管理loading状态，也支持手动控制
 */

import { ref } from 'vue'

/**
 * Loading状态组合式函数
 * 用法1：手动控制 - const { loading, startLoading, stopLoading } = useLoading()
 * 用法2：自动管理 - const { loading, withLoading } = useLoading(); await withLoading(fetchData())
 */
export function useLoading(initialState = false) {
  /** 是否正在加载中 */
  const loading = ref(initialState)

  /**
   * 开始加载
   * 调用后loading变为true，通常在发起请求前调用
   */
  const startLoading = (): void => {
    loading.value = true
  }

  /**
   * 结束加载
   * 调用后loading变为false，通常在请求完成后调用
   */
  const stopLoading = (): void => {
    loading.value = false
  }

  /**
   * 自动管理loading的异步操作包装器
   * 传入一个异步函数或Promise，自动在执行期间设置loading=true，完成后设为false
   * 即使异步操作出错，也会正确关闭loading
   * @param task - 异步函数或Promise
   * @returns 异步操作的结果
   */
  async function withLoading<T>(task: Promise<T> | (() => Promise<T>)): Promise<T> {
    loading.value = true
    try {
      const result = typeof task === 'function' ? await task() : await task
      return result
    } finally {
      loading.value = false
    }
  }

  return { loading, startLoading, stopLoading, withLoading }
}
