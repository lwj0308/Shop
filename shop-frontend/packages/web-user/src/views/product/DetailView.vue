<template>
  <!-- 商品详情页 - 玻璃拟态风（Glassmorphism + 翡翠青绿渐变） -->
  <div class="product-detail">
    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 商品详情内容 -->
    <template v-else-if="product">
      <!-- 面包屑导航 -->
      <div class="breadcrumb">
        <router-link to="/" class="breadcrumb-link">首页</router-link>
        <span class="breadcrumb-sep">/</span>
        <router-link to="/category" class="breadcrumb-link">{{ product.categoryName || '全部分类' }}</router-link>
        <span class="breadcrumb-sep">/</span>
        <span class="breadcrumb-current">{{ product.name }}</span>
      </div>

      <!-- 商品主信息区：粘性图片 + 长详情 -->
      <div class="detail-main">
        <!-- 左侧：粘性图片区（玻璃面板） -->
        <div class="detail-left">
          <div class="image-sticky">
            <!-- 大图展示 -->
            <div class="main-image-wrapper">
              <img
                :src="currentImage"
                :alt="product.name"
                class="main-image"
              />
            </div>
            <!-- 底部缩略图 -->
            <div v-if="product.images && product.images.length" class="thumbnail-list">
              <div
                v-for="(img, index) in product.images"
                :key="index"
                :class="['thumbnail-item', { active: currentImageIndex === index }]"
                @click="currentImageIndex = index"
              >
                <img :src="img" :alt="`缩略图${index + 1}`" />
              </div>
            </div>
            <!-- 分享收藏栏 -->
            <div class="image-actions">
              <span class="image-action" @click="handleFavorite">
                <el-icon><Star /></el-icon> 收藏
              </span>
              <span class="image-action">
                <el-icon><Share /></el-icon> 分享
              </span>
            </div>
          </div>
        </div>

        <!-- 右侧：商品信息区 -->
        <div class="detail-right">
          <!-- 商品标题 -->
          <h1 class="detail-name">{{ product.name }}</h1>

          <!-- 价格区（玻璃面板 + 渐变价格） -->
          <div class="price-section">
            <div class="price-row">
              <span class="current-price">
                <span class="price-unit">¥</span>{{ currentPrice }}
              </span>
              <span class="original-price">¥{{ originalPrice }}</span>
              <span class="discount-tag">直降¥{{ discount }}</span>
            </div>
            <div class="price-info">
              <span>促销：<span class="highlight">满2000减100</span></span>
              <span>评论：<span class="sales-count">{{ product.commentSummary?.totalCount ?? 0 }}</span></span>
            </div>
          </div>

          <!-- 分隔线 -->
          <div class="divider"></div>

          <!-- SKU选择器：胶囊按钮 -->
          <div class="sku-selector">
            <div v-for="spec in product.specs" :key="spec.name" class="sku-group">
              <span class="sku-label">{{ spec.name }}</span>
              <div class="sku-list">
                <button
                  v-for="value in spec.values"
                  :key="value"
                  :class="['sku-btn', { active: selectedSpecs[spec.name] === value }]"
                  @click="selectSpec(spec.name, value)"
                >
                  {{ value }}
                </button>
              </div>
            </div>
          </div>

          <!-- 数量选择器 -->
          <div class="quantity-section">
            <span class="qty-label">数量</span>
            <div class="qty-control">
              <button class="qty-btn" @click="quantity = Math.max(1, quantity - 1)">-</button>
              <input type="text" :value="quantity" class="qty-input" readonly />
              <button class="qty-btn" @click="quantity = Math.min(currentStock, quantity + 1)">+</button>
            </div>
            <span class="stock-text">库存 {{ currentStock }} 件</span>
          </div>

          <!-- 服务承诺标签 -->
          <div class="service-tags">
            <span class="service-item"><el-icon><Check /></el-icon> 7天无理由</span>
            <span class="service-item"><el-icon><Check /></el-icon> 正品保障</span>
            <span class="service-item"><el-icon><Check /></el-icon> 极速退款</span>
            <span class="service-item"><el-icon><Check /></el-icon> 全国联保</span>
          </div>

          <!-- 操作按钮：玻璃次按钮 + 渐变主按钮 -->
          <div class="detail-actions">
            <button
              class="btn-cart"
              :disabled="!selectedSku || cartLoading"
              @click="handleAddToCart"
            >
              {{ cartLoading ? '添加中...' : '加入购物车' }}
            </button>
            <button
              class="btn-buy"
              :disabled="!selectedSku"
              @click="handleBuyNow"
            >
              立即购买
            </button>
          </div>

          <!-- 分隔线 -->
          <div class="divider"></div>

          <!-- 商品描述 -->
          <div class="detail-section">
            <h2 class="section-heading">商品描述</h2>
            <p class="detail-desc">{{ product.subtitle || '精选优质商品，品质保障，让您购物无忧。' }}</p>
          </div>

          <!-- 规格参数 -->
          <div class="detail-section">
            <h2 class="section-heading">规格参数</h2>
            <div class="spec-table">
              <div class="spec-row">
                <span class="spec-label">品牌</span>
                <span class="spec-value">SHOPMALL</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">商品分类</span>
                <span class="spec-value">{{ product.categoryName || '未分类' }}</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">库存</span>
                <span class="spec-value">{{ product.totalStock }} 件</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">SKU数量</span>
                <span class="spec-value">{{ product.skus?.length || 0 }} 种</span>
              </div>
            </div>
          </div>

          <!-- 用户评价 -->
          <div class="detail-section">
            <h2 class="section-heading">用户评价</h2>
            <!-- 评价摘要：好评率 + 总评价数 -->
            <div class="review-summary">
              <span class="review-rate">{{ goodRatePercent }}%</span>
              <span class="review-rate-label">好评率 · 共 {{ reviewTotal }} 条评价</span>
            </div>
            <!-- 评分筛选 Tab：全部 / 好评 / 中评 / 差评 -->
            <div class="review-tabs">
              <button
                v-for="tab in scoreTypeTabs"
                :key="tab.value"
                :class="['review-tab', { active: currentScoreType === tab.value }]"
                @click="switchScoreType(tab.value)"
              >{{ tab.label }}</button>
            </div>
            <!-- 评价列表加载中 -->
            <div v-if="reviewLoading" class="review-loading">
              <el-skeleton :rows="6" animated />
            </div>
            <!-- 评价列表空状态 -->
            <div v-else-if="reviewList.length === 0" class="review-empty">
              <span class="empty-icon">💬</span>
              <p class="empty-text">暂无评价，快来抢沙发吧</p>
            </div>
            <!-- 评价列表 -->
            <div v-else class="review-list">
              <div v-for="review in reviewList" :key="review.id" class="review-item">
                <div class="review-user">
                  <div class="user-avatar">{{ getAvatarText(review) }}</div>
                  <span class="user-name">{{ getDisplayName(review) }}</span>
                  <el-rate
                    :model-value="review.score"
                    disabled
                    size="small"
                    :colors="['#F7BA0A', '#F7BA0A', '#C9A961']"
                  />
                  <span class="review-date">{{ formatDate(review.createTime, 'date') }}</span>
                </div>
                <p class="review-content">{{ review.content }}</p>
                <!-- 评价图片 -->
                <div v-if="review.images && review.images.length" class="review-images">
                  <el-image
                    v-for="(img, index) in review.images"
                    :key="index"
                    :src="img"
                    :preview-src-list="review.images"
                    :initial-index="index"
                    fit="cover"
                    class="review-image"
                    preview-teleported
                  />
                </div>
                <!-- 商家回复 -->
                <div v-if="review.reply" class="merchant-reply">
                  <span class="reply-label">商家回复：</span>
                  <span class="reply-text">{{ review.reply }}</span>
                </div>
                <!-- 追评列表 -->
                <div
                  v-if="review.replyList && review.replyList.length"
                  class="append-list"
                >
                  <div v-for="append in review.replyList" :key="append.id" class="append-item">
                    <div class="append-header">
                      <span class="append-tag">追评</span>
                      <span class="append-date">{{ formatDate(append.createTime, 'date') }}</span>
                    </div>
                    <p class="append-content">{{ append.content }}</p>
                    <div v-if="append.images && append.images.length" class="review-images">
                      <el-image
                        v-for="(img, index) in append.images"
                        :key="index"
                        :src="img"
                        :preview-src-list="append.images"
                        :initial-index="index"
                        fit="cover"
                        class="review-image"
                        preview-teleported
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!-- 分页 -->
            <div v-if="reviewTotal > reviewPageSize" class="review-pagination">
              <el-pagination
                v-model:current-page="reviewPageNum"
                :page-size="reviewPageSize"
                :total="reviewTotal"
                layout="prev, pager, next"
                small
                @current-change="handleReviewPageChange"
              />
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 商品不存在 -->
    <el-empty v-else description="商品不存在或已下架" />

    <!-- 看了又看：相关推荐商品 -->
    <section v-if="!loading && product && relatedProducts.length > 0" class="related-section">
      <div class="section-header-center">
        <p class="section-eyebrow">YOU MAY ALSO LIKE</p>
        <h2 class="section-title">看了又看</h2>
        <p class="section-desc">与该商品同分类的热销推荐</p>
      </div>
      <div v-if="relatedLoading" class="related-loading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else class="related-grid">
        <div
          v-for="item in relatedProducts"
          :key="item.id"
          class="related-item"
          @click="goToRelatedProduct(item.id)"
        >
          <div class="related-image">
            <img :src="item.mainImage" :alt="item.name" loading="lazy" />
          </div>
          <div class="related-info">
            <h4 class="related-name">{{ item.name }}</h4>
            <p class="related-price">¥{{ formatRelatedPrice(item.minPrice) }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 商品详情页（玻璃拟态风 - Glassmorphism）
 * 面包屑导航 + 左右分栏(图片+信息) + 店铺信息 + Tab(评价等)
 */

import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, Share, Check } from '@element-plus/icons-vue'
import { getProductDetail, getCommentList, getRelatedProducts } from '@shop/shared'
import { useCart, useDebounce } from '@shop/shared'
import { formatDate, commentScoreTypeOptions } from '@shop/shared'
import { addFavorite } from '@shop/shared'
// 注意：CommentScoreType 是 enum（值），不能用 import type 导入
import { CommentScoreType } from '@shop/shared'
import type { ProductInfo, ProductSku, CommentInfo } from '@shop/shared'
import { withAuth } from '@/utils/authGuard'

