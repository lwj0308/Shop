<template>
  <!-- 首页 - 极简独立站风格 -->
  <div class="home-page">
    <!-- Hero 区：全屏大图 + 大标题 + CTA 按钮 -->
    <section class="hero-section">
      <div class="hero-bg" :style="{ backgroundImage: `url(${heroImage})` }"></div>
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <p class="hero-eyebrow">2026 新季上市</p>
        <h1 class="hero-title">品质生活<br/>从这里开始</h1>
        <p class="hero-subtitle">精选全球好物，为您带来极致购物体验</p>
        <div class="hero-actions">
          <router-link to="/category" class="btn-hero-primary">
            探索全部分类
            <el-icon><ArrowRight /></el-icon>
          </router-link>
          <a href="javascript:void(0)" class="btn-hero-outline" @click="scrollToProducts">精选好物</a>
        </div>
      </div>
      <div class="hero-scroll-hint">
        <span class="scroll-line"></span>
        <span class="scroll-text">向下滚动</span>
      </div>
    </section>

    <!-- 分类入口：圆形图标网格 -->
    <section class="category-section">
      <div class="container">
        <div class="category-grid">
          <div
            v-for="cat in categories"
            :key="cat.name"
            class="category-item"
            @click="handleCategoryClick(cat.keyword)"
          >
            <div class="category-icon">
              <el-icon :size="28"><component :is="cat.icon" /></el-icon>
            </div>
            <span class="category-name">{{ cat.name }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 精选商品：大图卡片网格 -->
    <section id="products" class="products-section">
      <div class="container">
        <div class="section-header">
          <p class="section-eyebrow">CURATED SELECTION</p>
          <h2 class="section-title">精选好物</h2>
          <p class="section-desc">为您精心挑选的品质商品</p>
        </div>
        <div v-if="productsLoading" class="products-loading">
          <div v-for="i in 6" :key="i" class="product-skeleton">
            <el-skeleton :rows="3" animated />
          </div>
        </div>
        <div v-else class="products-grid">
          <ProductCard
            v-for="item in recommendProducts"
            :key="item.id"
            :product="item"
          />
        </div>
        <div class="section-footer">
          <router-link to="/category" class="btn-outline">
            查看全部商品
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>
    </section>

    <!-- 品牌承诺区 -->
    <section class="promise-section">
      <div class="container">
        <div class="promise-grid">
          <div class="promise-item">
            <div class="promise-icon">
              <el-icon :size="32"><CircleCheck /></el-icon>
            </div>
            <h3 class="promise-title">正品保障</h3>
            <p class="promise-desc">所有商品均为品牌授权正品，假一赔十</p>
          </div>
          <div class="promise-item">
            <div class="promise-icon">
              <el-icon :size="32"><Van /></el-icon>
            </div>
            <h3 class="promise-title">极速配送</h3>
            <p class="promise-desc">当日下单次日达，全国大部分地区覆盖</p>
          </div>
          <div class="promise-item">
            <div class="promise-icon">
              <el-icon :size="32"><RefreshRight /></el-icon>
            </div>
            <h3 class="promise-title">7天无理由</h3>
            <p class="promise-desc">不满意可7天无理由退换，购物无忧</p>
          </div>
          <div class="promise-item">
            <div class="promise-icon">
              <el-icon :size="32"><Service /></el-icon>
            </div>
            <h3 class="promise-title">专属客服</h3>
            <p class="promise-desc">7x12小时在线客服，全程贴心服务</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 新品上架：横向滚动 -->
    <section v-if="newProducts.length > 0" class="new-arrivals-section">
      <div class="container">
        <div class="section-header">
          <p class="section-eyebrow">JUST ARRIVED</p>
          <h2 class="section-title">新品上架</h2>
          <p class="section-desc">最新到货的优质商品</p>
        </div>
        <div class="new-arrivals-list">
          <div
            v-for="item in newProducts"
            :key="item.id"
            class="new-arrival-item"
            @click="goToProduct(item.id)"
          >
            <div class="new-arrival-image">
              <img :src="item.mainImage" :alt="item.name" loading="lazy" />
            </div>
            <div class="new-arrival-info">
              <h4 class="new-arrival-name">{{ item.name }}</h4>
              <p class="new-arrival-price">¥{{ formatPrice(item.minPrice) }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 猜你喜欢：基于浏览足迹的个性化推荐 -->
    <section v-if="guessProducts.length > 0" class="guess-section">
      <div class="container">
        <div class="section-header">
          <p class="section-eyebrow">FOR YOU</p>
          <h2 class="section-title">猜你喜欢</h2>
          <p class="section-desc">基于您的浏览记录，为您精心推荐</p>
        </div>
        <div v-if="guessLoading" class="products-loading">
          <div v-for="i in 5" :key="i" class="product-skeleton">
            <el-skeleton :rows="3" animated />
          </div>
        </div>
        <div v-else class="guess-grid">
          <ProductCard
            v-for="item in guessProducts"
            :key="item.id"
            :product="item"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 首页（极简独立站风格）
 * 布局：全屏Hero大图 + 分类入口 + 精选商品网格 + 品牌承诺 + 新品上架
 */

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, CircleCheck, Van, RefreshRight, Service,
  Monitor, Cellphone, Headset, House,
  ShoppingBag, Football
} from '@element-plus/icons-vue'
import ProductCard from '@/components/ProductCard.vue'
import { getHotProducts, getNewProducts, getGuessProducts } from '@shop/shared'
import type { ProductInfo } from '@shop/shared'

const router = useRouter()

/** Hero 区背景图（使用渐变作为占位，实际可替换为真实图片） */
const heroImage = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=luxury%20minimalist%20e-commerce%20hero%20banner%20dark%20elegant%20products%20showcase%20premium%20quality%20soft%20lighting%20fashion%20lifestyle&image_size=landscape_16_9'

/** 分类入口数据 */
const categories = [
  { name: '家用电器', keyword: '家电', icon: House },
  { name: '手机数码', keyword: '手机', icon: Cellphone },
  { name: '电脑办公', keyword: '电脑', icon: Monitor },
  { name: '影音耳机', keyword: '耳机', icon: Headset },
  { name: '运动户外', keyword: '运动', icon: Football },
  { name: '全部商品', keyword: '', icon: ShoppingBag },
]

/** 热销推荐商品（精选好物区，按销量降序取前6个） */
const recommendProducts = ref<ProductInfo[]>([])

/** 新品上架数据（按创建时间降序取前6个） */
const newProducts = ref<ProductInfo[]>([])

/** 猜你喜欢数据（登录用户基于足迹分类推荐，未登录降级为全站热销） */
const guessProducts = ref<ProductInfo[]>([])

/** 商品是否加载中 */
const productsLoading = ref(true)

/** 猜你喜欢是否加载中 */
const guessLoading = ref(true)

/**
 * 从后端加载推荐数据
 * 并发请求三个推荐接口：
 * - 热销推荐：用于"精选好物"区（按销量降序）
 * - 新品推荐：用于"新品上架"区（按创建时间降序）
 * - 猜你喜欢：登录用户基于足迹分类，未登录降级为全站热销
 */
const loadRecommendData = async () => {
  // 三个接口互不依赖，并发请求提升加载速度
  const [hotRes, newRes, guessRes] = await Promise.allSettled([
    getHotProducts(6),
    getNewProducts(6),
    getGuessProducts(10),
  ])

  // 热销推荐（精选好物区）
  if (hotRes.status === 'fulfilled' && hotRes.value.code === 200) {
    recommendProducts.value = hotRes.value.data || []
  }

  // 新品上架区
  if (newRes.status === 'fulfilled' && newRes.value.code === 200) {
    newProducts.value = newRes.value.data || []
  }

  // 猜你喜欢区
  if (guessRes.status === 'fulfilled' && guessRes.value.code === 200) {
    guessProducts.value = guessRes.value.data || []
  }

  productsLoading.value = false
  guessLoading.value = false
}

/**
 * 点击分类跳转到搜索页
 */
const handleCategoryClick = (keyword: string) => {
  if (keyword) {
    router.push({ path: '/search', query: { keyword } })
  } else {
    router.push('/category')
  }
}

/**
 * 点击新品跳转到详情页
 */
const goToProduct = (id: number) => {
  router.push(`/product/${id}`)
}

/**
 * 滚动到精选商品区
 */
const scrollToProducts = () => {
  document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' })
}

/**
 * 格式化价格（去掉.00）
 */
const formatPrice = (price: number | undefined) => {
  if (price == null) return '0'
  return price.toFixed(2).replace(/\.00$/, '')
}

onMounted(() => {
  loadRecommendData()
})
</script>

<style scoped>
.home-page {
  padding: 0;
}

/* ==================== Hero 区 ==================== */
.hero-section {
  position: relative;
  height: 100vh;
  min-height: 600px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* Hero 背景图 */
.hero-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* Hero 遮罩层：深色渐变，让文字清晰 */
.hero-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(0, 0, 0, 0.6) 0%, rgba(0, 0, 0, 0.3) 100%);
}

