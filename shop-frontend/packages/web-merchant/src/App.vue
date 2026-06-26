<template>
  <!-- 根组件：包含全局loading遮罩和路由出口 -->
  <div id="merchant-app">
    <!-- 全局loading遮罩：在API请求期间显示 -->
    <div v-if="merchantStore.globalLoading" class="global-loading">
      <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
      <span class="loading-text">加载中...</span>
    </div>
    <!-- 路由出口：当前路由对应的页面组件会渲染在这里 -->
    <router-view />
  </div>
</template>

<script setup lang="ts">
/**
 * 根组件
 * 商家后台的最外层Vue组件
 * 包含全局loading状态展示
 */

import { Loading } from '@element-plus/icons-vue'
import { useMerchantStore } from '@/stores/merchant'

const merchantStore = useMerchantStore()
</script>

<style scoped>
.global-loading {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.loading-icon {
  animation: spin 1s linear infinite;
  color: #409eff;
}

.loading-text {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
