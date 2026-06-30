<template>
  <!-- 个人中心页 - 玻璃拟态风（Glassmorphism + 翡翠青绿渐变） -->
  <div class="user-center">
    <!-- 用户信息卡片：翡翠渐变玻璃卡 -->
    <div class="user-card">
      <!-- 装饰性光晕 -->
      <div class="card-glow"></div>
      <div class="user-info">
        <!-- 用户头像：玻璃白底圆形 -->
        <div class="user-avatar">
          <img v-if="userInfo?.avatar" :src="userInfo.avatar" alt="头像" />
          <span v-else>👤</span>
        </div>
        <!-- 用户名和注册时间 -->
        <div class="user-detail">
          <div class="user-name">{{ userInfo?.nickname || 'ShopMall用户' }}</div>
          <div class="user-meta">
            <span class="user-phone">{{ formatPhone(userInfo?.phone || '') }}</span>
          </div>
        </div>
        <!-- 退出登录按钮 -->
        <div class="user-settings" @click="handleLogout">
          <span>退出登录 ›</span>
        </div>
      </div>
    </div>

    <!-- 订单快捷入口：玻璃面板 -->
    <div class="order-shortcut">
      <div class="shortcut-header">
        <h3 class="shortcut-title">我的订单</h3>
        <span class="shortcut-more" @click="$router.push({ name: 'OrderList' })">全部订单 ›</span>
      </div>
      <div class="order-grid">
        <!-- 待付款 -->
        <div class="order-entry" @click="goOrderList(ORDER_STATUS.UNPAID)">
          <div class="entry-icon-wrapper">
            <span class="entry-icon">💰</span>
          </div>
          <span class="entry-label">待付款</span>
        </div>
        <!-- 待发货 -->
        <div class="order-entry" @click="goOrderList(ORDER_STATUS.PENDING_DELIVERY)">
          <div class="entry-icon-wrapper">
            <span class="entry-icon">📦</span>
          </div>
          <span class="entry-label">待发货</span>
        </div>
        <!-- 运输中 -->
        <div class="order-entry" @click="goOrderList(ORDER_STATUS.SHIPPING)">
          <div class="entry-icon-wrapper">
            <span class="entry-icon">🚚</span>
          </div>
          <span class="entry-label">运输中</span>
        </div>
        <!-- 已收货 -->
        <div class="order-entry" @click="goOrderList(ORDER_STATUS.RECEIVED)">
          <div class="entry-icon-wrapper">
            <span class="entry-icon">✅</span>
          </div>
          <span class="entry-label">已收货</span>
        </div>
        <!-- 退款售后 -->
        <div class="order-entry" @click="goOrderList(ORDER_STATUS.REFUNDING)">
          <div class="entry-icon-wrapper">
            <span class="entry-icon">🔄</span>
          </div>
          <span class="entry-label">退款售后</span>
        </div>
      </div>
    </div>

    <!-- 功能入口：玻璃面板 -->
    <div class="service-section">
      <h3 class="section-title">我的服务</h3>
      <div class="service-grid">
        <div
          v-for="item in serviceItems"
          :key="item.name"
          class="service-item"
          @click="handleServiceClick(item)"
        >
          <div class="service-icon" :style="{ background: item.bgColor }">{{ item.icon }}</div>
          <span class="service-name">{{ item.name }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心页
 * 玻璃拟态风（Glassmorphism + 翡翠青绿渐变）
 * 用户信息卡片、订单快捷入口、功能网格全部玻璃化
 *
 * 功能说明（小白版）：
 * 1. 从用户Store获取当前登录用户的信息（昵称、头像、手机号）
 * 2. 订单快捷入口：点击后跳转到订单列表页，并按对应状态筛选
 * 3. 服务功能入口：收货地址、收藏、足迹等（部分功能暂未实现对应页面）
 * 4. 退出登录：清除登录状态，返回首页
 */

import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatPhone } from '@shop/shared'
import { ORDER_STATUS } from '@shop/shared'

const router = useRouter()
const userStore = useUserStore()

/** 当前用户信息（从Store获取，响应式） */
const userInfo = userStore.userInfo

/** 服务功能列表 */
const serviceItems = [
  { name: '收货地址', icon: '📍', bgColor: '#E6F7FF', action: 'address' },
  { name: '我的收藏', icon: '❤️', bgColor: '#FFF1F0', action: 'favorite' },
  { name: '浏览足迹', icon: '👣', bgColor: '#F6FFED', action: 'footprint' },
  { name: '账户余额', icon: '💰', bgColor: '#FFFBE6', action: 'account' },
  { name: '优惠券', icon: '🎫', bgColor: '#F9F0FF', action: 'coupon' },
  { name: '联系客服', icon: '📞', bgColor: '#FFF7E6', action: 'service' },
  { name: '帮助中心', icon: '❓', bgColor: '#F5F5F5', action: 'help' },
  { name: '退出登录', icon: '🚪', bgColor: '#FFF1F0', action: 'logout' },
]

/**
 * 跳转到订单列表页，并按状态筛选
 * @param status - 订单状态
 */
const goOrderList = (status: number) => {
  router.push({ name: 'OrderList', query: { status } })
}

/**
 * 处理服务功能点击
 * @param item - 被点击的服务项
 */
