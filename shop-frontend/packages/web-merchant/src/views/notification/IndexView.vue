<template>
  <!-- 商家消息通知页 -->
  <div class="notification-list">
    <!-- 搜索区 -->
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="queryForm.isRead" placeholder="全部" clearable style="width: 120px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryForm.type" placeholder="全部类型" clearable style="width: 140px">
            <el-option v-for="opt in notificationTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-if="unreadCount > 0" type="success" plain @click="handleReadAll">全部已读</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 通知表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.type)" size="small">{{ row.typeDesc || getTypeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" min-width="180" />
        <el-table-column label="内容" prop="content" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isRead === 0" type="danger" size="small">未读</el-tag>
            <el-tag v-else type="info" size="small">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" prop="createTime" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.isRead === 0" type="primary" text size="small" @click="handleRead(row)">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 商家消息通知列表页
 * 展示当前商家的站内通知，支持按类型和已读状态筛选
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMerchantNotificationList,
  getMerchantUnreadCount,
  markMerchantAllNotificationsRead,
} from '@shop/shared'
import {
  notificationTypeOptions,
  notificationTypeTextMap,
  type NotificationInfo,
} from '@shop/shared'

/** 加载状态 */
const loading = ref(false)
/** 通知列表 */
const tableData = ref<NotificationInfo[]>([])
/** 总条数 */
const total = ref(0)
/** 未读数量 */
const unreadCount = ref(0)

/** 查询参数 */
const queryForm = reactive({
  isRead: undefined as number | undefined,
  type: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 点击搜索 */
function handleSearch() {
  queryForm.pageNum = 1
  loadData()
}

/** 重置搜索条件 */
function handleReset() {
  queryForm.isRead = undefined
  queryForm.type = undefined
  queryForm.pageNum = 1
  loadData()
}

/** 加载通知列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantNotificationList({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      type: queryForm.type,
      isRead: queryForm.isRead,
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 加载未读数量 */
async function loadUnreadCount() {
  try {
    const res = await getMerchantUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 静默失败
  }
}

/** 全部标记已读 */
async function handleReadAll() {
  try {
    await markMerchantAllNotificationsRead()
    ElMessage.success('已全部标记为已读')
    loadData()
    loadUnreadCount()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 获取通知类型中文描述 */
function getTypeText(type: number): string {
  return notificationTypeTextMap[type] || '通知'
}

/** 获取标签颜色 */
function tagType(type: number): string {
  const map: Record<number, string> = {
    1: 'primary',
    2: 'success',
    3: 'warning',
    4: 'info',
    5: 'success',
    6: 'info',
  }
  return map[type] || 'info'
}

/** 格式化时间 */
function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

/**
 * 标记单条已读
 * 注意：商家端没有单条标记已读的接口，这里通过刷新页面实现
 * 实际上商家端 Controller 只提供了全部标记已读，这里点击后跳过
 */
function handleRead(_row: NotificationInfo) {
  ElMessage.info('请点击"全部已读"按钮批量标记')
}

onMounted(() => {
  loadData()
  loadUnreadCount()
})
</script>

<style scoped>
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
