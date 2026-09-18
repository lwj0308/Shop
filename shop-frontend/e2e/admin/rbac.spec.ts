/**
 * F-E-09 RBAC 权限验证 E2E 测试
 * <p>
 * 测试场景：角色管理 → 角色列表 → 新增角色 → 管理员列表 → 权限验证
 * 所有 API 请求通过 page.route() mock，不依赖真实后端服务。
 * </p>
 * <p>
 * 小白理解：RBAC 就是"基于角色的权限控制"。
 * 这个文件测试管理后台的角色管理、管理员管理功能，
 * 以及不同权限的管理员看到的页面内容不同（按钮级权限控制）。
 * </p>
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { mockGet, mockAdminCommonGetApis } from '../helpers/api-mock'

/** 管理后台基础地址（新建 browser context 时需要用完整 URL） */
const ADMIN_BASE_URL = 'http://localhost:3002'

/** 模拟的当前管理员信息（超级管理员，拥有所有权限） */
const mockAdminInfoData = {
  id: 1,
  username: 'admin',
  nickname: '超级管理员',
  avatar: '',
  roles: ['admin'],
  permissions: ['*'],
}

/** 模拟的仪表盘数据 */
const mockDashboardData = {
  todayOrders: 100,
  todaySales: 500000,
  totalUsers: 1000,
  totalMerchants: 50,
}

/** 模拟的角色列表数据（含超级管理员和运营人员两个角色） */
const mockRoleListData = {
  records: [
    {
      id: 1,
      name: '超级管理员',
      roleKey: 'admin',
      sort: 1,
      dataScope: 1,
      status: 1,
      remark: '系统超级管理员',
      createTime: '2026-01-01 10:00:00',
    },
    {
      id: 2,
      name: '运营人员',
      roleKey: 'operator',
      sort: 2,
      dataScope: 2,
      status: 1,
      remark: '运营团队',
      createTime: '2026-02-01 10:00:00',
    },
  ],
  total: 2,
  current: 1,
  size: 10,
}

/** 模拟的所有角色列表（新增/编辑管理员时下拉选择用） */
const mockAllRolesData = [
  { id: 1, name: '超级管理员' },
  { id: 2, name: '运营人员' },
]

/** 模拟的部门树数据（新增/编辑管理员时选择部门用） */
const mockDeptTreeData = [
  {
    id: 1,
    name: '总公司',
    children: [
      { id: 2, name: '技术部' },
      { id: 3, name: '运营部' },
    ],
  },
]

