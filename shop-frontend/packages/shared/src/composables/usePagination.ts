/**
 * 分页逻辑组合式函数
 * 把分页相关的状态和操作封装在一起，避免每个列表页都重复写分页逻辑
 * 包括：页码、每页条数、总数、翻页、跳转等
 */

import { ref, computed } from 'vue'
import type { PageParams, PageResult } from '../types'

/** 分页组合式函数的配置选项 */
interface UsePaginationOptions {
  /** 每页条数，默认10 */
  pageSize?: number
}

/**
 * 分页逻辑组合式函数
 * 提供分页状态管理和翻页操作，配合API请求使用
 *
 * 用法示例：
 * const { pagination, totalPages, nextPage, prevPage, resetPage } = usePagination()
 * // 调用API时传入分页参数
 * const res = await getProductList({ pageNum: pagination.value.pageNum, pageSize: pagination.value.pageSize })
 * // 更新总数
 * updateTotal(res.data.total)
 */
export function usePagination(options?: UsePaginationOptions) {
  /** 分页参数（响应式） */
  const pagination = ref<PageParams>({
    pageNum: 1,
    pageSize: options?.pageSize ?? 10,
  })

  /** 总记录数 */
  const total = ref(0)

  /** 总页数（自动计算） */
  const totalPages = computed(() => {
    return Math.ceil(total.value / pagination.value.pageSize) || 1
  })

  /** 是否有下一页 */
  const hasNextPage = computed(() => {
    return pagination.value.pageNum < totalPages.value
  })

  /** 是否有上一页 */
  const hasPrevPage = computed(() => {
    return pagination.value.pageNum > 1
  })

  /**
   * 下一页
   * 如果已经是最后一页，就不会再翻
   */
  const nextPage = (): void => {
    if (hasNextPage.value) {
      pagination.value.pageNum++
    }
  }

  /**
   * 上一页
   * 如果已经是第一页，就不会再翻
   */
  const prevPage = (): void => {
    if (hasPrevPage.value) {
      pagination.value.pageNum--
    }
  }

  /**
   * 跳转到指定页码
   * @param page - 目标页码
   */
  const goToPage = (page: number): void => {
    if (page >= 1 && page <= totalPages.value) {
      pagination.value.pageNum = page
    }
  }

  /**
   * 重置到第一页
   * 切换筛选条件后调用，从第一页重新开始
   */
  const resetPage = (): void => {
    pagination.value.pageNum = 1
  }

  /**
   * 更新总记录数
   * 从API响应中获取总数后调用
   * @param count - 总记录数
   */
  const updateTotal = (count: number): void => {
    total.value = count
  }

  /**
   * 从API分页响应中批量更新分页状态
   * 传入后端返回的PageResult，自动更新total和pageNum
   * @param result - 后端分页响应数据
   */
  const updateFromResult = <T>(result: PageResult<T>): void => {
    total.value = result.total
    pagination.value.pageNum = result.pageNum
    pagination.value.pageSize = result.pageSize
  }

  return {
    pagination,
    total,
    totalPages,
    hasNextPage,
    hasPrevPage,
    nextPage,
    prevPage,
    goToPage,
    resetPage,
    updateTotal,
    updateFromResult,
  }
}
