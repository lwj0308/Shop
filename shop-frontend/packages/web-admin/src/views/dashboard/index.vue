<template>
  <!-- 管理后台仪表盘：玻璃统计卡 + 玻璃图表面板 + 玻璃待办/登录 -->
  <div class="dashboard-page">
    <!-- 顶部概览横幅：渐变 mesh 背景 + 标题 -->
    <div class="overview-banner">
      <div class="overview-header">
        <div>
          <h1 class="overview-title">📊 平台运营概览</h1>
          <div class="overview-meta">实时数据看板 · 数据每 5 分钟自动刷新</div>
        </div>
      </div>
    </div>

    <!-- 数据概览卡片：4 个，渐变图标 + 大数字，hover 上浮 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon icon-emerald">
          <el-icon :size="22"><User /></el-icon>
        </div>
        <div class="stat-label">总用户数</div>
        <div class="stat-value">{{ overview.totalUsers }}</div>
        <div class="stat-trend up">实时同步</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon icon-cyan">
          <el-icon :size="22"><OfficeBuilding /></el-icon>
        </div>
        <div class="stat-label">总商家数</div>
        <div class="stat-value">{{ overview.totalMerchants }}</div>
        <div class="stat-trend up">实时同步</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon icon-violet">
          <el-icon :size="22"><ShoppingCart /></el-icon>
        </div>
        <div class="stat-label">总订单数</div>
        <div class="stat-value">{{ overview.totalOrders }}</div>
        <div class="stat-trend up">实时同步</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon icon-amber">
          <el-icon :size="22"><Wallet /></el-icon>
        </div>
        <div class="stat-label">今日销售额</div>
        <div class="stat-value">¥{{ overview.todaySalesAmount }}</div>
        <div class="stat-trend up">实时同步</div>
      </div>
    </div>

    <!-- 图表区域：左边柱状图对比核心指标，右边饼图展示待办占比，均用玻璃卡片包裹 -->
    <div class="chart-row">
      <el-card class="glass-chart-card">
        <template #header>
          <div class="card-header">
            <span class="section-title"><span class="dot-mark"></span>平台数据概览</span>
          </div>
        </template>
        <div ref="barChartRef" class="chart-container"></div>
      </el-card>
      <el-card class="glass-chart-card">
        <template #header>
          <div class="card-header">
            <span class="section-title"><span class="dot-mark"></span>待办事项分布</span>
          </div>
        </template>
        <div ref="pieChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- 第二行：待办事项 + 最近登录日志 -->
    <div class="second-row">
      <!-- 待办事项：玻璃卡片 + 渐变软背景项 -->
      <el-card class="glass-chart-card">
        <template #header>
          <div class="card-header">
            <span class="section-title"><span class="dot-mark"></span>待办事项</span>
          </div>
        </template>
        <div class="todo-list">
          <div class="todo-item todo-amber" @click="router.push('/business/merchant')">
            <span class="todo-label">待审核商家</span>
            <span class="todo-count">{{ overview.pendingAuditMerchants }}</span>
          </div>
          <div class="todo-item todo-rose" @click="router.push('/business/refund')">
            <span class="todo-label">待处理退款</span>
            <span class="todo-count">{{ overview.pendingRefunds }}</span>
          </div>
          <div class="todo-item todo-emerald" @click="router.push('/business/order')">
            <span class="todo-label">待发货订单</span>
            <span class="todo-count">{{ overview.pendingShipOrders }}</span>
          </div>
        </div>
      </el-card>

      <!-- 最近登录日志：玻璃卡片 -->
      <el-card class="glass-chart-card">
        <template #header>
          <div class="card-header">
            <span class="section-title"><span class="dot-mark"></span>最近登录</span>
            <el-button text type="primary" @click="router.push('/log/login')">查看全部</el-button>
          </div>
        </template>
        <el-table :data="recentLogins" stripe size="small">
          <el-table-column label="用户" prop="username" width="100" />
          <el-table-column label="IP" prop="ip" width="120" />
          <el-table-column label="时间" prop="loginTime" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理后台仪表盘
 * 展示平台级数据概览、待办事项、最近登录日志，并用 ECharts 图表可视化核心数据
 */

import { reactive, ref, shallowRef, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, OfficeBuilding, ShoppingCart, Wallet } from '@element-plus/icons-vue'
import { getDashboardOverview } from '@shop/shared/api/modules/admin'
// ECharts 按需引入：只打包用到的图表和组件，减小最终体积
import * as echarts from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'

