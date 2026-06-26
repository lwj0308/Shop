<template>
  <!-- 消息通知页 -->
  <div class="notification-page">
    <div class="page-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2 class="page-title">消息通知</h2>
        <button
          v-if="unreadCount > 0"
          class="read-all-btn"
          @click="handleReadAll"
        >全部已读</button>
      </div>

      <!-- 筛选区 -->
      <div class="filter-bar">
        <!-- 已读状态 Tab -->
        <div class="tab-group">
          <button
            :class="['tab-btn', { active: activeTab === 'all' }]"
            @click="switchTab('all')"
          >全部</button>
          <button
            :class="['tab-btn', { active: activeTab === 'unread' }]"
            @click="switchTab('unread')"
          >
            未读
            <span v-if="unreadCount > 0" class="tab-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </button>
        </div>

        <!-- 类型筛选 -->
        <select v-model="filterType" class="type-select" @change="loadData">
          <option :value="undefined">全部类型</option>
          <option v-for="opt in notificationTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>

      <!-- 通知列表 -->
      <div v-loading="loading" class="notification-list">
        <div v-if="list.length === 0 && !loading" class="empty-state">
          <span class="empty-icon">📭</span>
          <p class="empty-text">暂无通知</p>
        </div>

        <div
          v-for="item in list"
          :key="item.id"
          :class="['notification-item', { unread: item.isRead === 0 }]"
          @click="handleClickNotification(item)"
        >
          <!-- 未读标记点 -->
          <span v-if="item.isRead === 0" class="unread-dot"></span>

          <div class="item-content">
            <div class="item-header">
              <span :class="['type-tag', `type-${item.type}`]">{{ item.typeDesc || getTypeText(item.type) }}</span>
              <span class="item-time">{{ formatTime(item.createTime) }}</span>
            </div>
            <h4 class="item-title">{{ item.title }}</h4>
            <p class="item-desc">{{ item.content }}</p>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination">
        <button :disabled="pageNum <= 1" class="page-btn" @click="changePage(pageNum - 1)">上一页</button>
        <span class="page-info">{{ pageNum }} / {{ totalPages }}</span>
        <button :disabled="pageNum >= totalPages" class="page-btn" @click="changePage(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 消息通知列表页（用户端）
 * 展示当前用户的所有站内通知，支持按类型和已读状态筛选
 */

import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getUserNotificationList,
  getUserUnreadCount,
  markUserNotificationRead,
  markUserAllNotificationsRead,
} from '@shop/shared'
import {
  notificationTypeOptions,
  notificationTypeTextMap,
  type NotificationInfo,
} from '@shop/shared'

/** 通知列表 */
const list = ref<NotificationInfo[]>([])
/** 总条数 */
const total = ref(0)
/** 每页条数 */
const pageSize = 10
/** 当前页码 */
const pageNum = ref(1)
/** 总页数 */
const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

/** 加载状态 */
const loading = ref(false)

/** 当前 Tab：all 全部 / unread 未读 */
const activeTab = ref<'all' | 'unread'>('all')

/** 类型筛选 */
const filterType = ref<number | undefined>(undefined)

/** 未读数量 */
const unreadCount = ref(0)

/**
 * 加载通知列表
 */
async function loadData() {
  loading.value = true
  try {
    const res = await getUserNotificationList({
      pageNum: pageNum.value,
      pageSize,
      type: filterType.value,
      isRead: activeTab.value === 'unread' ? 0 : undefined,
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载通知失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载未读数量
 */
async function loadUnreadCount() {
  try {
    const res = await getUserUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 静默失败，不影响页面
  }
}

/**
 * 切换 Tab
 */
function switchTab(tab: 'all' | 'unread') {
  activeTab.value = tab
  pageNum.value = 1
  loadData()
}

/**
 * 点击通知：标记为已读
 */
async function handleClickNotification(item: NotificationInfo) {
  // 已读的通知不处理
  if (item.isRead === 1) return

  try {
    await markUserNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    // 标记失败静默处理
  }
}

/**
 * 全部标记已读
 */
async function handleReadAll() {
  try {
    await markUserAllNotificationsRead()
    list.value.forEach(item => { item.isRead = 1 })
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/**
 * 翻页
 */
function changePage(page: number) {
  pageNum.value = page
  loadData()
}

/**
 * 获取通知类型中文描述
 */
function getTypeText(type: number): string {
  return notificationTypeTextMap[type] || '通知'
}

/**
 * 格式化时间
 */
function formatTime(time: string): string {
  if (!time) return ''
  // 简单格式化：取 yyyy-MM-dd HH:mm 部分
  return time.replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  loadData()
  loadUnreadCount()
})
</script>

<style scoped>
.notification-page {
  background-color: var(--color-bg);
  min-height: 100vh;
  padding: var(--space-xl) var(--space-lg);
}

.page-container {
  max-width: 800px;
  margin: 0 auto;
}

/* 页面标题 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.read-all-btn {
  padding: 8px 16px;
  font-size: 14px;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
}

.read-all-btn:hover {
  background: var(--color-primary);
  color: #fff;
}

/* 筛选区 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
  gap: var(--space-md);
  flex-wrap: wrap;
}

.tab-group {
  display: flex;
  gap: var(--space-xs);
}

.tab-btn {
  padding: 6px 16px;
  font-size: 14px;
  color: var(--color-text-secondary);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--transition-base);
  display: flex;
  align-items: center;
  gap: 4px;
}

.tab-btn.active {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.tab-badge {
  background: var(--color-accent);
  color: #fff;
  font-size: 11px;
  border-radius: var(--radius-pill);
  padding: 1px 6px;
  min-width: 18px;
  text-align: center;
}

.type-select {
  padding: 6px 12px;
  font-size: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
  color: var(--color-text);
  cursor: pointer;
  outline: none;
}

/* 通知列表 */
.notification-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.empty-state {
  text-align: center;
  padding: var(--space-3xl) var(--space-lg);
  color: var(--color-text-muted);
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: var(--space-md);
}

.empty-text {
  font-size: 14px;
  margin: 0;
}

/* 通知项 */
.notification-item {
  position: relative;
  display: flex;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  cursor: pointer;
  transition: var(--transition-base);
}

.notification-item:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.notification-item.unread {
  border-left: 3px solid var(--color-primary);
}

/* 未读小圆点 */
.unread-dot {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  width: 8px;
  height: 8px;
  background: var(--color-accent);
  border-radius: 50%;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.type-tag {
  display: inline-block;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
}

.type-tag.type-1 { background: #e3f2fd; color: #1976d2; }
.type-tag.type-2 { background: #e8f5e9; color: #2e7d32; }
.type-tag.type-3 { background: #fff3e0; color: #e65100; }
.type-tag.type-4 { background: #f3e5f5; color: #7b1fa2; }
.type-tag.type-5 { background: #e8f5e9; color: #2e7d32; }
.type-tag.type-6 { background: #eceff1; color: #455a64; }

.item-time {
  font-size: 12px;
  color: var(--color-text-muted);
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 4px 0;
}

.item-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  margin-top: var(--space-xl);
}

.page-btn {
  padding: 6px 16px;
  font-size: 14px;
  color: var(--color-text);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--transition-base);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 响应式 */
@media (max-width: 768px) {
  .notification-page {
    padding: var(--space-md);
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