const handleServiceClick = (item: { name: string; action: string }) => {
  switch (item.action) {
    case 'logout':
      handleLogout()
      break
    case 'coupon':
      router.push({ name: 'MyCoupon' })
      break
    case 'favorite':
      router.push({ name: 'FavoriteList' })
      break
    case 'footprint':
      router.push({ name: 'FootprintList' })
      break
    case 'address':
    case 'account':
    case 'service':
    case 'help':
      ElMessage.info('功能开发中，敬请期待')
      break
  }
}

/**
 * 退出登录
 * 弹出确认弹窗，确认后清除登录状态并返回首页
 */
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要退出登录吗？',
      '退出登录',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push({ name: 'Home' })
  } catch {
    // 用户点击取消
  }
}

/** 页面加载时获取最新的用户信息 */
onMounted(async () => {
  try {
    await userStore.fetchUserInfo()
  } catch {
    // 获取失败不阻塞页面，使用本地缓存的用户信息
  }
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
/* 整页淡入上浮动效 */
.user-center {
  padding: 0;
  animation: fadeInUp 0.5s ease both;
}

/* ==================== 用户信息卡片：翡翠渐变玻璃卡 ==================== */
.user-card {
  position: relative;
  overflow: hidden;
  background: var(--gradient-brand);
  border-radius: var(--radius-card);
  padding: 32px 28px;
  margin-bottom: 24px;
  color: #fff;
  box-shadow: 0 12px 32px rgba(16, 185, 129, 0.25);
}

/* 装饰性光晕：右上角圆形光斑 */
.card-glow {
  position: absolute;
  top: -50%;
  right: -20%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.25) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.user-info {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 1;
}

/* 用户头像：玻璃白底圆形 */
.user-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 2px solid rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-detail {
  flex: 1;
  min-width: 0;
}

/* 用户名：白色加粗 */
.user-name {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

/* 手机号：半透明白色胶囊 */
.user-phone {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
  padding: 3px 10px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-pill);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

/* 退出登录按钮：玻璃白边胶囊 */
.user-settings {
  color: rgba(255, 255, 255, 0.95);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  flex-shrink: 0;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-pill);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.user-settings:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* ==================== 订单快捷入口：玻璃面板 ==================== */
.order-shortcut {
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-card);
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
}

.shortcut-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-glass-border);
}

/* 标题：翡翠色加粗 */
.shortcut-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
  position: relative;
  padding-left: 12px;
}

/* 标题左侧翡翠竖条装饰 */
.shortcut-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  background: var(--gradient-brand);
  border-radius: var(--radius-pill);
}

/* 全部订单链接 */
.shortcut-more {
  font-size: 13px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--transition-base);
  padding: 4px 10px;
  border-radius: var(--radius-pill);
}

.shortcut-more:hover {
  color: var(--color-primary);
  background: rgba(16, 185, 129, 0.08);
}

.order-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

/* 订单入口：hover 上浮 + 翡翠色 */
.order-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 12px 8px;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.order-entry:hover {
  background: rgba(16, 185, 129, 0.06);
  transform: translateY(-2px);
}

.order-entry:hover .entry-label {
  color: var(--color-primary);
}

/* 图标 wrapper：玻璃圆形 */
.entry-icon-wrapper {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-glass);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--color-glass-border);
  transition: all var(--transition-base);
  box-shadow: var(--shadow-sm);
}

.order-entry:hover .entry-icon-wrapper {
  background: var(--gradient-brand-soft);
  border-color: rgba(16, 185, 129, 0.3);
  transform: scale(1.08);
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.15);
}

.entry-icon {
  font-size: 26px;
  line-height: 1;
}

.entry-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  transition: color var(--transition-base);
  font-weight: 500;
}

/* ==================== 功能入口：玻璃面板 ==================== */
.service-section {
  background: var(--color-glass);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid var(--color-glass-border);
  border-radius: var(--radius-card);
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
}

/* 标题：翡翠色加粗 + 左侧装饰条 */
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 20px;
  color: var(--color-text);
  position: relative;
  padding-left: 12px;
}

.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  background: var(--gradient-brand);
  border-radius: var(--radius-pill);
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

/* 服务项：hover 上浮 */
.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 16px 8px;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.service-item:hover {
  background: rgba(16, 185, 129, 0.06);
  transform: translateY(-4px);
  box-shadow: var(--shadow-sm);
}

.service-item:hover .service-name {
  color: var(--color-primary);
}

/* 服务图标：圆角方块 + hover 放大 */
.service-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-sm);
}

.service-item:hover .service-icon {
  transform: scale(1.1) rotate(-5deg);
  box-shadow: var(--shadow-md);
}

.service-name {
  font-size: 13px;
  color: var(--color-text-secondary);
  transition: color var(--transition-base);
  font-weight: 500;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .user-card {
    padding: 24px 20px;
  }

  .user-avatar {
    width: 56px;
    height: 56px;
    font-size: 28px;
  }

  .user-name {
    font-size: 18px;
  }

  .service-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
  }

  .order-grid {
    gap: 8px;
  }

  .entry-icon-wrapper {
    width: 48px;
    height: 48px;
  }

  .entry-icon {
    font-size: 22px;
  }

  .service-icon {
    width: 44px;
    height: 44px;
    font-size: 20px;
  }
}

@media (max-width: 480px) {
  .service-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }

  .service-item {
    padding: 12px 4px;
  }

  .service-name {
    font-size: 12px;
  }
}
</style>