const route = useRoute()
const router = useRouter()
const { addToCart } = useCart()

/** 商品信息 */
const product = ref<ProductInfo | null>(null)
/** 是否加载中 */
const loading = ref(true)
/** 用户选中的规格值（如：{颜色: "红色", 存储: "256GB"}） */
const selectedSpecs = reactive<Record<string, string>>({})
/** 购买数量 */
const quantity = ref(1)
/** 当前选中的图片索引 */
const currentImageIndex = ref(0)

/** RL-08：加入购物车按钮防抖（1秒冷却，防止快速连续点击） */
const { loading: cartLoading, run: runAddToCart } = useDebounce(1000)

// ==================== 相关推荐 ====================
/** 相关推荐商品列表（同分类商品，按销量降序） */
const relatedProducts = ref<ProductInfo[]>([])
/** 相关推荐是否加载中 */
const relatedLoading = ref(false)

// ==================== 评价相关状态 ====================
/** 评价列表（接口返回） */
const reviewList = ref<CommentInfo[]>([])
/** 评价总数 */
const reviewTotal = ref(0)
/** 评价列表是否正在加载 */
const reviewLoading = ref(false)
/** 当前评价页码 */
const reviewPageNum = ref(1)
/** 每页评价条数 */
const reviewPageSize = 5
/** 当前选中的评分类型（全部/好评/中评/差评） */
const currentScoreType = ref<CommentScoreType>(CommentScoreType.ALL)
/** 评分类型 Tab 列表（从常量里拿） */
const scoreTypeTabs = commentScoreTypeOptions

