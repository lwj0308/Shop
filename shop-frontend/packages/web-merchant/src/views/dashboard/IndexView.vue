<template>
  <!-- 工作台/仪表盘：数据卡片 + 趋势图 + 待办事项 + 最近订单 -->
  <div class="dashboard-page" v-loading="loading">
    <!-- 数据概览卡片行：4列网格 -->
    <div class="stat-cards">
      <!-- 今日销售额 -->
      <div class="stat-card" @click="goTo('/order/list')">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value">{{ formatPriceWithSymbol(stats.todaySales) }}</div>
        <div class="stat-change up">↑ 12.5% 较昨日</div>
        <div class="stat-icon">💰</div>
      </div>
      <!-- 今日订单数 -->
      <div class="stat-card" @click="goTo('/order/list')">
        <div class="stat-label">今日订单数</div>
        <div class="stat-value">{{ stats.todayOrders }}</div>
        <div class="stat-change up">↑ 8.2% 较昨日</div>
        <div class="stat-icon">📋</div>
      </div>
      <!-- 待发货订单 -->
      <div class="stat-card" @click="goTo('/order/list')">
        <div class="stat-label">待发货订单</div>
        <div class="stat-value">{{ stats.pendingShip }}</div>
        <div class="stat-change down">↓ 3.1% 较昨日</div>
        <div class="stat-icon">🚚</div>
      </div>
      <!-- 商品总数 -->
      <div class="stat-card" @click="goTo('/product/list')">
        <div class="stat-label">商品总数</div>
        <div class="stat-value">{{ stats.totalProducts }}</div>
        <div class="stat-change up">↑ 2 新增</div>
        <div class="stat-icon">📦</div>
      </div>
    </div>

    <!-- 第二行：销售趋势图 + 待办事项 -->
    <div class="second-row">
      <!-- 销售趋势图 -->
      <el-card class="chart-card">
        <template #header>
          <div class="card-header">
            <span>近7日销售趋势</span>
          </div>
        </template>
        <div class="chart-placeholder">
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
        </div>
      </el-card>

      <!-- 待办事项 -->
      <el-card class="todo-card">
        <template #header>
          <div class="card-header">
            <span>待办事项</span>
          </div>
        </template>
        <div class="todo-list">
          <div class="todo-item" style="background: rgba(245,158,11,0.1);" @click="goTo('/order/list')">
            <span class="todo-label">待发货订单</span>
            <span class="todo-count" style="color: var(--color-warning);">12</span>
          </div>
          <div class="todo-item" style="background: rgba(239,68,68,0.1);" @click="goTo('/order/list')">
            <span class="todo-label">退款待处理</span>
            <span class="todo-count" style="color: var(--color-danger);">3</span>
          </div>
          <div class="todo-item" style="background: rgba(59,130,246,0.1);" @click="goTo('/product/list')">
            <span class="todo-label">库存预警</span>
            <span class="todo-count" style="color: var(--color-info);">5</span>
          </div>
          <div class="todo-item" style="background: rgba(34,197,94,0.1);" @click="goTo('/data')">
            <span class="todo-label">新评价待回复</span>
            <span class="todo-count" style="color: var(--color-success);">8</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 最近订单 -->
    <el-card class="recent-orders">
      <template #header>
        <div class="card-header">
          <span>最近订单</span>
          <el-button text type="primary" @click="goTo('/order/list')">查看全部 →</el-button>
        </div>
      </template>
      <el-table :data="recentOrders" stripe>
        <el-table-column label="订单号" width="180">
          <template #default="{ row }">
            <span class="order-no" @click="goTo(`/order/${row.id}`)">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品信息" min-width="200">
          <template #default="{ row }">
            <span>{{ row.productName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="amount">{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.statusType" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180" prop="time" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 工作台页面
 * 展示今日销售额/订单数/待发货/商品总数 + 销售趋势 + 待办事项 + 最近订单
 * 优化点：数据卡片点击跳转对应页面、待办事项交互、趋势图展示
 */

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatPriceWithSymbol, getDashboardStats, getSalesTrend } from '@shop/shared'

const router = useRouter()

/** 页面加载中状态，给 v-loading 用 */
const loading = ref(false)

/** 统计数据（金额单位：分） */
const stats = reactive({
  /** 今日订单数 */
  todayOrders: 0,
  /** 今日销售额（分） */
  todaySales: 0,
  /** 在售商品数（后端统计接口暂未提供，保留默认值） */
  totalProducts: 0,
  /** 待发货订单数 */
  pendingShip: 0,
})

/** 销售趋势数据（UI 展示用，percent 是柱状图高度百分比） */
interface SalesTrendItem {
  date: string
  amount: number
  percent: number
}

const salesTrend = ref<SalesTrendItem[]>([])

/** 最近订单数据（暂保留 mock，后续可接入 getMerchantOrderList） */
interface RecentOrder {
  id: number
  orderNo: string
  productName: string
  amount: string
  statusType: string
  statusLabel: string
  time: string
}

const recentOrders = ref<RecentOrder[]>([])

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
 * 加载仪表盘数据
 * 并行调用两个接口：今日统计 + 近7日销售趋势
 * 接口失败时保留默认值 0，不影响页面展示
 */
const loadDashboardData = async () => {
  loading.value = true
  try {
    // 并行请求，提升加载速度
    const [statsRes, trendRes] = await Promise.all([
      getDashboardStats(),
      getSalesTrend(7),
    ])

    // 今日统计：后端返回的是真实数据，直接赋值
    const dashboardStats = statsRes.data
    stats.todaySales = dashboardStats.todaySales
    stats.todayOrders = dashboardStats.todayOrders
    stats.pendingShip = dashboardStats.pendingShip

    // 销售趋势：计算每根柱子的高度百分比（以最大值为基准）
    const trendList = trendRes.data
    const maxAmount = trendList.reduce((max, item) => Math.max(max, item.amount), 0)
    salesTrend.value = trendList.map((item) => ({
      date: formatDateShort(item.date),
      amount: item.amount,
      percent: maxAmount > 0 ? Math.round((item.amount / maxAmount) * 100) : 0,
    }))
  } catch (err) {
    // 接口失败时记录错误，保留默认值 0，页面仍可展示
    console.error('[工作台] 加载统计数据失败:', err)
  } finally {
    loading.value = false
  }

  // 最近订单暂用 mock 数据，后续接入 getMerchantOrderList
  recentOrders.value = [
    { id: 1, orderNo: 'SN20240617001', productName: '运动跑鞋 Pro ×2', amount: '¥598.00', statusType: 'warning', statusLabel: '待发货', time: '2024-06-17 14:30' },
    { id: 2, orderNo: 'SN20240617002', productName: '纯棉T恤 ×1', amount: '¥129.00', statusType: 'primary', statusLabel: '运输中', time: '2024-06-17 12:15' },
    { id: 3, orderNo: 'SN20240616008', productName: '双肩背包 ×1', amount: '¥259.00', statusType: 'success', statusLabel: '已收货', time: '2024-06-16 18:45' },
    { id: 4, orderNo: 'SN20240616005', productName: '智能手表 ×1', amount: '¥1,299.00', statusType: 'danger', statusLabel: '退款中', time: '2024-06-16 10:20' },
    { id: 5, orderNo: 'SN20240615012', productName: '蓝牙耳机 ×3', amount: '¥897.00', statusType: 'success', statusLabel: '已收货', time: '2024-06-15 09:30' },
  ]
}

/** 跳转到指定页面 */
const goTo = (path: string) => {
  router.push(path)
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
/* ==================== 页面容器 ==================== */
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ==================== 数据卡片行：4列网格 ==================== */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

/* 统计卡片：白底圆角 + 顶部渐变指示线（hover 显现）+ 上浮动效 */
.stat-card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
  padding: 22px;
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform 0.3s var(--ease-out), box-shadow 0.3s var(--ease-out);
}

/* hover 上浮 + 加深阴影 */
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-hover);
}

