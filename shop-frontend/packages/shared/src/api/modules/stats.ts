/**
 * 商家统计相关API
 * 提供仪表盘和数据中心所需的统计数据
 */

import { get } from '../request'
import type { DashboardStats, SalesTrendItem, DataOverview, ProductRankItem } from '../../types'

/** 获取仪表盘统计（今日销售额、今日订单数、待发货数） */
export function getDashboardStats() {
  return get<DashboardStats>('/merchant/stats/dashboard')
}

/** 获取销售趋势（最近N天每日销售额） */
export function getSalesTrend(days = 7) {
  return get<SalesTrendItem[]>('/merchant/stats/sales-trend', { days })
}

/** 获取数据中心概览（指定时间范围统计） */
export function getDataOverview(startDate: string, endDate: string) {
  return get<DataOverview>('/merchant/stats/overview', { startDate, endDate })
}

/** 获取商品销量排行 */
export function getProductRank(startDate: string, endDate: string, limit = 5) {
  return get<ProductRankItem[]>('/merchant/stats/product-rank', { startDate, endDate, limit })
}