/** 模拟的管理员用户列表数据 */
const mockAdminUserListData = {
  records: [
    {
      id: 1,
      username: 'admin',
      nickname: '超级管理员',
      phone: '13812345678',
      dept: { id: 2, name: '技术部' },
      roles: [{ id: 1, name: '超级管理员' }],
      status: 1,
      createTime: '2026-01-01 10:00:00',
    },
    {
      id: 2,
      username: 'operator01',
      nickname: '运营小王',
      phone: '13987654321',
      dept: { id: 3, name: '运营部' },
      roles: [{ id: 2, name: '运营人员' }],
      status: 1,
      createTime: '2026-02-01 10:00:00',
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
 * 测试中直接把登录信息塞到浏览器的 localStorage 里，
 * 前端就以为我们已经登录了，不用走真正的登录流程。
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

test.describe('F-E-09 RBAC 权限验证', () => {
  // 每个测试前都 mock 管理后台常用接口（按任务要求）
  test.beforeEach(async ({ page }) => {
    await mockAdminCommonGetApis(page)
  })

  /**
   * 测试1：角色管理页应正常加载
   * 小白理解：以管理员身份访问角色管理页，
   * 检查页面标题"角色管理"能正常显示。
   */
  test('角色管理页应正常加载', async ({ page }) => {
    // 设置登录状态
    await mockAdminLoggedIn(page)
    // mock 页面加载需要的 API
    await mockAdminPageApis(page)
    // mock 角色列表接口（页面加载时会自动请求）
    await mockGet(page, '**/admin/role/list**', mockRoleListData)

    // 访问角色管理页
    await page.goto('/system/role')

    // 验证页面标题"角色管理"可见
    await expect(page.getByRole('heading', { name: '角色管理' })).toBeVisible()
  })

  /**
   * 测试2：角色列表应显示已有角色
   * 小白理解：角色管理页的表格应显示 mock 的角色数据，
   * 包括"超级管理员"和"运营人员"。
   */
  test('角色列表应显示已有角色', async ({ page }) => {
    await mockAdminLoggedIn(page)
    await mockAdminPageApis(page)
    // mock 角色列表接口
    await mockGet(page, '**/admin/role/list**', mockRoleListData)

    // 访问角色管理页
    await page.goto('/system/role')

    // 等待表格加载完成，验证角色名称显示在表格中
    // 注意：用 exact: true 精确匹配"超级管理员"，避免匹配到备注"系统超级管理员"
    const tableBody = page.locator('.el-table__body')
    await expect(tableBody.getByText('超级管理员', { exact: true })).toBeVisible()
    await expect(tableBody.getByText('运营人员', { exact: true })).toBeVisible()
  })

  /**
   * 测试3：点击"新增角色"应显示角色表单
   * 小白理解：点击"新增角色"按钮，应弹出包含角色名称、角色标识等表单项的弹窗。
   */
  test('点击新增角色应显示角色表单', async ({ page }) => {
    await mockAdminLoggedIn(page)
    await mockAdminPageApis(page)
    // mock 角色列表接口
    await mockGet(page, '**/admin/role/list**', mockRoleListData)

    // 访问角色管理页
    await page.goto('/system/role')

    // 等待页面加载完成
    await expect(page.getByRole('heading', { name: '角色管理' })).toBeVisible()

    // 点击"新增角色"按钮
    await page.getByRole('button', { name: '新增角色' }).click()

    // 验证弹窗显示（弹窗内有"角色名称"表单项，用它来定位弹窗）
    const dialog = page.locator('.el-dialog').filter({ hasText: '角色名称' })
    await expect(dialog).toBeVisible()

    // 验证弹窗中有"角色名称"输入框（placeholder 是"请输入角色名称"）
    await expect(dialog.getByPlaceholder('请输入角色名称')).toBeVisible()
  })

  /**
   * 测试4：管理员用户列表页应正常加载
   * 小白理解：访问管理员管理页，mock 管理员列表、角色列表、部门树数据，
   * 验证页面正常显示管理员数据（用户名、昵称等）。
   */
  test('管理员用户列表页应正常加载', async ({ page }) => {
    await mockAdminLoggedIn(page)
    await mockAdminPageApis(page)
    // mock 管理员列表接口
    await mockGet(page, '**/admin/user/list**', mockAdminUserListData)
    // mock 所有角色接口（新增/编辑管理员表单需要）
    await mockGet(page, '**/admin/role/all**', mockAllRolesData)
    // mock 部门树接口（新增/编辑管理员表单需要）
    await mockGet(page, '**/admin/dept/tree**', mockDeptTreeData)

    // 访问管理员管理页
    await page.goto('/system/admin-user')

    // 等待表格加载完成，验证管理员数据显示在表格中
    const tableBody = page.locator('.el-table__body')
    await expect(tableBody.getByText('admin')).toBeVisible()
    await expect(tableBody.getByText('运营小王')).toBeVisible()
  })

  /**
   * 测试5：不同角色的管理员应看到不同的菜单项
   * <p>
   * 小白理解：超级管理员（有所有权限）能看到"新增角色"等操作按钮，
   * 普通管理员（无相应权限）看不到这些按钮。
   * 这验证了 v-permission 指令根据权限控制按钮显示是否生效。
   * </p>
   * <p>
   * 实现方式：用两个独立的浏览器 context（各自有独立的 localStorage），
   * 分别模拟超级管理员和普通管理员登录，对比他们看到的按钮差异。
   * </p>
   */
  test('不同角色的管理员应看到不同的菜单项', async ({ page, browser }) => {
    // ========== 场景1：超级管理员（拥有所有权限） ==========
    await mockAdminLoggedIn(page, { permissions: ['*'], roles: ['admin'] })
    await mockAdminPageApis(page)
    await mockGet(page, '**/admin/role/list**', mockRoleListData)

    // 访问角色管理页
    await page.goto('/system/role')
    await expect(page.getByRole('heading', { name: '角色管理' })).toBeVisible()

    // 超级管理员应能看到"新增角色"按钮（有 admin:role:add 权限，v-permission 检查通过）
    await expect(page.getByRole('button', { name: '新增角色' })).toBeVisible()

    // ========== 场景2：普通管理员（只有查看角色权限，没有新增权限） ==========
    // 创建新的浏览器 context（独立的 localStorage，互不影响）
    const context2 = await browser.newContext()
    const page2 = await context2.newPage()

    // 同样 mock 管理后台常用接口
    await mockAdminCommonGetApis(page2)
    // 普通管理员：只有 admin:role:list 权限，没有 admin:role:add 权限
    await mockAdminLoggedIn(page2, { permissions: ['admin:role:list'], roles: ['operator'] })
    await mockAdminPageApis(page2)
    await mockGet(page2, '**/admin/role/list**', mockRoleListData)

    // 访问角色管理页（新 context 没有 baseURL 配置，用完整 URL）
    await page2.goto(`${ADMIN_BASE_URL}/system/role`)
    await expect(page2.getByRole('heading', { name: '角色管理' })).toBeVisible()

    // 普通管理员应看不到"新增角色"按钮（没有 admin:role:add 权限，v-permission 会移除按钮）
    await expect(page2.getByRole('button', { name: '新增角色' })).not.toBeVisible()

    // 清理第二个 context
    await context2.close()
  })
})
