/**
 * F-E-06 商家入驻全流程 E2E 测试
 * <p>
 * 测试场景：申请入驻 → 等待审核 → 审核通过 → 登录成功
 * 涵盖登录页加载、入驻链接、入驻申请表单、提交申请、登录验证等 7 个测试用例。
 * 所有 API 请求都通过 page.route() mock，不依赖真实后端服务。
 * </p>
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { mockPost, mockMerchantCommonGetApis } from '../helpers/api-mock'

/**
 * 填写并提交入驻申请表单（走完3步流程）
 * <p>
 * 小白理解：入驻申请有3个步骤——填基本信息、传营业执照、确认提交。
 * 这个辅助函数把这3步都做完，让每个测试不用重复写一大堆代码。
 * </p>
 *
 * @param page Playwright 页面对象
 */
async function fillAndSubmitApplyForm(page: Page): Promise<void> {
  // ===== 第一步：填写基本信息 =====
  // 依次填写商家名称、联系人、联系电话、地址、描述
  await page.getByPlaceholder('请输入商家名称').fill('测试店铺')
  await page.getByPlaceholder('联系人姓名').fill('张三')
  await page.getByPlaceholder('手机号码').fill('13812345678')
  await page.getByPlaceholder('请输入商家地址').fill('北京市朝阳区测试地址100号')
  await page.getByPlaceholder('简要介绍你的商家和主营产品').fill('E2E测试商家，主营测试商品')

  // 点击「下一步」按钮，进入第二步（上传资质）
  await page.getByRole('button', { name: '下一步' }).click()
  // 等待第二步表单渲染完成
  await page.waitForTimeout(500)

  // ===== 第二步：上传营业执照 =====
  // el-upload 组件内部会渲染一个 <input type="file">，用 setInputFiles 模拟用户选择文件
  // 这里传一个假的图片文件（内容不重要，前端只校验类型和大小）
  await page.setInputFiles('input[type="file"]', {
    name: 'license.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from('fake-image-content-for-e2e-test'),
  })
  // 等待图片预览生成
  await page.waitForTimeout(500)

  // 点击「下一步」按钮，进入第三步（确认提交）
  await page.getByRole('button', { name: '下一步' }).click()
  // 等待第三步确认页面渲染完成
  await page.waitForTimeout(500)

  // ===== 第三步：确认并提交 =====
  // 点击「提交审核」按钮，会弹出二次确认弹窗
  await page.getByRole('button', { name: '提交审核' }).click()
  // 弹窗里的确认按钮文本是「确定提交」
  await page.getByRole('button', { name: '确定提交' }).click()
}

test.describe('F-E-06 商家入驻全流程', () => {
  // 每个测试前都先 mock 商家端常用 GET 接口（/merchant/info 等）
  // 这样页面加载时不会因为接口报错而白屏
  test.beforeEach(async ({ page }) => {
    await mockMerchantCommonGetApis(page)
  })

  test('商家登录页应正常加载', async ({ page }) => {
    // 打开登录页（/login 是白名单路由，不需要登录就能访问）
    await page.goto('/login')

    // 验证页面标题包含「商家登录」
    await expect(page).toHaveTitle(/商家登录/)

    // 验证登录表单卡片存在且可见
    await expect(page.locator('.login-form')).toBeVisible()
  })

  test('登录页应显示"去入驻"或"注册"链接', async ({ page }) => {
    // 打开登录页
    await page.goto('/login')

    // 验证「立即入驻」链接存在（在表单底部）
    await expect(page.getByText('立即入驻')).toBeVisible()
  })

  test('点击入驻链接应跳转到入驻申请页', async ({ page }) => {
    // 打开登录页
    await page.goto('/login')

    // 点击「立即入驻」链接
    await page.getByText('立即入驻').click()

    // 验证 URL 跳转到了 /apply（入驻申请页）
    await expect(page).toHaveURL(/\/apply/)
  })

  test('入驻申请表单应包含店铺名称、联系人、手机号等字段', async ({ page }) => {
    // 打开入驻申请页（/apply 也是白名单路由，不需要登录）
    await page.goto('/apply')

    // 验证第一步表单的各个输入框都存在
    await expect(page.getByPlaceholder('请输入商家名称')).toBeVisible()
    await expect(page.getByPlaceholder('联系人姓名')).toBeVisible()
    await expect(page.getByPlaceholder('手机号码')).toBeVisible()
    await expect(page.getByPlaceholder('请输入商家地址')).toBeVisible()
    await expect(page.getByPlaceholder('简要介绍你的商家和主营产品')).toBeVisible()
  })

  test('填写入驻信息后提交应成功', async ({ page }) => {
    // mock 入驻申请接口 POST /merchant/apply，返回成功（带一个假的申请ID）
    await mockPost(page, '**/merchant/apply**', { id: 1001 })

    // 打开入驻申请页
    await page.goto('/apply')

    // 走完3步流程并提交
    await fillAndSubmitApplyForm(page)

    // 验证提交成功后显示成功提示「入驻申请已提交」
    await expect(page.getByText('入驻申请已提交')).toBeVisible({ timeout: 5000 })
  })

  test('入驻申请提交后应显示成功提示或跳转', async ({ page }) => {
    // mock 入驻申请接口返回成功
    await mockPost(page, '**/merchant/apply**', { id: 1002 })

    // 打开入驻申请页
    await page.goto('/apply')

    // 走完3步流程并提交
    await fillAndSubmitApplyForm(page)

    // 提交成功后会先显示「入驻申请已提交」提示，然后跳转到登录页
    // 这里验证成功提示出现（任一条件满足即可）
    const successMessage = page.getByText('入驻申请已提交')
    const loginUrl = page.waitForURL(/\/login/, { timeout: 5000 })

    // 等待成功提示 或 跳转到登录页，任一先发生即算通过
    await Promise.race([
      successMessage.waitFor({ state: 'visible' }),
      loginUrl,
    ])
  })

  test('审核通过后用账号密码登录应成功', async ({ page }) => {
    // mock 登录接口 POST /merchant/auth/login，返回 token
    // 注意：后端用 Sa-Token，返回的是 { token } 字段（不是 accessToken）
    await mockPost(page, '**/merchant/auth/login**', { token: 'mock-login-token-from-e2e-test' })

    // 打开登录页
    await page.goto('/login')

    // 填写联系电话和密码
    await page.getByPlaceholder('联系电话').fill('13812345678')
    await page.getByPlaceholder('密码').fill('123456')

    // 点击登录按钮（登录按钮是自定义的 .submit-btn，不是 el-button）
    await page.locator('.submit-btn').click()

    // 登录成功后会跳转到首页（工作台），URL 不再包含 /login
    // 用 waitForURL 等待跳转完成
    await page.waitForURL(url => !url.toString().includes('/login'), { timeout: 5000 })

    // 再次确认当前 URL 是首页（工作台）
    await expect(page).toHaveURL(/localhost:3001\/?$/)
  })
})
