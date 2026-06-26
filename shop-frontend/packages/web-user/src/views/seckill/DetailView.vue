<template>
  <!-- 秒杀详情页 - 商品信息 + 倒计时 + 抢购按钮 -->
  <div class="seckill-detail">
    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="detail-skeleton">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- 加载失败：错误提示 + 重试按钮 -->
    <div v-else-if="errorMsg" class="detail-error">
      <el-empty :description="errorMsg">
        <el-button type="primary" @click="loadData">重新加载</el-button>
      </el-empty>
    </div>

    <!-- 活动不存在 -->
    <div v-else-if="!seckill" class="detail-error">
      <el-empty description="秒杀活动不存在或已下架" />
    </div>

    <!-- 详情内容 -->
    <div v-else class="container detail-container">
      <!-- 面包屑导航 -->
      <div class="breadcrumb">
        <router-link to="/" class="breadcrumb-link">首页</router-link>
        <span class="breadcrumb-sep">/</span>
        <router-link to="/seckill" class="breadcrumb-link">限时秒杀</router-link>
        <span class="breadcrumb-sep">/</span>
        <span class="breadcrumb-current">秒杀活动 #{{ seckill.id }}</span>
      </div>

      <!-- 顶部倒计时条：红色背景，醒目展示剩余时间 -->
      <div class="countdown-bar" :class="{ ended: isEnded }">
        <span class="countdown-bar-label">
          {{ isEnded ? '活动已结束' : '距活动结束仅剩' }}
        </span>
        <div v-if="!isEnded" class="countdown-bar-blocks">
          <span class="time-block">{{ countdown.days }}</span>
          <span class="time-label">天</span>
          <span class="time-block">{{ countdown.hours }}</span>
          <span class="time-label">时</span>
          <span class="time-block">{{ countdown.minutes }}</span>
          <span class="time-label">分</span>
          <span class="time-block">{{ countdown.seconds }}</span>
          <span class="time-label">秒</span>
        </div>
      </div>

      <!-- 主信息区：左右分栏 -->
      <div class="detail-main">
        <!-- 左侧：商品图片占位 -->
        <div class="detail-left">
          <div class="product-image">
            <el-icon :size="120" class="product-image-icon"><Goods /></el-icon>
            <span class="image-tag">限时秒杀</span>
          </div>
        </div>

        <!-- 右侧：商品信息 + 抢购按钮 -->
        <div class="detail-right">
          <!-- 商品名称 -->
          <h1 class="product-name">商品 #{{ seckill.productId }}</h1>
          <p class="product-sku">SKU ID: {{ seckill.skuId }}</p>

          <!-- 价格区：秒杀价（红色大字）+ 原价（划线） -->
          <div class="price-section">
            <div class="price-seckill">
              <span class="price-unit">¥</span>
              <span class="price-value">{{ formatPrice(seckill.seckillPrice) }}</span>
            </div>
            <span class="price-original">¥{{ formatPrice(seckill.originalPrice) }}</span>
            <span class="discount-tag">
              直降¥{{ formatPrice(seckill.originalPrice - seckill.seckillPrice) }}
            </span>
          </div>

          <!-- 限购提示 -->
          <div class="limit-tip">
            <el-icon><Warning /></el-icon>
            <span>每人限购 {{ seckill.limitCount }} 件，超低折扣，错过等一年</span>
          </div>

          <!-- 库存进度条 -->
          <div class="stock-section">
            <div class="stock-header">
              <span class="stock-title">秒杀进度</span>
              <span class="stock-num">
                剩余 <strong>{{ seckill.availableCount }}</strong> 件 / 共 {{ seckill.totalCount }} 件
              </span>
            </div>
            <el-progress
              :percentage="calcSoldPercent(seckill)"
              :stroke-width="14"
              color="#ff4d4f"
              :format="() => `已抢 ${calcSoldPercent(seckill)}%`"
            />
          </div>

          <!-- 活动时间 -->
          <div class="time-section">
            <div class="time-row">
              <span class="time-label">开始时间</span>
              <span class="time-value">{{ formatTime(seckill.startTime) }}</span>
            </div>
            <div class="time-row">
              <span class="time-label">结束时间</span>
              <span class="time-value">{{ formatTime(seckill.endTime) }}</span>
            </div>
          </div>

          <!-- 抢购按钮：大按钮，loading 状态防重复点击 -->
          <button
            class="btn-seckill"
            :disabled="!canBuy"
            @click="handleSeckill"
          >
            <span v-if="submitting" class="btn-loading">
              <span class="loading-dot"></span>
              <span class="loading-dot"></span>
              <span class="loading-dot"></span>
              抢购中...
            </span>
            <span v-else-if="isEnded">活动已结束</span>
            <span v-else-if="seckill.availableCount <= 0">已抢光</span>
            <span v-else>立即抢购</span>
          </button>

          <!-- 服务承诺 -->
          <div class="service-tags">
            <span class="service-item"><el-icon><Check /></el-icon> 100%正品</span>
            <span class="service-item"><el-icon><Check /></el-icon> 极速发货</span>
            <span class="service-item"><el-icon><Check /></el-icon> 售后无忧</span>
          </div>
        </div>
      </div>

      <!-- 活动描述 -->
      <div v-if="seckill.description" class="description-section">
        <h2 class="section-heading">活动说明</h2>
        <p class="description-text">{{ seckill.description }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 秒杀详情页
 * 展示单个秒杀活动详情，用户可在此参与抢购
 * 抢购流程：检查登录 → 调用 executeSeckill API → 成功跳订单列表
 * 不需要登录就能浏览，点击抢购时才检查登录
 */

import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Goods, Warning, Check } from '@element-plus/icons-vue'
import {
  getPublicSeckillDetail,
  executeSeckill,
  isAuthenticated,
} from '@shop/shared'
import type { SeckillInfo } from '@shop/shared'
import { SeckillStatus } from '@shop/shared'
import { useAuthModalStore } from '@/stores/authModal'

