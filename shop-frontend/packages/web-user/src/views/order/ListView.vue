<template>
  <!-- 订单列表页 - 参考V1原型设计 -->
  <div class="order-list">
    <h1 class="page-title">我的订单</h1>

    <!-- Tab切换：全部/待付款/待发货/运输中/已收货/已完成 -->
    <div class="tab-bar">
      <span
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-item', { active: currentTab === tab.key }]"
        @click="switchTab(tab.key)"
      >{{ tab.label }}</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 空状态 -->
    <div v-else-if="orderList.length === 0" class="empty-wrapper">
      <el-empty description="暂无订单">
        <button class="btn-shopping" @click="$router.push({ name: 'Home' })">去购物</button>
      </el-empty>
    </div>

    <!-- 订单卡片列表 -->
    <div v-else class="order-cards">
      <div
        v-for="order in orderList"
        :key="order.id"
        class="order-card"
      >
        <!-- 店铺名+订单状态 -->
        <div class="order-header">
          <div class="order-shop">
            <span class="shop-name">{{ order.merchantName || 'ShopMall旗舰店' }}</span>
          </div>
          <span class="order-status" :style="{ color: getStatusColor(order.status) }">
            {{ getStatusLabel(order.status) }}
          </span>
        </div>

        <!-- 商品信息（点击跳转详情） -->
        <div class="order-body" @click="goDetail(order.id)">
          <img :src="getFirstImage(order)" :alt="getFirstName(order)" class="order-image" loading="lazy" />
          <div class="order-info">
            <p class="order-name">{{ getFirstName(order) }}</p>
            <p v-if="order.items.length > 1" class="order-sku">等{{ order.items.length }}件商品</p>
            <p v-else-if="order.items[0]" class="order-sku">{{ order.items[0].skuName }}</p>
          </div>
          <div class="order-price-info">
            <div class="order-price">{{ formatPriceWithSymbol(order.payAmount) }}</div>
            <div class="order-qty">共{{ getTotalQuantity(order) }}件</div>
          </div>
        </div>

        <!-- 操作栏 -->
        <div class="order-footer">
          <span class="order-remark">订单号：{{ order.orderNo }}</span>
          <div class="order-actions">
            <!-- 待付款：取消订单 + 立即付款 -->
            <button v-if="order.status === ORDER_STATUS.UNPAID" class="btn-outline" @click="handleCancel(order)">取消订单</button>
            <button v-if="order.status === ORDER_STATUS.UNPAID" class="btn-primary" @click="handlePay(order)">立即付款</button>

            <!-- 运输中：确认收货 -->
            <button v-if="order.status === ORDER_STATUS.SHIPPING" class="btn-primary" @click="handleConfirm(order)">确认收货</button>

            <!-- 已收货/已完成/已取消：再次购买 -->
            <button
              v-if="[ORDER_STATUS.RECEIVED, ORDER_STATUS.COMPLETED, ORDER_STATUS.CANCELLED].includes(order.status)"
              class="btn-outline"
              @click="goDetail(order.id)"
            >查看详情</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="pagination-wrapper">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单列表页
 * 参考V1原型设计：Tab切换、订单卡片、状态标签、操作按钮
 *
 * 功能说明（小白版）：
 * 1. 顶部Tab按订单状态筛选（全部/待付款/待发货/运输中/已收货/已完成）
 * 2. 每个订单卡片展示：商家名、订单状态、第一件商品信息、总金额
 * 3. 根据订单状态显示不同的操作按钮（取消、付款、确认收货等）
 * 4. 点击订单卡片可以跳转到订单详情页
 * 5. 底部分页，每页显示10条
 */