/** 好评率（取整百分比） */
const goodRatePercent = computed(() => {
  const summary = product.value?.commentSummary
  if (!summary || summary.totalCount === 0) return 0
  // goodRate 后端返回 0-100 的数，保留整数百分比
  return Math.round(summary.goodRate)
})

/**
 * 获取评价展示昵称
 * 匿名评价显示"匿名用户"
 * @param review - 评价对象
 */
const getDisplayName = (review: CommentInfo): string => {
  if (review.isAnonymous === 1) return '匿名用户'
  return review.userNickname || `用户${review.userId}`
}

/**
 * 获取头像首字（用于头像占位）
 * 匿名用户返回"匿"，其他返回昵称第一个字
 * @param review - 评价对象
 */
const getAvatarText = (review: CommentInfo): string => {
  if (review.isAnonymous === 1) return '匿'
  const name = review.userNickname || '用'
  return name.charAt(0)
}

/**
 * 切换评分类型 Tab 后重新拉取评价
 * @param type - 评分类型枚举
 */
const switchScoreType = (type: CommentScoreType) => {
  if (currentScoreType.value === type) return
  currentScoreType.value = type
  reviewPageNum.value = 1
  fetchReviewList()
}

/**
 * 翻页时重新拉取评价
 */
const handleReviewPageChange = () => {
  fetchReviewList()
}

