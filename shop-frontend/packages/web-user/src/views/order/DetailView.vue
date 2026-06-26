<template>
  <!-- 订单详情页 - 参考V1原型设计 -->
  <div class="order-detail">
    <!-- 返回链接 -->
    <div class="back-link" @click="$router.push({ name: 'OrderList' })">← 返回订单列表</div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- 订单不存在 -->
    <div v-else-if="!order" class="empty-wrapper">
      <el-empty description="订单不存在" />
    </div>

    <template v-else>
      <!-- 订单状态 -->
      <div class="status-card">
        <div class="status-header">
          <span class="status-icon">{{ statusIcon }}</span>
          <div>
            <div class="status-text">{{ getStatusLabel(order.status) }}</div>
            <div class="status-desc">{{ statusDesc }}</div>
          </div>
        </div>
        <!-- 状态进度条（已取消/退款状态不显示进度条） -->
        <div v-if="showProgress" class="progress-bar">
          <div
            v-for="(step, index) in progressSteps"
            :key="index"
            class="progress-step"
            :class="step.state"
          >
            <div class="step-circle">
              <template v-if="step.state === 'done'">✓</template>
              <template v-else>{{ index + 1 }}</template>
            </div>
            <div class="step-label">{{ step.label }}</div>
            <div v-if="index < progressSteps.length - 1" class="progress-line" :class="step.state"></div>
          </div>
        </div>
      </div>

      <!-- 主体内容：左右分栏 -->
      <div class="detail-layout">
        <!-- 左侧：收货信息 & 物流 -->
        <div class="detail-left">
          <!-- 收货信息 -->
          <div class="info-card">
            <h3 class="card-title">收货信息</h3>
            <div class="address-info">
              <span>📍</span>
              <div>
                <div><span class="name">{{ order.address.name }}</span> {{ order.address.phone }}</div>
                <p class="address-text">{{ fullAddress }}</p>
              </div>
            </div>
          </div>

          <!-- 物流信息（只有发货后才有） -->
          <div v-if="order.deliveryTime" class="info-card">
            <h3 class="card-title">物流信息</h3>
            <div class="logistics-info">
              <p class="logistics-desc">商家已于 {{ formatDate(order.deliveryTime) }} 发货</p>
              <p v-if="order.status === ORDER_STATUS.SHIPPING" class="logistics-tip">您的包裹正在运输中，请耐心等待</p>
              <p v-if="order.receiveTime" class="logistics-tip">已于 {{ formatDate(order.receiveTime) }} 确认收货</p>
            </div>
          </div>
        </div>

        <!-- 右侧：商品清单 & 订单信息 -->
        <div class="detail-right">
          <!-- 商品清单 -->
          <div class="info-card">
            <h3 class="card-title">商品清单</h3>
            <div v-for="item in order.items" :key="item.id" class="goods-item">
              <img :src="item.productImage" :alt="item.productName" class="goods-image" loading="lazy" />
              <div class="goods-info">
                <p class="goods-name">{{ item.productName }}</p>
                <p class="goods-sku">{{ item.skuName }}</p>
                <!-- 已完成订单：根据评价状态显示"去评价"或"已评价" -->
                <div v-if="order.status === ORDER_STATUS.COMPLETED" class="goods-review">
                  <button
                    v-if="item.isReviewed === 0"
                    class="btn-review"
                    @click="goToReview(item.id, item.productId)"
                  >去评价</button>
                  <span v-else class="reviewed-text">已评价</span>
                </div>
              </div>
              <div class="goods-price-qty">
                <div>{{ formatPriceWithSymbol(item.price) }}</div>
                <div class="qty">×{{ item.quantity }}</div>
              </div>
            </div>
          </div>

          <!-- 订单信息 -->
          <div class="info-card">
            <h3 class="card-title">订单信息</h3>
            <div class="order-info-list">
              <div class="info-row"><span class="info-label">订单编号</span><span>{{ order.orderNo }}</span></div>
              <div class="info-row"><span class="info-label">下单时间</span><span>{{ formatDate(order.createTime) }}</span></div>
              <div v-if="order.payTime" class="info-row"><span class="info-label">付款时间</span><span>{{ formatDate(order.payTime) }}</span></div>
              <div v-if="order.deliveryTime" class="info-row"><span class="info-label">发货时间</span><span>{{ formatDate(order.deliveryTime) }}</span></div>
              <div v-if="order.receiveTime" class="info-row"><span class="info-label">收货时间</span><span>{{ formatDate(order.receiveTime) }}</span></div>
              <div v-if="order.cancelTime" class="info-row"><span class="info-label">取消时间</span><span>{{ formatDate(order.cancelTime) }}</span></div>
              <div v-if="order.cancelReason" class="info-row"><span class="info-label">取消原因</span><span>{{ order.cancelReason }}</span></div>
              <div v-if="order.remark" class="info-row"><span class="info-label">备注</span><span>{{ order.remark }}</span></div>
              <div class="info-row"><span class="info-label">商品总额</span><span>{{ formatPriceWithSymbol(order.totalAmount) }}</span></div>
              <div class="info-row"><span class="info-label">运费</span><span>{{ order.freightAmount > 0 ? formatPriceWithSymbol(order.freightAmount) : '免运费' }}</span></div>
              <div v-if="order.discountAmount > 0" class="info-row"><span class="info-label">优惠</span><span>-{{ formatPriceWithSymbol(order.discountAmount) }}</span></div>
              <div class="info-row total-row">
                <span>实付金额</span>
                <span class="total-price">{{ formatPriceWithSymbol(order.payAmount) }}</span>
              </div>
            </div>
          </div>

          <!-- 操作按钮（根据状态显示） -->
          <div v-if="showActions" class="action-buttons">
            <!-- 待付款：取消订单 + 立即付款 -->
            <button v-if="order.status === ORDER_STATUS.UNPAID" class="btn-outline" @click="handleCancel">取消订单</button>
            <button v-if="order.status === ORDER_STATUS.UNPAID" class="btn-primary" @click="handlePay">立即付款</button>

            <!-- 运输中：确认收货 -->
            <button v-if="order.status === ORDER_STATUS.SHIPPING" class="btn-primary" @click="handleConfirm">确认收货</button>

            <!-- 退款中/已退款：无操作 -->
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单详情页
 * 参考V1原型设计：状态进度条、收货信息、物流时间线、商品清单、订单信息
 *
 * 功能说明（小白版）：
 * 1. 从路由参数获取订单ID，调用后端接口获取订单详情
 * 2. 顶部展示订单当前状态和进度条（提交订单→付款→发货→收货→完成）
 * 3. 左侧展示收货地址和物流信息
 * 4. 右侧展示商品清单和订单金额明细
 * 5. 底部根据订单状态显示操作按钮（取消、付款、确认收货等）
 */