import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, cancelOrder, confirmReceive } from '@shop/shared'
import { formatPriceWithSymbol } from '@shop/shared'
import { ORDER_STATUS, ORDER_STATUS_MAP } from '@shop/shared'
import type { OrderInfo, OrderStatus } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 是否正在加载 */
const loading = ref(true)
/** 订单列表 */
const orderList = ref<OrderInfo[]>([])
/** 当前选中的Tab（用订单状态值，-1表示全部） */
const currentTab = ref<number>(-1)
/** 当前页码 */
const pageNum = ref(1)
/** 每页条数 */
const pageSize = 10
/** 总记录数 */
const total = ref(0)

/** Tab选项：key是状态值，-1表示全部 */
const tabs = [
  { key: -1, label: '全部' },
  { key: ORDER_STATUS.UNPAID, label: '待付款' },
  { key: ORDER_STATUS.PENDING_DELIVERY, label: '待发货' },
  { key: ORDER_STATUS.SHIPPING, label: '运输中' },
  { key: ORDER_STATUS.RECEIVED, label: '已收货' },
  { key: ORDER_STATUS.COMPLETED, label: '已完成' },
]

/**
 * 获取订单状态的中文名称
 * @param status - 订单状态码
 * @returns 状态中文名，如"待付款"
 */
const getStatusLabel = (status: OrderStatus): string => {
  return ORDER_STATUS_MAP[status]?.label || '未知'
}

/**
 * 获取订单状态对应的颜色
 * @param status - 订单状态码
 * @returns 颜色值
 */
const getStatusColor = (status: OrderStatus): string => {
  return ORDER_STATUS_MAP[status]?.color || '#909399'
}

/**
 * 获取订单第一件商品的图片
 * @param order - 订单信息
 * @returns 图片URL
 */
const getFirstImage = (order: OrderInfo): string => {
  return order.items[0]?.productImage || '/placeholder.svg'
}

/**
 * 获取订单第一件商品的名称
 * @param order - 订单信息
 * @returns 商品名称
 */
const getFirstName = (order: OrderInfo): string => {
  return order.items[0]?.productName || '未知商品'
}

/**
 * 计算订单商品总数量
 * @param order - 订单信息
 * @returns 总数量
 */
const getTotalQuantity = (order: OrderInfo): number => {
  return order.items.reduce((sum, item) => sum + item.quantity, 0)
}

/**
 * 加载订单列表数据
 * 调用后端接口获取当前Tab状态下的订单
 */
