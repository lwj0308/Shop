<template>
  <!-- 分类页：左侧分类树 + 右侧商品列表 -->
  <div class="category-page">
    <div class="category-container">
      <!-- 左侧：分类树 -->
      <aside class="category-sidebar">
        <h3 class="sidebar-title">全部商品分类</h3>
        <div class="category-tree">
          <!-- 全部商品入口：点击后重置分类筛选，显示所有商品 -->
          <div class="tree-item">
            <div
              class="tree-node level-1 all-products"
              :class="{ active: selectedCategoryId === 0 }"
              @click="selectAllProducts"
            >
              <span class="node-name">全部商品</span>
            </div>
          </div>
          <!-- 遍历一级分类 -->
          <div v-for="cat in categoryTree" :key="cat.id" class="tree-item">
            <div
              class="tree-node level-1"
              :class="{ active: selectedCategoryId === cat.id }"
              @click="selectCategory(cat.id, cat.name)"
            >
              <el-icon v-if="cat.children?.length" @click.stop="toggleExpand(cat.id)" class="expand-icon">
                <ArrowDown v-if="expandedIds.has(cat.id)" />
                <ArrowRight v-else />
              </el-icon>
              <span class="node-name">{{ cat.name }}</span>
            </div>
            <!-- 二级分类（展开时显示） -->
            <div v-if="expandedIds.has(cat.id) && cat.children?.length" class="tree-children">
              <div v-for="sub in cat.children" :key="sub.id" class="tree-item">
                <div
                  class="tree-node level-2"
                  :class="{ active: selectedCategoryId === sub.id }"
                  @click="selectCategory(sub.id, sub.name)"
                >
                  <el-icon v-if="sub.children?.length" @click.stop="toggleExpand(sub.id)" class="expand-icon">
                    <ArrowDown v-if="expandedIds.has(sub.id)" />
                    <ArrowRight v-else />
                  </el-icon>
                  <span class="node-name">{{ sub.name }}</span>
                </div>
                <!-- 三级分类（展开时显示） -->
                <div v-if="expandedIds.has(sub.id) && sub.children?.length" class="tree-children">
                  <div
                    v-for="leaf in sub.children"
                    :key="leaf.id"
                    class="tree-node level-3"
                    :class="{ active: selectedCategoryId === leaf.id }"
                    @click="selectCategory(leaf.id, leaf.name)"
                  >
                    <span class="node-name">{{ leaf.name }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧：商品列表 -->
      <section class="category-content">
        <!-- 面包屑 + 排序栏 -->
        <div class="content-header">
          <div class="breadcrumb">
            <span class="breadcrumb-link" @click="selectAllProducts">全部商品</span>
            <span v-if="selectedCategoryName" class="separator">></span>
            <span v-if="selectedCategoryName" class="current">{{ selectedCategoryName }}</span>
          </div>
          <div class="sort-bar">
            <button
              v-for="option in sortOptions"
              :key="option.key"
              :class="['sort-btn', { active: currentSort === option.key }]"
              @click="changeSort(option.key)"
            >
              {{ option.label }}
              <el-icon v-if="currentSort === option.key && option.key !== 'default'">
                <CaretBottom v-if="sortOrder === 'desc'" />
                <CaretTop v-else />
              </el-icon>
            </button>
          </div>
        </div>

        <!-- 商品网格：v-loading放在外层，避免loading遮罩成为grid子元素影响布局 -->
        <div v-loading="loading" class="product-grid-wrapper">
          <div class="product-grid">
            <!-- loading时显示占位元素，避免高度变化抖动 -->
            <div v-if="loading && productList.length === 0" class="loading-placeholder"></div>
            <ProductCard
              v-for="product in productList"
              :key="product.id"
              :product="product"
            />
            <!-- 空状态 -->
            <div v-if="!loading && productList.length === 0" class="empty-state">
              <el-empty description="该分类下暂无商品" />
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="total > 0" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[12, 24, 48]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadProducts"
            @current-change="loadProducts"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 分类页
 * 左侧分类树（可展开收起），右侧商品列表（支持排序、分页）
 * 点击分类后调用商品列表API，按categoryId筛选商品
 */
import { ref, onMounted, reactive } from 'vue'
import { ArrowDown, ArrowRight, CaretBottom, CaretTop } from '@element-plus/icons-vue'
import { getCategoryTree, getProductList } from '@shop/shared'
import type { CategoryInfo, ProductInfo, PageParams, ProductSearchParams } from '@shop/shared'
import ProductCard from '@/components/ProductCard.vue'

/* ==================== 分类树数据 ==================== */
/** 分类树（从后端获取） */
const categoryTree = ref<CategoryInfo[]>([])
/** 当前选中的分类ID */
const selectedCategoryId = ref<number>(0)
/** 当前选中的分类名称（用于面包屑显示） */
const selectedCategoryName = ref<string>('')
/** 展开的分类ID集合（用Set方便增删） */
const expandedIds = reactive<Set<number>>(new Set())

/* ==================== 商品列表数据 ==================== */
/** 商品列表 */
const productList = ref<ProductInfo[]>([])
/** 加载状态 */
const loading = ref(false)
/** 总条数（分页用） */
const total = ref(0)
/** 当前页码 */
const pageNum = ref(1)
/** 每页条数 */
const pageSize = ref(12)
/** 当前排序方式 */
const currentSort = ref<string>('default')
/** 排序方向（asc/desc） */
const sortOrder = ref<'asc' | 'desc'>('desc')

/** 排序选项列表 */
const sortOptions = [
  { key: 'default', label: '综合' },
  { key: 'price', label: '价格' },
  { key: 'createTime', label: '新品' },
]

/**
 * 加载分类树
 */
const loadCategoryTree = async () => {
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.data || []
    // 默认展开第一个一级分类
    if (categoryTree.value.length > 0) {
      expandedIds.add(categoryTree.value[0].id)
    }
  } catch {
    categoryTree.value = []
  }
}

