<template>
  <!-- 搜索页：搜索前展示热门词和历史，搜索后展示商品列表 -->
  <!-- 注意：搜索框在顶部导航栏(DefaultLayout)，这里不重复显示 -->
  <div class="search-page">
    <!-- 搜索前：热门搜索 + 搜索历史 -->
    <div v-if="!hasSearched" class="search-before">
      <!-- 热门搜索 -->
      <div class="search-section">
        <h3 class="section-title">
          <el-icon class="title-icon"><Search /></el-icon>
          热门搜索
        </h3>
        <div class="tag-list">
          <span
            v-for="(tag, index) in hotSearchTags"
            :key="tag"
            class="search-tag"
            :class="{ 'tag-hot': index < 3 }"
            @click="searchByTag(tag)"
          >
            <span v-if="index < 3" class="tag-rank">{{ index + 1 }}</span>
            {{ tag }}
          </span>
        </div>
      </div>
      <!-- 搜索历史 -->
      <div class="search-section">
        <div class="section-header">
          <h3 class="section-title">
            <el-icon class="title-icon"><Clock /></el-icon>
            搜索历史
          </h3>
          <span v-if="searchHistory.length > 0" class="clear-btn" @click="clearHistory">
            <el-icon><Delete /></el-icon> 清除
          </span>
        </div>
        <div class="tag-list">
          <span
            v-for="tag in searchHistory"
            :key="tag"
            class="search-tag history-tag"
            @click="searchByTag(tag)"
          >{{ tag }}</span>
          <span v-if="searchHistory.length === 0" class="empty-hint">暂无搜索历史</span>
        </div>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-else class="search-result">
      <!-- 结果统计 + 排序栏 -->
      <div class="result-bar">
        <div class="result-info">
          搜索"<span class="keyword-highlight">{{ currentKeyword }}</span>"
          <span class="result-count">共 {{ total }} 件商品</span>
        </div>
        <div class="sort-bar">
          <button
            v-for="option in sortOptions"
            :key="option.key"
            :class="['sort-btn', { active: currentSort === option.key }]"
            @click="changeSort(option.key)"
          >
            {{ option.label }}
            <el-icon v-if="currentSort === option.key && option.key === 'price'" class="sort-arrow">
              <CaretBottom v-if="sortOrder === 'desc'" />
              <CaretTop v-else />
            </el-icon>
          </button>
          <!-- 价格区间筛选 -->
          <div class="price-filter">
            <input
              v-model.number="minPriceInput"
              type="number"
              placeholder="最低价"
              class="price-input"
              @keyup.enter="applyPriceFilter"
            />
            <span class="price-separator">-</span>
            <input
              v-model.number="maxPriceInput"
              type="number"
              placeholder="最高价"
              class="price-input"
              @keyup.enter="applyPriceFilter"
            />
            <button class="price-confirm" @click="applyPriceFilter">确定</button>
          </div>
        </div>
      </div>

      <!-- 商品网格 -->
      <div v-loading="loading" class="product-grid-wrapper">
        <div class="product-grid">
          <ProductCard
            v-for="item in searchResults"
            :key="item.id"
            :product="item"
          />
        </div>
        <!-- 空状态 -->
        <div v-if="!loading && searchResults.length === 0" class="empty-state">
          <el-empty description="没有找到相关商品，换个关键词试试吧" />
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
          @size-change="loadSearchResults"
          @current-change="loadSearchResults"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 搜索页
 * 搜索前：展示热门搜索词和搜索历史（localStorage持久化）
 * 搜索后：调用searchProducts API，展示商品列表，支持排序、价格筛选、分页
 */
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Clock, Delete, CaretBottom, CaretTop } from '@element-plus/icons-vue'
import { searchProducts, getHotKeywords } from '@shop/shared'
import type { ProductInfo, PageParams, ProductSearchParams } from '@shop/shared'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const router = useRouter()

/* ==================== 搜索关键词 ==================== */
/** 搜索框中的关键词 */
const keyword = ref((route.query.keyword as string) || '')
/** 当前正在搜索的关键词（搜索后固定） */
const currentKeyword = ref('')
/** 是否已执行搜索 */
const hasSearched = ref(!!route.query.keyword)

