<template>
  <!-- 商品卡片组件 - 玻璃拟态风（Glassmorphism） -->
  <article class="product-card" @click="goToDetail">
    <!-- 图片区：1:1正方形比例，hover时放大 -->
    <div class="card-media">
      <img
        :src="product.mainImage"
        :alt="product.name"
        class="card-image"
        loading="lazy"
      />

      <!-- 品牌标签：左上角玻璃胶囊 -->
      <span v-if="product.brandName" class="card-brand">{{ product.brandName }}</span>

      <!-- 浮动加购按钮：hover时淡入上浮 -->
      <button class="add-btn" @click.stop="handleQuickAdd" aria-label="加入购物车">
        <el-icon :size="18"><ShoppingBag /></el-icon>
      </button>
    </div>

    <!-- 信息区：固定高度结构保证卡片等高 -->
    <div class="card-body">
      <!-- 商品名称：2行省略（外层包裹保证固定高度） -->
      <div class="card-title-wrapper">
        <h3 class="card-title" v-html="product.name"></h3>
      </div>

      <!-- 副标题：灰色小字（始终占位，保证卡片等高） -->
      <p class="card-subtitle">{{ product.subtitle || '\u00A0' }}</p>

      <!-- 价格 + 评分行 -->
      <div class="card-footer">
        <!-- 翡翠青绿渐变价格 -->
        <div class="card-price">
          <span class="price-symbol">¥</span>
          <span class="price-value">{{ formatPriceValue(product.minPrice) }}</span>
        </div>

        <!-- 评分：星星 + 评论数（如果有） -->
        <div v-if="product.commentSummary && product.commentSummary.totalCount > 0" class="card-rating">
          <span class="rating-stars">
            <span
              v-for="n in 5"
              :key="n"
              class="star"
              :class="{ filled: n <= Math.round(product.commentSummary.avgScore) }"
            >★</span>
          </span>
          <span class="rating-count">({{ formatReviewCount(product.commentSummary.totalCount) }})</span>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * 商品卡片组件（玻璃拟态风 - Glassmorphism）
 *
 * 设计特点：
 * 1. 1:1 正方形图片比例，hover 时缓慢放大
 * 2. 品牌名以玻璃胶囊标注在图片左上角
 * 3. hover 时右下角浮现玻璃加购按钮
 * 4. 翡翠青绿渐变价格文字
 * 5. 星级评分 + 评论数
 * 6. 卡片整体 hover 上浮 + 阴影加深
 *
 * 点击后跳转到商品详情页
 */

import { useRouter } from 'vue-router'
import { ShoppingBag } from '@element-plus/icons-vue'
import type { ProductInfo } from '@shop/shared'

/** 组件属性：接收一个商品信息对象 */
const props = defineProps<{
  product: ProductInfo
}>()

const router = useRouter()

/** 点击卡片跳转到商品详情页 */
const goToDetail = () => {
  router.push(`/product/${props.product.id}`)
}

/**
 * 快速加入购物车（卡片上的按钮）
 * 实际跳转到详情页让用户选择SKU
 */
const handleQuickAdd = () => {
  router.push(`/product/${props.product.id}`)
}

/**
 * 格式化价格数值（去掉¥符号，只保留数字部分）
 * 卡片里¥符号单独用小字显示
 * 加了空值保护，防止API没返回价格时报错
 */
const formatPriceValue = (price: number | undefined) => {
  if (price == null) return '0'
  return price.toFixed(2).replace(/\.00$/, '')
}

/**
 * 格式化评论数（超过1000显示k+）
 * 加了空值保护
 */
const formatReviewCount = (count: number | undefined) => {
  if (count == null) return '0'
  if (count >= 1000) {
    return (count / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  }
  return String(count)
}
</script>

<style scoped>
/* ==================== 卡片容器：玻璃卡片 ==================== */
.product-card {
  cursor: pointer;
  position: relative;
  background: var(--color-glass);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  /* min-width:0 允许卡片缩小到网格列宽，不被内容撑宽（关键：Grid 布局下防溢出） */
  min-width: 0;
  transition: transform 0.3s var(--ease-out), box-shadow 0.3s var(--ease-out),
              border-color 0.3s var(--ease-out);
}

/* hover：整体上浮 + 阴影加深 + 翡翠描边 */
.product-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--color-primary-light);
}

/* ==================== 图片区：1:1 正方形 ==================== */
.card-media {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  /* 浅渐变底色，图片未加载时也不突兀 */
  background: linear-gradient(135deg, #F8FAFC, #ECFDF5);
}

/* 商品图片：填满容器，hover 时缓慢放大 */
.card-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.5s var(--ease-out);
}

.product-card:hover .card-image {
  transform: scale(1.05);
}

/* ==================== 品牌标签：玻璃胶囊 ==================== */
.card-brand {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 12px;
  background: var(--color-glass-strong);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-pill);
  color: var(--color-text);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
  z-index: 2;
}

/* ==================== 浮动加购按钮：hover 淡入上浮 ==================== */
.add-btn {
  position: absolute;
  bottom: 12px;
  right: 12px;
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 50%;
  background: var(--gradient-brand);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-md);
  z-index: 2;
  /* 默认隐藏：透明 + 下移 */
  opacity: 0;
  transform: translateY(8px);
  transition: all 0.3s var(--ease-out);
}

.product-card:hover .add-btn {
  opacity: 1;
  transform: translateY(0);
}

.add-btn:hover {
  background: var(--gradient-brand-hover);
  transform: scale(1.08);
}

/* ==================== 信息区：固定高度结构，保证卡片等高 ==================== */
.card-body {
  padding: 16px 18px 18px;
  /* 兜底：防止内部内容溢出撑高卡片 */
  overflow: hidden;
}

/* 商品名称外层包裹：固定高度，不管名称1行还是2行都一样高 */
.card-title-wrapper {
  height: 42px;
  margin: 0 0 6px;
  overflow: hidden;
}

/* 商品名称：现代无衬线，2行省略 */
.card-title {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.4;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.3s var(--ease-out);
  /* 长单词（如英文品牌名）强制换行，不撑宽卡片 */
  overflow-wrap: break-word;
  word-break: break-word;
}

.product-card:hover .card-title {
  color: var(--color-primary);
}

/* 搜索高亮关键词 */
.card-title :deep(em) {
  color: var(--color-primary);
  font-style: normal;
  font-weight: 700;
}

/* 副标题：灰色小字，固定高度保证卡片等高 */
.card-subtitle {
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-text-muted);
  margin: 0 0 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  /* 固定高度，没有副标题时也占位 */
  height: 17px;
}

/* ==================== 价格 + 评分行：固定高度 ==================== */
.card-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  /* 固定高度，有评分和无评分都一样高 */
  height: 26px;
  overflow: hidden;
  line-height: 1;
}

/* 翡翠青绿渐变价格 */
.card-price {
  display: flex;
  align-items: baseline;
  font-family: var(--font-heading);
  font-variant-numeric: tabular-nums;
  /* 渐变文字 */
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  min-width: 0;
  overflow: hidden;
}

.price-symbol {
  font-size: 14px;
  font-weight: 600;
  margin-right: 1px;
}

.price-value {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

/* 评分：星星 + 评论数 */
.card-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 2px;
  min-width: 0;
  overflow: hidden;
}

.rating-stars {
  display: flex;
  gap: 1px;
}

.star {
  font-size: 11px;
  color: var(--color-border);
  line-height: 1;
}

.star.filled {
  color: var(--color-primary);
}

.rating-count {
  font-size: 11px;
  color: var(--color-text-muted);
}
</style>