/**
 * 选中"全部商品"，重置分类筛选，加载所有商品
 * 点击左侧分类树顶部的"全部商品"时触发
 */
const selectAllProducts = () => {
  selectedCategoryId.value = 0
  selectedCategoryName.value = ''
  pageNum.value = 1
  loadProducts()
}

/**
 * 选中某个分类，加载该分类下的商品
 * @param id 分类ID
 * @param name 分类名称
 */
const selectCategory = (id: number, name: string) => {
  selectedCategoryId.value = id
  selectedCategoryName.value = name
  pageNum.value = 1
  loadProducts()
}

/**
 * 展开/收起分类
 * @param id 分类ID
 */
const toggleExpand = (id: number) => {
  if (expandedIds.has(id)) {
    expandedIds.delete(id)
  } else {
    expandedIds.add(id)
  }
}

/**
 * 切换排序方式
 * 同一个排序再点一次就切换升序/降序
 * @param sortKey 排序字段
 */
const changeSort = (sortKey: string) => {
  if (currentSort.value === sortKey && sortKey !== 'default') {
    // 同一个排序，切换升降序
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    currentSort.value = sortKey
    if (sortKey !== 'default') {
      sortOrder.value = 'desc'
    }
  }
  pageNum.value = 1
  loadProducts()
}

/**
 * 加载商品列表
 * 根据选中的分类ID和排序方式请求后端
 */