const route = useRoute()
const router = useRouter()
const authModalStore = useAuthModalStore()

/** 秒杀活动详情数据 */
const seckill = ref<SeckillInfo | null>(null)

/** 是否加载中 */
const loading = ref(true)

/** 错误信息（加载失败时展示） */
const errorMsg = ref('')

/** 是否正在提交抢购（用于按钮 loading 状态，防止重复点击） */
const submitting = ref(false)

/**
 * 倒计时显示数据
 * 天/时/分/秒 各两位数字
 */
const countdown = reactive({
  days: '00',
  hours: '00',
  minutes: '00',
  seconds: '00',
})

/** 倒计时定时器ID（用于卸载时清理） */
let countdownTimer: ReturnType<typeof setInterval> | null = null

/**
 * 活动是否已结束
 * 1. 活动状态为"已结束"或"已下架"
 * 2. 或者当前时间已超过 endTime
 */
const isEnded = computed(() => {
  if (!seckill.value) return false
  // 状态判断：已结束或已下架
  if (
    seckill.value.status === SeckillStatus.ENDED ||
    seckill.value.status === SeckillStatus.OFFLINE
  ) {
    return true
  }
  // 时间判断：当前时间超过 endTime
  const endTime = new Date(seckill.value.endTime.replace(' ', 'T')).getTime()
  return Date.now() >= endTime
})

/**
 * 按钮是否可点击
 * 活动未结束 + 库存大于0 + 不在提交中
 */
const canBuy = computed(() => {
  if (!seckill.value) return false
  if (submitting.value) return false
  if (isEnded.value) return false
  return seckill.value.availableCount > 0
})

/**
 * 更新倒计时显示
 * 根据活动 endTime 计算剩余时间，拆成天/时/分/秒
 */
const updateCountdown = () => {
  if (!seckill.value) return
  const endTime = new Date(seckill.value.endTime.replace(' ', 'T')).getTime()
  let diff = Math.max(0, Math.floor((endTime - Date.now()) / 1000))
  // 计算天/时/分/秒
  const days = Math.floor(diff / 86400)
  diff -= days * 86400
  const hours = Math.floor(diff / 3600)
  diff -= hours * 3600
  const minutes = Math.floor(diff / 60)
  const seconds = diff - minutes * 60
  // 两位补零展示
  const pad = (n: number) => String(n).padStart(2, '0')
  countdown.days = pad(days)
  countdown.hours = pad(hours)
  countdown.minutes = pad(minutes)
  countdown.seconds = pad(seconds)
}

/**
 * 加载秒杀活动详情
 * 从路由参数获取活动ID，调用公开接口（不需要登录）
 * 处理加载中、成功、失败、空数据四种状态
 */
const loadData = async () => {
  loading.value = true
  errorMsg.value = ''
  // 从路由参数获取秒杀活动ID
  const id = Number(route.params.id)
  if (!id || isNaN(id)) {
    errorMsg.value = '活动ID不正确'
    loading.value = false
    return
  }
  try {
    const res = await getPublicSeckillDetail(id)
    seckill.value = res.data || null
    // 数据加载完成后立即更新一次倒计时，避免首次显示 00:00:00
    updateCountdown()
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载秒杀详情失败'
    errorMsg.value = msg
  } finally {
    loading.value = false
  }
}