/* ==================== 热门搜索 & 历史 ==================== */
/** 热门搜索词（从后端API动态获取，基于Redis ZSet按搜索次数排序） */
const hotSearchTags = ref<string[]>([])
/** 默认热搜词（API无数据时作为fallback） */
const defaultHotTags = ['手机', '电脑', '耳机', '空调', '运动鞋', '笔记本', '冰箱', 'T恤']
/** 搜索历史（从localStorage读取） */
const searchHistory = ref<string[]>([])

/* ==================== 搜索结果 ==================== */
/** 搜索结果列表 */
const searchResults = ref<ProductInfo[]>([])
/** 加载状态 */
const loading = ref(false)
/** 总条数 */
const total = ref(0)
/** 当前页码 */
const pageNum = ref(1)
/** 每页条数 */
const pageSize = ref(12)

/* ==================== 排序 & 筛选 ==================== */
/** 当前排序方式 */
const currentSort = ref<string>('default')
/** 排序方向 */
const sortOrder = ref<'asc' | 'desc'>('desc')
/** 价格区间 - 最低价输入框 */
const minPriceInput = ref<number | undefined>(undefined)
/** 价格区间 - 最高价输入框 */
const maxPriceInput = ref<number | undefined>(undefined)
/** 价格区间 - 实际生效的最低价（元） */
const minPrice = ref<number | undefined>(undefined)
/** 价格区间 - 实际生效的最高价（元） */
const maxPrice = ref<number | undefined>(undefined)

/** 排序选项 */
const sortOptions = [
  { key: 'default', label: '综合' },
  { key: 'price', label: '价格' },
  { key: 'createTime', label: '新品' },
]

/**
 * 从localStorage加载搜索历史
 */
const loadHistory = () => {
  try {
    const saved = localStorage.getItem('searchHistory')
    searchHistory.value = saved ? JSON.parse(saved) : []
  } catch {
    searchHistory.value = []
  }
}

/**
 * 保存搜索词到历史记录
 * @param kw 关键词
 */
const saveHistory = (kw: string) => {
  // 去重：如果已存在先删除
  const list = searchHistory.value.filter(item => item !== kw)
  // 放到最前面
  list.unshift(kw)
  // 最多保留10条
  searchHistory.value = list.slice(0, 10)
  localStorage.setItem('searchHistory', JSON.stringify(searchHistory.value))
}

/**
 * 清除搜索历史
 */
const clearHistory = () => {
  searchHistory.value = []
  localStorage.removeItem('searchHistory')
}

/**
 * 执行搜索
 * 1. 去空格
 * 2. 保存到历史记录
 * 3. 更新URL
 * 4. 调用API加载结果
 */
const handleSearch = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  currentKeyword.value = kw
  hasSearched.value = true
  // 重置筛选条件
  pageNum.value = 1
  currentSort.value = 'default'
  sortOrder.value = 'desc'
  minPriceInput.value = undefined
  maxPriceInput.value = undefined
  minPrice.value = undefined
  maxPrice.value = undefined
  // 保存历史
  saveHistory(kw)
  // 更新URL（不刷新页面）
  router.replace({ path: '/search', query: { keyword: kw } })
  // 加载搜索结果
  loadSearchResults()
}

/**
 * 点击标签快速搜索
 */
const searchByTag = (tag: string) => {
  keyword.value = tag
  handleSearch()
}

/**
 * 切换排序方式
 * 同一个排序再点一次切换升/降序
 */
const changeSort = (sortKey: string) => {
  if (currentSort.value === sortKey && sortKey !== 'default') {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    currentSort.value = sortKey
    if (sortKey !== 'default') {
      sortOrder.value = 'desc'
    }
  }
  pageNum.value = 1
  loadSearchResults()
}

/**
 * 应用价格区间筛选
 */
const applyPriceFilter = () => {
  minPrice.value = minPriceInput.value
  maxPrice.value = maxPriceInput.value
  pageNum.value = 1
  loadSearchResults()
}

/**
 * 调用搜索API加载结果
 */
