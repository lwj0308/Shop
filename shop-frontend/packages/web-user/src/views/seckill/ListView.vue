<template>
  <!-- 秒杀列表页 - 限时秒杀活动展示 -->
  <div class="seckill-list">
    <!-- 顶部Banner：大标题 + 倒计时风格 -->
    <div class="seckill-banner">
      <div class="banner-bg"></div>
      <div class="banner-content">
        <p class="banner-eyebrow">FLASH SALE</p>
        <h1 class="banner-title">限时秒杀</h1>
        <p class="banner-subtitle">超低折扣 · 限量抢购 · 手慢无</p>
        <!-- 装饰性倒计时块（每天滚动展示） -->
        <div class="banner-countdown">
          <span class="countdown-label">距结束</span>
          <span class="countdown-block">{{ countdown.hours }}</span>
          <span class="countdown-sep">:</span>
          <span class="countdown-block">{{ countdown.minutes }}</span>
          <span class="countdown-sep">:</span>
          <span class="countdown-block">{{ countdown.seconds }}</span>
        </div>
      </div>
    </div>

    <!-- 列表内容容器 -->
    <div class="container">
      <!-- 加载中：骨架屏 -->
      <div v-if="loading" class="list-loading">
        <div v-for="i in 8" :key="i" class="card-skeleton">
          <el-skeleton :rows="5" animated />
        </div>
      </div>

      <!-- 加载失败：错误提示 + 重试按钮 -->
      <div v-else-if="errorMsg" class="list-error">
        <el-empty :description="errorMsg">
          <el-button type="primary" @click="loadData">重新加载</el-button>
        </el-empty>
      </div>

      <!-- 空数据：没有进行中的秒杀活动 -->
      <div v-else-if="seckillList.length === 0" class="list-empty">
        <el-empty description="暂无进行中的秒杀活动，敬请期待" />
      </div>

      <!-- 正常列表：4列网格 -->
      <el-row v-else :gutter="16" class="list-grid">
        <el-col
          v-for="item in seckillList"
          :key="item.id"
          :xs="12"
          :sm="8"
          :md="6"
        >
          <article class="seckill-card" @click="goToDetail(item.id)">
            <!-- 商品图片占位区 -->
            <div class="card-image">
              <el-icon :size="48" class="card-image-icon"><Goods /></el-icon>
              <span class="card-tag">秒杀中</span>
            </div>

            <!-- 卡片信息区 -->
            <div class="card-body">
              <!-- 商品名称（用 productId / skuId 展示） -->
              <h3 class="card-name">商品 #{{ item.productId }}</h3>
              <p class="card-sku">SKU: {{ item.skuId }}</p>

              <!-- 价格区：秒杀价（红色大字）+ 原价（划线灰色） -->
              <div class="card-price-row">
                <div class="price-seckill">
                  <span class="price-unit">¥</span>
                  <span class="price-value">{{ formatPrice(item.seckillPrice) }}</span>
                </div>
                <span class="price-original">¥{{ formatPrice(item.originalPrice) }}</span>
              </div>

              <!-- 进度条：已售百分比 -->
              <div class="card-progress">
                <el-progress
                  :percentage="calcSoldPercent(item)"
                  :stroke-width="8"
                  :show-text="false"
                  color="#ff4d4f"
                />
                <div class="progress-text">
                  <span>已抢 {{ calcSoldPercent(item) }}%</span>
                  <span class="stock-left">剩余 {{ item.availableCount }} 件</span>
                </div>
              </div>

              <!-- 抢购按钮：库存为0时禁用 -->
              <button
                class="card-btn"
                :disabled="item.availableCount <= 0"
                @click.stop="goToDetail(item.id)"
              >
                {{ item.availableCount <= 0 ? '已抢光' : '立即抢购' }}
              </button>
            </div>
          </article>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 秒杀列表页
 * 展示当前所有进行中的秒杀活动
 * 用户可点击卡片跳转到秒杀详情页参与抢购
 * 不需要登录就能浏览，点击抢购时在详情页才检查登录
 */

import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Goods } from '@element-plus/icons-vue'
import { getPublicSeckillList } from '@shop/shared'
import type { SeckillInfo } from '@shop/shared'

const router = useRouter()

/** 秒杀活动列表数据 */
const seckillList = ref<SeckillInfo[]>([])

/** 是否加载中 */
const loading = ref(true)

/** 错误信息（加载失败时展示给用户） */
const errorMsg = ref('')

/**
 * 顶部倒计时显示（装饰性，按当天23:59:59计算）
 * 每秒刷新一次，给页面增加紧迫感
 */
const countdown = reactive({
  hours: '00',
  minutes: '00',
  seconds: '00',
})

/** 倒计时定时器ID（用于卸载时清理） */
let countdownTimer: ReturnType<typeof setInterval> | null = null

/**
 * 更新顶部倒计时
 * 计算当前时间到当天23:59:59的剩余时间
 */
const updateCountdown = () => {
  const now = new Date()
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  let diff = Math.max(0, Math.floor((end.getTime() - now.getTime()) / 1000))
  const hours = Math.floor(diff / 3600)
  diff -= hours * 3600
  const minutes = Math.floor(diff / 60)
  const seconds = diff - minutes * 60
  // 两位补零展示
  const pad = (n: number) => String(n).padStart(2, '0')
  countdown.hours = pad(hours)
  countdown.minutes = pad(minutes)
  countdown.seconds = pad(seconds)
}

/**
 * 加载秒杀活动列表
 * 调用公开接口（不需要登录）
 * 处理加载中、成功、失败、空数据四种状态
 */
