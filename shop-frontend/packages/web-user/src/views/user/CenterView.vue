<template>
  <!-- 个人中心页 - 参考V1原型设计 -->
  <div class="user-center">
    <!-- 用户信息卡片 -->
    <div class="user-card">
      <div class="user-info">
        <!-- 用户头像 -->
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

    <!-- 订单快捷入口 -->
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

    <!-- 功能入口 -->
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
 * 参考V1原型设计：用户信息卡片、订单快捷入口、功能网格
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
.user-center {
  padding: 0;
}

/* ==================== 用户信息卡片 ==================== */
.user-card {
  background: linear-gradient(135deg, #E4393C 0%, #C62F32 100%);
  border-radius: var(--radius-card);
  padding: 24px;
  margin-bottom: 24px;
  color: #fff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  overflow: hidden;
  flex-shrink: 0;
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

.user-name {
  font-size: 20px;
  font-weight: bold;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.user-phone {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.user-settings {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;
  flex-shrink: 0;
}

.user-settings:hover {
  color: #fff;
}

/* ==================== 订单快捷入口 ==================== */
.order-shortcut {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 20px;
  margin-bottom: 24px;
}

.shortcut-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.shortcut-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0;
}

.shortcut-more {
  font-size: 14px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 0.2s;
}

.shortcut-more:hover {
  color: var(--color-primary);
}

.order-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.order-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.order-entry:hover .entry-label {
  color: var(--color-primary);
}

.entry-icon-wrapper {
  position: relative;
}

.entry-icon {
  font-size: 28px;
}

.entry-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  transition: color 0.2s;
}

/* ==================== 功能入口 ==================== */
.service-section {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 20px;
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 16px;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.service-item:hover .service-name {
  color: var(--color-primary);
}

.service-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  transition: transform 0.2s;
}

.service-item:hover .service-icon {
  transform: scale(1.1);
}

.service-name {
  font-size: 14px;
  color: var(--color-text-secondary);
  transition: color 0.2s;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .service-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }

  .order-grid {
    gap: 12px;
  }

  .entry-icon {
    font-size: 24px;
  }
}
</style>
