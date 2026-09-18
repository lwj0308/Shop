/**
 * F-E-08 管理员审核商家 E2E 测试
 * <p>
 * 测试场景：管理员登录 → 查看商家列表 → 审核待审核商家 → 通过审核
 * 所有 API 请求通过 page.route() mock，不依赖真实后端服务。
 * </p>
 * <p>
 * 小白理解：这个文件测试管理后台"商家审核"的完整流程。
 * 我们用 Playwright 模拟浏览器操作，但所有接口返回假数据，
 * 所以不需要后端服务启动也能跑测试。
 * </p>
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { mockGet, mockPost, mockPut, mockAdminCommonGetApis } from '../helpers/api-mock'

/** 模拟的验证码响应数据（登录页加载时会请求验证码） */
const mockCaptchaData = {
  key: 'mock-captcha-key',
  image: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjx0ZXh0IHg9IjAiIHk9IjE1Ij7mo7zmo7E8L3RleHQ+PC9zdmc+',
}

/** 模拟的登录成功响应（含 token，登录接口返回） */
const mockLoginData = {
  token: 'mock-admin-token-e2e-test',
  adminUserId: 1,
  username: 'admin',
  nickname: '超级管理员',
}

/** 模拟的当前管理员信息（含角色和权限，登录成功后会请求） */
const mockAdminInfoData = {
  id: 1,
  username: 'admin',
  nickname: '超级管理员',
  avatar: '',
  roles: ['admin'],
  permissions: ['*'],
}

/** 模拟的仪表盘数据（跳转到首页后会请求） */
const mockDashboardData = {
  todayOrders: 100,
  todaySales: 500000,
  totalUsers: 1000,
  totalMerchants: 50,
}

/** 模拟的商家列表数据（包含一条待审核和一条已通过的商家） */
const mockMerchantListData = {
  records: [
    {
      id: 1,
      shopName: '测试店铺A',
      contactName: '张三',
      contactPhone: '13812345678',
      status: 0, // 0=待审核，商家列表中会显示"通过""拒绝"按钮
      createTime: '2026-07-15 10:00:00',
    },
    {
      id: 2,
      shopName: '测试店铺B',
      contactName: '李四',
      contactPhone: '13987654321',
      status: 1, // 1=已通过
      createTime: '2026-07-14 10:00:00',
    },
  ],
  total: 2,
  current: 1,
  size: 10,
}

/**
 * 模拟管理员已登录状态
 * <p>
 * 小白理解：管理后台大部分页面都需要先登录才能访问。
 * 测试中我们不走真正的登录流程，而是直接把登录信息（Token、管理员信息）
 * 塞到浏览器的 localStorage 里，这样前端就以为我们已经登录了。
 * </p>
 * <p>
 * 实现原理：
 * 1. Token 存在 localStorage 的 shop_access_token 里，用 Base64 编码
 * 2. 管理员信息存在 localStorage 的 admin_info 里，JSON 格式
 * 3. addInitScript 会在每次页面导航前执行，确保 localStorage 已设置
 * </p>
 *
 * @param page Playwright 页面对象
 * @param options 可选配置：permissions 权限列表，roles 角色列表
 */
async function mockAdminLoggedIn(page: Page, options?: {
  permissions?: string[]
  roles?: string[]
}): Promise<void> {
  const permissions = options?.permissions ?? ['*']
  const roles = options?.roles ?? ['admin']

  // addInitScript 的回调在浏览器中执行，参数通过第二个参数传入
  await page.addInitScript(({ permissions, roles }) => {
    // Token 用 Base64 编码存储（和 shared/utils/auth.ts 的 encode 函数保持一致）
    localStorage.setItem('shop_access_token', btoa(encodeURIComponent('mock-admin-token-e2e')))
    localStorage.setItem('shop_refresh_token', btoa(encodeURIComponent('mock-refresh-token')))
    // 管理员信息直接 JSON 存储（和 stores/admin.ts 的持久化方式一致）
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1,
      username: 'admin',
      nickname: '超级管理员',
      avatar: '',
      roles,
      permissions,
    }))
  }, { permissions, roles })
}

/**
 * Mock 管理后台页面加载时需要的额外 API
 * <p>
 * 小白理解：mockAdminCommonGetApis 已经 mock 了 /admin/info 和 /admin/dashboard，
 * 但实际代码请求的接口路径是 /admin/user/current 和 /admin/dashboard/overview，
 * 这里补上实际路径的 mock，确保页面不会因为接口报错而白屏。
 * </p>
 *
 * @param page Playwright 页面对象
 */