// 注册需要用到的 ECharts 模块（柱状图、饼图 + 标题/提示/图例/网格组件 + Canvas 渲染器）
echarts.use([BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const router = useRouter()

/** 概览数据 */
const overview = reactive({
  totalUsers: 0,
  totalMerchants: 0,
  totalOrders: 0,
  todaySalesAmount: '0.00',
  todayOrderCount: 0,
  pendingAuditMerchants: 0,
  pendingRefunds: 0,
  pendingShipOrders: 0,
})

/** 最近登录记录 */
const recentLogins = ref<any[]>([])

/** 柱状图、饼图的 DOM 容器引用 */
const barChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)

/**
 * 图表实例
 * 用 shallowRef 而不是 ref：ECharts 实例是复杂对象，ref 会把它做成深度响应式代理，
 * 可能导致 ECharts 内部判断异常；shallowRef 只代理最外层，更安全。
 */
const barChart = shallowRef<ECharts | null>(null)
const pieChart = shallowRef<ECharts | null>(null)

/**
 * 初始化柱状图：把 DOM 容器交给 ECharts，并填入第一份数据
 */
function initBarChart() {
  if (!barChartRef.value) return
  barChart.value = echarts.init(barChartRef.value)
  updateBarChart()
}

/**
 * 初始化饼图：把 DOM 容器交给 ECharts，并填入第一份数据
 */
function initPieChart() {
  if (!pieChartRef.value) return
  pieChart.value = echarts.init(pieChartRef.value)
  updatePieChart()
}

/**
 * 用最新概览数据刷新柱状图
 * 展示总用户数、总商家数、总订单数、今日订单数，方便横向对比各项核心指标
 */
function updateBarChart() {
  if (!barChart.value) return
  // 柱状图配置：x 轴是分类名称，y 轴是数值，series 用 bar 类型
  // 视觉层：柱子用翡翠青绿渐变，与整体玻璃拟态风格一致
  barChart.value.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['总用户数', '总商家数', '总订单数', '今日订单数'],
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '数量',
        type: 'bar',
        data: [
          overview.totalUsers,
          overview.totalMerchants,
          overview.totalOrders,
          overview.todayOrderCount,
        ],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#06B6D4' },
            { offset: 1, color: '#10B981' },
          ]),
          borderRadius: [8, 8, 0, 0],
        },
        barWidth: '40%',
      },
    ],
  })
}

/**
 * 用最新概览数据刷新饼图
 * 展示待发货订单、待处理退款、待审核商家的占比；
 * 如果三项待办都为 0，则居中显示"暂无待办"。
 */
function updatePieChart() {
  if (!pieChart.value) return
  const pendingShip = overview.pendingShipOrders
  const pendingRefund = overview.pendingRefunds
  const pendingAudit = overview.pendingAuditMerchants

  // 三项待办全部为 0 时，清空数据只显示提示文字（第二个参数 true 表示不合并、整体替换）
  if (pendingShip + pendingRefund + pendingAudit === 0) {
    pieChart.value.setOption(
      {
        title: {
          text: '暂无待办',
          left: 'center',
          top: 'center',
          textStyle: { color: '#909399', fontSize: 16 },
        },
      },
      true,
    )
    return
  }

  // 饼图配置：环形图，label 显示名称和数量，tooltip 显示占比
  // 视觉层：饼图用翡翠青绿色系
  pieChart.value.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      color: ['#10B981', '#06B6D4', '#34D399'],
      series: [
        {
          name: '待办事项',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          label: { show: true, formatter: '{b}: {c}' },
          data: [
            { value: pendingShip, name: '待发货订单' },
            { value: pendingRefund, name: '待处理退款' },
            { value: pendingAudit, name: '待审核商家' },
          ],
        },
      ],
    },
    true,
  )
}

/**
 * 窗口大小变化时让两个图表自适应宽度
 */
function handleResize() {
  barChart.value?.resize()
  pieChart.value?.resize()
}

/**
 * 加载仪表盘数据
 * 调用后端接口获取概览数据；失败时保持默认值0，不影响页面使用
 */
async function loadDashboard() {
  try {
    const res = await getDashboardOverview()
    const data = res.data.data
    overview.totalUsers = data.totalUsers || 0
    overview.totalMerchants = data.totalMerchants || 0
    overview.totalOrders = data.totalOrders || 0
    overview.todaySalesAmount = data.todaySalesAmount || '0.00'
    overview.todayOrderCount = data.todayOrderCount || 0
    overview.pendingAuditMerchants = data.pendingAuditMerchants || 0
    overview.pendingRefunds = data.pendingRefunds || 0
    overview.pendingShipOrders = data.pendingShipOrders || 0
    // 最近登录日志（后端可能不返回，保留空数组）
    recentLogins.value = data.recentLogins || []
    // 数据加载完成后，刷新两个图表
    updateBarChart()
    updatePieChart()
  } catch {
    // 仪表盘加载失败不影响使用，保持默认值0
  }
}

