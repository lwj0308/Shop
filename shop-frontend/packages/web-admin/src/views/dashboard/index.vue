<template>
  <!-- 管理后台仪表盘：数据概览 + 图表 + 待办事项 + 最近登录 -->
  <div class="dashboard-page">
    <!-- 数据概览卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">总用户数</div>
        <div class="stat-value">{{ overview.totalUsers }}</div>
        <div class="stat-icon" style="background: #ecf5ff; color: #409EFF;">
          <el-icon :size="24"><User /></el-icon>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总商家数</div>
        <div class="stat-value">{{ overview.totalMerchants }}</div>
        <div class="stat-icon" style="background: #f0f9eb; color: #67C23A;">
          <el-icon :size="24"><OfficeBuilding /></el-icon>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总订单数</div>
        <div class="stat-value">{{ overview.totalOrders }}</div>
        <div class="stat-icon" style="background: #fdf6ec; color: #E6A23C;">
          <el-icon :size="24"><ShoppingCart /></el-icon>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value">¥{{ overview.todaySalesAmount }}</div>
        <div class="stat-icon" style="background: #fef0f0; color: #F56C6C;">
          <el-icon :size="24"><Wallet /></el-icon>
        </div>
      </div>
    </div>

    <!-- 图表区域：左边柱状图对比核心指标，右边饼图展示待办占比 -->
    <div class="chart-row">
      <el-card>
        <template #header>
          <div class="card-header">平台数据概览</div>
        </template>
        <div ref="barChartRef" class="chart-container"></div>
      </el-card>
      <el-card>
        <template #header>
          <div class="card-header">待办事项分布</div>
        </template>
        <div ref="pieChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- 第二行：待办事项 + 最近登录日志 -->
    <div class="second-row">
      <!-- 待办事项 -->
      <el-card>
        <template #header>
          <div class="card-header">待办事项</div>
        </template>
        <div class="todo-list">
          <div class="todo-item" style="background: #fdf6ec;" @click="router.push('/business/merchant')">
            <span class="todo-label">待审核商家</span>
            <span class="todo-count" style="color: #E6A23C;">{{ overview.pendingAuditMerchants }}</span>
          </div>
          <div class="todo-item" style="background: #fef0f0;" @click="router.push('/business/refund')">
            <span class="todo-label">待处理退款</span>
            <span class="todo-count" style="color: #F56C6C;">{{ overview.pendingRefunds }}</span>
          </div>
          <div class="todo-item" style="background: #ecf5ff;" @click="router.push('/business/order')">
            <span class="todo-label">待发货订单</span>
            <span class="todo-count" style="color: #409EFF;">{{ overview.pendingShipOrders }}</span>
          </div>
        </div>
      </el-card>

      <!-- 最近登录日志 -->
      <el-card>
        <template #header>
          <div class="card-header">
            <span>最近登录</span>
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
        itemStyle: { color: '#409EFF' },
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
  pieChart.value.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
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
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

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
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chart-container {
  height: 300px;
}

.second-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.todo-item:hover {
  opacity: 0.85;
}

.todo-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.todo-count {
  font-size: 20px;
  font-weight: 700;
}
</style>
