/**
 * Playwright E2E 测试配置文件
 * <p>
 * 配置三端（用户端/商家后台/管理后台）的端到端测试。
 * Playwright 会模拟真实浏览器操作，测试用户完整使用流程。
 * </p>
 */
import { defineConfig, devices } from '@playwright/test'

/**
 * 读取环境变量，决定测试哪个环境
 * 默认 local（本地开发环境），也可通过 CI_ENV=staging 切换
 */
const baseUrlUser = process.env.E2E_BASE_URL_USER || 'http://localhost:3000'
const baseUrlMerchant = process.env.E2E_BASE_URL_MERCHANT || 'http://localhost:3001'
const baseUrlAdmin = process.env.E2E_BASE_URL_ADMIN || 'http://localhost:3002'

export default defineConfig({
  // 测试目录：e2e 文件夹下的所有 .spec.ts 文件
  testDir: './e2e',

  // 测试执行超时时间（30秒，E2E 测试比较慢）
  timeout: 30 * 1000,

  // 断言超时时间（5秒，页面元素可能需要时间渲染）
  expect: {
    timeout: 5000,
  },

  // 并行执行：E2E 测试通常串行更稳定，但不同项目可以并行
  fullyParallel: false,
  workers: 1,

  // 失败重试：CI 环境2次，本地1次
  retries: process.env.CI ? 2 : 1,

  // 测试报告：HTML 格式，方便查看截图和视频
  reporter: [
    ['html', { open: 'never' }],
    ['list'],
  ],

  // 全局配置
  use: {
    // 截图：仅在失败时截图
    screenshot: 'only-on-failure',
    // 视频：首次失败时录制
    video: 'retain-on-failure',
    // 跟踪：首次失败时记录（可用于调试回放）
    trace: 'on-first-retry',
  },

  // 按项目分组配置（三端各自独立运行）
  // 小白理解：projects 数组定义了多组测试，每组可以用不同浏览器、跑不同文件。
  // 当前只配置三端 chromium 测试（主流程覆盖）。
  // 如需跨浏览器兼容性测试（firefox/webkit），请先运行 `npx playwright install firefox`
  // 安装浏览器，再在此数组中追加对应的 project 配置。
  projects: [
    // ===== 用户端 E2E 测试 =====
    {
      name: 'web-user',
      testMatch: 'user/**/*.spec.ts',
      use: {
        baseURL: baseUrlUser,
        browserName: 'chromium',
      },
    },

    // ===== 商家后台 E2E 测试 =====
    {
      name: 'web-merchant',
      testMatch: 'merchant/**/*.spec.ts',
      use: {
        baseURL: baseUrlMerchant,
        browserName: 'chromium',
      },
    },

    // ===== 管理后台 E2E 测试 =====
    {
      name: 'web-admin',
      testMatch: 'admin/**/*.spec.ts',
      use: {
        baseURL: baseUrlAdmin,
        browserName: 'chromium',
      },
    },
  ],

  // 本地开发环境启动配置（运行 E2E 前自动启动 dev server）
  // webServer: {
  //   command: 'npm run dev:user',
  //   url: 'http://localhost:3000',
  //   reuseExistingServer: true,
  //   timeout: 60 * 1000,
  // },
})
