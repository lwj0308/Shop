/**
 * Vite配置文件
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
    // Element Plus 按需自动导入 - 不用手动写 import { ElButton } from 'element-plus'
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    // Element Plus 组件按需自动注册 - 模板里直接用 <el-button> 就行
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      // @ 指向 src 目录，这样写 import xxx from '@/xxx' 更简洁
      '@': resolve(__dirname, 'src'),
      // @shop/shared 指向共享包源码目录
      '@shop/shared': resolve(__dirname, '../shared/src'),
    },
  },
  server: {
    port: 3000,
    // 开发代理：把 /api 开头的请求转发到后端网关，解决跨域问题
    proxy: {
      '/api': {
        target: 'http://localhost:8844',
        changeOrigin: true,
      },
    },
  },
})