import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder, confirmReceive } from '@shop/shared'
import { formatPriceWithSymbol, formatDate } from '@shop/shared'
import { ORDER_STATUS, ORDER_STATUS_MAP } from '@shop/shared'
import type { OrderInfo, OrderStatus } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 是否正在加载 */
const loading = ref(true)
/** 订单详情数据 */
const order = ref<OrderInfo | null>(null)

/** 从路由参数获取订单ID */
const orderId = computed(() => Number(route.params.id))

/** 完整收货地址 */
const fullAddress = computed(() => {
  if (!order.value?.address) return ''
  const addr = order.value.address
  return `${addr.province}${addr.city}${addr.district}${addr.detail}`
})

/** 状态图标 */
const statusIcon = computed(() => {
  if (!order.value) return ''
  const status = order.value.status
  if (status === ORDER_STATUS.UNPAID) return '💰'
  if (status === ORDER_STATUS.CANCELLED) return '✕'
  if (status === ORDER_STATUS.REFUNDING || status === ORDER_STATUS.REFUNDED) return '↩'
  return '🚚'
})

/** 状态描述文字 */
const statusDesc = computed(() => {
  if (!order.value) return ''
  const status = order.value.status
  switch (status) {
    case ORDER_STATUS.UNPAID:
      return '请尽快完成支付，超时订单将自动取消'
    case ORDER_STATUS.CANCELLED:
      return order.value.cancelReason || '订单已取消'
    case ORDER_STATUS.PENDING_DELIVERY:
      return '商家正在备货，请耐心等待发货'
    case ORDER_STATUS.SHIPPING:
      return '您的包裹正在运输中，请耐心等待'
    case ORDER_STATUS.RECEIVED:
      return '订单已完成，感谢您的购买'
    case ORDER_STATUS.COMPLETED:
      return '订单已完成，感谢您的购买'
    case ORDER_STATUS.REFUNDING:
      return '退款申请处理中，请耐心等待'
    case ORDER_STATUS.REFUNDED:
      return '退款已完成'
    default:
      return ''
  }
})

