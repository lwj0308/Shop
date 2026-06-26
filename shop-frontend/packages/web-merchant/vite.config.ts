/**
 * Vite配置文件 - 商家后台
 * 配置路径别名、Element Plus按需引入、开发代理等
 */

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需自动导入
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    // Element Plus 组件按需自动注册
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@shop/shared': resolve(__dirname, '../shared/src'),
    },
  },
  server: {
    port: 3001,
    // 开发代理：把 /api 开头的请求转发到后端网关
    proxy: {
      '/api': {
        target: 'http://localhost:8844',
        changeOrigin: true,
      },
    },
  },
})
