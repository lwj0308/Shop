<template>
  <!-- 商品卡片组件 - 杂志奢华风（Editorial Luxury） -->
  <article class="product-card" @click="goToDetail">
    <!-- 图片区：4:5竖版比例，hover时放大+滤镜变化 -->
    <div class="card-media">
      <img
        :src="product.mainImage"
        :alt="product.name"
        class="card-image"
        loading="lazy"
      />

      <!-- 品牌标签：左上角，极简大写字母 -->
      <span v-if="product.brandName" class="card-brand">{{ product.brandName }}</span>

      <!-- 底部滑入的加购条：hover时从底部滑入 -->
      <div class="card-overlay">
        <button class="add-btn" @click.stop="handleQuickAdd">
          <span>加入购物车</span>
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </div>

      <!-- 底部金色细线：hover时展开 -->
      <div class="card-line"></div>
    </div>

    <!-- 信息区：大量留白 -->
    <div class="card-body">
      <!-- 商品名称：衬线字体，2行省略（外层包裹保证固定高度） -->
      <div class="card-title-wrapper">
        <h3 class="card-title" v-html="product.name"></h3>
      </div>

      <!-- 副标题：灰色小字（始终占位，保证卡片等高） -->
      <p class="card-subtitle">{{ product.subtitle || '\u00A0' }}</p>

      <!-- 价格 + 评分行 -->
      <div class="card-footer">
        <!-- 香槟金价格 -->
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
 * 商品卡片组件（杂志奢华风 - Editorial Luxury）
 *
 * 设计特点：
 * 1. 4:5竖版图片比例（比1:1更高级，像时尚杂志）
 * 2. 品牌名以极简大写字母标注在图片左上角
 * 3. hover时图片放大+底部滑入加购条
 * 4. 衬线字体商品名 + 香槟金价格
 * 5. 星级评分 + 评论数
 * 6. 底部金色细线hover时展开
 *
 * 点击后跳转到商品详情页
 */

import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
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
/* ==================== 卡片容器 ==================== */
.product-card {
  cursor: pointer;
  position: relative;
  background: transparent;
  /* min-width:0 允许卡片缩小到网格列宽，不被内容撑宽 */
  min-width: 0;
}

/* ==================== 图片区：正方形比例 ==================== */
.card-media {
  position: relative;
  width: 100%;
  /* 正方形比例：宽度=高度 */
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* 商品图片：绝对定位填满容器，hover时放大 */
.card-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94),
              filter 0.6s ease;
  filter: brightness(0.98);
  display: block;
}

.product-card:hover .card-image {
  transform: scale(1.06);
  filter: brightness(1.05);
}

/* ==================== 品牌标签：左上角极简大写字母 ==================== */
.card-brand {
  position: absolute;
  top: 16px;
  left: 16px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  color: var(--color-text);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  z-index: 2;
}

/* ==================== 底部滑入加购条 ==================== */
.card-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  transform: translateY(100%);
  transition: transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 2;
}

.product-card:hover .card-overlay {
  transform: translateY(0);
}

/* 加购按钮：全宽黑底白字 */
.add-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  cursor: pointer;
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  transition: background 0.3s ease;
}

.add-btn:hover {
  background: #000;
}

/* ==================== 底部金色细线：hover时展开 ==================== */
.card-line {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  width: 0;
  background: var(--color-accent);
  transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 3;
}

.product-card:hover .card-line {
  width: 100%;
}

/* ==================== 信息区：所有元素固定高度，保证卡片等高 ==================== */
.card-body {
  padding: 16px 0 0;
  /* 兜底：防止内部内容溢出撑高卡片 */
  overflow: hidden;
}

/* 商品名称外层包裹：固定高度，不管名称1行还是2行都一样高 */
.card-title-wrapper {
  height: 42px;
  margin: 0 0 6px;
  overflow: hidden;
}

/* 商品名称：衬线字体，2行省略 */
.card-title {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 400;
  color: var(--color-text);
  line-height: 1.4;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.3s ease;
  /* 长单词（如英文品牌名）强制换行，不撑宽卡片 */
  overflow-wrap: break-word;
  word-break: break-word;
}

.product-card:hover .card-title {
  color: var(--color-accent);
}

/* 搜索高亮关键词 */
.card-title :deep(em) {
  color: var(--color-accent);
  font-style: normal;
  font-weight: 600;
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
  letter-spacing: 0.02em;
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
  height: 24px;
  /* 防止价格文字（20px字号）行高溢出撑高footer */
  overflow: hidden;
  /* 行高设为1，让20px字号占20px高度，在24px内 */
  line-height: 1;
}

/* 香槟金价格 */
.card-price {
  display: flex;
  align-items: baseline;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
  /* min-width:0 允许价格区域缩小，不被长价格撑宽 */
  min-width: 0;
  overflow: hidden;
}

.price-symbol {
  font-size: 13px;
  margin-right: 1px;
}

.price-value {
  font-size: 20px;
  font-weight: 300;
  letter-spacing: -0.02em;
}

/* 评分：星星 + 评论数 */
.card-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 2px;
  /* min-width:0 允许评分区域缩小 */
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
  color: var(--color-accent);
}

.rating-count {
  font-size: 11px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}
</style>
