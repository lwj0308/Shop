<template>
  <!-- 我的优惠券页 -->
  <div class="my-coupon-page">
    <div class="page-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2 class="page-title">我的优惠券</h2>
        <button class="receive-btn" @click="$router.push({ name: 'CouponReceive' })">
          去领券 ›
        </button>
      </div>

      <!-- 状态 Tab -->
      <div class="tab-group">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          :class="['tab-btn', { active: activeStatus === tab.value }]"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 优惠券列表 -->
      <div v-loading="loading" class="coupon-list">
        <div v-if="list.length === 0 && !loading" class="empty-state">
          <span class="empty-icon">🎫</span>
          <p class="empty-text">暂无优惠券</p>
          <button class="empty-btn" @click="$router.push({ name: 'CouponReceive' })">去领券中心看看</button>
        </div>

        <div
          v-for="item in list"
          :key="item.id"
          :class="['coupon-card', { disabled: item.status !== UserCouponStatus.UNUSED }]"
        >
          <!-- 左侧金额区 -->
          <div class="coupon-amount">
            <template v-if="item.couponType === CouponType.DISCOUNT">
              <span class="amount-num">{{ formatDiscount(item.amount) }}</span>
              <span class="amount-unit">折</span>
            </template>
            <template v-else>
              <span class="amount-symbol">¥</span>
              <span class="amount-num">{{ formatAmount(item.amount) }}</span>
            </template>
            <p class="amount-threshold">{{ getThresholdText(item) }}</p>
          </div>

          <!-- 中间分割线（带锯齿） -->
          <div class="coupon-divider">
            <span class="circle top"></span>
            <span class="circle bottom"></span>
          </div>

          <!-- 右侧信息区 -->
          <div class="coupon-info">
            <h4 class="coupon-name">{{ item.couponName }}</h4>
            <p class="coupon-type">{{ item.couponTypeDesc }}</p>
            <p class="coupon-time">有效期：{{ formatDate(item.validStartTime) }} - {{ formatDate(item.validEndTime) }}</p>
            <div class="coupon-bottom">
              <span :class="['coupon-status', `status-${item.status}`]">{{ item.statusDesc }}</span>
              <button
                v-if="item.status === UserCouponStatus.UNUSED"
                class="use-btn"
                @click="$router.push({ name: 'Cart' })"
              >去使用</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination">
        <button :disabled="pageNum <= 1" class="page-btn" @click="changePage(pageNum - 1)">上一页</button>
        <span class="page-info">{{ pageNum }} / {{ totalPages }}</span>
        <button :disabled="pageNum >= totalPages" class="page-btn" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的优惠券页（用户端）
 * 展示当前用户已领取的优惠券，按状态分Tab：未使用/已使用/已过期
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyCoupons } from '@shop/shared'
import {
  CouponType,
  UserCouponStatus,
  type UserCouponInfo,
} from '@shop/shared'

/** 优惠券列表 */
const list = ref<UserCouponInfo[]>([])
/** 总条数 */
const total = ref(0)
/** 每页条数 */
const pageSize = 10
/** 当前页码 */
const pageNum = ref(1)
/** 总页数 */
const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

/** 加载状态 */
const loading = ref(false)

/** 当前选中的状态Tab：0未使用 1已使用 2已过期 */
const activeStatus = ref<UserCouponStatus>(UserCouponStatus.UNUSED)

/** Tab 选项 */
const tabs = [
  { label: '未使用', value: UserCouponStatus.UNUSED },
  { label: '已使用', value: UserCouponStatus.USED },
  { label: '已过期', value: UserCouponStatus.EXPIRED },
]

/**
 * 加载优惠券列表
 */
async function loadData() {
  loading.value = true
  try {
    const res = await getMyCoupons({
      pageNum: pageNum.value,
      pageSize,
      status: activeStatus.value,
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载优惠券失败')
  } finally {
    loading.value = false
  }
}

/**
 * 切换Tab
 */
function switchTab(status: UserCouponStatus) {
  activeStatus.value = status
  pageNum.value = 1
  loadData()
}

/**
 * 翻页
 */
function changePage(page: number) {
  pageNum.value = page
  loadData()
}

/**
 * 格式化金额（去掉小数点末尾的0）
 * 例：20.00 → 20，20.50 → 20.5
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
 * 满减：满XX元可用；立减：无门槛；折扣：无门槛
 */
function getThresholdText(item: UserCouponInfo): string {
  if (item.couponType === CouponType.FULL_REDUCTION) {
    return `满${formatAmount(item.threshold)}元可用`
  }
  return '无门槛'
}

/**
 * 格式化日期（只取 yyyy-MM-dd）
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
.my-coupon-page {
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

.receive-btn {
  padding: 8px 16px;
  font-size: 14px;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.receive-btn:hover {
  background: var(--color-primary);
  color: #fff;
}

/* Tab */
.tab-group {
  display: flex;
  gap: var(--space-xs);
  margin-bottom: var(--space-lg);
}

.tab-btn {
  padding: 8px 24px;
  font-size: 14px;
  color: var(--color-text-secondary);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.tab-btn.active {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
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
  font-size: 14px;
  margin: 0 0 var(--space-md) 0;
}

.empty-btn {
  padding: 8px 24px;
  font-size: 14px;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.empty-btn:hover {
  background: var(--color-primary);
  color: #fff;
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

.coupon-card.disabled {
  opacity: 0.5;
  filter: grayscale(0.5);
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

.coupon-card.disabled .coupon-amount {
  background: linear-gradient(135deg, #b0bec5 0%, #78909c 100%);
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

.coupon-time {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0 0 8px 0;
}

.coupon-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.coupon-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
}

.coupon-status.status-0 {
  background: #e8f5e9;
  color: #2e7d32;
}

.coupon-status.status-1 {
  background: #eceff1;
  color: #455a64;
}

.coupon-status.status-2 {
  background: #ffebee;
  color: #c62828;
}

.use-btn {
  padding: 4px 16px;
  font-size: 12px;
  color: #fff;
  background: var(--color-primary);
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.use-btn:hover {
  opacity: 0.85;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  margin-top: var(--space-xl);
}

.page-btn {
  padding: 6px 16px;
  font-size: 14px;
  color: var(--color-text);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--transition-base);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 响应式 */
@media (max-width: 768px) {
  .my-coupon-page {
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