/** 是否显示进度条（已取消和退款状态不显示） */
const showProgress = computed(() => {
  if (!order.value) return false
  const status = order.value.status
  return ![ORDER_STATUS.CANCELLED, ORDER_STATUS.REFUNDING, ORDER_STATUS.REFUNDED].includes(status)
})

/** 是否显示操作按钮 */
const showActions = computed(() => {
  if (!order.value) return false
  const status = order.value.status
  return [ORDER_STATUS.UNPAID, ORDER_STATUS.SHIPPING].includes(status)
})

/**
 * 进度条步骤
 * 根据订单状态计算每个步骤的状态：done(已完成)、current(当前)、(未到达)
 */
const progressSteps = computed(() => {
  if (!order.value) return []
  const status = order.value.status
  const steps = [
    { label: '提交订单', state: 'done' },
    { label: '付款成功', state: '' },
    { label: '商家发货', state: '' },
    { label: '确认收货', state: '' },
    { label: '交易完成', state: '' },
  ]

  // 根据状态设置步骤
  if (status >= ORDER_STATUS.PENDING_DELIVERY) {
    steps[1].state = 'done'
  }
  if (status >= ORDER_STATUS.SHIPPING) {
    steps[2].state = 'done'
  }
  if (status >= ORDER_STATUS.RECEIVED) {
    steps[3].state = 'done'
  }
  if (status >= ORDER_STATUS.COMPLETED) {
    steps[4].state = 'done'
  }

  // 设置当前步骤（第一个未完成的步骤）
  const currentIdx = steps.findIndex(s => s.state !== 'done')
  if (currentIdx !== -1) {
    steps[currentIdx].state = 'current'
  }

  return steps
})

/**
 * 获取订单状态的中文名称
 * @param status - 订单状态码
 * @returns 状态中文名
 */
const getStatusLabel = (status: OrderStatus): string => {
  return ORDER_STATUS_MAP[status]?.label || '未知'
}

/**
 * 取消订单
 */