/**
 * 拉取商品评价列表
 * 按当前选中的 scoreType 和页码请求
 */
const fetchReviewList = async () => {
  if (!product.value) return
  reviewLoading.value = true
  try {
    const res = await getCommentList(product.value.id, {
      pageNum: reviewPageNum.value,
      pageSize: reviewPageSize,
      scoreType: currentScoreType.value,
    })
    reviewList.value = res.data.records || []
    reviewTotal.value = res.data.total || 0
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载评价失败'
    ElMessage.error(msg)
    // 失败时清空数据，避免展示旧数据
    reviewList.value = []
    reviewTotal.value = 0
  } finally {
    reviewLoading.value = false
  }
}

/** 当前展示的大图 */
const currentImage = computed(() => {
  if (product.value?.images && product.value.images.length > 0) {
    return product.value.images[currentImageIndex.value] || product.value.mainImage
  }
  return product.value?.mainImage || ''
})

/**
 * 当前选中的SKU
 * 当用户选完所有规格后，根据规格组合从SKU列表中匹配对应的SKU
 */
const selectedSku = computed<ProductSku | null>(() => {
  if (!product.value?.skus || product.value.skus.length === 0) return null
  // 检查是否所有规格都已选择
  const specNames = product.value.specs.map(s => s.name)
  if (!specNames.every(name => selectedSpecs[name])) return null
  // 根据选中的规格组合查找匹配的SKU
  return product.value.skus.find(sku => {
    return specNames.every(name => sku.specValues[name] === selectedSpecs[name])
  }) ?? null
})

/** 当前库存：选中SKU后显示SKU库存，否则显示总库存 */
const currentStock = computed(() => {
  if (selectedSku.value) return selectedSku.value.stock
  return product.value?.totalStock ?? 0
})

/** 当前价格（选中SKU后显示SKU价格） */
const currentPrice = computed(() => {
  if (selectedSku.value) return selectedSku.value.price.toFixed(2).replace(/\.00$/, '')
  return product.value?.minPrice.toFixed(2).replace(/\.00$/, '') ?? '0'
})

/** 原价（选中SKU后显示SKU原价，否则比现价高25%） */
const originalPrice = computed(() => {
  if (selectedSku.value?.originalPrice) return selectedSku.value.originalPrice.toFixed(0)
  const price = product.value?.minPrice ?? 0
  return (price * 1.25).toFixed(0)
})

/** 降价金额 */
const discount = computed(() => {
  const price = selectedSku.value?.price ?? product.value?.minPrice ?? 0
  return (price * 0.25).toFixed(0)
})

/**
 * 选择规格值（如选择"颜色"为"红色"）
 * 每次选择后检查是否所有规格都已选，如果都选了则自动匹配SKU
 * @param specName - 规格名称（如：颜色、存储）
 * @param value - 规格值（如：红色、256GB）
 */
const selectSpec = (specName: string, value: string) => {
  selectedSpecs[specName] = value
}

/**
 * 执行加入购物车操作（已登录状态下调用）（RL-08 改造：使用 useDebounce 防抖）
 * @returns 是否成功
 */
const doAddToCart = async (): Promise<boolean | undefined> => {
  if (!selectedSku.value || !product.value) return false

  return runAddToCart(async () => {
    try {
      await addToCart(product.value!.id, selectedSku.value!.id, quantity.value)
      ElMessage.success('已加入购物车')
      return true
    } catch (error) {
      const msg = error instanceof Error ? error.message : '加入购物车失败'
      ElMessage.error(msg)
      return false
    }
  })
}

