/**
 * 管理后台登录页 E2E 示例测试
 * <p>
 * 验证管理后台登录页能正常打开。
 * 运行前需要启动 dev server（npm run dev:admin）
 * </p>
 */
import { test, expect } from '@playwright/test'

test.describe('管理后台登录', () => {
  test('登录页应正常加载', async ({ page }) => {
    await page.goto('/')

    await expect(page.locator('body')).toBeVisible()
  })
})