/* 顶部翡翠渐变指示线，默认隐藏，hover 显现 */
.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-brand);
  opacity: 0;
  transition: opacity 0.3s var(--ease-out);
}

.stat-card:hover::before {
  opacity: 1;
}

/* 卡片标签：小字 muted */
.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
  margin-bottom: 8px;
  padding-right: 56px;
}

/* 大数字：Sora 字体 + 粗体 */
.stat-value {
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 800;
  color: var(--color-text);
  line-height: 1;
  margin-bottom: 6px;
}

/* 第一张卡片（销售额）数字用翡翠渐变填充 */
.stat-card:first-child .stat-value {
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* 趋势变化：胶囊标签 */
.stat-change {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  margin-top: 8px;
  padding: 3px 8px;
  border-radius: var(--radius-tag);
}

.stat-change.up {
  background: rgba(34, 197, 94, 0.12);
  color: var(--color-success);
}

.stat-change.down {
  background: rgba(239, 68, 68, 0.12);
  color: var(--color-danger);
}

/* 卡片右上角图标：翡翠渐变柔光底 + 翡翠文字 */
.stat-icon {
  position: absolute;
  right: 16px;
  top: 16px;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-button);
  background: var(--gradient-brand-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

/* ==================== 第二行：趋势图 + 待办事项 ==================== */
.second-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

/* 卡片标题：翡翠圆点 + Sora 字体 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.card-header span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-display);
}

/* 标题前翡翠渐变小圆点 */
.card-header span::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gradient-brand);
  flex-shrink: 0;
}

