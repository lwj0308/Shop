<template>
  <!-- 包裹 Element Plus 中文语言包，让所有组件（如分页的"前往"）显示中文 -->
  <el-config-provider :locale="zhCn">
    <!-- 路由出口：当前路由对应的页面组件会渲染在这里 -->
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
/**
 * 根组件
 * 最外层的Vue组件，所有页面都在 <router-view /> 里渲染
 * 通过 el-config-provider 注入中文语言包，让 Element Plus 组件显示中文文案
 */
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
