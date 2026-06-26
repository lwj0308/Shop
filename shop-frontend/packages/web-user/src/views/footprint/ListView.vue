<template>
  <!-- 浏览足迹页 - 极简独立站风格 -->
  <div class="footprint-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <p class="page-eyebrow">BROWSING HISTORY</p>
      <h1 class="page-title">浏览足迹</h1>
      <p class="page-desc">您最近浏览过的商品</p>
    </div>

    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="products-loading">
      <div v-for="i in 6" :key="i" class="product-skeleton">
        <el-skeleton :rows="3" animated />
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="footprintProducts.length === 0" class="empty-state">
      <span class="empty-icon">👣</span>
      <p class="empty-text">还没有浏览记录</p>
      <router-link to="/category" class="btn-outline">去逛逛</router-link>
    </div>

    <!-- 商品网格 -->
    <div v-else class="products-grid">
      <ProductCard
        v-for="item in footprintProducts"
        :key="item.id"
        :product="item"
      />
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="pagination">
      <el-pagination
        v-model:current-page="pageNum"
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
 * 浏览足迹页
 * 展示用户最近浏览过的商品列表，按浏览时间倒序
 *
 * 数据流（小白版）：
 * 1. 调用足迹列表接口，拿到用户浏览过的商品ID列表（每条只有 productId）
 * 2. 对每个 productId 并发调用商品详情接口，拿到完整商品信息
 * 3. 用 ProductCard 组件展示
 *
 * 说明：足迹是浏览商品详情时自动记录的，无需用户手动操作
 */

import { ref, onMounted } from 'vue'
import ProductCard from '@/components/ProductCard.vue'
import { getFootprintList, getProductDetail } from '@shop/shared'
import type { ProductInfo } from '@shop/shared'

/** 足迹对应的商品详情列表（用于展示） */
const footprintProducts = ref<ProductInfo[]>([])

/** 足迹总数（用于分页） */
const total = ref(0)

/** 当前页码 */
const pageNum = ref(1)

/** 每页条数 */
const pageSize = 12

/** 是否加载中 */
const loading = ref(true)

/**
 * 加载足迹列表
 * 先调足迹列表接口拿 productId 列表，再并发查每个商品详情
 */
const fetchFootprintList = async () => {
  loading.value = true
  try {
    const res = await getFootprintList({ pageNum: pageNum.value, pageSize })
    const records = res.data.records || []
    total.value = res.data.total || 0

    // 并发查询每个商品的详情（一页最多12条，并发请求不会过多）
    const detailResults = await Promise.allSettled(
      records.map((item) => getProductDetail(item.productId)),
    )

    // 遍历结果，收集查询成功的商品（避免复杂的类型谓词）
    const list: ProductInfo[] = []
    detailResults.forEach((r) => {
      if (r.status === 'fulfilled' && r.value.code === 200 && r.value.data) {
        list.push(r.value.data)
      }
    })
    footprintProducts.value = list
  } catch {
    footprintProducts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 翻页
 */
const handlePageChange = () => {
  fetchFootprintList()
}

onMounted(() => {
  fetchFootprintList()
})
</script>

<style scoped>
.footprint-page {
  padding: 0;
}

/* ==================== 页面头部 ==================== */
.page-header {
  text-align: center;
  padding: 48px 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 40px;
}

.page-eyebrow {
  font-size: var(--font-size-caption);
  letter-spacing: 0.3em;
  color: var(--color-text-muted);
  text-transform: uppercase;
  margin-bottom: 8px;
  font-weight: 500;
}

.page-title {
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.page-desc {
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
  margin: 0;
}

/* ==================== 加载骨架屏 ==================== */
.products-loading {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-lg);
}

.product-skeleton {
  background: #fff;
  padding: var(--space-lg);
  border-radius: var(--radius-md);
}

/* ==================== 空状态 ==================== */
.empty-state {
  text-align: center;
  padding: 80px 0;
}

.empty-icon {
  font-size: 64px;
  color: var(--color-text-muted);
  display: block;
  margin-bottom: 16px;
}

.empty-text {
  font-size: var(--font-size-body);
  color: var(--color-text-muted);
  margin: 0 0 24px;
  letter-spacing: 0.02em;
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  padding: 12px 32px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  text-decoration: none;
  font-size: 14px;
  letter-spacing: 0.05em;
  transition: all var(--transition-base);
}

.btn-outline:hover {
  background: var(--color-primary);
  color: #fff;
}

/* ==================== 商品网格 ==================== */
.products-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-lg);
}

/* ==================== 分页 ==================== */
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 48px;
  margin-bottom: 40px;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1024px) {
  .products-grid,
  .products-loading {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .page-title {
    font-size: 28px;
  }

  .products-grid,
  .products-loading {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
