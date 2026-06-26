<template>
  <div class="not-found">
    <h1>404</h1>
    <p>抱歉，您访问的页面不存在</p>
    <el-button type="primary" @click="router.push('/')">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  color: var(--color-text);
}
.not-found h1 {
  font-size: 72px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 16px;
}
.not-found p {
  font-size: 18px;
  color: var(--color-text-secondary);
  margin-bottom: 24px;
}
</style>
