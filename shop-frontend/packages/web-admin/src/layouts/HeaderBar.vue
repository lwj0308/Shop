<template>
  <!-- 顶部导航栏 -->
  <div class="header-bar">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="header-left">
      <el-icon class="collapse-btn" @click="$emit('toggleCollapse')">
        <Fold v-if="!collapse" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧：通知 + 主题切换 + 全屏 + 用户信息 -->
    <div class="header-right">
      <!-- 通知铃铛：点击跳转通知列表，未读数量徽章 -->
      <el-tooltip content="消息通知">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notification-badge">
          <el-icon class="header-action" @click="goToNotification">
            <Bell />
          </el-icon>
        </el-badge>
      </el-tooltip>

      <!-- 主题切换按钮 -->
      <el-tooltip :content="isDark ? '切换亮色模式' : '切换暗黑模式'">
        <el-icon class="header-action" @click="toggleTheme()">
          <Sunny v-if="isDark" />
          <Moon v-else />
        </el-icon>
      </el-tooltip>

      <!-- 全屏按钮 -->
      <el-tooltip content="全屏">
        <el-icon class="header-action" @click="toggleFullscreen">
          <FullScreen />
        </el-icon>
      </el-tooltip>

      <!-- 用户下拉菜单 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="28" :icon="UserFilled" />
          <span class="username">{{ adminStore.displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 顶部导航栏
 *
 * 包含：侧边栏折叠按钮、面包屑导航、通知铃铛、主题切换、全屏、用户信息
 */

import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Fold, Expand, Sunny, Moon, FullScreen,
  UserFilled, ArrowDown, SwitchButton, Bell,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAdminStore } from '@/stores/admin'
import { isDark, toggleTheme } from '@/composables/useTheme'
import { getAdminUnreadCount } from '@shop/shared'

defineProps<{
  /** 侧边栏是否折叠 */
  collapse: boolean
}>()

defineEmits<{
  /** 切换侧边栏折叠状态 */
  (e: 'toggleCollapse'): void
}>()

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

/** 未读通知数量（顶部铃铛徽章） */
const unreadCount = ref(0)

/** 面包屑导航（根据当前路由自动生成） */
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta?.title)
  return matched.map(item => ({
    path: item.path,
    title: item.meta.title as string,
  }))
})

/**
 * 获取未读通知数量
 * 调用后端API获取当前管理员的未读通知数，用于顶部铃铛徽章
 * 静默失败：API失败不影响页面正常使用
 */
async function fetchUnreadCount() {
  try {
    const res = await getAdminUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 静默失败
  }
}

/**
 * 跳转到消息通知页
 */
function goToNotification() {
  router.push('/notification')
}

/**
 * 监听路由变化，刷新未读数量
 * 用于在通知页操作后回到其他页时同步徽章
 */
watch(() => route.fullPath, () => {
  fetchUnreadCount()
})

/**
 * 切换全屏模式
 */
function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    document.documentElement.requestFullscreen()
  }
}

/**
 * 处理下拉菜单命令
 */
async function handleCommand(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      adminStore.logout()
    } catch {
      // 用户取消
    }
  }
}

/** 页面加载时获取未读通知数量 */
onMounted(() => {
  fetchUnreadCount()
})
</script>

<style scoped>
.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0 16px;
  height: 56px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text);
}

.collapse-btn:hover {
  color: var(--color-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-action {
  font-size: 18px;
  cursor: pointer;
  color: var(--color-text-secondary);
}

.header-action:hover {
  color: var(--color-primary);
}

/* 通知铃铛徽章：让 badge 与其他图标对齐 */
.notification-badge {
  display: inline-flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-text);
}

.username {
  font-size: 14px;
}
</style>
