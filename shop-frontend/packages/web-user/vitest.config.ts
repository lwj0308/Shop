/**
 * Vitest 配置文件 - web-user 用户端
 * <p>
 * 用户端是 Vue 3 + Element Plus 应用，组件测试需要浏览器环境（happy-dom）。
 * 复用 vite.config.ts 的路径别名和 Element Plus 自动导入配置，
 * 这样测试时组件里的 <el-button> 等也能正常解析。
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
    // Vue 单文件组件解析
    vue(),
    // Element Plus 按需自动导入（和 vite.config.ts 保持一致）
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      // @ 指向 src 目录（和 vite.config.ts 一致）
      '@': resolve(__dirname, 'src'),
      // @shop/shared 指向共享包源码
      '@shop/shared': resolve(__dirname, '../shared/src'),
    },
  },
  test: {
    // Vue 组件测试需要 DOM 环境，happy-dom 比 jsdom 快很多
    environment: 'happy-dom',
    // 测试文件匹配规则：src 下所有 .test.ts 文件
    include: ['src/**/*.test.ts'],
    // 全局引入 jest-dom 断言（toBeInTheDocument 等用法）
    setupFiles: ['../../test-setup.ts'],
    // 禁用 CSS 处理：Element Plus 的 CSS 文件在测试环境无法解析
    css: false,
    // 内联 element-plus 和 @element-plus/icons-vue，避免依赖解析问题
    server: {
      deps: {
        inline: ['element-plus', '@element-plus/icons-vue'],
      },
    },
    // 覆盖率配置
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'coverage',
      // 覆盖率门槛：行覆盖率 ≥ 50%（搭建初期先低，后续提到 60%）
      thresholds: {
        lines: 50,
      },
      // 排除非业务代码：入口文件、路由声明、样式
      exclude: [
        'src/main.ts',
        'src/App.vue',
        'src/router/**',
        'src/styles/**',
        'src/vite-env.d.ts',
      ],
    },
  },
})
