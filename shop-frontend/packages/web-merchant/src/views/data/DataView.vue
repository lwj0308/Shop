<template>
  <!-- 数据中心页：日期选择 + 统计卡片 + 图表 + 排行表格 -->
  <div class="data-center" v-loading="loading">
    <!-- 日期范围选择器 -->
    <div class="date-card">
      <span class="date-label">统计周期：</span>
      <el-date-picker
        v-model="startDate"
        type="date"
        placeholder="开始日期"
        value-format="YYYY-MM-DD"
        style="width: 160px;"
      />
      <span class="date-sep">至</span>
      <el-date-picker
        v-model="endDate"
        type="date"
        placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 160px;"
      />
      <el-button type="primary" size="small" @click="loadData">查询</el-button>
      <div class="quick-dates">
        <el-button
          v-for="d in [7, 30, 90]"
          :key="d"
          size="small"
          :type="dateRange === d ? 'primary' : ''"
          @click="handleQuickRange(d)"
        >
          近{{ d }}天
        </el-button>
      </div>
    </div>

    <!-- 统计卡片行 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">销售额</div>
        <div class="stat-value">{{ formatPriceWithSymbol(stats.totalSales) }}</div>
        <div class="stat-change up">↑ 15.3% 较上期</div>
        <div class="stat-icon" style="background: #ecf5ff; color: #409EFF;">💰</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">订单数</div>
        <div class="stat-value">{{ stats.totalOrders }}</div>
        <div class="stat-change up">↑ 9.8% 较上期</div>
        <div class="stat-icon" style="background: #f0f9eb; color: #67C23A;">📋</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">客单价</div>
        <div class="stat-value">{{ formatPriceWithSymbol(stats.avgOrderAmount) }}</div>
        <div class="stat-change up">↑ 5.1% 较上期</div>
        <div class="stat-icon" style="background: #fdf6ec; color: #E6A23C;">💎</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">退款率</div>
        <div class="stat-value">{{ stats.refundRate }}%</div>
        <div class="stat-change down">↓ 1.5% 较上期</div>
        <div class="stat-icon" style="background: #fef0f0; color: #F56C6C;">↩️</div>
      </div>
    </div>

    <!-- 图表 + 排行 -->
    <div class="chart-row">
      <!-- 销售趋势柱状图 -->
      <el-card class="chart-card">
        <template #header>
          <span class="card-title">销售趋势</span>
        </template>
        <div class="bar-chart">
          <div
            v-for="(item, index) in salesTrend"
            :key="index"
            class="bar-item"
          >
            <div class="bar" :style="{ height: `${item.percent}%` }"></div>
            <span class="bar-label">{{ item.date }}</span>
          </div>
        </div>
      </el-card>

      <!-- 商品排行 -->
      <el-card class="rank-card">
        <template #header>
          <span class="card-title">商品销量排行</span>
        </template>
        <el-table :data="productRank" stripe>
          <el-table-column label="排名" width="50">
            <template #default="{ $index }">
              <span :class="['rank-badge', `rank-${$index + 1}`]">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="商品名称" prop="name" min-width="120">
            <template #default="{ row, $index }">
              <span :class="{ 'rank-name-bold': $index < 3 }">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="销量" prop="salesCount" width="80" />
          <el-table-column label="销售额" width="100">
            <template #default="{ row }">
              <span class="rank-amount">{{ formatPriceWithSymbol(row.salesAmount) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 数据中心页
 * 日期选择 + 统计卡片 + 柱状图 + 商品排行
 * 优化点：时间范围切换、图表交互、数据格式化
 */

import { ref, reactive, onMounted } from 'vue'
import { formatPriceWithSymbol, getDataOverview, getProductRank, getSalesTrend } from '@shop/shared'

/** 页面加载中状态，给 v-loading 用 */
const loading = ref(false)

/** 时间范围（天数），用于快捷按钮高亮和销售趋势接口参数 */
const dateRange = ref(30)

/** 开始日期（yyyy-MM-dd） */
const startDate = ref('')

/** 结束日期（yyyy-MM-dd） */
const endDate = ref('')

/** 统计数据（金额单位：分） */
const stats = reactive({
  totalSales: 0,
  totalOrders: 0,
  avgOrderAmount: 0,
  refundRate: 0,
})

/** 销售趋势数据（UI 展示用，percent 是柱状图高度百分比） */
interface SalesTrendItem {
  date: string
  amount: number
  percent: number
}

const salesTrend = ref<SalesTrendItem[]>([])

/** 商品排行数据（UI 展示用，name 对应后端的 productName） */
interface ProductRankItem {
  name: string
  salesCount: number
  salesAmount: number
}

const productRank = ref<ProductRankItem[]>([])

/**
 * 把 Date 对象转成 yyyy-MM-dd 字符串
 * 后端接口要求日期格式为 yyyy-MM-dd
 * @param date - 日期对象
 * @returns 格式化后的日期字符串，如 "2026-06-24"
 */
const formatDateToStr = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 把 yyyy-MM-dd 格式的日期转成简短的 M/D 格式
 * 方便在柱状图下方显示，避免文字太长挤在一起
 * @param dateStr - 日期字符串，如 "2026-06-17"
 * @returns 简短日期，如 "6/17"
 */
const formatDateShort = (dateStr: string): string => {
  const parts = dateStr.split('-')
  if (parts.length !== 3) return dateStr
  return `${parseInt(parts[1], 10)}/${parseInt(parts[2], 10)}`
}

/**
 * 根据天数设置日期范围
 * 比如近7天：开始日期=今天往前推6天，结束日期=今天
 * @param days - 天数（7/30/90）
 */
const setDateRange = (days: number) => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - days + 1)
  startDate.value = formatDateToStr(start)
  endDate.value = formatDateToStr(end)
}