/* Hero 内容 */
.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
  max-width: 800px;
  padding: 0 var(--space-lg);
  animation: fadeInUp 1s ease;
}

.hero-eyebrow {
  font-size: var(--font-size-small);
  letter-spacing: 0.3em;
  text-transform: uppercase;
  opacity: 0.85;
  margin-bottom: var(--space-md);
  font-weight: 500;
}

.hero-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-hero);
  font-weight: 700;
  line-height: 1.2;
  margin: 0 0 var(--space-md);
  letter-spacing: -0.02em;
}

.hero-subtitle {
  font-size: var(--font-size-h3);
  opacity: 0.9;
  margin: 0 0 var(--space-xl);
  font-weight: 300;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: var(--space-md);
  justify-content: center;
  flex-wrap: wrap;
}

/* Hero 主按钮：白底黑字 */
.btn-hero-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 16px 36px;
  background: #fff;
  color: var(--color-primary);
  border: 1px solid #fff;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-body);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-decoration: none;
  transition: var(--transition-base);
}

.btn-hero-primary:hover {
  background: transparent;
  color: #fff;
}

/* Hero 次按钮：透明边框 */
.btn-hero-outline {
  display: inline-flex;
  align-items: center;
  padding: 16px 36px;
  background: transparent;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-body);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-decoration: none;
  transition: var(--transition-base);
  cursor: pointer;
}

