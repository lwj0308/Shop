/**
 * Vitest 配置文件 - shared 共享工具包
 * <p>
 * shared 包是纯 TypeScript 工具库，没有 Vue 组件，
 * 所以用 node 环境就够，不需要 happy-dom 浏览器模拟环境。
 * 覆盖率统计排除 index.ts（纯导出文件）和 types（类型声明无逻辑）。
 * </p>
 */
import { defineConfig } from 'vitest/config'
import { resolve } from 'path'

export default defineConfig({
  resolve: {
    alias: {
      // @shop/shared 指向自身 src 目录，方便包内自测
      '@shop/shared': resolve(__dirname, 'src'),
    },
  },
  test: {
    // shared 是纯工具库，用 node 环境即可，不需要浏览器模拟
    environment: 'node',
    // 测试文件匹配规则：src 下所有 .test.ts 文件
    include: ['src/**/*.test.ts'],
    // 覆盖率配置
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'coverage',
      // 覆盖率门槛：行覆盖率 ≥ 50%（测试搭建初期先低一些，后续逐步提高）
      thresholds: {
        lines: 50,
      },
      // 排除纯导出文件和类型声明（没有实际逻辑代码）
      exclude: ['src/index.ts', 'src/types/**', 'src/env.d.ts'],
    },
  },
})