const loadData = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await getPublicSeckillList()
    seckillList.value = res.data || []
  } catch (error) {
    // 把错误信息展示给用户
    const msg = error instanceof Error ? error.message : '加载秒杀活动失败'
    errorMsg.value = msg
  } finally {
    loading.value = false
  }
}

/**
 * 跳转到秒杀详情页
 * @param id 秒杀活动ID
 */
const goToDetail = (id: number) => {
  router.push(`/seckill/${id}`)
}

/**
 * 计算已售百分比
 * 用总数减去剩余得到已售数量，再除以总数得到百分比
 * @param item 秒杀活动信息
 * @returns 0-100 的整数百分比
 */
const calcSoldPercent = (item: SeckillInfo): number => {
  if (!item.totalCount || item.totalCount <= 0) return 0
  const sold = item.totalCount - item.availableCount
  const percent = Math.round((sold / item.totalCount) * 100)
  // 防止边界值超出 0-100
  return Math.max(0, Math.min(100, percent))
}

/**
 * 格式化价格：去掉末尾的 .00
 * @param price 价格数字
 * @returns 格式化后的字符串（如 99.9）
 */
const formatPrice = (price: number | undefined): string => {
  if (price == null) return '0'
  return price.toFixed(2).replace(/\.00$/, '')
}

onMounted(() => {
  loadData()
  // 启动倒计时，每秒刷新一次
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
})

onUnmounted(() => {
  // 组件卸载时清理定时器，避免内存泄漏
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.seckill-list {
  padding: 0;
}

/* ==================== 顶部 Banner：红色视觉冲击 ==================== */
.seckill-banner {
  position: relative;
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%);
}

/* Banner 背景装饰：径向渐变 */
.banner-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at 20% 30%, rgba(255, 255, 255, 0.15) 0%, transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(255, 255, 255, 0.1) 0%, transparent 40%);
}

.banner-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
  padding: 0 var(--space-lg);
}

.banner-eyebrow {
  font-size: var(--font-size-small);
  letter-spacing: 0.3em;
  opacity: 0.85;
  margin-bottom: var(--space-sm);
  font-weight: 500;
}

.banner-title {
  font-family: var(--font-heading);
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 var(--space-sm);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.banner-subtitle {
  font-size: var(--font-size-h3);
  opacity: 0.9;
  margin: 0 0 var(--space-lg);
  font-weight: 300;
  letter-spacing: 0.05em;
}

/* 倒计时块：黑色底白字，类似电子表 */
.banner-countdown {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: var(--radius-md);
  backdrop-filter: blur(4px);
}

.countdown-label {
  font-size: var(--font-size-small);
  letter-spacing: 0.1em;
  margin-right: 8px;
  opacity: 0.9;
}

.countdown-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  background: #1a1a1a;
  color: #fff;
  font-family: 'Courier New', monospace;
  font-size: 18px;
  font-weight: 600;
  border-radius: var(--radius-sm);
  font-variant-numeric: tabular-nums;
}

.countdown-sep {
  font-size: 20px;
  font-weight: 700;
  opacity: 0.9;
}

/* ==================== 列表容器 ==================== */
.list-grid {
  padding: var(--space-xl) 0 var(--space-3xl);
}

/* 加载中骨架屏 */
.list-loading {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-md);
  padding: var(--space-xl) 0 var(--space-3xl);
}

.card-skeleton {
  background: #fff;
  padding: var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

/* 错误和空数据 */
.list-error,
.list-empty {
  padding: var(--space-3xl) 0;
}

/* ==================== 秒杀卡片 ==================== */
.seckill-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: var(--transition-base);
  margin-bottom: var(--space-md);
  display: flex;
  flex-direction: column;
}

.seckill-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-hover);
  border-color: #ff4d4f;
}

/* 商品图片占位区：灰色背景 + 商品图标 */
.card-image {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe7e7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff4d4f;
  overflow: hidden;
}

.card-image-icon {
  opacity: 0.4;
}

/* 秒杀中标签：右上角红色 */
.card-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 4px 10px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  border-radius: var(--radius-sm);
  z-index: 1;
}

/* 卡片信息区 */
.card-body {
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

/* 商品名称：黑色加粗 */
.card-name {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
  line-height: 1.4;
  /* 单行省略 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* SKU 信息：灰色小字 */
.card-sku {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
}

/* 价格行：秒杀价 + 原价 */
.card-price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

/* 秒杀价：红色大字 */
.price-seckill {
  color: #ff4d4f;
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: baseline;
}

.price-unit {
  font-size: 13px;
  font-weight: 600;
  margin-right: 1px;
}

.price-value {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

/* 原价：划线灰色 */
.price-original {
  color: var(--color-text-muted);
  text-decoration: line-through;
  font-size: 13px;
  font-weight: 400;
}

/* 进度条区 */
.card-progress {
  margin-top: 4px;
}

.progress-text {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.stock-left {
  color: #ff4d4f;
  font-weight: 600;
}

/* 抢购按钮：红色实心 */
.card-btn {
  width: 100%;
  padding: 10px 16px;
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: var(--transition-base);
  margin-top: auto;
}

.card-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #cf1322 0%, #d4380d 100%);
  transform: translateY(-1px);
}

/* 按钮禁用：已抢光 */
.card-btn:disabled {
  background: var(--color-bg-tertiary);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 1024px) {
  .list-loading {
    grid-template-columns: repeat(3, 1fr);
  }

  .banner-title {
    font-size: 44px;
  }
}

@media (max-width: 768px) {
  .list-loading {
    grid-template-columns: repeat(2, 1fr);
  }

  .banner-title {
    font-size: 36px;
  }

  .banner-subtitle {
    font-size: var(--font-size-body);
  }

  .countdown-block {
    min-width: 28px;
    height: 28px;
    font-size: 16px;
  }
}
</style>
