<template>
  <!-- 默认布局：透明导航栏 + 主内容区 + 极简底部 -->
  <div class="default-layout" :class="{ 'is-home': isHomeRoute }">
    <!-- 顶部导航栏：透明背景，滚动后变白 -->
    <header class="layout-header" :class="{ scrolled: isScrolled }">
      <div class="header-content">
        <!-- 左侧：Logo -->
        <router-link to="/" class="logo">
          <span class="logo-text">SHOPMALL</span>
        </router-link>

        <!-- 中间：导航菜单 -->
        <nav class="nav-menu">
          <router-link to="/" class="nav-link" :class="{ active: isHomeRoute }">首页</router-link>
          <router-link to="/category" class="nav-link" :class="{ active: isCategoryRoute }">全部分类</router-link>
          <a href="javascript:void(0)" class="nav-link" @click="handleComingSoon">新品上架</a>
          <a href="javascript:void(0)" class="nav-link" @click="handleComingSoon">品牌专区</a>
          <router-link to="/seckill" class="nav-link" :class="{ active: isSeckillRoute }">限时秒杀</router-link>
        </nav>

        <!-- 右侧：操作图标 -->
        <div class="header-actions">
          <!-- 搜索图标：点击展开搜索下拉框 -->
          <div class="action-item search-trigger">
            <el-icon :size="20" @click="toggleSearch"><Search /></el-icon>
            <!-- 搜索下拉框 -->
            <transition name="search-dropdown">
              <div v-if="showSearch" class="search-dropdown">
                <div class="search-box">
                  <input
                    v-model="searchKeyword"
                    type="text"
                    placeholder="搜索商品、品牌..."
                    class="search-input"
                    @keyup.enter="handleSearch"
                    ref="searchInputRef"
                  />
                  <button class="search-btn" @click="handleSearch">
                    <el-icon :size="18"><Search /></el-icon>
                  </button>
                </div>
                <!-- 热门搜索词 -->
                <div class="hot-search">
                  <span class="hot-label">热门搜索</span>
                  <a
                    v-for="word in hotWords"
                    :key="word"
                    href="javascript:void(0)"
                    class="hot-word"
                    @click="searchByWord(word)"
                  >{{ word }}</a>
                </div>
              </div>
            </transition>
          </div>

          <!-- 账户图标 -->
          <div class="action-item">
            <el-icon :size="20" @click="goToUserCenter"><User /></el-icon>
          </div>

          <!-- 通知图标：铃铛 + 未读数量徽章 -->
          <div v-if="isLoggedIn" class="action-item notification-action" @click="goToNotification">
            <el-icon :size="20"><Bell /></el-icon>
            <span v-if="unreadCount > 0" class="cart-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </div>

          <!-- 购物车图标 -->
          <div class="action-item cart-action">
            <el-icon :size="20" @click="goToCart"><ShoppingBag /></el-icon>
            <span v-if="cartCount > 0" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区：子路由的页面会渲染在这里 -->
    <main class="layout-main">
      <router-view />
    </main>

    <!-- 底部：极简风格 -->
    <footer class="layout-footer">
      <div class="footer-content">
        <!-- 服务承诺 -->
        <div class="footer-services">
          <div class="service-item">
            <span class="service-title">正品保障</span>
            <span class="service-desc">品牌授权 正品无忧</span>
          </div>
          <div class="service-item">
            <span class="service-title">极速配送</span>
            <span class="service-desc">当日下单 次日送达</span>
          </div>
          <div class="service-item">
            <span class="service-title">7天无理由</span>
            <span class="service-desc">不满意 可退换</span>
          </div>
          <div class="service-item">
            <span class="service-title">售后无忧</span>
            <span class="service-desc">专属客服 全程服务</span>
          </div>
        </div>

        <!-- 底部链接 -->
        <div class="footer-links">
          <div class="footer-col">
            <h4>购物指南</h4>
            <a href="javascript:void(0)">购物流程</a>
            <a href="javascript:void(0)">会员介绍</a>
            <a href="javascript:void(0)">常见问题</a>
          </div>
          <div class="footer-col">
            <h4>配送方式</h4>
            <a href="javascript:void(0)">上门自提</a>
            <a href="javascript:void(0)">配送费用</a>
            <a href="javascript:void(0)">配送范围</a>
          </div>
          <div class="footer-col">
            <h4>支付方式</h4>
            <a href="javascript:void(0)">在线支付</a>
            <a href="javascript:void(0)">货到付款</a>
            <a href="javascript:void(0)">分期付款</a>
          </div>
          <div class="footer-col">
            <h4>售后服务</h4>
            <a href="javascript:void(0)">售后政策</a>
            <a href="javascript:void(0)">价格保护</a>
            <a href="javascript:void(0)">退款说明</a>
          </div>
          <div class="footer-col">
            <h4>关于我们</h4>
            <a href="javascript:void(0)">品牌故事</a>
            <a href="javascript:void(0)">联系方式</a>
            <a href="javascript:void(0)">加入我们</a>
          </div>
        </div>

        <!-- 版权信息 -->
        <div class="footer-bottom">
          <p>&copy; 2026 SHOPMALL. All Rights Reserved.</p>
        </div>
      </div>
    </footer>

    <!-- 全局登录弹窗：未登录时执行需登录操作会弹出 -->
    <AuthModal />
  </div>
