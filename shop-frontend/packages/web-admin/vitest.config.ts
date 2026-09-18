/**
 * Vitest 配置文件 - web-admin 管理后台
 * <p>
 * 管理后台是 Vue 3 + Element Plus + Pinia + ECharts 应用。
 * ECharts 依赖 Canvas，在 happy-dom 环境下需要 mock。
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
    // ECharts 和 Element Plus 在测试环境需要内联处理
    server: {
      deps: {
        inline: ['echarts', 'element-plus', '@element-plus/icons-vue'],
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
        'src/directives/**',
      ],
    },
  },
})