/**
 * 页面挂载后：加载数据、初始化两个图表、监听窗口大小变化
 */
onMounted(() => {
  loadDashboard()
  initBarChart()
  initPieChart()
  window.addEventListener('resize', handleResize)
})

/**
 * 页面卸载前：移除窗口监听、销毁图表实例释放内存，避免内存泄漏
 */
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  barChart.value?.dispose()
  pieChart.value?.dispose()
})
</script>

<style scoped>
/* 仪表盘根容器：垂直布局 + 间距 */
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部概览横幅：mesh 渐变 + 玻璃边框 + 装饰光晕 */
.overview-banner {
  background: var(--gradient-mesh), linear-gradient(135deg, rgba(16, 185, 129, 0.06) 0%, rgba(6, 182, 212, 0.06) 100%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-card);
  padding: 24px 28px;
  position: relative;
  overflow: hidden;
}

.overview-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.15) 0, transparent 70%);
  pointer-events: none;
}

.overview-header {
  position: relative;
}

.overview-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.overview-meta {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 6px;
}

/* 数据卡片网格：4 列 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

/* 单个统计卡片：白底 + 圆角 + 顶部渐变指示条 hover 显现 + 上浮动效 */
.stat-card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 20px;
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  transition: transform 0.3s var(--ease-out), box-shadow 0.3s var(--ease-out);
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-hover);
}

/* 顶部 3px 渐变指示条，hover 时显现 */
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

/* 渐变图标：四种色系渐变 */
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-button);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 14px;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
}

/* 翡翠青绿渐变（主色） */
.icon-emerald {
  background: var(--gradient-brand);
}

/* 青蓝渐变 */
.icon-cyan {
  background: linear-gradient(135deg, #06B6D4 0%, #3B82F6 100%);
  box-shadow: 0 4px 12px rgba(6, 182, 212, 0.25);
}

/* 紫粉渐变 */
.icon-violet {
  background: linear-gradient(135deg, #8B5CF6 0%, #EC4899 100%);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.25);
}

/* 琥珀红渐变 */
.icon-amber {
  background: linear-gradient(135deg, #F59E0B 0%, #EF4444 100%);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.25);
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.stat-value {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
  color: var(--color-text);
  margin: 4px 0 6px;
  letter-spacing: -0.02em;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
}

.stat-trend.up {
  color: var(--color-success);
}

/* 图表行：2 列 */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* 玻璃图表卡片：覆盖 el-card 默认样式 */
.glass-chart-card {
  background: var(--color-glass) !important;
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: var(--radius-card) !important;
  box-shadow: var(--shadow-card) !important;
}

.glass-chart-card :deep(.el-card__header) {
  border-bottom: 1px solid var(--color-border) !important;
  padding: 16px 20px;
}

.glass-chart-card :deep(.el-card__body) {
  padding: 20px;
}

.chart-container {
  height: 300px;
}

/* 第二行：待办 + 登录日志，2 列 */
.second-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* 卡片标题区：左右对齐 */
.card-header {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 章节标题：渐变小圆点 + 文字 */
.section-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-display);
}

.dot-mark {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gradient-brand);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
}

/* 待办列表：垂直布局 + 间距 */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 待办项：玻璃软背景 + 左侧色条 + hover 上浮 */
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: var(--radius-button);
  cursor: pointer;
  transition: transform 0.25s var(--ease-out), box-shadow 0.25s var(--ease-out);
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.todo-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}

.todo-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card);
}

.todo-amber {
  background: rgba(245, 158, 11, 0.08);
}

.todo-amber::before {
  background: linear-gradient(to bottom, #F59E0B, #FBBF24);
}

.todo-rose {
  background: rgba(239, 68, 68, 0.08);
}

.todo-rose::before {
  background: linear-gradient(to bottom, #EF4444, #F87171);
}

.todo-emerald {
  background: rgba(16, 185, 129, 0.08);
}

.todo-emerald::before {
  background: var(--gradient-brand);
}

.todo-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.todo-count {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text);
}

/* 响应式：窄屏单列 */
@media (max-width: 1100px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-row,
  .second-row {
    grid-template-columns: 1fr;
  }
}
</style>
