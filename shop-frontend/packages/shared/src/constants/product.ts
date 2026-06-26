/**
 * 商品状态常量
 * 定义商品上下架等状态码
 */

/** 商品状态枚举 */
export const PRODUCT_STATUS = {
  /** 下架 */
  OFF_SHELF: 0,
  /** 上架 */
  ON_SHELF: 1,
} as const

/** 商品状态描述映射 */
export const PRODUCT_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '已下架', color: '#909399' },
  1: { label: '已上架', color: '#67C23A' },
}
