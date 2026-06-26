<template>
  <!-- 购物车页 - 极简独立站风格 -->
  <div class="cart-page">
    <!-- 页面标题：衬线字体 -->
    <div class="cart-header">
      <h1 class="page-title">购物车</h1>
      <span class="cart-count">{{ cartList.length }} 件商品</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="cart-loading">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 购物车为空 -->
    <div v-else-if="cartList.length === 0" class="cart-empty">
      <el-empty description="购物车空空如也" />
      <button class="btn-continue" @click="$router.push('/')">继续购物</button>
    </div>

    <!-- 购物车列表 -->
    <template v-else>
      <!-- 表头 -->
      <div class="cart-table-header">
        <span class="col-check">选择</span>
        <span class="col-product">商品信息</span>
        <span class="col-price">单价</span>
        <span class="col-qty">数量</span>
        <span class="col-subtotal">小计</span>
        <span class="col-action">操作</span>
      </div>

      <!-- 商品列表 -->
      <div class="cart-list">
        <div v-for="item in cartList" :key="item.id" class="cart-item">
          <!-- 选中复选框 -->
          <div class="col-check">
            <el-checkbox
              :model-value="item.checked"
              @change="(val: boolean) => handleCheckItem(item.id, val)"
            />
          </div>

          <!-- 商品图片 + 信息 -->
          <div class="col-product">
            <img :src="item.productImage" :alt="item.productName" class="item-image" loading="lazy" />
            <div class="item-info">
              <h4 class="item-name">{{ item.productName }}</h4>
              <p class="item-sku">{{ item.skuName }}</p>
            </div>
          </div>

          <!-- 单价 -->
          <span class="col-price">{{ formatPriceWithSymbol(item.price) }}</span>

          <!-- 数量选择器（防抖） -->
          <div class="col-qty">
            <el-input-number
              :model-value="item.quantity"
              :min="1"
              :max="item.stock"
              size="small"
              @change="(val: number) => handleQuantityChange(item.id, val)"
            />
          </div>

          <!-- 小计：香槟金 -->
          <span class="col-subtotal">{{ formatPriceWithSymbol(item.subtotal) }}</span>

          <!-- 删除按钮：极简文字 -->
          <div class="col-action">
            <span class="remove-btn" @click="handleRemoveItem(item.id, item.productName)">删除</span>
          </div>
        </div>
      </div>

      <!-- 底部结算栏：浮动卡片 -->
      <div class="cart-footer">
        <label class="select-all">
          <el-checkbox
            :model-value="isAllChecked"
            @change="handleToggleAll"
          >
            全选
          </el-checkbox>
        </label>
        <span class="delete-selected" @click="handleRemoveSelected">删除选中</span>
        <div class="footer-right">
          <div class="cart-summary">
            <span class="summary-text">已选 <span class="selected-count">{{ checkedCount }}</span> 件</span>
            <span class="total-label">合计</span>
            <span class="total-price">{{ formatPriceWithSymbol(checkedTotal) }}</span>
          </div>
          <button class="checkout-btn" :disabled="!hasCheckedItems" @click="handleCheckout">去结算</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 购物车页
 * 参考V1原型设计：商品列表、底部固定结算栏、红色价格
 */

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCart } from '@shop/shared'
import { formatPriceWithSymbol } from '@shop/shared'

const router = useRouter()
const { cartList, checkedTotal, hasCheckedItems, fetchCartList, updateCartItem, removeCartItem } = useCart()

/** 是否加载中 */
const loading = ref(true)

/** 数量修改防抖定时器映射 */
const quantityTimers = new Map<number, ReturnType<typeof setTimeout>>()

/** 已选数量 */
const checkedCount = computed(() => {
  return cartList.value.filter(item => item.checked).reduce((sum, item) => sum + item.quantity, 0)
})

/** 是否全选 */
const isAllChecked = computed(() => {
  return cartList.value.length > 0 && cartList.value.every(item => item.checked)
})

/**
 * 处理购物车项选中状态变更
 */
const handleCheckItem = async (id: number, checked: boolean) => {
  try {
    await updateCartItem({ id, checked })
  } catch (error) {
    const msg = error instanceof Error ? error.message : '操作失败'
    ElMessage.error(msg)
  }
}

/**
 * 全选/取消全选
 */
const handleToggleAll = async (checked: boolean) => {
  for (const item of cartList.value) {
    if (item.checked !== checked) {
      await updateCartItem({ id: item.id, checked })
    }
  }
}

/**
 * 处理数量修改（防抖）
 */
const handleQuantityChange = (id: number, quantity: number) => {
  if (quantityTimers.has(id)) {
    clearTimeout(quantityTimers.get(id))
  }
  quantityTimers.set(id, setTimeout(async () => {
    quantityTimers.delete(id)
    try {
      await updateCartItem({ id, quantity })
    } catch (error) {
      const msg = error instanceof Error ? error.message : '修改数量失败'
      ElMessage.error(msg)
    }
  }, 500))
}

/**
 * 删除购物车项（带确认弹窗）
 */
