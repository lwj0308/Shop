<template>
  <!-- 管理后台主布局：玻璃侧边栏 + 玻璃顶栏 + mesh 光晕内容区 -->
  <el-container class="admin-layout">
    <!-- 左侧侧边栏：深色玻璃质感，宽度随折叠状态切换 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <Sidebar :collapse="isCollapse" />
    </el-aside>

    <!-- 右侧主区域：顶栏 + 标签页 + 内容 -->
    <el-container class="main-container">
      <!-- 顶部导航栏：玻璃白质感 -->
      <el-header class="header">
        <HeaderBar :collapse="isCollapse" @toggle-collapse="isCollapse = !isCollapse" />
      </el-header>

      <!-- 标签页导航 -->
      <TagsView />

      <!-- 内容区域：带 mesh 光晕背景 -->
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
/* 整体布局：占满视口，加上 mesh 光晕背景营造氛围 */
.admin-layout {
  height: 100vh;
  overflow: hidden;
  background:
    var(--gradient-mesh),
    var(--color-bg);
}

/* 侧边栏容器：深色玻璃 + 模糊 + 右侧细分隔线 */
.sidebar {
  background: var(--color-sidebar-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid var(--color-glass-border);
  transition: width 0.3s var(--ease-out);
  overflow: hidden;
  box-shadow: 4px 0 24px rgba(15, 23, 42, 0.06);
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶栏：玻璃白质感，底部分隔线 */
.header {
  height: 56px;
  padding: 0;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-glass-strong);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  display: flex;
  align-items: center;
  box-shadow: var(--shadow-header);
}

/* 内容区：mesh 光晕背景 + 内边距 */
.main-content {
  background: transparent;
  padding: 24px;
  overflow-y: auto;
}

/* 页面切换动画：保留原淡入淡出 + 轻微上移 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: opacity 0.3s var(--ease-out), transform 0.3s var(--ease-out);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
