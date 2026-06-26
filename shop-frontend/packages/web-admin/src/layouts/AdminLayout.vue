<template>
  <!-- 管理后台主布局 -->
  <el-container class="admin-layout">
    <!-- 左侧侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <Sidebar :collapse="isCollapse" />
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container class="main-container">
      <!-- 顶部导航栏 -->
      <el-header class="header">
        <HeaderBar :collapse="isCollapse" @toggle-collapse="isCollapse = !isCollapse" />
      </el-header>

      <!-- 标签页导航 -->
      <TagsView />

      <!-- 内容区域 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
/**
 * 管理后台主布局
 *
 * 经典后台布局：左侧侧边栏 + 顶部导航栏 + 标签页 + 内容区
 * 支持侧边栏折叠/展开
 */

import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import HeaderBar from './HeaderBar.vue'
import TagsView from './TagsView.vue'

/** 侧边栏是否折叠 */
const isCollapse = ref(false)
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  background-color: var(--color-sidebar-bg);
  transition: width 0.3s;
  overflow: hidden;
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 56px;
  padding: 0;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-card);
  display: flex;
  align-items: center;
}

.main-content {
  background: var(--color-bg);
  padding: 16px;
  overflow-y: auto;
}

/* 页面切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