const handleRemoveItem = async (id: number, name: string) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除"${name}"吗？`,
      '删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await removeCartItem(id)
    ElMessage.success('已删除')
  } catch {
    // 用户点击取消
  }
}

/**
 * 删除选中的商品
 */
const handleRemoveSelected = async () => {
  const checkedItems = cartList.value.filter(item => item.checked)
  if (checkedItems.length === 0) {
    ElMessage.warning('请先选择要删除的商品')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${checkedItems.length} 件商品吗？`,
      '删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    for (const item of checkedItems) {
      await removeCartItem(item.id)
    }
    ElMessage.success('已删除选中商品')
  } catch {
    // 用户点击取消
  }
}

/**
 * 去结算
 */
const handleCheckout = () => {
  router.push({ name: 'OrderConfirm' })
}

onMounted(async () => {
  try {
    await fetchCartList()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.cart-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 140px;
}

/* ==================== 页面标题 ==================== */
.cart-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding-bottom: 32px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 0;
}

.page-title {
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0;
  letter-spacing: -0.01em;
}

.cart-count {
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.cart-loading {
  padding: 40px 0;
}

/* 空状态 */
.cart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120px 0;
  gap: 32px;
}

.btn-continue {
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  padding: 14px 40px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.btn-continue:hover {
  background: var(--color-primary);
  color: #fff;
}

/* ==================== 表头 ==================== */
.cart-table-header {
  display: grid;
  grid-template-columns: 60px 1fr 120px 140px 120px 80px;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.col-price,
.col-subtotal {
  text-align: center;
}

.col-qty {
  text-align: center;
}

/* ==================== 商品列表 ==================== */
.cart-list {
  display: flex;
  flex-direction: column;
}

.cart-item {
  display: grid;
  grid-template-columns: 60px 1fr 120px 140px 120px 80px;
  align-items: center;
  padding: 24px 0;
  border-bottom: 1px solid var(--color-border);
  transition: background var(--transition-base);
}

.cart-item:hover {
  background: var(--color-bg-secondary);
}

/* 复选框列 */
.col-check {
  display: flex;
  justify-content: center;
}

/* 商品信息列 */
.col-product {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.item-image {
  width: 96px;
  height: 96px;
  object-fit: cover;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0.01em;
}

.item-sku {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
}

/* 单价 */
.col-price {
  font-size: 14px;
  color: var(--color-text-secondary);
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* 数量列 */
.col-qty {
  display: flex;
  justify-content: center;
}

/* 小计：香槟金 */
.col-subtotal {
  font-size: 16px;
  color: var(--color-accent);
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  text-align: center;
  letter-spacing: -0.01em;
}

/* 删除按钮：极简文字 */
.col-action {
  display: flex;
  justify-content: center;
}

.remove-btn {
  font-size: 12px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.remove-btn:hover {
  color: var(--color-error);
}

/* ==================== 底部结算栏：浮动固定 ==================== */
.cart-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  border-top: 1px solid var(--color-border);
  padding: 20px 0;
  display: flex;
  align-items: center;
  z-index: 50;
  max-width: 1280px;
  margin: 0 auto;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.delete-selected {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-left: 24px;
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.delete-selected:hover {
  color: var(--color-error);
}

.footer-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 32px;
}

.cart-summary {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.summary-text {
  color: var(--color-text-muted);
}

.selected-count {
  color: var(--color-text);
  font-weight: 500;
}

.total-label {
  color: var(--color-text);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  font-size: 12px;
}

.total-price {
  color: var(--color-accent);
  font-size: 28px;
  font-weight: 300;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

/* 结算按钮：黑底白字 */
.checkout-btn {
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

.checkout-btn:hover:not(:disabled) {
  background: transparent;
  color: var(--color-primary);
}

.checkout-btn:disabled {
  background: var(--color-bg-secondary);
  color: var(--color-text-muted);
  border-color: var(--color-border);
  cursor: not-allowed;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 1024px) {
  .cart-table-header {
    grid-template-columns: 40px 1fr 100px 120px 100px 60px;
  }

  .cart-item {
    grid-template-columns: 40px 1fr 100px 120px 100px 60px;
  }

  .item-image {
    width: 72px;
    height: 72px;
  }
}

@media (max-width: 768px) {
  .cart-page {
    padding: 24px 0 120px;
  }

  .page-title {
    font-size: 24px;
  }

  .cart-table-header {
    display: none;
  }

  .cart-item {
    grid-template-columns: 40px 1fr 60px;
    grid-template-areas:
      "check product product"
      "check price qty"
      "check subtotal action";
    gap: 12px;
    padding: 16px 0;
  }

  .col-check {
    grid-area: check;
    align-items: flex-start;
  }

  .col-product {
    grid-area: product;
  }

  .col-price {
    grid-area: price;
    text-align: left;
  }

  .col-qty {
    grid-area: qty;
    justify-content: flex-end;
  }

  .col-subtotal {
    grid-area: subtotal;
    text-align: left;
  }

  .col-action {
    grid-area: action;
    justify-content: flex-end;
  }

  .item-image {
    width: 64px;
    height: 64px;
  }

  .footer-right {
    gap: 16px;
  }

  .total-price {
    font-size: 22px;
  }

  .checkout-btn {
    padding: 14px 24px;
  }
}
</style>