const loadSearchResults = async () => {
  if (!currentKeyword.value) return
  loading.value = true
  try {
    // 组装请求参数
    const params: PageParams & ProductSearchParams = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: currentKeyword.value,
    }
    // 非默认排序才传排序参数
    if (currentSort.value !== 'default') {
      params.sortBy = currentSort.value
      params.sortOrder = sortOrder.value
    }
    // 价格区间筛选（元转分，后端按分计算）
    if (minPrice.value !== undefined && minPrice.value !== null) {
      params.minPrice = Math.round(minPrice.value * 100)
    }
    if (maxPrice.value !== undefined && maxPrice.value !== null) {
      params.maxPrice = Math.round(maxPrice.value * 100)
    }
    const res = await searchProducts(params)
    searchResults.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    searchResults.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 监听URL参数变化（从导航栏搜索时触发）
 */
watch(() => route.query.keyword, (newKeyword) => {
  const kw = (newKeyword as string) || ''
  if (kw && kw !== currentKeyword.value) {
    keyword.value = kw
    handleSearch()
  }
})

/**
 * 加载热门搜索词
 * 调用后端API获取，基于Redis ZSet按搜索次数排序
 * 如果API返回空列表（无人搜索过），使用默认热搜词
 */
const loadHotKeywords = async () => {
  try {
    const res = await getHotKeywords()
    const keywords = res.data || []
    hotSearchTags.value = keywords.length > 0 ? keywords : defaultHotTags
  } catch {
    // API失败时使用默认热搜词
    hotSearchTags.value = defaultHotTags
  }
}

onMounted(() => {
  loadHistory()
  loadHotKeywords()
  // 如果URL带了关键词，直接搜索
  if (keyword.value) {
    currentKeyword.value = keyword.value
    hasSearched.value = true
    saveHistory(keyword.value)
    loadSearchResults()
  }
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.search-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 48px 0 80px;
}

/* ==================== 搜索前：热门 & 历史 ==================== */
.search-before {
  max-width: 800px;
  margin: 0 auto;
  padding: 80px 0;
}

.search-section {
  margin-bottom: 64px;
}

.search-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

/* 区块标题：衬线字体 */
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 24px;
  letter-spacing: -0.01em;
}

.title-icon {
  font-size: 18px;
  color: var(--color-accent);
}

.clear-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.clear-btn:hover {
  color: var(--color-primary);
}

/* 标签列表 */
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

/* 搜索标签：极简边框风格 */
.search-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: transparent;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-base);
  border: 1px solid var(--color-border);
  letter-spacing: 0.02em;
}

.search-tag:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary);
}

/* 前三名热门标签：排名序号 */
.tag-hot {
  font-weight: 500;
}

.tag-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: var(--color-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

/* 第二名香槟金 */
.tag-hot:nth-child(2) .tag-rank {
  background: var(--color-accent);
}

/* 第三名浅灰 */
.tag-hot:nth-child(3) .tag-rank {
  background: var(--color-text-muted);
}

.history-tag {
  background: var(--color-bg);
}

.empty-hint {
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

/* ==================== 搜索结果 ==================== */
.search-result {
  padding-top: 24px;
}

/* 结果统计 + 排序栏 */
.result-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 40px;
  flex-wrap: wrap;
  gap: 16px;
}

.result-info {
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.keyword-highlight {
  color: var(--color-text);
  font-weight: 500;
}

.result-count {
  margin-left: 12px;
  color: var(--color-text-muted);
}

/* 排序栏 */
.sort-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
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

.sort-arrow {
  font-size: 10px;
}

/* 价格区间筛选 */
.price-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
  padding-left: 16px;
  border-left: 1px solid var(--color-border);
}

.price-input {
  width: 80px;
  height: 32px;
  border: 1px solid var(--color-border);
  padding: 0 12px;
  font-size: 12px;
  text-align: center;
  outline: none;
  color: var(--color-text);
  background: transparent;
  transition: border-color var(--transition-base);
}

.price-input:focus {
  border-color: var(--color-primary);
}

.price-separator {
  color: var(--color-text-muted);
  font-size: 12px;
}

.price-confirm {
  height: 32px;
  padding: 0 16px;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.price-confirm:hover {
  background: transparent;
  color: var(--color-primary);
}

/* 商品网格 */
.product-grid-wrapper {
  min-height: 400px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 120px 0;
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
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .search-page {
    padding: 24px 0 40px;
  }

  .search-before {
    padding: 40px 0;
  }

  .product-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .result-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .price-filter {
    margin-left: 0;
    padding-left: 0;
    border-left: none;
  }
}
</style>
