/**
 * 商家统计相关类型定义
 */

/** 仪表盘统计（今日数据概览） */
export interface DashboardStats {
  /** 今日销售额（分） */
  todaySales: number
  /** 今日订单数 */
  todayOrders: number
  /** 待发货订单数 */
  pendingShip: number
}

/** 销售趋势项 */
export interface SalesTrendItem {
  /** 日期，格式 yyyy-MM-dd */
  date: string
  /** 当日销售额（分） */
  amount: number
}

/** 数据中心概览（指定时间范围统计） */
export interface DataOverview {
  /** 总销售额（分） */
  totalSales: number
  /** 总订单数 */
  totalOrders: number
  /** 客单价（分） */
  avgOrderAmount: number
  /** 退款率（百分比，如 3.2 表示 3.2%） */
  refundRate: number
}

/** 商品销量排行项 */
export interface ProductRankItem {
  /** 商品名称 */
  productName: string
  /** 销量（件数） */
  salesCount: number
  /** 销售额（分） */
  salesAmount: number
}