</template>

<script setup lang="ts">
/**
 * 默认布局组件（极简独立站风格）
 * 透明导航栏（滚动后变白） + 主内容区 + 极简底部
 * 导航栏：Logo左 + 菜单中 + 图标右（搜索/账户/购物车）
 */

import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, User, ShoppingBag, Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useCart } from '@shop/shared'
import { isAuthenticated } from '@shop/shared'
import { getHotKeywords } from '@shop/shared'
import { getUserUnreadCount } from '@shop/shared'
import AuthModal from '@/components/AuthModal.vue'
import { useAuthModalStore } from '@/stores/authModal'

const router = useRouter()
const route = useRoute()
const { cartCount, fetchCartCount } = useCart()
const authModalStore = useAuthModalStore()

/** 当前是否已登录 */
const isLoggedIn = ref(false)

/** 未读通知数量（顶部铃铛徽章） */
const unreadCount = ref(0)

/** 当前是否在首页（首页时导航栏透明） */
const isHomeRoute = computed(() => route.path === '/')

/** 当前是否在分类页面 */
const isCategoryRoute = computed(() => route.path === '/category')

/** 当前是否在秒杀页面（列表或详情） */
const isSeckillRoute = computed(() => route.path.startsWith('/seckill'))

/** 是否已滚动（滚动后导航栏变白） */
const isScrolled = ref(false)

/** 是否显示搜索下拉框 */
const showSearch = ref(false)

/** 搜索关键词 */
const searchKeyword = ref('')

/** 搜索输入框的ref引用 */
const searchInputRef = ref<HTMLInputElement>()

/** 热门搜索词（从后端API动态获取） */
const hotWords = ref<string[]>([])
/** 默认热搜词（API无数据时作为fallback） */
const defaultHotWords = ['手机', '电脑', '耳机', '空调', '运动鞋']

/**
 * 监听滚动事件
 * 首页时滚动超过50px导航栏变白，其他页面始终白色
 */
const handleScroll = () => {
  isScrolled.value = window.scrollY > 50
}

/**
 * 切换搜索下拉框显示
 * 展开时自动聚焦输入框
 */
const toggleSearch = async () => {
  showSearch.value = !showSearch.value
  if (showSearch.value) {
    await nextTick()
    searchInputRef.value?.focus()
  }
}

/**
 * 处理搜索
 * 按回车或点击搜索按钮时触发，跳转到搜索页
 */
const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  if (keyword) {
    router.push({ path: '/search', query: { keyword } })
    showSearch.value = false
    searchKeyword.value = ''
  }
}

/**
 * 点击热搜词直接搜索
 */
const searchByWord = (word: string) => {
  searchKeyword.value = word
  handleSearch()
}

/**
 * 跳转到购物车页面
 * 未登录时弹出登录弹窗，登录成功后跳转购物车页
 */
const goToCart = () => {
  if (!isAuthenticated()) {
    authModalStore.openAuthModal({
      description: '登录后查看购物车',
      execute: () => {
        router.push('/cart')
      },
    })
    return
  }
  router.push('/cart')
}

/**
 * 跳转到个人中心
 * 未登录时弹出登录弹窗，登录成功后跳转个人中心
 */
const goToUserCenter = () => {
  if (!isAuthenticated()) {
    authModalStore.openAuthModal({
      description: '登录后访问个人中心',
      execute: () => {
        router.push('/user/center')
      },
    })
    return
  }
  router.push('/user/center')
}

/**
 * 跳转到消息通知页
 * 未登录时弹出登录弹窗，登录成功后跳转通知页
 */
