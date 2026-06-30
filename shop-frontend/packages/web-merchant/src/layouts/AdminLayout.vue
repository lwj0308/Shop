<template>
  <!-- 后台管理布局：左侧深色玻璃侧边栏 + 顶部玻璃白导航 + 翡翠氛围内容区 -->
  <div class="admin-layout">
    <!-- 左侧菜单栏：深色玻璃背景 + 高斯模糊 -->
    <aside class="admin-sidebar">
      <!-- Logo区域：渐变图标 + 渐变文字 -->
      <div class="sidebar-logo">
        <span class="logo-icon">🏪</span>
        <span class="logo-text">商家后台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="transparent"
        text-color="rgba(255,255,255,0.7)"
        active-text-color="#34D399"
      >
        <el-menu-item index="/">
          <el-icon><DataBoard /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu index="product">
          <template #title>
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </template>
          <el-menu-item index="/product/list">商品列表</el-menu-item>
          <el-menu-item index="/product/edit">发布商品</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="order">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>订单管理</span>
          </template>
          <el-menu-item index="/order/list">订单列表</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="comment">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>评价管理</span>
          </template>
          <el-menu-item index="/comment/list">评价列表</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/data">
          <el-icon><TrendCharts /></el-icon>
          <span>数据中心</span>
        </el-menu-item>
        <el-menu-item index="/settlement">
          <el-icon><Wallet /></el-icon>
          <span>结算管理</span>
        </el-menu-item>
        <el-menu-item index="/coupon">
          <el-icon><Ticket /></el-icon>
          <span>优惠券管理</span>
        </el-menu-item>
        <el-menu-item index="/promotion">
          <el-icon><Discount /></el-icon>
          <span>满减活动</span>
        </el-menu-item>
        <el-menu-item index="/seckill">
          <el-icon><AlarmClock /></el-icon>
          <span>秒杀活动</span>
        </el-menu-item>
        <el-menu-item index="/shop/settings">
          <el-icon><Setting /></el-icon>
          <span>店铺设置</span>
        </el-menu-item>
        <el-menu-item index="/notification">
          <el-icon><Bell /></el-icon>
          <span>消息通知</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 右侧内容区 -->
    <div class="admin-main">
      <!-- 顶部导航栏：白底，底部阴影 -->
      <header class="admin-header">
        <div class="header-left">
          <!-- 面包屑根据路由自动生成 -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="parentTitle">{{ parentTitle }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle && currentTitle !== '工作台'">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 通知铃铛：点击跳转通知列表，未读数量徽章 -->
          <div class="notification-bell" @click="goToNotification">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
              <el-icon :size="18"><Bell /></el-icon>
            </el-badge>
          </div>
          <!-- 商家头像和名称 -->
          <el-dropdown>
            <div class="user-info">
              <div class="user-avatar">{{ merchantName.charAt(0) }}</div>
              <span class="user-name">{{ merchantName }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容区：浅灰蓝背景 #F0F2F5，子路由页面渲染在这里 -->
      <div class="admin-content">
        <!-- 使用 :key 强制路由切换时重新挂载组件，确保 onMounted 钩子触发 -->
        <router-view :key="$route.fullPath" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 后台管理布局组件
 * 商家后台所有页面都使用这个布局：左侧深蓝灰菜单 + 顶部面包屑 + 浅灰蓝内容区
 * 优化点：菜单高亮当前路由、面包屑自动生成、退出登录确认弹窗、页面切换动画
 */

import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { DataBoard, Goods, Document, TrendCharts, Setting, ArrowDown, ChatDotRound, Wallet, Bell, Ticket, Discount, AlarmClock } from '@element-plus/icons-vue'
import { useMerchantStore } from '@/stores/merchant'
import { getMerchantUnreadCount } from '@shop/shared'

const route = useRoute()
const router = useRouter()
const merchantStore = useMerchantStore()

/** 未读通知数量（顶部铃铛徽章） */
const unreadCount = ref(0)

/**
 * 监听路由变化（调试用）
 * 当用户点击菜单切换页面时，打印当前路由路径，确认路由切换是否被检测到
 * 同时刷新未读通知数量（用于在通知页操作后回到其他页时同步徽章）
 */
watch(
  () => route.fullPath,
  (newPath, oldPath) => {
    console.log('[AdminLayout] 路由变化:', oldPath, '->', newPath)
    fetchUnreadCount()
  },
)

/**
 * 获取未读通知数量
 * 调用后端API获取当前商家的未读通知数，用于顶部铃铛徽章
 * 静默失败：API失败不影响页面正常使用
 */
const fetchUnreadCount = async () => {
  try {
    const res = await getMerchantUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 静默失败，不影响页面
  }
}

/**
 * 跳转到消息通知页
 */
const goToNotification = () => {
  router.push('/notification')
}

/**
 * 当前激活的菜单项
 * 根据路由路径自动高亮对应的菜单项
 * 特殊处理：编辑商品页 /product/edit/123 也高亮"添加商品"菜单
 */
const activeMenu = computed(() => {
  const path = route.path
  // 编辑商品页（带id参数）也高亮"添加商品"菜单
  if (path.startsWith('/product/edit')) {
    return '/product/edit'
  }
  // 订单详情页高亮"订单列表"菜单
  if (path.match(/^\/order\/\d+$/)) {
    return '/order/list'
  }
  return path
})

/** 当前页面标题（用于面包屑导航） */
const currentTitle = computed(() => (route.meta.title as string) || '')

/** 父级标题（用于面包屑二级导航，如"商品管理"、"订单管理"） */
const parentTitle = computed(() => (route.meta.parentTitle as string) || '')

/** 商家名称（显示在右上角） */
const merchantName = computed(() => merchantStore.merchantInfo?.name || '商家管理员')

/**
 * 退出登录
 * 弹出确认弹窗，防止误操作
 */
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await merchantStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 用户点击取消，不做任何操作
  }
}