/**
 * 加入购物车（RL-08 改造：防抖保护）
 * 未登录时弹出登录弹窗，登录成功后自动执行加购
 */
const handleAddToCart = () => {
  if (!selectedSku.value) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  // 未登录：弹出登录弹窗，登录成功后自动加购；已登录：直接加购
  withAuth('登录后加入购物车', async () => {
    await doAddToCart()
  })
}

/**
 * 立即购买
 * 未登录时弹出登录弹窗，登录成功后跳转确认订单页
 */
const handleBuyNow = () => {
  if (!selectedSku.value) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  // 未登录：弹出登录弹窗，登录成功后跳转确认订单页
  withAuth('登录后立即购买', () => {
    router.push({ name: 'OrderConfirm' })
  })
}

/**
 * 执行收藏操作（已登录状态下调用）
 * 调用后端收藏接口，后端通过唯一索引保证不能重复收藏
 * @returns 是否成功
 */
const doFavorite = async (): Promise<boolean> => {
  if (!product.value) return false
  try {
    await addFavorite(product.value.id)
    ElMessage.success('已收藏该商品')
    return true
  } catch (error) {
    const msg = error instanceof Error ? error.message : '收藏失败'
    ElMessage.error(msg)
    return false
  }
}

/**
 * 收藏商品
 * 未登录时弹出登录弹窗，登录后执行真实收藏接口
 */
const handleFavorite = () => {
  withAuth('登录后收藏商品', () => {
    doFavorite()
  })
}

/**
 * 加载相关推荐商品（看了又看）
 * 查询与当前商品同分类的其他商品，按销量降序取前5个
 */
const fetchRelatedProducts = async () => {
  if (!product.value) return
  relatedLoading.value = true
  try {
    const res = await getRelatedProducts(product.value.id, 5)
    relatedProducts.value = res.data || []
  } catch {
    // 相关推荐加载失败不影响主流程
    relatedProducts.value = []
  } finally {
    relatedLoading.value = false
  }
}

/**
 * 跳转到相关推荐商品详情
 * @param id - 商品ID
 */
const goToRelatedProduct = (id: number) => {
  router.push(`/product/${id}`)
}

/**
 * 格式化相关推荐商品价格（去掉.00）
 * @param price - 价格
 */
const formatRelatedPrice = (price: number | undefined) => {
  if (price == null) return '0'
  return price.toFixed(2).replace(/\.00$/, '')
}

/**
 * 关注店铺
 * 未登录时弹出登录弹窗，登录后执行关注
 */
const handleFollowShop = () => {
  withAuth('登录后关注店铺', () => {
    ElMessage.success('已关注店铺')
  })
}

/**
 * 初始化默认选中的规格
 * 单SKU：直接选中该SKU的所有规格值
 * 多SKU：默认选中第一个SKU的所有规格值（即每个规格的第一个可选值）
 */
const initDefaultSelection = () => {
  if (!product.value?.specs || product.value.specs.length === 0) return
  // 遍历每个规格，默认选第一个值
  product.value.specs.forEach(spec => {
    if (spec.values.length > 0) {
      selectedSpecs[spec.name] = spec.values[0]
    }
  })
}

/**
 * 加载商品详情
 * 商品详情加载完成后并行加载评价列表和相关推荐
 */
