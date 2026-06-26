<template>
  <!-- 结算确认页 - 参考V1原型设计 -->
  <div class="order-confirm">
    <h1 class="page-title">确认订单</h1>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else>
      <!-- 无选中商品时的空状态 -->
      <div v-if="checkedItems.length === 0" class="empty-wrapper">
        <el-empty description="没有可结算的商品">
          <button class="btn-back" @click="$router.push({ name: 'Cart' })">返回购物车</button>
        </el-empty>
      </div>

      <template v-else>
        <!-- 收货地址卡片 -->
        <div class="address-card">
          <div class="card-header">
            <h3 class="card-title">收货地址</h3>
            <span v-if="addressList.length > 1" class="switch-btn" @click="showAddressDialog = true">切换地址</span>
          </div>

          <!-- 有地址：展示当前选中的地址 -->
          <div v-if="currentAddress" class="address-info">
            <span class="address-icon">📍</span>
            <div class="address-detail">
              <div class="address-user">
                <span class="user-name">{{ currentAddress.name }}</span>
                <span class="user-phone">{{ currentAddress.phone }}</span>
                <span v-if="currentAddress.isDefault === 1" class="tag-green">默认</span>
              </div>
              <p class="address-text">{{ fullAddress(currentAddress) }}</p>
            </div>
          </div>

          <!-- 无地址：提示添加 -->
          <div v-else class="address-empty">
            <span class="address-empty-text">暂无收货地址，请先添加</span>
            <button class="btn-add-address" @click="showAddressDialog = true">添加地址</button>
          </div>
        </div>

        <!-- 商品清单 -->
        <div class="goods-card">
          <div class="shop-header">
            <span class="shop-name">{{ merchantName }}</span>
          </div>
          <div v-for="item in checkedItems" :key="item.id" class="goods-item">
            <img :src="item.productImage" :alt="item.productName" class="goods-image" loading="lazy" />
            <div class="goods-info">
              <p class="goods-name">{{ item.productName }}</p>
              <p class="goods-sku">{{ item.skuName }}</p>
            </div>
            <div class="goods-price-qty">{{ formatPriceWithSymbol(item.price) }} × {{ item.quantity }}</div>
            <div class="goods-subtotal">{{ formatPriceWithSymbol(item.subtotal) }}</div>
          </div>
        </div>

        <!-- 费用明细 -->
        <div class="fee-card">
          <div class="fee-item">
            <span class="fee-label">运费</span>
            <span class="fee-value free">免运费</span>
          </div>
          <div class="fee-item">
            <span class="fee-label">商品件数</span>
            <span class="fee-value">共 {{ totalQuantity }} 件</span>
          </div>
          <div class="fee-item">
            <span class="fee-label">支付方式</span>
            <span class="fee-value">在线支付</span>
          </div>
          <!-- 优惠券选择 -->
          <div class="fee-item coupon-item" @click="showCouponDialog = true">
            <span class="fee-label">优惠券</span>
            <span class="fee-value coupon-value">
              <template v-if="selectedCoupon">
                <span class="coupon-discount">-{{ formatPriceWithSymbol(discountAmount) }}</span>
                <span class="coupon-name-text">{{ selectedCoupon.couponName }}</span>
              </template>
              <template v-else-if="usableCoupons.length > 0">
                <span class="coupon-available">{{ usableCoupons.length }}张可用</span>
              </template>
              <template v-else>
                <span class="coupon-none">暂无可用</span>
              </template>
              <span class="coupon-arrow">›</span>
            </span>
          </div>
          <!-- 优惠金额汇总（选了券才显示） -->
          <div v-if="selectedCoupon" class="fee-item discount-summary">
            <span class="fee-label">优惠</span>
            <span class="fee-value discount-value">-{{ formatPriceWithSymbol(discountAmount) }}</span>
          </div>
        </div>

        <!-- 提交订单 -->
        <div class="submit-card">
          <div class="submit-info">
            共{{ totalQuantity }}件商品，实付：<span class="total-price">{{ formatPriceWithSymbol(payAmount) }}</span>
          </div>
          <button class="submit-btn" :disabled="!currentAddress || submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </div>
      </template>
    </template>

    <!-- 地址选择弹窗 -->
    <el-dialog v-model="showAddressDialog" title="选择收货地址" width="520px">
      <div class="address-dialog-list">
        <div
          v-for="addr in addressList"
          :key="addr.id"
          class="address-dialog-item"
          :class="{ active: currentAddress?.id === addr.id }"
          @click="selectAddress(addr)"
        >
          <div class="address-dialog-info">
            <div class="address-dialog-user">
              <span class="user-name">{{ addr.name }}</span>
              <span class="user-phone">{{ addr.phone }}</span>
              <span v-if="addr.isDefault === 1" class="tag-green">默认</span>
            </div>
            <p class="address-dialog-text">{{ fullAddress(addr) }}</p>
          </div>
          <span v-if="currentAddress?.id === addr.id" class="check-icon">✓</span>
        </div>
      </div>
    </el-dialog>

    <!-- 优惠券选择弹窗 -->
    <el-dialog v-model="showCouponDialog" title="选择优惠券" width="520px">
      <div class="coupon-dialog-list">
        <!-- 不使用优惠券选项 -->
        <div
          class="coupon-dialog-item"
          :class="{ active: selectedCoupon === null }"
          @click="selectCoupon(null)"
        >
          <span class="coupon-dialog-name">不使用优惠券</span>
          <span v-if="selectedCoupon === null" class="check-icon">✓</span>
        </div>
        <!-- 可用优惠券列表 -->
        <div
          v-for="coupon in usableCoupons"
          :key="coupon.id"
          class="coupon-dialog-item"
          :class="{ active: selectedCoupon?.id === coupon.id }"
          @click="selectCoupon(coupon)"
        >
          <div class="coupon-dialog-info">
            <div class="coupon-dialog-top">
              <span class="coupon-dialog-amount">{{ getCouponDesc(coupon) }}</span>
              <span class="coupon-dialog-name">{{ coupon.couponName }}</span>
            </div>
            <p class="coupon-dialog-time">有效期：{{ coupon.validStartTime?.replace('T', ' ').substring(0, 10) }} 至 {{ coupon.validEndTime?.replace('T', ' ').substring(0, 10) }}</p>
          </div>
          <span v-if="selectedCoupon?.id === coupon.id" class="check-icon">✓</span>
        </div>
        <!-- 无可用券时的提示 -->
        <div v-if="usableCoupons.length === 0" class="coupon-dialog-empty">
          暂无可用优惠券
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 结算确认页
 * 参考V1原型设计：地址卡片、商品清单、费用明细、提交订单按钮
 *
 * 功能说明（小白版）：
 * 1. 进入页面后，从购物车获取已勾选的商品，从用户服务获取收货地址列表
 * 2. 用户可以切换收货地址（如果有多个）
 * 3. 点击"提交订单"后，调用后端创建订单接口，拿到订单号
 * 4. 创建订单成功后，调用支付接口发起支付，然后跳转到支付结果页
 */

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useCart } from '@shop/shared'
import { getAddressList, createOrder, getUsableCoupons } from '@shop/shared'
import { formatPriceWithSymbol } from '@shop/shared'
import type { AddressInfo, UserCouponInfo } from '@shop/shared'