/**
 * 点击快捷按钮（近7/30/90天）时调用
 * 设置对应日期范围后重新加载数据
 * @param days - 天数
 */
const handleQuickRange = (days: number) => {
  dateRange.value = days
  setDateRange(days)
  loadData()
}

/**
 * 加载数据中心数据
 * 并行调用三个接口：概览统计 + 商品排行 + 销售趋势
 * 接口失败时保留默认值，不影响页面展示
 */
const loadData = async () => {
  // 没有完整日期范围时不请求，避免后端报错
  if (!startDate.value || !endDate.value) {
    return
  }

  loading.value = true
  try {
    // 三个接口并行请求，提升加载速度
    const [overviewRes, rankRes, trendRes] = await Promise.all([
      getDataOverview(startDate.value, endDate.value),
      getProductRank(startDate.value, endDate.value, 5),
      getSalesTrend(dateRange.value),
    ])

    // 概览统计：直接赋值后端返回的真实数据
    const overview = overviewRes.data
    stats.totalSales = overview.totalSales
    stats.totalOrders = overview.totalOrders
    stats.avgOrderAmount = overview.avgOrderAmount
    stats.refundRate = overview.refundRate

    // 商品排行：后端字段是 productName，前端展示用 name，需要做一次映射
    productRank.value = rankRes.data.map((item) => ({
      name: item.productName,
      salesCount: item.salesCount,
      salesAmount: item.salesAmount,
    }))

    // 销售趋势：计算每根柱子的高度百分比（以最大值为基准）
    const trendList = trendRes.data
    const maxAmount = trendList.reduce((max, item) => Math.max(max, item.amount), 0)
    salesTrend.value = trendList.map((item) => ({
      date: formatDateShort(item.date),
      amount: item.amount,
      percent: maxAmount > 0 ? Math.round((item.amount / maxAmount) * 100) : 0,
    }))
  } catch (err) {
    // 接口失败时记录错误，保留默认值，页面仍可展示
    console.error('[数据中心] 加载统计数据失败:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 默认加载近30天数据
  setDateRange(dateRange.value)
  loadData()
})
</script>

<style scoped>
.data-center {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 日期选择卡片 */
.date-card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 16px 20px;
  box-shadow: var(--shadow-card);
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.date-label {
  font-weight: 500;
  color: var(--color-text);
}

.date-sep {
  color: var(--color-text-muted);
}

.quick-dates {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

/* 统计卡片行 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 20px;
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
}

.stat-change {
  font-size: 12px;
  margin-top: 8px;
}

.stat-change.up {
  color: var(--color-success);
}

.stat-change.down {
  color: var(--color-danger);
}

.stat-icon {
  position: absolute;
  right: 16px;
  top: 16px;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  opacity: 0.8;
}

/* 图表 + 排行行 */
.chart-row {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

/* 柱状图 */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 200px;
  padding: 0 20px;
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
}

.bar {
  width: 100%;
  max-width: 40px;
  background: linear-gradient(180deg, #409EFF, #66B1FF);
  border-radius: 4px 4px 0 0;
  min-height: 4px;
  transition: height 0.5s ease;
}

.bar:hover {
  opacity: 0.8;
}

.bar-label {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 8px;
}

/* 排行徽章 */
.rank-badge {
  display: inline-flex;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: #c0c4cc;
  color: #fff;
}

.rank-1 { background: #F56C6C; }
.rank-2 { background: #E6A23C; }
.rank-3 { background: #409EFF; }

.rank-name-bold {
  font-weight: 500;
}

.rank-amount {
  color: var(--color-danger);
}
</style>
