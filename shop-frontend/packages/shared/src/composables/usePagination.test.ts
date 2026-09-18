/**
 * usePagination 分页组合式函数测试
 * <p>
 * 验证分页状态管理、翻页边界保护、跳转校验、updateFromResult 同步。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import { usePagination } from './usePagination'

describe('usePagination 初始状态', () => {
  it('默认页码为1，每页10条', () => {
    const { pagination, total, totalPages } = usePagination()
    expect(pagination.value.pageNum).toBe(1)
    expect(pagination.value.pageSize).toBe(10)
    expect(total.value).toBe(0)
    expect(totalPages.value).toBe(1) // 0条数据时总页数为1（兜底）
  })

  it('可自定义每页条数', () => {
    const { pagination } = usePagination({ pageSize: 20 })
    expect(pagination.value.pageSize).toBe(20)
  })
})

describe('totalPages 总页数计算', () => {
  it('0条数据总页数应为1（兜底）', () => {
    const { totalPages } = usePagination()
    expect(totalPages.value).toBe(1)
  })

  it('25条数据每页10条应为3页', () => {
    const { total, totalPages, updateTotal } = usePagination()
    updateTotal(25)
    expect(totalPages.value).toBe(3)
  })

  it('20条数据每页10条应为2页', () => {
    const { totalPages, updateTotal } = usePagination()
    updateTotal(20)
    expect(totalPages.value).toBe(2)
  })

  it('自定义pageSize时总页数应正确计算', () => {
    const { totalPages, updateTotal } = usePagination({ pageSize: 20 })
    updateTotal(50)
    expect(totalPages.value).toBe(3)
  })
})

describe('nextPage / prevPage 翻页', () => {
  it('nextPage应页码加1', () => {
    const { nextPage, pagination, updateTotal } = usePagination()
    updateTotal(100) // 10页
    nextPage()
    expect(pagination.value.pageNum).toBe(2)
  })

  it('最后一页时nextPage不应再翻', () => {
    const { nextPage, pagination, updateTotal, goToPage } = usePagination()
    updateTotal(20) // 2页
    goToPage(2)
    nextPage()
    expect(pagination.value.pageNum).toBe(2)
  })

  it('prevPage应页码减1', () => {
    const { prevPage, pagination, updateTotal, goToPage } = usePagination()
    updateTotal(100)
    goToPage(3)
    prevPage()
    expect(pagination.value.pageNum).toBe(2)
  })

  it('第一页时prevPage不应再翻', () => {
    const { prevPage, pagination } = usePagination()
    prevPage()
    expect(pagination.value.pageNum).toBe(1)
  })
})

describe('hasNextPage / hasPrevPage', () => {
  it('第一页有数据时hasNextPage应为true', () => {
    const { hasNextPage, hasPrevPage, updateTotal } = usePagination()
    updateTotal(100)
    expect(hasNextPage.value).toBe(true)
    expect(hasPrevPage.value).toBe(false)
  })

  it('最后一页时hasNextPage应为false', () => {
    const { hasNextPage, updateTotal, goToPage } = usePagination()
    updateTotal(20) // 2页
    goToPage(2)
    expect(hasNextPage.value).toBe(false)
  })

  it('只有1页时hasNextPage和hasPrevPage都为false', () => {
    const { hasNextPage, hasPrevPage, updateTotal } = usePagination()
    updateTotal(5) // 1页
    expect(hasNextPage.value).toBe(false)
    expect(hasPrevPage.value).toBe(false)
  })
})

describe('goToPage 跳转指定页', () => {
  it('跳转到合法页码应成功', () => {
    const { goToPage, pagination, updateTotal } = usePagination()
    updateTotal(100) // 10页
    goToPage(5)
    expect(pagination.value.pageNum).toBe(5)
  })

  it('跳转到第0页应被忽略', () => {
    const { goToPage, pagination, updateTotal } = usePagination()
    updateTotal(100)
    goToPage(0)
    expect(pagination.value.pageNum).toBe(1)
  })

  it('跳转超出总页数应被忽略', () => {
    const { goToPage, pagination, updateTotal } = usePagination()
    updateTotal(20) // 2页
    goToPage(10)
    expect(pagination.value.pageNum).toBe(1)
  })

  it('跳转负数页码应被忽略', () => {
    const { goToPage, pagination, updateTotal } = usePagination()
    updateTotal(100)
    goToPage(-1)
    expect(pagination.value.pageNum).toBe(1)
  })
})

describe('resetPage 重置页码', () => {
  it('应将页码重置为1', () => {
    const { resetPage, pagination, updateTotal, goToPage } = usePagination()
    updateTotal(100)
    goToPage(5)
    resetPage()
    expect(pagination.value.pageNum).toBe(1)
  })
})

describe('updateFromResult 从API结果同步', () => {
  it('应同步total、pageNum、pageSize', () => {
    const { pagination, total, updateFromResult } = usePagination()
    updateFromResult({
      records: [],
      total: 55,
      pageNum: 3,
      pageSize: 20,
      pages: 3,
    })
    expect(total.value).toBe(55)
    expect(pagination.value.pageNum).toBe(3)
    expect(pagination.value.pageSize).toBe(20)
  })
})
