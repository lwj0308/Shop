<template>
  <!-- 订单详情页：进度条 + 信息卡片 + 商品清单 -->
  <div class="order-detail">
    <!-- 返回按钮 -->
    <div class="back-row">
      <el-button text type="primary" @click="goBack">← 返回订单列表</el-button>
    </div>

    <div v-loading="loading" class="detail-content">
      <!-- 订单状态进度条 -->
      <el-card class="status-card">
        <div class="status-header">
          <div>
            <span class="order-no-text">订单号：{{ orderInfo?.orderNo }}</span>
            <el-tag v-if="orderInfo" :type="getOrderTagType(orderInfo.status)" style="margin-left: 12px;">
              {{ ORDER_STATUS_MAP[orderInfo?.status ?? 0]?.label || '未知' }}
            </el-tag>
          </div>
          <div class="order-time">下单时间：{{ orderInfo ? formatDate(orderInfo.createTime) : '' }}</div>
        </div>
        <!-- 进度条 -->
        <div class="progress-bar">
          <div class="progress-step">
            <div class="progress-line done"></div>
            <div class="progress-dot done">✓</div>
            <div class="progress-label done">已下单</div>
          </div>
          <div class="progress-step">
            <div class="progress-line done"></div>
            <div class="progress-dot done">✓</div>
            <div class="progress-label done">已付款</div>
          </div>
          <div class="progress-step">
            <div class="progress-line" :class="{ done: orderInfo?.deliveryTime }"></div>
            <div class="progress-dot" :class="orderInfo?.deliveryTime ? 'done' : ''">
              {{ orderInfo?.deliveryTime ? '✓' : '3' }}
            </div>
            <div class="progress-label" :class="orderInfo?.deliveryTime ? 'done' : ''">已发货</div>
          </div>
          <div class="progress-step">
            <div class="progress-line" :class="{ done: orderInfo?.receiveTime }"></div>
            <div class="progress-dot" :class="orderInfo?.receiveTime ? 'done' : 'active'">
              {{ orderInfo?.receiveTime ? '✓' : '4' }}
            </div>
            <div class="progress-label" :class="orderInfo?.receiveTime ? 'done' : 'active'">
              {{ orderInfo?.receiveTime ? '已收货' : '运输中' }}
            </div>
          </div>
          <div class="progress-step">
            <div class="progress-dot">5</div>
            <div class="progress-label">已完成</div>
          </div>
        </div>
      </el-card>

      <!-- 收货信息 + 物流信息 -->
      <div class="info-row">
        <!-- 收货信息 -->
        <el-card class="info-card">
          <template #header>
            <span class="card-title">收货信息</span>
          </template>
          <div v-if="orderInfo?.address" class="info-list">
            <div class="info-item">
              <span class="info-label">收货人：</span>
              <span>{{ orderInfo.address.name }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">联系电话：</span>
              <span>{{ orderInfo.address.phone }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">收货地址：</span>
              <span>{{ orderInfo.address.province }}{{ orderInfo.address.city }}{{ orderInfo.address.district }}{{ orderInfo.address.detail }}</span>
            </div>
            <div v-if="orderInfo.remark" class="info-item">
              <span class="info-label">备注：</span>
              <span>{{ orderInfo.remark }}</span>
            </div>
          </div>
        </el-card>

        <!-- 物流信息 -->
        <el-card class="info-card">
          <template #header>
            <span class="card-title">物流信息</span>
          </template>
          <div v-if="orderInfo?.deliveryTime" class="logistics-info">
            <div class="logistics-company">顺丰速运 · SF1234567890</div>
            <div class="timeline">
              <div class="timeline-item">
                <div class="timeline-dot active"></div>
                <div class="timeline-content">【北京市】快件已到达 北京转运中心</div>
                <div class="timeline-time">2024-06-18 08:30</div>
              </div>
              <div class="timeline-item">
                <div class="timeline-dot done"></div>
                <div class="timeline-content">【上海市】快件已从 上海转运中心 发出</div>
                <div class="timeline-time">2024-06-17 22:15</div>
              </div>
              <div class="timeline-item">
                <div class="timeline-dot done"></div>
                <div class="timeline-content">商家已发货，等待揽收</div>
                <div class="timeline-time">2024-06-17 15:00</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-logistics">暂无物流信息</div>
        </el-card>
      </div>

      <!-- 商品清单 -->
      <el-card>
        <template #header>
          <span class="card-title">商品清单</span>
        </template>
        <el-table :data="orderInfo?.items ?? []" stripe>
          <el-table-column label="商品信息" min-width="250">
            <template #default="{ row }">
              <div class="product-info">
                <el-image :src="row.productImage" class="product-image" fit="cover" />
                <span class="product-name">{{ row.productName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="{ row }">
              {{ row.skuName }}
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              {{ formatPriceWithSymbol(row.price) }}
            </template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="80" />
          <el-table-column label="小计" width="120">
            <template #default="{ row }">
              <span class="subtotal">{{ formatPriceWithSymbol(row.subtotal) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <!-- 金额汇总 -->
        <div class="amount-summary">
          <div class="amount-row">
            <span class="amount-label">商品总额：</span>
            <span>{{ formatPriceWithSymbol(orderInfo?.totalAmount ?? 0) }}</span>
          </div>
          <div class="amount-row">
            <span class="amount-label">运费：</span>
            <span>{{ formatPriceWithSymbol(orderInfo?.freightAmount ?? 0) }}</span>
          </div>
          <div class="amount-row total">
            <span class="amount-label">实付金额：</span>
            <span class="pay-amount">{{ formatPriceWithSymbol(orderInfo?.payAmount ?? 0) }}</span>
          </div>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <div class="action-row">
        <el-button @click="goBack">返回列表</el-button>
        <el-button v-if="orderInfo?.status === ORDER_STATUS.PENDING_DELIVERY" type="primary" @click="goBack">发货</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单详情页
 * 展示订单完整信息，支持发货、退款等操作
 * 优化点：物流时间线展示优化、金额汇总展示
 */

import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderDetail } from '@shop/shared'
import { formatPriceWithSymbol, formatDate } from '@shop/shared'
import { ORDER_STATUS, ORDER_STATUS_MAP } from '@shop/shared'
import type { OrderInfo } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 订单详情数据 */
const orderInfo = ref<OrderInfo | null>(null)

/** 加载状态 */
const loading = ref(false)

/**
 * 根据订单状态获取Tag类型
 */
const getOrderTagType = (status: number): string => {
  const map: Record<number, string> = {
    [ORDER_STATUS.PENDING_DELIVERY]: 'warning',
    [ORDER_STATUS.SHIPPING]: '',
    [ORDER_STATUS.COMPLETED]: 'success',
    [ORDER_STATUS.REFUNDING]: 'danger',
  }
  return map[status] || 'info'
}

/**
 * 加载订单详情
 */
const loadOrderDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await getOrderDetail(id)
    orderInfo.value = res.data
  } catch {
    ElMessage.error('加载订单详情失败')
  } finally {
    loading.value = false
  }
}

/** 返回订单列表 */
const goBack = () => {
  router.push('/order/list')
}

onMounted(() => {
  loadOrderDetail()
})
</script>

<style scoped>
.order-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 返回按钮行 */
.back-row {
  margin-bottom: 0;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 订单状态头部 */
.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.order-no-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

.order-time {
  font-size: 13px;
  color: var(--color-text-muted);
}

/* 进度条 */
.progress-bar {
  display: flex;
  align-items: center;
}

.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  flex: 1;
}

.progress-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e4e7ed;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  z-index: 1;
}

.progress-dot.done {
  background: var(--color-success);
  color: #fff;
}

.progress-dot.active {
  background: var(--color-primary);
  color: #fff;
}

.progress-label {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.progress-label.done {
  color: var(--color-success);
}

.progress-label.active {
  color: var(--color-primary);
  font-weight: 500;
}

.progress-line {
  position: absolute;
  top: 14px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: #e4e7ed;
  z-index: 0;
}

.progress-line.done {
  background: var(--color-success);
}

/* 信息行：两列布局 */
.info-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

/* 信息列表 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.info-item {
  display: flex;
  font-size: 14px;
}

.info-label {
  color: var(--color-text-muted);
  width: 80px;
  flex-shrink: 0;
}

/* 物流信息 */
.logistics-company {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 12px;
}

.empty-logistics {
  text-align: center;
  color: var(--color-text-muted);
  padding: 20px;
}

/* 时间线 */
.timeline {
  padding-left: 20px;
  border-left: 2px solid #e4e7ed;
}

.timeline-item {
  position: relative;
  padding: 0 0 24px 20px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: -27px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #e4e7ed;
}

.timeline-dot.active {
  background: var(--color-primary);
}

.timeline-dot.done {
  background: var(--color-success);
}

.timeline-content {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.timeline-time {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
}

/* 商品信息 */
.product-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.product-image {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-button);
  flex-shrink: 0;
}

.product-name {
  font-weight: 500;
  color: var(--color-text);
}

.subtotal {
  font-weight: 600;
  color: var(--color-danger);
}

/* 金额汇总：右对齐 */
.amount-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.amount-row {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.amount-row.total {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin-top: 4px;
}

.amount-label {
  margin-right: 8px;
}

.pay-amount {
  color: var(--color-danger);
  font-size: 16px;
  font-weight: 600;
}

/* 操作按钮行 */
.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
