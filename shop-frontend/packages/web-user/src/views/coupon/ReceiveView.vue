<template>
  <!-- 领券中心页 -->
  <div class="coupon-receive-page">
    <div class="page-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2 class="page-title">领券中心</h2>
        <button class="my-coupon-btn" @click="$router.push({ name: 'MyCoupon' })">
          我的优惠券 ›
        </button>
      </div>

      <!-- 优惠券列表 -->
      <div v-loading="loading" class="coupon-list">
        <div v-if="list.length === 0 && !loading" class="empty-state">
          <span class="empty-icon">🎫</span>
          <p class="empty-text">暂无可领取的优惠券</p>
          <p class="empty-desc">先去逛逛商品吧，有优惠活动会在这里通知你</p>
        </div>

        <div
          v-for="item in list"
          :key="item.id"
          class="coupon-card"
        >
          <!-- 左侧金额区 -->
          <div class="coupon-amount">
            <template v-if="item.type === CouponType.DISCOUNT">
              <span class="amount-num">{{ formatDiscount(item.amount) }}</span>
              <span class="amount-unit">折</span>
            </template>
            <template v-else>
              <span class="amount-symbol">¥</span>
              <span class="amount-num">{{ formatAmount(item.amount) }}</span>
            </template>
            <p class="amount-threshold">{{ getThresholdText(item) }}</p>
          </div>

          <!-- 中间分割线 -->
          <div class="coupon-divider">
            <span class="circle top"></span>
            <span class="circle bottom"></span>
          </div>

          <!-- 右侧信息区 -->
          <div class="coupon-info">
            <h4 class="coupon-name">{{ item.name }}</h4>
            <p class="coupon-type">{{ item.typeDesc }}</p>
            <p class="coupon-time">领取期：{{ formatDate(item.receiveStartTime) }} - {{ formatDate(item.receiveEndTime) }}</p>
            <p class="coupon-valid">使用期：{{ formatDate(item.validStartTime) }} - {{ formatDate(item.validEndTime) }}</p>
            <div class="coupon-bottom">
              <span class="coupon-remain">{{ getRemainText(item) }}</span>
              <button
                class="receive-btn"
                :disabled="receiveLoading === item.id || !canReceive(item)"
                @click="handleReceive(item)"
              >
                {{ receiveLoading === item.id ? '领取中...' : getReceiveBtnText(item) }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 领券中心页（用户端）
 * 展示当前可领取的优惠券，用户点击"立即领取"按钮领取
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReceivableCouponList, receiveCoupon } from '@shop/shared'
import {
  CouponType,
  type CouponInfo,
} from '@shop/shared'

/** 优惠券列表 */
const list = ref<CouponInfo[]>([])
/** 加载状态 */
const loading = ref(false)
/** 当前正在领取的优惠券ID（防止重复点击） */
const receiveLoading = ref<number | null>(null)

/**
 * 加载可领取的优惠券列表
 */
async function loadData() {
  loading.value = true
  try {
    const res = await getReceivableCouponList()
    list.value = res.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载优惠券失败')
  } finally {
    loading.value = false
  }
}

/**
 * 领取优惠券
 */
async function handleReceive(coupon: CouponInfo) {
  receiveLoading.value = coupon.id
  try {
    await receiveCoupon(coupon.id)
    ElMessage.success('领取成功！去"我的优惠券"查看')
    // 领取成功后刷新列表（已领完的券会从列表移除）
    await loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '领取失败，请稍后重试')
  } finally {
    receiveLoading.value = null
  }
}

/**
 * 格式化金额
 */
function formatAmount(amount: number): string {
  return Number(amount).toFixed(2).replace(/\.?0+$/, '')
}

/**
 * 格式化折扣率（0.85 → 8.5）
 */
function formatDiscount(amount: number): string {
  return (Number(amount) * 10).toFixed(1).replace(/\.0$/, '')
}