/* ==================== el-card 玻璃化（深度穿透） ==================== */
.dashboard-page :deep(.el-card) {
  background: var(--color-glass-strong);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: var(--radius-card) !important;
  box-shadow: var(--shadow-card) !important;
}

.dashboard-page :deep(.el-card__header) {
  border-bottom: 1px solid var(--color-border-light) !important;
  padding: 16px 20px;
}

.dashboard-page :deep(.el-card__body) {
  padding: 20px;
}

/* 表格 hover 翡翠淡底（叠加在玻璃卡上） */
.dashboard-page :deep(.el-table) {
  background: transparent;
  --el-table-border-color: var(--color-border-light);
  --el-table-header-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(16, 185, 129, 0.04);
}

.dashboard-page :deep(.el-table th.el-table__cell) {
  background: transparent !important;
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.dashboard-page :deep(.el-table td.el-table__cell),
.dashboard-page :deep(.el-table th.el-table__cell) {
  border-bottom: 1px solid var(--color-border-light);
}

/* ==================== 柱状图 ==================== */
.chart-placeholder {
  width: 100%;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 220px;
  padding: 10px 4px 0;
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
}

/* 柱子：翡翠→青蓝垂直渐变，hover 提亮 */
.bar {
  width: 100%;
  max-width: 40px;
  background: linear-gradient(180deg, #10B981 0%, #06B6D4 100%);
  border-radius: 6px 6px 0 0;
  min-height: 4px;
  transition: height 0.6s var(--ease-out), filter 0.3s var(--ease-out), transform 0.3s var(--ease-out);
  transform-origin: bottom;
}

.bar:hover {
  filter: brightness(1.1) saturate(1.2);
  transform: scaleX(1.08);
}

.bar-label {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 8px;
  font-weight: 600;
}

/* ==================== 待办事项 ==================== */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 待办项：圆角 + 缓动，hover 上浮 */
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: transform 0.3s var(--ease-out), box-shadow 0.3s var(--ease-out);
}

.todo-item:hover {
  transform: translateX(4px);
  box-shadow: var(--shadow-card);
}

.todo-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.todo-count {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 800;
}

/* ==================== 最近订单 ==================== */
.order-no {
  color: var(--color-primary);
  cursor: pointer;
  font-weight: 600;
  font-family: var(--font-display);
  transition: color 0.2s var(--ease-out);
}

.order-no:hover {
  color: var(--color-primary-dark);
  text-decoration: underline;
}

.amount {
  font-weight: 700;
  color: var(--color-text);
}

/* ==================== 响应式 ==================== */
@media (max-width: 1100px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .second-row {
    grid-template-columns: 1fr;
  }
}
</style>
