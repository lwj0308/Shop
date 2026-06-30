<template>
  <!-- 购物车页 - 玻璃拟态风（Glassmorphism + 翡翠青绿渐变） -->
  <div class="cart-page">
    <!-- 页面标题：玻璃面板 + 渐变标题 -->
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

          <!-- 小计：渐变文字 -->
          <span class="col-subtotal">{{ formatPriceWithSymbol(item.subtotal) }}</span>

          <!-- 删除按钮：极简文字 -->
          <div class="col-action">
            <span class="remove-btn" @click="handleRemoveItem(item.id, item.productName)">删除</span>
          </div>
        </div>
      </div>

      <!-- 底部结算栏：浮动玻璃面板 -->
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
 * 玻璃拟态风（Glassmorphism + 翡翠青绿渐变）
 * 商品列表玻璃卡片 + 底部浮动玻璃结算栏 + 渐变按钮
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
/* 整页淡入上浮动效 */
.cart-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 140px;
  animation: fadeInUp 0.5s ease both;
}

/* ==================== 页面标题：玻璃面板 + 渐变标题文字 ==================== */
.cart-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 24px 28px;
  margin-bottom: 24px;
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

/* 主标题：翡翠青绿渐变文字 */
.page-title {
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 600;
  margin: 0;
  letter-spacing: -0.01em;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 商品数量标签：渐变软背景胶囊 */
.cart-count {
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 4px 12px;
  background: var(--gradient-brand-soft);
  border-radius: var(--radius-pill);
}

/* 加载骨架屏：玻璃面板 */
.cart-loading {
  padding: 40px 28px;
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
}

/* ==================== 空状态：玻璃插画卡片 ==================== */
.cart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 28px;
  gap: 32px;
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

/* 继续购物按钮：渐变主按钮 + 光泽掠过 */
.btn-continue {
  position: relative;
  overflow: hidden;
  background: var(--gradient-brand);
  color: #fff;
  border: none;
  padding: 14px 40px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  border-radius: var(--radius-pill);
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.25);
}

/* 光泽掠过伪元素 */
.btn-continue::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transition: left 0.6s ease;
}

/* hover 上浮 + 阴影加深 */
.btn-continue:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(16, 185, 129, 0.4);
}

.btn-continue:hover::after {
  left: 100%;
}

/* ==================== 表头：玻璃面板 ==================== */
.cart-table-header {
  display: grid;
  grid-template-columns: 60px 1fr 120px 140px 120px 80px;
  align-items: center;
  padding: 16px 24px;
  margin-bottom: 16px;
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  font-weight: 500;
}

.col-price,
.col-subtotal {
  text-align: center;
}

.col-qty {
  text-align: center;
}

/* ==================== 商品列表：玻璃卡片 ==================== */
.cart-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 购物车项：玻璃卡片 + hover 上浮 + 翡翠描边 */
.cart-item {
  display: grid;
  grid-template-columns: 60px 1fr 120px 140px 120px 80px;
  align-items: center;
  padding: 20px 24px;
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  transition: all var(--transition-base);
}

/* hover 上浮 + 翡翠描边 + 大阴影 */
.cart-item:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: rgba(16, 185, 129, 0.3);
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

/* 商品图片：圆角 + 浅渐变底 */
.item-image {
  width: 96px;
  height: 96px;
  object-fit: cover;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05), rgba(6, 182, 212, 0.05));
}

.item-info {
  flex: 1;
  min-width: 0;
}

/* 商品名称 */
.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0.01em;
  transition: color var(--transition-base);
}

/* hover 时商品名变翡翠色 */
.cart-item:hover .item-name {
  color: var(--color-primary);
}

/* SKU 规格 */
.item-sku {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
  padding: 2px 8px;
  background: rgba(16, 185, 129, 0.08);
  border-radius: var(--radius-pill);
  display: inline-block;
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

/* 小计：渐变文字 */
.col-subtotal {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: center;
  letter-spacing: -0.01em;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 删除按钮列 */
.col-action {
  display: flex;
  justify-content: center;
}

/* 删除按钮：hover 变红色 */
.remove-btn {
  font-size: 12px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
}

.remove-btn:hover {
  color: var(--color-error);
  background: rgba(239, 68, 68, 0.08);
}

/* ==================== 底部结算栏：浮动玻璃面板 + 翡翠描边 ==================== */
.cart-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--color-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid var(--color-glass-border);
  border-left: 1px solid var(--color-glass-border);
  border-right: 1px solid var(--color-glass-border);
  box-shadow: 0 -8px 32px rgba(16, 185, 129, 0.08);
  padding: 20px 0;
  display: flex;
  align-items: center;
  z-index: 50;
  max-width: 1280px;
  margin: 0 auto;
}

/* 全选标签 */
.select-all {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

/* 删除选中按钮 */
.delete-selected {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-left: 24px;
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
}

.delete-selected:hover {
  color: var(--color-error);
  background: rgba(239, 68, 68, 0.08);
}

/* 右侧汇总区 */
.footer-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 32px;
}

/* 汇总信息 */
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

/* 已选数量：翡翠色 */
.selected-count {
  color: var(--color-primary);
  font-weight: 600;
}

.total-label {
  color: var(--color-text);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  font-size: 12px;
}

/* 总价：渐变文字 */
.total-price {
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 结算按钮：渐变主按钮 + 光泽掠过 */
.checkout-btn {
  position: relative;
  overflow: hidden;
  background: var(--gradient-brand);
  color: #fff;
  border: none;
  padding: 16px 48px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  border-radius: var(--radius-pill);
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.3);
}

/* 光泽掠过伪元素 */
.checkout-btn::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transition: left 0.6s ease;
}

/* hover 上浮 + 阴影加深 */
.checkout-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(16, 185, 129, 0.45);
}

.checkout-btn:hover:not(:disabled)::after {
  left: 100%;
}

/* 禁用状态：灰色玻璃风 */
.checkout-btn:disabled {
  background: var(--color-bg-secondary);
  color: var(--color-text-muted);
  cursor: not-allowed;
  box-shadow: none;
  opacity: 0.6;
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
    padding: 16px;
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