const router = useRouter()
const { cartList, fetchCartList } = useCart()

/** 是否正在加载页面数据 */
const loading = ref(true)
/** 是否正在提交订单（防止重复点击） */
const submitting = ref(false)
/** 是否显示地址选择弹窗 */
const showAddressDialog = ref(false)
/** 是否显示优惠券选择弹窗 */
const showCouponDialog = ref(false)

/** 收货地址列表 */
const addressList = ref<AddressInfo[]>([])
/** 当前选中的收货地址 */
const currentAddress = ref<AddressInfo | null>(null)

/** 可用优惠券列表 */
const usableCoupons = ref<UserCouponInfo[]>([])
/** 当前选中的优惠券（null表示不使用优惠券） */
const selectedCoupon = ref<UserCouponInfo | null>(null)

/**
 * 已勾选的购物车商品（只有勾选的商品才会进入结算）
 * 从购物车列表中过滤出 checked 为 true 的商品
 */
const checkedItems = computed(() => {
  return cartList.value.filter(item => item.checked)
})

/** 商家名称（取第一个商品的商家名，目前购物车按单商家设计） */
const merchantName = computed(() => {
  return 'ShopMall旗舰店'
})

/** 已选商品总数量 */
const totalQuantity = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + item.quantity, 0)
})

/** 已选商品总金额（分） */
const checkedTotalAmount = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + item.subtotal, 0)
})

/**
 * 优惠金额（分）
 * 优惠券的 discountAmount 单位是"元"，乘以100转成"分"和订单金额统一单位
 * 如果没选优惠券则优惠0
 */