const handleCancel = async () => {
  if (!order.value) return
  try {
    await ElMessageBox.confirm(
      '确定要取消这个订单吗？取消后不可恢复',
      '取消订单',
      { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' },
    )
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    fetchOrderDetail()
  } catch (error) {
    if (error !== 'cancel' && error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

/**
 * 立即付款
 */
const handlePay = async () => {
  if (!order.value) return
  try {
    // 跳转到收银台页面，用户在收银台选择支付方式
    router.push({ name: 'PaymentPay', query: { orderNo: order.value.orderNo } })
  } catch (error) {
    const msg = error instanceof Error ? error.message : '跳转支付失败'
    ElMessage.error(msg)
  }
}

/**
 * 确认收货
 */
const handleConfirm = async () => {
  if (!order.value) return
  try {
    await ElMessageBox.confirm(
      '确认已收到商品吗？确认后订单将完成',
      '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning' },
    )
    await confirmReceive(order.value.id)
    ElMessage.success('已确认收货')
    fetchOrderDetail()
  } catch (error) {
    if (error !== 'cancel' && error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

/**
 * 跳转到发表评价页
 * 把订单ID、订单项ID、商品ID传给评价页
 * @param orderItemId - 订单项ID
 * @param productId - 商品ID
 */
const goToReview = (orderItemId: number, productId: number) => {
  router.push({
    name: 'ReviewCreate',
    query: {
      orderId: String(orderId.value),
      orderItemId: String(orderItemId),
      productId: String(productId),
    },
  })
}

/**
 * 获取订单详情
 */
const fetchOrderDetail = async () => {
  loading.value = true
  try {
    const res = await getOrderDetail(orderId.value)
    order.value = res.data
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载订单详情失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchOrderDetail()
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.order-detail {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 80px;
}

.back-link {
  font-size: 13px;
  color: var(--color-text-muted);
  cursor: pointer;
  margin-bottom: 32px;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  display: inline-block;
}

.back-link:hover {
  color: var(--color-primary);
}

/* 加载中 */
.loading-wrapper {
  padding: 40px 0;
}

/* 空状态 */
.empty-wrapper {
  padding: 120px 0;
}

/* ==================== 订单状态卡片 ==================== */
.status-card {
  padding: 0 0 48px;
  margin-bottom: 48px;
  border-bottom: 1px solid var(--color-border);
}

.status-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 48px;
}

.status-icon {
  font-size: 32px;
  color: var(--color-accent);
}

.status-text {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-text);
  letter-spacing: -0.01em;
  margin-bottom: 4px;
}

.status-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

/* ==================== 进度条 ==================== */
.progress-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
  flex: 1;
}

.step-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--color-bg-tertiary);
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all var(--transition-base);
}

.progress-step.done .step-circle {
  background: var(--color-primary);
  color: #fff;
}

.progress-step.current .step-circle {
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(26, 26, 26, 0.1);
}

.step-label {
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
  text-align: center;
}

.progress-step.done .step-label,
.progress-step.current .step-label {
  color: var(--color-text);
}

.progress-line {
  position: absolute;
  top: 18px;
  left: 50%;
  right: -50%;
  height: 1px;
  background: var(--color-border);
}

.progress-line.done {
  background: var(--color-primary);
}

/* ==================== 左右分栏 ==================== */
.detail-layout {
  display: grid;
  grid-template-columns: 1fr 440px;
  gap: 48px;
  align-items: flex-start;
}

.detail-left {
  display: flex;
  flex-direction: column;
  gap: 48px;
}

.detail-right {
  display: flex;
  flex-direction: column;
  gap: 48px;
}

/* ==================== 通用信息卡片 ==================== */
.info-card {
  padding: 0;
}

.card-title {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 24px;
  letter-spacing: -0.01em;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

/* ==================== 收货信息 ==================== */
.address-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 14px;
}

.address-info span:first-child {
  color: var(--color-accent);
}

.name {
  font-weight: 500;
  color: var(--color-text);
}

.address-text {
  color: var(--color-text-secondary);
  margin: 8px 0 0;
  line-height: 1.6;
}

/* ==================== 物流信息 ==================== */
.logistics-info {
  font-size: 13px;
}

.logistics-desc {
  color: var(--color-text);
  margin: 0 0 8px;
}

.logistics-tip {
  color: var(--color-text-muted);
  margin: 0;
}

/* ==================== 商品清单 ==================== */
.goods-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border);
}

.goods-item:last-child {
  border-bottom: none;
}

.goods-image {
  width: 72px;
  height: 72px;
  object-fit: cover;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-name {
  font-size: 14px;
  color: var(--color-text);
  margin: 0 0 8px;
  letter-spacing: 0.01em;
}

.goods-sku {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
}

.goods-price-qty {
  text-align: right;
  font-size: 14px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.goods-price-qty .qty {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-top: 4px;
}

/* 评价入口区：去评价按钮 / 已评价文字 */
.goods-review {
  margin-top: 8px;
}

.btn-review {
  padding: 6px 16px;
  font-size: 12px;
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
}

.btn-review:hover {
  background: var(--color-primary);
  color: #fff;
}

.reviewed-text {
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

/* ==================== 订单信息 ==================== */
.order-info-list {
  display: flex;
  flex-direction: column;
  font-size: 13px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
}

.info-label {
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.total-row {
  border-top: 1px solid var(--color-border);
  padding-top: 16px;
  margin-top: 4px;
  font-weight: 500;
  color: var(--color-text);
}

.total-price {
  color: var(--color-accent);
  font-size: 22px;
  font-weight: 300;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

/* ==================== 操作按钮 ==================== */
.action-buttons {
  display: flex;
  gap: 16px;
}

.btn-outline {
  flex: 1;
  background: transparent;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  padding: 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.btn-primary {
  flex: 1;
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  padding: 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.btn-primary:hover {
  background: transparent;
  color: var(--color-primary);
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 1024px) {
  .detail-layout {
    grid-template-columns: 1fr;
    gap: 48px;
  }
}

@media (max-width: 768px) {
  .order-detail {
    padding: 24px 0 40px;
  }

  .status-text {
    font-size: 22px;
  }

  .progress-bar {
    flex-wrap: wrap;
    gap: 24px;
  }

  .progress-line {
    display: none;
  }

  .goods-item {
    gap: 12px;
  }

  .goods-image {
    width: 64px;
    height: 64px;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