onMounted(async () => {
  const id = Number(route.params.id)
  if (!id || isNaN(id)) {
    loading.value = false
    return
  }
  try {
    const res = await getProductDetail(id)
    product.value = res.data
    // 商品加载完成后，自动选中默认规格
    initDefaultSelection()
    // 商品详情加载完成后，并行拉取评价列表和相关推荐
    fetchReviewList()
    fetchRelatedProducts()
  } catch {
    product.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.product-detail {
  padding: 0;
  max-width: 1280px;
  margin: 0 auto;
  /* 进入动画：与全局 fadeInUp 一致 */
  animation: fadeInUp 0.4s var(--ease-out);
}

.skeleton {
  padding: 80px 40px;
  background: var(--color-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  margin: 24px 0;
}

/* ==================== 面包屑导航 ==================== */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.breadcrumb-link {
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color var(--transition-base);
}

.breadcrumb-link:hover {
  color: var(--color-primary);
}

.breadcrumb-sep {
  color: var(--color-text-muted);
  opacity: 0.5;
}

.breadcrumb-current {
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
  font-weight: 600;
}

/* ==================== 商品主信息区：左右分栏 ==================== */
.detail-main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  padding: 24px 0 80px;
}

/* ==================== 左侧：粘性图片区（玻璃面板） ==================== */
.detail-left {
  position: relative;
}

/* 粘性定位：滚动时图片固定在视口顶部 */
.image-sticky {
  position: sticky;
  top: 96px;
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-xl);
  padding: var(--space-lg);
  box-shadow: var(--shadow-md);
}

/* 主图容器：圆角 + 浅渐变底 */
.main-image-wrapper {
  width: 100%;
  aspect-ratio: 1 / 1;
  background: linear-gradient(135deg, #F8FAFC, #ECFDF5);
  overflow: hidden;
  margin-bottom: 16px;
  position: relative;
  border-radius: var(--radius-lg);
}

.main-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s var(--ease-out);
}

.main-image-wrapper:hover .main-image {
  transform: scale(1.05);
}

/* 缩略图列表：玻璃圆角小图 */
.thumbnail-list {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.thumbnail-item {
  width: 64px;
  height: 64px;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-base);
  opacity: 0.7;
  background: #F8FAFC;
}

.thumbnail-item:hover {
  opacity: 1;
  border-color: var(--color-primary-light);
  transform: translateY(-2px);
}

.thumbnail-item.active {
  opacity: 1;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}

.thumbnail-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 分享收藏栏：玻璃分割线 */
.image-actions {
  display: flex;
  gap: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.image-action {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color var(--transition-base);
  letter-spacing: 0.02em;
  font-weight: 600;
}

.image-action:hover {
  color: var(--color-primary);
}

.image-action .el-icon {
  font-size: 16px;
}

/* ==================== 右侧：商品信息区 ==================== */
.detail-right {
  display: flex;
  flex-direction: column;
}

/* 商品标题：现代几何字体 */
.detail-name {
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.25;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

/* ==================== 价格区：玻璃面板 + 渐变价格 ==================== */
.price-section {
  margin-bottom: 28px;
}

/* 价格行：柔和渐变背景 + 圆角 */
.price-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  flex-wrap: wrap;
  padding: 20px 24px;
  background: var(--gradient-brand-soft);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
}

/* 当前价格：翡翠青绿渐变文字 */
.current-price {
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.price-unit {
  font-size: 18px;
  font-weight: 700;
  margin-right: 2px;
}

.original-price {
  color: var(--color-text-muted);
  text-decoration: line-through;
  font-size: 16px;
  font-weight: 400;
}

/* 降价标签：玻璃红胶囊 */
.discount-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: rgba(239, 68, 68, 0.12);
  color: var(--color-error);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.price-info {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.highlight {
  color: var(--color-primary);
  font-weight: 600;
}

.sales-count {
  color: var(--color-text);
  font-weight: 600;
}

/* ==================== 极细分隔线 ==================== */
.divider {
  height: 1px;
  background: var(--color-border);
  margin: 28px 0;
}

/* ==================== SKU 选择器：胶囊按钮 ==================== */
.sku-selector {
  margin-bottom: 28px;
}

.sku-group {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 20px;
}

.sku-group:last-child {
  margin-bottom: 0;
}

.sku-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  margin-top: 10px;
  white-space: nowrap;
  min-width: 40px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* SKU按钮：胶囊式，选中渐变背景 */
.sku-btn {
  padding: 10px 20px;
  border: 1.5px solid var(--color-border);
  background: var(--color-surface, #fff);
  border-radius: var(--radius-pill);
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  transition: all var(--transition-base);
  letter-spacing: 0.02em;
}

.sku-btn:hover {
  border-color: var(--color-primary-light);
  transform: translateY(-1px);
}

/* 选中：渐变背景 + 翡翠描边 */
.sku-btn.active {
  background: var(--gradient-brand-soft);
  border-color: var(--color-primary);
  color: var(--color-primary-hover);
}

/* ==================== 数量选择器 ==================== */
.quantity-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.qty-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  min-width: 40px;
}

/* 数量步进器：胶囊式 */
.qty-control {
  display: inline-flex;
  align-items: center;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
  background: var(--color-surface, #fff);
}

.qty-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: all var(--transition-base);
}

.qty-btn:hover {
  background: var(--gradient-brand-soft);
  color: var(--color-primary);
}

.qty-input {
  width: 48px;
  height: 40px;
  text-align: center;
  border: none;
  border-left: 1px solid var(--color-border);
  border-right: 1px solid var(--color-border);
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  outline: none;
  color: var(--color-text);
  background: transparent;
}

.stock-text {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-left: 8px;
  letter-spacing: 0.02em;
}

/* ==================== 服务承诺 ==================== */
.service-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding-top: 24px;
  margin-top: 28px;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.service-item {
  display: flex;
  align-items: center;
  gap: 6px;
  letter-spacing: 0.02em;
  font-weight: 500;
}

.service-item .el-icon {
  color: var(--color-primary);
  font-size: 14px;
}

/* ==================== 操作按钮：渐变主按钮 + 玻璃次按钮 ==================== */
.detail-actions {
  display: flex;
  gap: 14px;
  margin-top: 36px;
  margin-bottom: 8px;
}

/* 加入购物车：玻璃次按钮 */
.btn-cart {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--color-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--color-text);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px 32px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition-base);
  letter-spacing: 0.03em;
}

.btn-cart:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--color-primary);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.15);
  background: rgba(255, 255, 255, 0.85);
}