/** 页面加载时获取未读通知数量 */
onMounted(() => {
  fetchUnreadCount()
})
</script>

<style scoped>
/* ==================== 整体布局：左右结构 + 氛围光晕 ==================== */
.admin-layout {
  display: flex;
  min-height: 100vh;
  position: relative;
}

/* 内容区底层 mesh 光晕：翡翠+青蓝柔和扩散，营造氛围感 */
.admin-layout::before {
  content: '';
  position: fixed;
  inset: 0;
  background: var(--gradient-mesh);
  pointer-events: none;
  z-index: 0;
}

/* ==================== 左侧深色玻璃侧边栏 ==================== */
.admin-sidebar {
  width: 220px;
  /* 深色玻璃：半透明深底 + 高斯模糊 + 饱和度提升 */
  background: var(--color-sidebar-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  overflow-y: auto;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
  padding: 0 12px 16px;
}

/* Logo区域：渐变图标 + 渐变文字，不再加深背景 */
.sidebar-logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

/* Logo图标：翡翠青绿渐变方块，内嵌emoji */
.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-button);
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.35);
  flex-shrink: 0;
}

/* Logo文字：渐变填充（翡翠→青蓝） */
.logo-text {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 800;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -0.01em;
}

/* ==================== Element Plus 菜单玻璃化（深度穿透） ==================== */
/* 菜单容器透明，让深色玻璃底透出 */
.admin-sidebar :deep(.el-menu) {
  background: transparent !important;
  border-right: none !important;
  padding: 8px 0;
}

/* 菜单项 / 子菜单标题：透明底 + 圆角 + 缓动 */
.admin-sidebar :deep(.el-menu-item),
.admin-sidebar :deep(.el-sub-menu__title) {
  background: transparent !important;
  color: rgba(255, 255, 255, 0.7) !important;
  border-radius: var(--radius-button);
  margin: 2px 0;
  height: 46px;
  line-height: 46px;
  transition: all 0.3s var(--ease-out);
}

/* 菜单项 hover：浅白玻璃底 */
.admin-sidebar :deep(.el-menu-item:hover),
.admin-sidebar :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}

/* 子菜单展开后的内嵌菜单：稍深玻璃底，区分层级 */
.admin-sidebar :deep(.el-sub-menu .el-menu) {
  background: rgba(0, 0, 0, 0.18) !important;
  border-radius: var(--radius-button);
  margin: 2px 0;
  padding: 4px 0;
}

/* 子菜单内的菜单项缩进 */
.admin-sidebar :deep(.el-sub-menu .el-menu .el-menu-item) {
  height: 42px;
  line-height: 42px;
}

/* 菜单激活项：渐变柔光背景 + 翡翠文字 + 左侧渐变指示条 */
.admin-sidebar :deep(.el-menu-item.is-active) {
  background: var(--gradient-brand-soft) !important;
  color: var(--color-sidebar-active) !important;
  font-weight: 600;
  position: relative;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.15);
}

/* 激活项左侧渐变竖条指示器（带辉光） */
.admin-sidebar :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  border-radius: 0 4px 4px 0;
  background: var(--gradient-brand);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.5);
}

/* 子菜单内的激活项同样加指示条 */
.admin-sidebar :deep(.el-sub-menu .el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  border-radius: 0 4px 4px 0;
  background: var(--gradient-brand);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.5);
}

/* 菜单图标颜色跟随文字色 */
.admin-sidebar :deep(.el-menu-item .el-icon),
.admin-sidebar :deep(.el-sub-menu__title .el-icon) {
  color: inherit;
}

/* 折叠箭头颜色 */
.admin-sidebar :deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.5);
}

/* ==================== 右侧主区域 ==================== */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg);
  min-width: 0;
  position: relative;
  z-index: 1;
}

/* ==================== 顶部玻璃导航栏 ==================== */
.admin-header {
  height: 64px;
  background: var(--color-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--color-glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 20;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 面包屑翡翠色高亮 */
.admin-header :deep(.el-breadcrumb__inner) {
  color: var(--color-text-muted);
  font-weight: 500;
}
.admin-header :deep(.el-breadcrumb__inner.is-link):hover {
  color: var(--color-primary);
}

/* ==================== 通知铃铛：圆形玻璃按钮 + hover 辉光 ==================== */
.notification-bell {
  cursor: pointer;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-tag);
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  transition: all 0.3s var(--ease-out);
}

.notification-bell:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: var(--shadow-glow);
}

/* ==================== 用户信息：玻璃胶囊 + hover 翡翠边 ==================== */
.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 14px 4px 4px;
  border-radius: var(--radius-tag);
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid var(--color-border);
  transition: all 0.3s var(--ease-out);
}

.user-info:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-card);
}

/* 用户头像：翡翠青绿渐变圆形 */
.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--gradient-brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-display);
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

/* ==================== 内容区 ==================== */
.admin-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