const discountAmount = computed(() => {
  if (!selectedCoupon.value || !selectedCoupon.value.discountAmount) return 0
  return Math.round(selectedCoupon.value.discountAmount * 100)
})

/** 实付金额（分）= 商品总额 - 优惠金额 */
const payAmount = computed(() => {
  return Math.max(0, checkedTotalAmount.value - discountAmount.value)
})

/**
 * 拼接完整的收货地址字符串
 * 把省、市、区、详细地址拼在一起，方便用户一眼看全
 * @param addr - 地址信息
 * @returns 完整地址字符串，如"广东省深圳市南山区科技园路1号"
 */
const fullAddress = (addr: AddressInfo): string => {
  return `${addr.province}${addr.city}${addr.district}${addr.detail}`
}

/**
 * 选择收货地址（在弹窗中点击某个地址时调用）
 * @param addr - 选中的地址
 */
const selectAddress = (addr: AddressInfo) => {
  currentAddress.value = addr
  showAddressDialog.value = false
}

/**
 * 选择优惠券（在弹窗中点击某张券时调用）
 * @param coupon - 选中的优惠券（传null表示不使用优惠券）
 */
const selectCoupon = (coupon: UserCouponInfo | null) => {
  selectedCoupon.value = coupon
  showCouponDialog.value = false
}

/**
 * 获取优惠券简短描述（弹窗列表展示用）
 * @param coupon - 优惠券信息
 */
const getCouponDesc = (coupon: UserCouponInfo): string => {
  if (coupon.couponType === 2) {
    // 折扣
    return `${(Number(coupon.amount) * 10).toFixed(1).replace(/\.0$/, '')}折`
  }
  return `¥${Number(coupon.amount).toFixed(2).replace(/\.?0+$/, '')}`
}

/**
 * 提交订单
 * 这是下单的核心流程：
 * 1. 校验是否选择了收货地址
 * 2. 调用后端创建订单接口，传入地址ID、购物车项ID列表和优惠券ID
 * 3. 创建成功后，跳转到收银台页面
 */