/* 立即购买：翡翠青绿渐变主按钮 + 光泽掠过 */
.btn-buy {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--gradient-brand);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  padding: 16px 32px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  letter-spacing: 0.03em;
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.3);
  transition: transform 0.2s var(--ease-out), box-shadow 0.3s var(--ease-out);
}

.btn-buy:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.4), 0 0 32px rgba(6, 182, 212, 0.3);
}

/* 光泽掠过 */
.btn-buy::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 30%, rgba(255, 255, 255, 0.35) 50%, transparent 70%);
  transform: translateX(-100%);
  transition: transform 0.6s var(--ease-out);
  pointer-events: none;
}

.btn-buy:hover:not(:disabled)::after {
  transform: translateX(100%);
}

/* 按钮禁用状态 */
.btn-cart:disabled,
.btn-buy:disabled {
  background: var(--color-bg-secondary);
  color: var(--color-text-muted);
  border-color: var(--color-border);
  box-shadow: none;
  cursor: not-allowed;
}

/* ==================== 详情区块（玻璃面板） ==================== */
.detail-section {
  margin-bottom: 48px;
  padding: 24px;
  background: var(--color-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.detail-section:last-child {
  margin-bottom: 0;
}

/* 区块标题：现代几何字体 */
.section-heading {
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 20px;
  letter-spacing: -0.01em;
}

/* 商品描述文字 */
.detail-desc {
  font-size: 15px;
  color: var(--color-text-secondary);
  line-height: 1.8;
  margin: 0;
  letter-spacing: 0.01em;
}

/* ==================== 规格参数表 ==================== */
.spec-table {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.spec-row {
  display: flex;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
  transition: background var(--transition-base);
}

.spec-row:first-child {
  border-top: none;
}

.spec-row:last-child {
  border-bottom: none;
}

.spec-row:hover {
  background: var(--gradient-brand-soft);
}

.spec-label {
  width: 160px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

.spec-value {
  flex: 1;
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

/* ==================== 评价区 ==================== */
/* 评价摘要：渐变背景面板 */
.review-summary {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  padding: 24px;
  background: var(--gradient-brand-soft);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
}

/* 评分大字：渐变文字 */
.review-rate {
  font-family: var(--font-heading);
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  font-size: 48px;
  font-weight: 800;
  line-height: 1;
}

.review-rate-label {
  font-size: 14px;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
  font-weight: 500;
}

/* 评分筛选 Tab：胶囊式 */
.review-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.review-tab {
  padding: 8px 18px;
  background: var(--color-surface, #fff);
  color: var(--color-text-secondary);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  transition: all var(--transition-base);
}

.review-tab:hover {
  border-color: var(--color-primary-light);
  color: var(--color-primary);
}

.review-tab.active {
  background: var(--gradient-brand);
  color: #fff;
  border-color: transparent;
}

/* 评价加载与空状态 */
.review-loading {
  padding: 24px 0;
}

.review-empty {
  padding: 48px 0;
  text-align: center;
}

.review-empty .empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 12px;
}

.review-empty .empty-text {
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 评价项：玻璃卡片 */
.review-item {
  padding: 20px;
  background: var(--color-surface, #fff);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  transition: var(--transition-base);
}

.review-item:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-sm);
}

.review-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

/* 用户头像：渐变底圆形 */
.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--gradient-brand-soft);
  color: var(--color-primary-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}

.user-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

.review-date {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-left: auto;
  letter-spacing: 0.02em;
}

.review-content {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  margin: 0 0 12px;
  padding-left: 56px;
}

/* 评价图片 */
.review-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: 56px;
  margin-bottom: 12px;
}

.review-image {
  width: 80px;
  height: 80px;
  cursor: pointer;
  object-fit: cover;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

/* 商家回复：玻璃面板 */
.merchant-reply {
  margin-left: 56px;
  margin-bottom: 12px;
  padding: 14px 18px;
  background: var(--color-glass);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.7;
}

.reply-label {
  color: var(--color-primary);
  font-weight: 700;
}

.reply-text {
  color: var(--color-text-secondary);
}

/* 追评列表 */
.append-list {
  margin-left: 56px;
  border-left: 2px solid var(--color-primary-light);
  padding-left: 16px;
}

.append-item {
  padding: 8px 0;
}

.append-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.append-tag {
  display: inline-block;
  padding: 2px 10px;
  background: var(--gradient-brand);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  border-radius: var(--radius-pill);
}

.append-date {
  font-size: 12px;
  color: var(--color-text-muted);
}

.append-content {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  margin: 0 0 8px;
}

/* 分页 */
.review-pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* ==================== 看了又看：相关推荐 ==================== */
.related-section {
  padding: 48px 0 80px;
  border-top: 1px solid var(--color-border);
  margin-top: 40px;
}

.section-header-center {
  text-align: center;
  margin-bottom: 32px;
}

.related-section .section-eyebrow {
  font-size: var(--font-size-caption);
  letter-spacing: 0.3em;
  color: var(--color-primary);
  text-transform: uppercase;
  margin-bottom: 8px;
  font-weight: 600;
}

.related-section .section-title {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 8px;
  letter-spacing: -0.01em;
}

.related-section .section-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
}

.related-loading {
  padding: 24px 0;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
}

/* 相关推荐卡片：玻璃卡片 */
.related-item {
  cursor: pointer;
  background: var(--color-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  padding: 8px;
  transition: transform var(--transition-base), box-shadow var(--transition-base),
              border-color var(--transition-base);
}

.related-item:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--color-primary-light);
}

.related-image {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: linear-gradient(135deg, #F8FAFC, #ECFDF5);
  margin-bottom: 12px;
  border-radius: var(--radius-md);
}

.related-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease-out);
}

.related-item:hover .related-image img {
  transform: scale(1.05);
}

.related-info {
  text-align: center;
  padding: 0 4px;
}

.related-name {
  font-size: 13px;
  color: var(--color-text);
  margin: 0 0 4px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  height: 38px;
}

/* 相关推荐价格：渐变文字 */
.related-price {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 700;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 1024px) {
  .detail-main {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .image-sticky {
    position: static;
  }

  .detail-name {
    font-size: 26px;
  }

  .current-price {
    font-size: 30px;
  }

  .related-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .detail-main {
    gap: 20px;
    padding: 16px 0 40px;
  }

  .detail-name {
    font-size: 22px;
    margin-bottom: 16px;
  }

  .current-price {
    font-size: 26px;
  }

  .detail-actions {
    flex-direction: column;
  }

  .service-tags {
    gap: 12px;
  }

  .spec-label {
    width: 100px;
  }

  .review-content,
  .review-images,
  .merchant-reply,
  .append-list {
    padding-left: 0;
    margin-left: 0;
  }

  .related-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