.btn-hero-outline:hover {
  border-color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

/* 滚动提示 */
.hero-scroll-hint {
  position: absolute;
  bottom: var(--space-xl);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  color: #fff;
  opacity: 0.7;
  animation: bounce 2s infinite;
}

.scroll-line {
  width: 1px;
  height: 40px;
  background: #fff;
}

.scroll-text {
  font-size: var(--font-size-caption);
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

/* Hero 动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes bounce {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-10px); }
}

/* ==================== 分类入口 ==================== */
.category-section {
  padding: var(--space-3xl) 0;
  background: var(--color-bg);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--space-lg);
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  cursor: pointer;
  transition: var(--transition-base);
}

.category-item:hover {
  transform: translateY(-4px);
}

.category-icon {
  width: 80px;
  height: 80px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text);
  transition: var(--transition-base);
  background: var(--color-bg);
}

.category-item:hover .category-icon {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.category-name {
  font-size: var(--font-size-small);
  color: var(--color-text);
  letter-spacing: 0.05em;
}

/* ==================== 精选商品 ==================== */
.products-section {
  padding: var(--space-3xl) 0;
  background: var(--color-bg-secondary);
}

/* 区块标题 */
.section-header {
  text-align: center;
  margin-bottom: var(--space-2xl);
}

.section-eyebrow {
  font-size: var(--font-size-caption);
  letter-spacing: 0.3em;
  color: var(--color-text-muted);
  text-transform: uppercase;
  margin-bottom: var(--space-sm);
  font-weight: 500;
}

.section-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-h1);
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 var(--space-sm);
  letter-spacing: -0.02em;
}

.section-desc {
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
  margin: 0;
}

/* 商品网格：3列 */
.products-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-lg);
}

/* 加载骨架屏 */
.products-loading {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-lg);
}

.product-skeleton {
  background: #fff;
  padding: var(--space-lg);
  border-radius: var(--radius-md);
}

/* 区块底部按钮 */
.section-footer {
  text-align: center;
  margin-top: var(--space-2xl);
}

/* ==================== 品牌承诺 ==================== */
.promise-section {
  padding: var(--space-3xl) 0;
  background: var(--color-bg);
}

.promise-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-xl);
}

.promise-item {
  text-align: center;
  padding: var(--space-lg);
}

.promise-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto var(--space-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  border: 1px solid var(--color-border);
  border-radius: 50%;
}

.promise-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-h3);
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 var(--space-sm);
}

.promise-desc {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0;
}

/* ==================== 新品上架 ==================== */
.new-arrivals-section {
  padding: var(--space-3xl) 0;
  background: var(--color-bg-secondary);
}

.new-arrivals-list {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--space-md);
}

.new-arrival-item {
  cursor: pointer;
  transition: var(--transition-base);
}

.new-arrival-item:hover {
  transform: translateY(-4px);
}

.new-arrival-image {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--color-bg);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-sm);
}

.new-arrival-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.new-arrival-item:hover .new-arrival-image img {
  transform: scale(1.05);
}

.new-arrival-info {
  text-align: center;
}

.new-arrival-name {
  font-size: var(--font-size-small);
  color: var(--color-text);
  margin: 0 0 var(--space-xs);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  height: 38px;
}

.new-arrival-price {
  font-size: var(--font-size-body);
  color: var(--color-accent);
  font-weight: 600;
  margin: 0;
}

/* ==================== 猜你喜欢 ==================== */
.guess-section {
  padding: var(--space-3xl) 0;
  background: var(--color-bg);
}

/* 猜你喜欢网格：5列展示更多商品 */
.guess-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-lg);
}

/* ==================== 响应式 ==================== */
@media (max-width: 1024px) {
  .products-grid,
  .products-loading {
    grid-template-columns: repeat(2, 1fr);
  }

  .new-arrivals-list {
    grid-template-columns: repeat(3, 1fr);
  }

  .category-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .promise-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .guess-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 36px;
  }

  .hero-subtitle {
    font-size: var(--font-size-body);
  }

  .products-grid,
  .products-loading {
    grid-template-columns: 1fr;
  }

  .new-arrivals-list {
    grid-template-columns: repeat(2, 1fr);
  }

  .category-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .guess-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