const handleSubmit = async () => {
  if (!currentAddress.value) {
    ElMessage.warning('请先选择收货地址')
    return
  }

  submitting.value = true
  try {
    // 创建订单，传入收货地址ID、购物车项ID列表和可选的优惠券ID
    const cartItemIds = checkedItems.value.map(item => item.id)
    const orderRes = await createOrder({
      addressId: currentAddress.value.id,
      cartItemIds,
      userCouponId: selectedCoupon.value?.id,
    })

    // 跳转到收银台页面
    const orderNo = orderRes.data.orderNo
    ElMessage.success('下单成功，正在跳转收银台...')

    router.push({ name: 'PaymentPay', query: { orderNo } })
  } catch (error) {
    const msg = error instanceof Error ? error.message : '下单失败，请重试'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

/**
 * 页面加载时获取数据
 * 1. 获取购物车列表（拿到已勾选的商品）
 * 2. 获取收货地址列表（默认选中默认地址或第一个）
 * 3. 获取可用优惠券列表（根据商品总金额查询）
 */
onMounted(async () => {
  try {
    // 先获取购物车列表，拿到勾选商品后才能算金额查可用券
    await fetchCartList()
    const totalAmountYuan = Number((checkedTotalAmount.value / 100).toFixed(2))

    // 并行获取地址列表和可用优惠券
    const [addressRes, couponRes] = await Promise.all([
      getAddressList(),
      getUsableCoupons(totalAmountYuan),
    ])

    // 设置地址列表，默认选中默认地址（isDefault=1），没有默认就选第一个
    addressList.value = addressRes.data
    if (addressList.value.length > 0) {
      const defaultAddr = addressList.value.find(addr => addr.isDefault === 1)
      currentAddress.value = defaultAddr || addressList.value[0]
    }

    // 设置可用优惠券列表
    usableCoupons.value = couponRes.data || []
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载失败，请刷新重试'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.order-confirm {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 140px;
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

.btn-back {
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

.btn-back:hover {
  background: var(--color-primary);
  color: #fff;
}

/* ==================== 收货地址 ==================== */
.address-card {
  padding: 0 0 32px;
  margin-bottom: 48px;
  border-bottom: 1px solid var(--color-border);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.card-title {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 400;
  margin: 0;
  letter-spacing: -0.01em;
}

.switch-btn {
  font-size: 12px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.switch-btn:hover {
  color: var(--color-primary);
}

.address-info {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.address-icon {
  font-size: 20px;
  color: var(--color-accent);
}

.address-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.user-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text);
}

.user-phone {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.tag-green {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  font-size: 11px;
  letter-spacing: 0.05em;
}

.address-text {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
  line-height: 1.6;
}

/* 无地址状态 */
.address-empty {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 0;
}

.address-empty-text {
  font-size: 14px;
  color: var(--color-text-muted);
}

.btn-add-address {
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  padding: 8px 20px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
}

.btn-add-address:hover {
  background: var(--color-primary);
  color: #fff;
}

/* ==================== 商品清单 ==================== */
.goods-card {
  margin-bottom: 48px;
}

.shop-header {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 0;
}

.shop-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.goods-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 0;
  border-bottom: 1px solid var(--color-border);
}

.goods-item:last-child {
  border-bottom: none;
}

.goods-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
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
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.goods-subtotal {
  font-size: 15px;
  color: var(--color-accent);
  font-weight: 400;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  min-width: 80px;
  text-align: right;
}

/* ==================== 费用明细 ==================== */
.fee-card {
  padding: 0 0 32px;
  margin-bottom: 48px;
  border-bottom: 1px solid var(--color-border);
}

.fee-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  font-size: 14px;
}

.fee-label {
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.fee-value {
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.fee-value.free {
  color: var(--color-text-muted);
}

/* 优惠券选择行 */
.coupon-item {
  cursor: pointer;
  transition: color var(--transition-base);
}

.coupon-item:hover .fee-label {
  color: var(--color-primary);
}

.coupon-value {
  display: flex;
  align-items: center;
  gap: 8px;
}

.coupon-discount {
  color: var(--color-accent);
  font-weight: 500;
}

.coupon-name-text {
  font-size: 12px;
  color: var(--color-text-muted);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-available {
  color: var(--color-accent);
  font-size: 13px;
}

.coupon-none {
  color: var(--color-text-muted);
  font-size: 13px;
}

.coupon-arrow {
  color: var(--color-text-muted);
  font-size: 14px;
}

/* 优惠汇总 */
.discount-summary .discount-value {
  color: var(--color-accent);
  font-weight: 500;
}

/* 优惠券选择弹窗 */
.coupon-dialog-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.coupon-dialog-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: all var(--transition-base);
}

.coupon-dialog-item:hover {
  border-color: var(--color-primary);
}

.coupon-dialog-item.active {
  border-color: var(--color-primary);
  background: var(--color-bg-secondary);
}

.coupon-dialog-info {
  flex: 1;
  min-width: 0;
}

.coupon-dialog-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.coupon-dialog-amount {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-accent);
  min-width: 60px;
}

.coupon-dialog-name {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.coupon-dialog-time {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

.coupon-dialog-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--color-text-muted);
  font-size: 14px;
}

/* ==================== 提交订单 ==================== */
.submit-card {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  border-top: 1px solid var(--color-border);
  padding: 20px 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 32px;
  z-index: 50;
  max-width: 1280px;
  margin: 0 auto;
}

.submit-info {
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.total-price {
  color: var(--color-accent);
  font-size: 28px;
  font-weight: 300;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.submit-btn {
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  padding: 16px 48px;
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.submit-btn:hover:not(:disabled) {
  background: transparent;
  color: var(--color-primary);
}

.submit-btn:disabled {
  background: var(--color-bg-secondary);
  color: var(--color-text-muted);
  border-color: var(--color-border);
  cursor: not-allowed;
}

/* ==================== 地址选择弹窗 ==================== */
.address-dialog-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.address-dialog-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: all var(--transition-base);
}

.address-dialog-item:hover {
  border-color: var(--color-primary);
}

.address-dialog-item.active {
  border-color: var(--color-primary);
  background: var(--color-bg-secondary);
}

.address-dialog-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.address-dialog-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
  line-height: 1.5;
}

.check-icon {
  color: var(--color-primary);
  font-size: 18px;
  font-weight: bold;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .order-confirm {
    padding: 24px 0 120px;
  }

  .page-title {
    font-size: 24px;
    margin-bottom: 24px;
  }

  .goods-item {
    gap: 12px;
  }

  .goods-image {
    width: 64px;
    height: 64px;
  }

  .submit-card {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
    gap: 16px;
  }

  .submit-info {
    text-align: center;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>
