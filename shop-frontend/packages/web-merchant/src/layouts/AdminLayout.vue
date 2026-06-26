<template>
  <!-- 后台管理布局：左侧深蓝灰侧边栏 + 顶部白底导航 + 浅灰蓝内容区 -->
  <div class="admin-layout">
    <!-- 左侧菜单栏：深蓝灰背景 #304156 -->
    <aside class="admin-sidebar">
      <!-- Logo区域：更深背景 #263445 -->
      <div class="sidebar-logo">
        <span class="logo-icon">🏪</span>
        <span class="logo-text">商家后台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
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
/* 整体布局：左右结构 */
.admin-layout {
  display: flex;
  min-height: 100vh;
}

/* 左侧侧边栏：深蓝灰背景，固定宽度 */
.admin-sidebar {
  width: 220px;
  background-color: var(--color-sidebar-bg);
  overflow-y: auto;
  flex-shrink: 0;
}

/* Logo区域：更深背景，居中显示 */
.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background-color: var(--color-sidebar-dark);
}

.logo-icon {
  font-size: 24px;
}

/* Element Plus菜单选中项左边框高亮 */
.admin-sidebar :deep(.el-menu-item.is-active) {
  border-left: 3px solid var(--color-sidebar-active) !important;
}

.admin-sidebar :deep(.el-sub-menu .el-menu-item.is-active) {
  border-left: 3px solid var(--color-sidebar-active) !important;
}

/* 右侧主区域 */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg);
  min-width: 0;
}

/* 顶部导航栏：白底，底部阴影 */
.admin-header {
  height: 60px;
  background: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: var(--shadow-header);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 通知铃铛：可点击，hover 变色 */
.notification-bell {
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  transition: color 0.2s;
}

.notification-bell:hover {
  color: var(--color-primary);
}

/* 用户信息：头像 + 名称 + 下拉箭头 */
.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 用户头像：蓝色圆形，显示名称首字 */
.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.user-name {
  font-size: 14px;
  color: var(--color-text);
}

/* 内容区：带内边距 */
.admin-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>
