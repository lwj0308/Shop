/**
 * F-E-01 用户注册登录 E2E 测试
 * <p>
 * 测试场景：用户注册登录全流程，包括首页加载、弹窗展示、注册、登录、表单校验。
 * 所有接口均通过 page.route() mock，不依赖真实后端。
 * 运行前提：已启动用户端 dev server（http://localhost:3000）
 */
import { test, expect } from '@playwright/test'
import {
  mockGet,
  mockPost,
  mockUserCommonGetApis,
  mockLoginResult,
  mockUserInfo,
} from '../helpers/api-mock'

test.describe('F-E-01 用户注册登录', () => {
  /**
   * 每个测试前先 mock 公共 GET 接口
   * 小白理解：首页打开时会请求商品推荐、分类等接口，
   * 提前 mock 好这些接口，页面才不会白屏。
   */
  test.beforeEach(async ({ page }) => {
    await mockUserCommonGetApis(page)
  })

  /**
   * 测试1：首页应正常加载，显示SHOPMALL标题和导航
   * 小白理解：打开网站首页，检查导航栏和Logo是否出现。
   */
  test('首页应正常加载，显示SHOPMALL标题和导航', async ({ page }) => {
    await page.goto('/')

    // 等待导航栏出现
    await expect(page.locator('.layout-header')).toBeVisible()

    // 检查 Logo 文本 "SHOPMALL" 可见
    await expect(page.locator('.logo-text')).toHaveText('SHOPMALL')

    // 检查导航菜单中有"首页"和"限时秒杀"等链接
    await expect(page.getByRole('link', { name: '首页' })).toBeVisible()
    await expect(page.getByText('限时秒杀')).toBeVisible()
  })

  /**
   * 测试2：点击账户图标应弹出登录弹窗，显示"登录"和"注册"Tab
   * 小白理解：点击右上角的账户图标，应该弹出一个登录弹窗，
   * 弹窗里有两个标签页：登录 和 注册。
   */
  test('点击账户图标应弹出登录弹窗，显示登录和注册Tab', async ({ page }) => {
    await page.goto('/')

    // 小白理解：.header-actions 里有多个图标（搜索、账户、购物车），
    // 索引 0 是搜索图标，索引 1 是账户图标，所以用 nth(1)
    await page.locator('.header-actions .el-icon').nth(1).click()

    // 等待登录弹窗出现
    await expect(page.locator('.auth-modal')).toBeVisible()

    // 验证弹窗中有两个 Tab：登录 和 注册
    const tabs = page.locator('.auth-tab')
    await expect(tabs).toHaveCount(2)
    await expect(tabs.nth(0)).toHaveText('登录')
    await expect(tabs.nth(1)).toHaveText('注册')
  })

  /**
   * 测试3：注册流程 - 切换到注册Tab，填写信息并注册成功
   * 小白理解：模拟用户注册的全过程——填手机号、验证码、密码，
   * 勾选协议，点注册按钮。mock 注册接口返回成功，验证出现成功提示。
   */
  test('注册流程 - 填写信息并注册成功', async ({ page }) => {
    // mock 发送验证码接口返回成功
    await mockPost(page, '**/user/auth/send-code', { success: true })
    // mock 注册接口返回成功（返回新用户ID）
    await mockPost(page, '**/user/auth/register', { id: 1002 })

    await page.goto('/')

    // 点击账户图标打开弹窗
    await page.locator('.header-actions .el-icon').nth(1).click()
    await expect(page.locator('.auth-modal')).toBeVisible()

    // 切换到"注册"Tab（第二个标签）
    await page.locator('.auth-tab').nth(1).click()
    await page.waitForTimeout(300)

    // 小白理解：登录表单和注册表单都有"请输入手机号"输入框，
    // 用 .form-area:visible 只定位当前可见的注册表单区域
    const visibleForm = page.locator('.auth-modal .form-area:visible')

    // 填写手机号
    await visibleForm.getByPlaceholder('请输入手机号').fill('13812345678')
    // 填写验证码（直接填假数据，不需要真的点"获取验证码"按钮）
    await visibleForm.getByPlaceholder('请输入验证码').fill('123456')
    // 填写密码（必须6-20位，包含字母和数字，否则校验不通过）
    await visibleForm.getByPlaceholder('请设置密码（6-20位，含字母和数字）').fill('Test1234')
    // 再次输入密码（确认密码，必须和上面一致）
    await visibleForm.getByPlaceholder('请再次输入密码').fill('Test1234')

    // 勾选"我已阅读并同意用户协议"复选框
    await page.locator('.agreement-label input').check()

    // 点击注册按钮（用 :visible 确保点的是注册表单里的按钮）
    await page.locator('.auth-modal .submit-btn:visible').click()

    // 验证注册成功——Element Plus 成功提示消息出现
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试4：登录流程 - 填写手机号和密码，点击登录验证成功
   * 小白理解：mock 登录接口返回假的 token，mock 用户信息接口返回假用户数据，
   * 然后填表单点登录，验证出现"登录成功"提示。
   */
  test('登录流程 - 填写信息并登录成功', async ({ page }) => {
    // mock 登录接口，返回假的 accessToken 和 refreshToken
    await mockPost(page, '**/user/auth/login', mockLoginResult)
    // mock 获取用户信息接口，登录成功后前端会自动调用此接口
    await mockGet(page, '**/user/info', mockUserInfo)

    await page.goto('/')

    // 点击账户图标打开弹窗
    await page.locator('.header-actions .el-icon').nth(1).click()
    await expect(page.locator('.auth-modal')).toBeVisible()

    // 小白理解：用 .form-area:visible 限定到当前可见的登录表单
    const visibleForm = page.locator('.auth-modal .form-area:visible')

    // 填写手机号
    await visibleForm.getByPlaceholder('请输入手机号').fill('13812345678')
    // 填写密码
    await visibleForm.getByPlaceholder('请输入密码').fill('Test1234')

    // 点击登录按钮
    await page.locator('.auth-modal .submit-btn:visible').click()

    // 验证登录成功——出现"登录成功"提示消息
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试5：表单校验 - 不填手机号直接点登录，应显示"请输入手机号"错误提示
   * 小白理解：表单都有校验规则，手机号是必填项。
   * 不填就点登录，Element Plus 会在输入框下方显示红色错误文字。
   */
  test('表单校验 - 不填手机号直接点登录应显示错误提示', async ({ page }) => {
    await page.goto('/')

    // 点击账户图标打开弹窗
    await page.locator('.header-actions .el-icon').nth(1).click()
    await expect(page.locator('.auth-modal')).toBeVisible()

    // 不填写任何信息，直接点击登录按钮
    await page.locator('.auth-modal .submit-btn:visible').click()

    // 验证出现"请输入手机号"校验错误提示
    await expect(page.getByText('请输入手机号')).toBeVisible({ timeout: 3000 })
  })
})