const goToNotification = () => {
  if (!isAuthenticated()) {
    authModalStore.openAuthModal({
      description: '登录后查看消息通知',
      execute: () => {
        router.push('/notification')
      },
    })
    return
  }
  router.push('/notification')
}

/**
 * 获取未读通知数量
 * 已登录用户才调用，用于顶部铃铛徽章
 */
const fetchUnreadCount = async () => {
  if (!isAuthenticated()) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await getUserUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 静默失败，不影响页面
  }
}

/**
 * "即将上线"功能提示
 */
const handleComingSoon = () => {
  ElMessage.info('该功能即将上线，敬请期待')
}

/**
 * 加载热门搜索词
 * 调用后端API获取，基于Redis ZSet按搜索次数排序
 * 如果API返回空列表，使用默认热搜词
 */
const loadHotWords = async () => {
  try {
    const res = await getHotKeywords()
    const keywords = res.data || []
    hotWords.value = keywords.length > 0 ? keywords.slice(0, 8) : defaultHotWords
  } catch {
    hotWords.value = defaultHotWords
  }
}

/**
 * 点击页面外部时关闭搜索下拉框
 */
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (showSearch.value && !target.closest('.search-trigger')) {
    showSearch.value = false
  }
}

/** 路由变化时关闭搜索框，并刷新登录状态和未读数量 */
watch(() => route.path, () => {
  showSearch.value = false
  // 路由变化时刷新登录状态和未读数量
  isLoggedIn.value = isAuthenticated()
  fetchUnreadCount()
})

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
  document.addEventListener('click', handleClickOutside)
  handleScroll()
  isLoggedIn.value = isAuthenticated()
  if (isLoggedIn.value) {
    fetchCartCount()
    fetchUnreadCount()
  }
  loadHotWords()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
/* ==================== 布局根容器 ==================== */
.default-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  /* 页面背景叠加 mesh 光晕，营造氛围感 */
  background-color: var(--color-bg);
  background-image: var(--gradient-mesh);
  background-attachment: fixed;
}

/* ==================== 顶部玻璃导航栏 ==================== */
/* 导航栏默认透明（首页 Hero 区上方），滚动或非首页时变为毛玻璃白 */
.layout-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: transparent;
  transition: background 0.4s var(--ease-out), box-shadow 0.4s var(--ease-out),
              backdrop-filter 0.4s var(--ease-out);
}

/* 滚动后 / 非首页：毛玻璃白 + 柔和阴影 + 细底边 */
.layout-header.scrolled,
.default-layout:not(.is-home) .layout-header {
  background: var(--color-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: var(--shadow-sm);
  border-bottom: 1px solid var(--color-glass-border);
}

/* 滚动后进一步加强模糊与阴影 */
.layout-header.scrolled {
  background: var(--color-glass-strong);
  box-shadow: var(--shadow-md);
}

.header-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 var(--space-xl);
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xl);
}

/* ---------- Logo（渐变文字） ---------- */
.logo {
  text-decoration: none;
  display: flex;
  align-items: center;
}

.logo-text {
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
  /* 翡翠青绿渐变文字 */
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  transition: transform 0.3s var(--ease-out);
}

.logo:hover .logo-text {
  transform: scale(1.03);
}

/* 首页未滚动时，Logo 变白（适配深色 Hero 背景） */
.default-layout.is-home .layout-header:not(.scrolled) .logo-text {
  background: none;
  -webkit-text-fill-color: #fff;
  color: #fff;
}

/* ---------- 导航菜单（胶囊式） ---------- */
.nav-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.nav-link {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-secondary);
  text-decoration: none;
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  position: relative;
  transition: all 0.3s var(--ease-out);
  letter-spacing: 0.01em;
}

.nav-link:hover {
  color: var(--color-primary);
  background: var(--gradient-brand-soft);
}

.nav-link.active {
  color: var(--color-primary-dark);
  background: var(--gradient-brand-soft);
}

/* 激活态下方小渐变条 */
.nav-link.active::after {
  content: '';
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
  width: 18px;
  height: 3px;
  border-radius: var(--radius-pill);
  background: var(--gradient-brand);
}

/* 首页未滚动时，导航文字变白（适配深色 Hero） */
.default-layout.is-home .layout-header:not(.scrolled) .nav-link {
  color: rgba(255, 255, 255, 0.9);
  background: transparent;
}

.default-layout.is-home .layout-header:not(.scrolled) .nav-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
}

.default-layout.is-home .layout-header:not(.scrolled) .nav-link.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
}

.default-layout.is-home .layout-header:not(.scrolled) .nav-link.active::after {
  background: #fff;
}