const loadProducts = async () => {
  loading.value = true
  // 立即清空旧商品列表，避免加载完成后从"有商品"到"无商品"的高度突变导致抖动
  productList.value = []
  try {
    // 组装请求参数，必填的分页参数 + 可选的筛选排序参数
    const params: PageParams & ProductSearchParams = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    // 选了分类才传categoryId
    if (selectedCategoryId.value) {
      params.categoryId = selectedCategoryId.value
    }
    // 非默认排序才传排序参数
    if (currentSort.value !== 'default') {
      params.sortBy = currentSort.value
      params.sortOrder = sortOrder.value
    }
    const res = await getProductList(params)
    productList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    productList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadCategoryTree()
  // 默认加载全部商品（不选分类）
  await loadProducts()
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.category-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 80px;
}

.category-container {
  display: flex;
  gap: 48px;
  align-items: flex-start;
}

/* ==================== 左侧分类树 ==================== */
.category-sidebar {
  width: 240px;
  flex-shrink: 0;
  position: sticky;
  top: 96px;
}

.sidebar-title {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 400;
  color: var(--color-text);
  padding: 0 0 20px;
  margin: 0 0 20px;
  border-bottom: 1px solid var(--color-border);
  letter-spacing: -0.01em;
}

.tree-item {
  user-select: none;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text-secondary);
  /* 只过渡颜色和边框，不过渡font-weight（避免文字宽度变化引起抖动） */
  transition: color var(--transition-base), border-color var(--transition-base);
  border-left: 2px solid transparent;
  padding-left: 12px;
  letter-spacing: 0.02em;
}

.tree-node:hover {
  color: var(--color-primary);
}

.tree-node.active {
  color: var(--color-primary);
  font-weight: 500;
  border-left-color: var(--color-primary);
}

/* "全部商品"入口：底部加分隔线，和下面的分类区分开 */
.tree-node.all-products {
  font-weight: 500;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 8px;
  padding-bottom: 12px;
}

/* 二级分类缩进 */
.tree-node.level-2 {
  padding-left: 32px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.tree-node.level-2.active {
  color: var(--color-primary);
}

/* 三级分类缩进 */
.tree-node.level-3 {
  padding-left: 52px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.tree-node.level-3.active {
  color: var(--color-primary);
}

.expand-icon {
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-muted);
  flex-shrink: 0;
  transition: color var(--transition-base);
}

.expand-icon:hover {
  color: var(--color-primary);
}

.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-children {
  border-top: 1px solid var(--color-border);
  margin-top: 4px;
  padding-top: 4px;
}

/* ==================== 右侧商品列表 ==================== */
.category-content {
  flex: 1;
  min-width: 0;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 0 20px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 32px;
  flex-wrap: wrap;
  gap: 16px;
}

.breadcrumb {
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

/* 面包屑中的"全部商品"可点击链接 */
.breadcrumb-link {
  cursor: pointer;
  transition: color var(--transition-base);
}

.breadcrumb-link:hover {
  color: var(--color-primary);
}

.breadcrumb .separator {
  margin: 0 8px;
  color: var(--color-text-muted);
  opacity: 0.5;
}

.breadcrumb .current {
  color: var(--color-text);
  font-weight: 500;
}

/* 排序栏 */
.sort-bar {
  display: flex;
  gap: 4px;
}

.sort-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.sort-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.sort-btn.active {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

/* 商品网格外层包裹：v-loading放在这里，避免loading遮罩成为grid子元素 */
.product-grid-wrapper {
  position: relative;
  min-height: 400px;
}

/* 商品网格：minmax(0,1fr)强制等宽列，防止内容撑宽某列导致卡片大小不一 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  align-items: start;
}

/* loading占位元素：固定高度，避免加载时页面抖动 */
.loading-placeholder {
  grid-column: 1 / -1;
  height: 400px;
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  align-items: center;
  /* 固定高度，避免空状态时页面抖动 */
  min-height: 400px;
  padding: 0;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 64px 0 0;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 1024px) {
  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .category-page {
    padding: 24px 0 40px;
  }

  .category-container {
    flex-direction: column;
    gap: 24px;
  }

  .category-sidebar {
    width: 100%;
    position: static;
  }

  .product-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .content-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
