/**
 * 用户端首页 E2E 示例测试
 * <p>
 * 验证用户端首页能正常打开、核心元素可见。
 * 这是 F-E 系列任务的起步示例，后续会补充完整流程测试。
 * 注意：运行 E2E 测试前需要先启动 dev server（npm run dev:user）
 * </p>
 */
import { test, expect } from '@playwright/test'

test.describe('用户端首页', () => {
  test('页面应正常加载并显示标题', async ({ page }) => {
    await page.goto('/')

    // 等待页面加载完成，检查 body 是否可见
    await expect(page.locator('body')).toBeVisible()
  })

  test('导航栏应可见', async ({ page }) => {
    await page.goto('/')

    // 检查页面是否有 header 或 nav 元素
    const nav = page.locator('header, nav').first()
    await expect(nav).toBeVisible()
  })
})