async function mockAdminPageApis(page: Page): Promise<void> {
  // 当前管理员信息（实际接口路径是 /admin/user/current）
  await mockGet(page, '**/admin/user/current', mockAdminInfoData)
  // 仪表盘概览数据（实际接口路径是 /admin/dashboard/overview）
  await mockGet(page, '**/admin/dashboard/overview', mockDashboardData)
}

// ==================== 测试用例 ====================

test.describe('F-E-08 管理员审核商家', () => {
  // 每个测试前都 mock 管理后台常用接口（按任务要求）
  test.beforeEach(async ({ page }) => {
    await mockAdminCommonGetApis(page)
  })

  /**
   * 测试1：管理后台登录页应正常加载
   * 小白理解：打开浏览器访问管理后台登录页，
   * 检查页面能正常显示登录表单。
   */
  test('管理后台登录页应正常加载', async ({ page }) => {
    // mock 验证码接口（登录页加载时会自动请求验证码图片）
    await mockPost(page, '**/admin/auth/captcha', mockCaptchaData)

    // 访问登录页
    await page.goto('/login')

    // 等待页面加载，检查登录表单标题"账号登录"是否可见
    await expect(page.getByText('账号登录')).toBeVisible()
    // 检查用户名输入框是否存在（通过 placeholder 定位）
    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible()
  })

  /**
   * 测试2：输入用户名密码登录应成功跳转到首页
   * 小白理解：在登录页填写用户名、密码、验证码，点击登录按钮，
   * 验证能成功跳转到后台首页（仪表盘）。
   */
  test('输入用户名密码登录应成功跳转到首页', async ({ page }) => {
    // mock 验证码接口
    await mockPost(page, '**/admin/auth/captcha', mockCaptchaData)
    // mock 登录接口，返回 token
    await mockPost(page, '**/admin/auth/login', mockLoginData)
    // mock 当前管理员信息接口（登录成功后会调用 fetchAdminInfo）
    await mockGet(page, '**/admin/user/current', mockAdminInfoData)
    // mock 仪表盘数据（跳转到首页后会调用）
    await mockGet(page, '**/admin/dashboard/overview', mockDashboardData)

    // 访问登录页
    await page.goto('/login')

    // 等待登录表单加载
    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible()

    // 填写登录表单（用户名、密码、验证码都随便填，因为接口是 mock 的）
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('123456')
    await page.getByPlaceholder('请输入验证码').fill('1234')

    // 点击登录按钮（按钮文本是"登 录"，中间有空格，用正则匹配更稳妥）
    await page.getByRole('button', { name: /登\s*录/ }).click()

    // 等待跳转到首页（仪表盘），最多等10秒
    await page.waitForURL('**/dashboard', { timeout: 10000 })

    // 验证 URL 包含 /dashboard
    await expect(page).toHaveURL(/\/dashboard/)
  })

  /**
   * 测试3：登录后侧边栏应显示菜单项
   * 小白理解：管理员登录后，页面左侧应显示导航菜单，
   * 包含"仪表盘""业务管理"等菜单项。
   */
  test('登录后侧边栏应显示菜单项', async ({ page }) => {
    // 设置登录状态（直接写入 localStorage，跳过登录流程）
    await mockAdminLoggedIn(page)
    // mock 页面加载需要的 API
    await mockAdminPageApis(page)

    // 访问首页（会自动重定向到 /dashboard）
    await page.goto('/')

    // 等待侧边栏渲染完成，检查"仪表盘"菜单项可见
    await expect(page.locator('.el-menu-item').filter({ hasText: '仪表盘' })).toBeVisible()

    // 检查"业务管理"菜单项可见（"商家管理"的父菜单）
    await expect(page.locator('.el-sub-menu__title').filter({ hasText: '业务管理' })).toBeVisible()
  })

  /**
   * 测试4：点击商家管理菜单应跳转到商家列表页
   * 小白理解：在侧边栏点击"业务管理"展开子菜单，再点击"商家管理"，
   * 验证页面 URL 跳转到商家管理页，且面包屑显示"商家管理"。
   *
   * 注意：本用例只验证"菜单点击跳转"行为本身（URL + 面包屑），
   * 不验证页面内容（heading、表格等）。
   * 原因：AdminLayout 的 router-view 用了 transition mode="out-in"，
   * 从 dashboard 切到 merchant 时会等 dashboard 离开动画完成再渲染新组件，
   * 加上 echarts 初始化等耗时，5 秒内 heading 可能还没渲染出来。
   * 页面内容渲染的验证由测试5（直接 goto 访问）覆盖，职责分离更稳定。
   */
  test('点击商家管理菜单应跳转到商家列表页', async ({ page }) => {
    // 设置登录状态
    await mockAdminLoggedIn(page)
    // mock 页面加载需要的 API
    await mockAdminPageApis(page)
    // mock 商家列表接口（跳转到商家管理页后会调用）
    await mockGet(page, '**/admin/manage/merchant/list**', mockMerchantListData)

    // 访问首页
    await page.goto('/')

    // 等待侧边栏渲染完成
    await expect(page.locator('.el-menu-item').filter({ hasText: '仪表盘' })).toBeVisible()

    // 点击"业务管理"展开子菜单（Element Plus 子菜单默认收起，需点击展开）
    await page.locator('.el-sub-menu__title').filter({ hasText: '业务管理' }).click()

    // 等待子菜单展开，"商家管理"菜单项可见后点击
    const merchantMenuItem = page.locator('.el-menu-item').filter({ hasText: '商家管理' })
    await expect(merchantMenuItem).toBeVisible()
    await merchantMenuItem.click()

    // 验证 URL 跳转到商家管理页
    await page.waitForURL('**/business/merchant', { timeout: 10000 })
    await expect(page).toHaveURL(/\/business\/merchant/)

    // 验证面包屑显示"商家管理"（确认路由匹配成功，AdminLayout 已识别新路由）
    // 小白理解：面包屑是顶部的导航路径，会根据当前路由自动更新。
    // 用 navigation 角色定位面包屑容器，再找里面的"商家管理"链接。
    await expect(page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: '商家管理' })).toBeVisible()
  })

  /**
   * 测试5：商家列表应显示待审核的商家
   * 小白理解：进入商家管理页面，表格应显示 mock 的商家数据，
   * 其中状态为"待审核"（status=0）的商家应显示"待审核"标签。
   */
  test('商家列表应显示待审核的商家', async ({ page }) => {
    // 设置登录状态
    await mockAdminLoggedIn(page)
    // mock 页面加载需要的 API
    await mockAdminPageApis(page)
    // mock 商家列表接口，返回含待审核商家的数据
    await mockGet(page, '**/admin/manage/merchant/list**', mockMerchantListData)

    // 直接访问商家管理页
    await page.goto('/business/merchant')

    // 等待表格加载完成，检查页面标题
    await expect(page.getByRole('heading', { name: '商家审核' })).toBeVisible()

    // 验证表格中显示了 mock 的商家名称
    await expect(page.getByText('测试店铺A')).toBeVisible()

    // 验证"待审核"标签存在（status=0 的商家显示"待审核"标签）
    await expect(page.locator('.el-tag').filter({ hasText: '待审核' })).toBeVisible()
  })

  /**
   * 测试6：点击"审核通过"按钮应成功审核
   * 小白理解：在待审核商家行点击"通过"按钮，弹出确认框，
   * 点击"确定"后验证审核接口被调用，显示成功提示。
   */
  test('点击审核通过按钮应成功审核', async ({ page }) => {
    // 设置登录状态
    await mockAdminLoggedIn(page)
    // mock 页面加载需要的 API
    await mockAdminPageApis(page)
    // mock 商家列表接口
    await mockGet(page, '**/admin/manage/merchant/list**', mockMerchantListData)

    // mock 审核接口（PUT 请求，用公共 mockPut 自动加 /api 前缀，
    // 避免直接用 page.route 拦截到页面导航）
    await mockPut(page, '**/admin/manage/merchant/audit', null)

    // 直接访问商家管理页
    await page.goto('/business/merchant')

    // 等待表格加载完成，确保商家数据显示
    await expect(page.getByText('测试店铺A')).toBeVisible()

    // 点击第一行的"通过"按钮（只有 status=0 待审核的商家才显示"通过"按钮）
    await page.getByRole('button', { name: '通过' }).first().click()

    // 等待 Element Plus 确认框出现（ElMessageBox 会 teleport 到 body）
    const messageBox = page.locator('.el-message-box')
    await expect(messageBox).toBeVisible()

    // 在点击"确定"前注册响应监听（点击确定后会发起审核请求）
    // 小白理解：waitForResponse 必须在请求发出前调用，否则会错过响应
    const auditResponsePromise = page.waitForResponse(
      (resp) => resp.url().includes('/api/admin/manage/merchant/audit'),
      { timeout: 5000 },
    )

    // 点击确认框中的"确定"按钮
    // 注意：Element Plus 默认 locale 是英文，按钮文本是 "OK" 不是 "确定"。
    // 用 .el-button--primary 选择器定位主按钮（确定按钮），不依赖文本，更稳健。
    // 小白理解：ElMessageBox 的"确定"按钮是 primary 类型（蓝色高亮按钮），
    // "取消"按钮是 default 类型（灰色按钮），用 primary class 直接定位主按钮。
    await messageBox.locator('.el-button--primary').click()

    // 等待审核接口被调用（验证点击确定后确实发起了审核请求）
    await auditResponsePromise

    // 等待成功提示出现（ElMessage 成功提示，class 包含 el-message--success）
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
  })
})