/**
 * 执行秒杀抢购（已登录状态下调用）
 * 成功 → 提示并跳转订单列表
 * 失败 → 展示错误信息（如"库存不足"、"超过限购"）
 * @returns 是否成功
 */
const doSeckill = async (): Promise<boolean> => {
  if (!seckill.value) return false
  if (submitting.value) return false
  submitting.value = true
  try {
    await executeSeckill(seckill.value.id)
    ElMessage.success('抢购成功，正在创建订单')
    // 跳转到订单列表页查看已创建的订单
    router.push({ name: 'OrderList' })
    return true
  } catch (error) {
    // 展示具体错误信息：库存不足、超过限购、活动已结束等
    const msg = error instanceof Error ? error.message : '抢购失败，请稍后重试'
    ElMessage.error(msg)
    return false
  } finally {
    submitting.value = false
  }
}

/**
 * 点击"立即抢购"按钮
 * 流程：检查登录 → 未登录则弹登录弹窗（登录成功后自动执行抢购）
 *      → 已登录则直接执行抢购
 */
const handleSeckill = () => {
  if (!seckill.value) return
  if (!canBuy.value) return
  if (!isAuthenticated()) {
    // 未登录：弹出登录弹窗，登录成功后自动执行抢购
    authModalStore.openAuthModal({
      description: '登录后参与秒杀抢购',
      execute: async () => {
        await doSeckill()
      },
    })
    return
  }
  // 已登录：直接执行抢购
  void doSeckill()
}

/**
 * 计算已售百分比（用于进度条）
 * 接受可空参数是因为模板中 seckill 是 ref，vue-tsc 无法通过 v-else 推断非空
 * @param item 秒杀活动信息（可能为 null）
 * @returns 0-100 的整数百分比
 */
const calcSoldPercent = (item: SeckillInfo | null): number => {
  if (!item || !item.totalCount || item.totalCount <= 0) return 0
  const sold = item.totalCount - item.availableCount
  const percent = Math.round((sold / item.totalCount) * 100)
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

/**
 * 格式化时间：去掉 T 分隔符，截取到分钟
 * 如 2026-06-25T10:00:00 → 2026-06-25 10:00
 */
const formatTime = (time: string): string => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  loadData()
  // 启动倒计时，每秒刷新一次
  countdownTimer = setInterval(() => {
    updateCountdown()
  }, 1000)
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
.seckill-detail {
  padding: 0;
}

.detail-container {
  padding-top: var(--space-md);
  padding-bottom: var(--space-3xl);
}

.detail-skeleton,
.detail-error {
  padding: var(--space-3xl) var(--space-lg);
  max-width: 1280px;
  margin: 0 auto;
}

/* ==================== 面包屑导航 ==================== */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.breadcrumb-link {
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color var(--transition-base);
}

.breadcrumb-link:hover {
  color: #ff4d4f;
}

.breadcrumb-sep {
  color: var(--color-text-muted);
  opacity: 0.5;
}

.breadcrumb-current {
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}

/* ==================== 顶部倒计时条：红色背景 ==================== */
.countdown-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px 24px;
  background: linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%);
  color: #fff;
  border-radius: var(--radius-md);
  margin-bottom: var(--space-xl);
  box-shadow: 0 4px 16px rgba(255, 77, 79, 0.3);
}

/* 已结束时：灰色背景 */
.countdown-bar.ended {
  background: linear-gradient(135deg, #bfbfbf 0%, #8c8c8c 100%);
  box-shadow: none;
}

.countdown-bar-label {
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.1em;
}

.countdown-bar-blocks {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 时间块：黑色底白字电子表风格 */
.time-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 36px;
  padding: 0 6px;
  background: #1a1a1a;
  color: #fff;
  font-family: 'Courier New', monospace;
  font-size: 20px;
  font-weight: 700;
  border-radius: var(--radius-sm);
  font-variant-numeric: tabular-nums;
}

.time-label {
  font-size: 14px;
  font-weight: 500;
  opacity: 0.9;
}

/* ==================== 主信息区：左右分栏 ==================== */
.detail-main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  padding: 24px 0 60px;
}

/* 左侧：商品图片 */
.detail-left {
  position: relative;
}