/**
 * 获取门槛文案
 */
function getThresholdText(item: CouponInfo): string {
  if (item.type === CouponType.FULL_REDUCTION) {
    return `满${formatAmount(item.threshold)}元可用`
  }
  return '无门槛'
}

/**
 * 获取剩余数量文案
 */
function getRemainText(item: CouponInfo): string {
  if (item.totalCount === 0) {
    return '不限量'
  }
  const remain = item.remainCount !== undefined ? item.remainCount : (item.totalCount - item.receivedCount)
  return remain > 0 ? `剩余 ${remain} 张` : '已领完'
}

/**
 * 是否可领取（剩余数量大于0或限量）
 */
function canReceive(item: CouponInfo): boolean {
  if (item.totalCount === 0) return true
  const remain = item.remainCount !== undefined ? item.remainCount : (item.totalCount - item.receivedCount)
  return remain > 0
}

/**
 * 获取领取按钮文案
 */
function getReceiveBtnText(item: CouponInfo): string {
  if (!canReceive(item)) return '已领完'
  return '立即领取'
}

/**
 * 格式化日期
 */
function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  return dateStr.replace('T', ' ').substring(0, 10)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.coupon-receive-page {
  background-color: var(--color-bg);
  min-height: 100vh;
  padding: var(--space-xl) var(--space-lg);
}

.page-container {
  max-width: 900px;
  margin: 0 auto;
}

/* 页面标题 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.my-coupon-btn {
  padding: 8px 16px;
  font-size: 14px;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.my-coupon-btn:hover {
  background: var(--color-primary);
  color: #fff;
}

/* 优惠券列表 */
.coupon-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: var(--space-3xl) var(--space-lg);
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: var(--space-md);
}

.empty-text {
  font-size: 16px;
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 13px;
  margin: 0;
}

/* 优惠券卡片 */
.coupon-card {
  display: flex;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: var(--transition-base);
}

.coupon-card:hover {
  box-shadow: var(--shadow-sm);
}

/* 左侧金额区 */
.coupon-amount {
  width: 140px;
  background: linear-gradient(135deg, #ff4e50 0%, #f9a825 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-md);
  flex-shrink: 0;
}

.amount-symbol {
  font-size: 16px;
  font-weight: 500;
}

.amount-num {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
}

.amount-unit {
  font-size: 14px;
  margin-left: 2px;
}

.amount-threshold {
  font-size: 12px;
  margin: 8px 0 0 0;
  opacity: 0.9;
}

/* 分割线 */
.coupon-divider {
  position: relative;
  width: 1px;
  background: repeating-linear-gradient(
    to bottom,
    var(--color-border) 0,
    var(--color-border) 4px,
    transparent 4px,
    transparent 8px
  );
}

.coupon-divider .circle {
  position: absolute;
  left: -5px;
  width: 10px;
  height: 10px;
  background: var(--color-bg);
  border-radius: 50%;
}

.coupon-divider .circle.top {
  top: -5px;
}

.coupon-divider .circle.bottom {
  bottom: -5px;
}

/* 右侧信息区 */
.coupon-info {
  flex: 1;
  padding: var(--space-md) var(--space-lg);
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.coupon-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 6px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-type {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0 0 6px 0;
}

.coupon-time,
.coupon-valid {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0 0 4px 0;
}

.coupon-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.coupon-remain {
  font-size: 12px;
  color: var(--color-accent);
}

.receive-btn {
  padding: 6px 20px;
  font-size: 13px;
  color: #fff;
  background: var(--color-primary);
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.receive-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.receive-btn:disabled {
  background: var(--color-bg-secondary);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 768px) {
  .coupon-receive-page {
    padding: var(--space-md);
  }

  .coupon-amount {
    width: 100px;
  }

  .amount-num {
    font-size: 28px;
  }

  .coupon-info {
    padding: var(--space-sm) var(--space-md);
  }
}
</style>