/* ---------- 右侧操作图标（玻璃圆钮） ---------- */
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-item {
  position: relative;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: var(--color-text);
  transition: all 0.3s var(--ease-out);
}

/* 玻璃态时图标钮带半透明背景 */
.layout-header.scrolled .action-item,
.default-layout:not(.is-home) .action-item {
  background: rgba(255, 255, 255, 0.5);
}

.action-item:hover {
  color: var(--color-primary);
  background: var(--gradient-brand-soft);
  transform: translateY(-2px);
}

/* 首页未滚动时，图标变白 */
.default-layout.is-home .layout-header:not(.scrolled) .action-item {
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.12);
}

.default-layout.is-home .layout-header:not(.scrolled) .action-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.22);
}

/* 购物车 / 通知角标：渐变胶囊 */
.cart-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--radius-pill);
  background: var(--gradient-brand);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-bg);
  box-shadow: var(--shadow-sm);
}

/* 首页未滚动时角标边框适配深色背景 */
.default-layout.is-home .layout-header:not(.scrolled) .cart-badge {
  border-color: rgba(15, 23, 42, 0.4);
}

/* ==================== 搜索下拉框（玻璃面板） ==================== */
.search-dropdown {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 420px;
  background: var(--color-glass-strong);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: var(--space-lg);
  z-index: 200;
}

.search-box {
  display: flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.7);
  transition: var(--transition-base);
}

.search-box:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.search-input {
  flex: 1;
  height: 44px;
  border: none;
  padding: 0 var(--space-md);
  font-size: var(--font-size-body);
  font-family: var(--font-body);
  outline: none;
  background: transparent;
  color: var(--color-text);
}

.search-input::placeholder {
  color: var(--color-text-muted);
}

.search-btn {
  width: 44px;
  height: 44px;
  border: none;
  background: var(--gradient-brand);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-base);
}

.search-btn:hover {
  background: var(--gradient-brand-hover);
}

/* 热门搜索词 */
.hot-search {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

.hot-label {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  margin-right: var(--space-xs);
}

.hot-word {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  text-decoration: none;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.5);
  transition: var(--transition-base);
}

.hot-word:hover {
  color: var(--color-primary);
  background: var(--gradient-brand-soft);
}

/* 搜索下拉框淡入动画 */
.search-dropdown-enter-active,
.search-dropdown-leave-active {
  transition: all 0.3s var(--ease-out);
}

.search-dropdown-enter-from,
.search-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.98);
}

/* ==================== 主内容区 ==================== */
.layout-main {
  flex: 1;
  /* 非首页时，顶部留出导航栏高度 */
  padding-top: 68px;
}

/* 首页不需要顶部padding（Hero区从顶部开始） */
.default-layout.is-home .layout-main {
  padding-top: 0;
}

/* ==================== 底部 ==================== */
.layout-footer {
  background-color: var(--color-bg);
  border-top: 1px solid var(--color-border);
  margin-top: var(--space-3xl);
}

.footer-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--space-2xl) var(--space-xl) var(--space-lg);
}

/* 服务承诺（玻璃卡片网格） */
.footer-services {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-lg);
  padding-bottom: var(--space-xl);
  border-bottom: 1px solid var(--color-border);
  margin-bottom: var(--space-xl);
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-md);
  border-radius: var(--radius-lg);
  background: var(--color-glass);
  border: 1px solid var(--color-glass-border);
  transition: var(--transition-base);
}

.service-item:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.service-title {
  font-size: var(--font-size-body);
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0.03em;
}

.service-desc {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
}

/* 底部链接 */
.footer-links {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-xl);
  margin-bottom: var(--space-xl);
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.footer-col h4 {
  font-family: var(--font-heading);
  font-size: var(--font-size-small);
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 var(--space-xs);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.footer-col a {
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: var(--font-size-small);
  transition: var(--transition-base);
}

.footer-col a:hover {
  color: var(--color-primary);
}

/* 版权信息 */
.footer-bottom {
  text-align: center;
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-border);
}

.footer-bottom p {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  letter-spacing: 0.05em;
}

/* ==================== 响应式 ==================== */
@media (max-width: 768px) {
  .nav-menu {
    display: none;
  }

  .header-content {
    padding: 0 var(--space-md);
  }

  .footer-services {
    grid-template-columns: repeat(2, 1fr);
  }

  .footer-links {
    grid-template-columns: repeat(2, 1fr);
  }

  .search-dropdown {
    width: calc(100vw - 32px);
    right: -8px;
  }
}
</style>
