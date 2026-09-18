/**
 * Vitest 配置文件 - web-merchant 商家后台
 * <p>
 * 商家后台是 Vue 3 + Element Plus 应用，配置和 web-user 类似。
 * 商家后台没有使用 Pinia，状态管理用简单的响应式对象。
 * </p>
 */
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
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
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.test.ts'],
    setupFiles: ['../../test-setup.ts'],
    css: false,
    server: {
      deps: {
        inline: ['element-plus', '@element-plus/icons-vue'],
      },
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'coverage',
      thresholds: {
        lines: 50,
      },
      exclude: [
        'src/main.ts',
        'src/App.vue',
        'src/router/**',
        'src/styles/**',
      ],
    },
  },
})