const fetchOrders = async () => {
  loading.value = true
  try {
    const params: { pageNum: number; pageSize: number; status?: OrderStatus } = {
      pageNum: pageNum.value,
      pageSize,
    }
    // currentTab为-1时表示"全部"，不传status参数
    if (currentTab.value !== -1) {
      params.status = currentTab.value as OrderStatus
    }

    const res = await getOrderList(params)
    orderList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载订单失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

/**
 * 切换Tab
 * 切换时重置页码为第1页，然后重新加载
 * @param status - 要切换的状态值
 */
const switchTab = (status: number) => {
  if (currentTab.value === status) return
  currentTab.value = status
  pageNum.value = 1
  fetchOrders()
}

/**
 * 翻页
 * @param page - 新的页码
 */
const handlePageChange = (page: number) => {
  pageNum.value = page
  fetchOrders()
}

/**
 * 跳转到订单详情页
 * @param id - 订单ID
 */
const goDetail = (id: number) => {
  router.push({ name: 'OrderDetail', params: { id } })
}

/**
 * 取消订单（待付款状态可用）
 * @param order - 要取消的订单
 */
const handleCancel = async (order: OrderInfo) => {
  try {
    await ElMessageBox.confirm(
      '确定要取消这个订单吗？取消后不可恢复',
      '取消订单',
      { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' },
    )
    await cancelOrder(order.id)
    ElMessage.success('订单已取消')
    fetchOrders()
  } catch (error) {
    // 用户点击"再想想"或取消失败
    if (error !== 'cancel' && error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

/**
 * 立即付款（待付款状态可用）
 * 调用支付接口创建支付单，然后跳转到支付结果页
 * @param order - 要付款的订单
 */
const handlePay = async (order: OrderInfo) => {
  try {
    // 跳转到收银台页面，用户在收银台选择支付方式
    router.push({ name: 'PaymentPay', query: { orderNo: order.orderNo } })
  } catch (error) {
    const msg = error instanceof Error ? error.message : '跳转支付失败'
    ElMessage.error(msg)
  }
}

/**
 * 确认收货（运输中状态可用）
 * @param order - 要确认收货的订单
 */
const handleConfirm = async (order: OrderInfo) => {
  try {
    await ElMessageBox.confirm(
      '确认已收到商品吗？确认后订单将完成',
      '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning' },
    )
    await confirmReceive(order.id)
    ElMessage.success('已确认收货')
    fetchOrders()
  } catch (error) {
    if (error !== 'cancel' && error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

/** 页面加载时获取第一页订单，并根据路由参数设置初始Tab */
onMounted(() => {
  // 从路由参数读取状态筛选（用户中心跳转时传的 ?status=X）
  const statusParam = route.query.status
  if (statusParam !== undefined) {
    currentTab.value = Number(statusParam)
  }
  fetchOrders()
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.order-list {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 80px;
}

.page-title {
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 48px;
  letter-spacing: -0.01em;
}

/* 加载中 */
.loading-wrapper {
  padding: 40px 0;
}

/* 空状态 */
.empty-wrapper {
  padding: 120px 0;
}

.btn-shopping {
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  padding: 14px 40px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  margin-top: 16px;
}

.btn-shopping:hover {
  background: var(--color-primary);
  color: #fff;
}

/* ==================== Tab切换 ==================== */
.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 48px;
  gap: 8px;
}

.tab-item {
  padding: 16px 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-base);
  border-bottom: 2px solid transparent;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.tab-item:hover {
  color: var(--color-primary);
}

.tab-item.active {
  color: var(--color-primary);
  font-weight: 500;
  border-bottom-color: var(--color-primary);
}

/* ==================== 订单卡片 ==================== */
.order-cards {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.order-card {
  border: 1px solid var(--color-border);
  transition: border-color var(--transition-base);
}

.order-card:hover {
  border-color: var(--color-text-muted);
}

.order-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border);
}

.order-shop {
  display: flex;
  align-items: center;
  gap: 8px;
}

.shop-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.order-status {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

/* 订单商品信息 */
.order-body {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  cursor: pointer;
  transition: background var(--transition-base);
}

.order-body:hover {
  background: var(--color-bg-secondary);
}

.order-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  flex-shrink: 0;
}

.order-info {
  flex: 1;
  min-width: 0;
}

.order-name {
  font-size: 14px;
  color: var(--color-text);
  margin: 0 0 8px;
  letter-spacing: 0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-sku {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
}

.order-price-info {
  text-align: right;
  flex-shrink: 0;
}

.order-price {
  font-size: 15px;
  font-weight: 400;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.order-qty {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
  letter-spacing: 0.02em;
}

/* 订单操作栏 */
.order-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
}

.order-remark {
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.order-actions {
  display: flex;
  gap: 12px;
}

/* 极简边框按钮 */
.btn-outline {
  background: transparent;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  padding: 8px 20px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* 黑底白字按钮 */
.btn-primary {
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  padding: 8px 20px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.btn-primary:hover {
  background: transparent;
  color: var(--color-primary);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 48px;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .order-list {
    padding: 24px 0 40px;
  }

  .page-title {
    font-size: 24px;
    margin-bottom: 24px;
  }

  .tab-bar {
    overflow-x: auto;
    margin-bottom: 24px;
  }

  .tab-item {
    padding: 12px 16px;
    white-space: nowrap;
  }

  .order-body {
    gap: 12px;
    padding: 16px;
  }

  .order-image {
    width: 64px;
    height: 64px;
  }

  .order-footer {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .order-actions {
    justify-content: flex-end;
  }
}
</style>