.product-image {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe7e7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff4d4f;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.product-image-icon {
  opacity: 0.3;
}

.image-tag {
  position: absolute;
  top: 16px;
  left: 16px;
  padding: 6px 14px;
  background: #ff4d4f;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.05em;
  border-radius: var(--radius-sm);
}

/* 右侧：商品信息 */
.detail-right {
  display: flex;
  flex-direction: column;
}

/* 商品名称 */
.product-name {
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 8px;
  line-height: 1.3;
  letter-spacing: -0.01em;
}

.product-sku {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 24px;
  letter-spacing: 0.02em;
}

/* ==================== 价格区 ==================== */
.price-section {
  display: flex;
  align-items: baseline;
  gap: 16px;
  flex-wrap: wrap;
  padding: 20px 24px;
  background: linear-gradient(135deg, #fff5f5 0%, #fff1f0 100%);
  border-radius: var(--radius-md);
  margin-bottom: 24px;
}

/* 秒杀价：红色大字 */
.price-seckill {
  color: #ff4d4f;
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: baseline;
}

.price-unit {
  font-size: 20px;
  font-weight: 600;
  margin-right: 2px;
}

.price-value {
  font-size: 44px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
}

/* 原价：划线灰色 */
.price-original {
  color: var(--color-text-muted);
  text-decoration: line-through;
  font-size: 18px;
  font-weight: 400;
}

/* 降价标签 */
.discount-tag {
  display: inline-block;
  padding: 4px 12px;
  background: #ff4d4f;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.05em;
  border-radius: var(--radius-pill);
}

/* ==================== 限购提示 ==================== */
.limit-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: var(--radius-sm);
  color: #d48806;
  font-size: 14px;
  margin-bottom: 24px;
}

.limit-tip .el-icon {
  font-size: 16px;
}

/* ==================== 库存进度条 ==================== */
.stock-section {
  margin-bottom: 24px;
}

.stock-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}

.stock-title {
  font-size: 14px;
  color: var(--color-text-secondary);
  letter-spacing: 0.05em;
}

.stock-num {
  font-size: 13px;
  color: var(--color-text-muted);
}

.stock-num strong {
  color: #ff4d4f;
  font-size: 18px;
  font-weight: 700;
  margin: 0 2px;
}

/* ==================== 活动时间 ==================== */
.time-section {
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 32px;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 0;
  font-size: 14px;
}

.time-label {
  color: var(--color-text-muted);
  min-width: 80px;
  letter-spacing: 0.05em;
}

.time-value {
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

/* ==================== 抢购按钮：大按钮黄色突出 ==================== */
.btn-seckill {
  width: 100%;
  padding: 20px 32px;
  background: linear-gradient(135deg, #ffa940 0%, #ff7a45 100%);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.2em;
  cursor: pointer;
  transition: var(--transition-base);
  box-shadow: 0 6px 20px rgba(255, 122, 69, 0.4);
  margin-bottom: 24px;
}

.btn-seckill:hover:not(:disabled) {
  background: linear-gradient(135deg, #ff7a45 0%, #ff4d4f 100%);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 77, 79, 0.5);
}

.btn-seckill:active:not(:disabled) {
  transform: translateY(0);
}

/* 按钮禁用：已结束/已抢光 */
.btn-seckill:disabled {
  background: var(--color-bg-tertiary);
  color: var(--color-text-muted);
  cursor: not-allowed;
  box-shadow: none;
}

/* 按钮加载动画：三个跳动的小点 */
.btn-loading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.loading-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.loading-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

/* ==================== 服务承诺 ==================== */
.service-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.service-item {
  display: flex;
  align-items: center;
  gap: 6px;
  letter-spacing: 0.02em;
}

.service-item .el-icon {
  color: #ff4d4f;
  font-size: 14px;
}

/* ==================== 活动说明 ==================== */
.description-section {
  padding: 32px 0;
  border-top: 1px solid var(--color-border);
}

.section-heading {
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 16px;
  letter-spacing: -0.01em;
}

.description-text {
  font-size: 15px;
  color: var(--color-text-secondary);
  line-height: 1.8;
  margin: 0;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 1024px) {
  .detail-main {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .product-name {
    font-size: 28px;
  }

  .price-value {
    font-size: 36px;
  }
}

@media (max-width: 768px) {
  .detail-main {
    gap: 24px;
  }

  .product-name {
    font-size: 22px;
  }

  .price-value {
    font-size: 32px;
  }

  .btn-seckill {
    padding: 16px 24px;
    font-size: 18px;
  }

  .countdown-bar {
    flex-direction: column;
    gap: 8px;
    padding: 12px;
  }

  .time-block {
    min-width: 30px;
    height: 30px;
    font-size: 16px;
  }

  .service-tags {
    gap: 12px;
  }
}
</style>
