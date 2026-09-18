/**
 * F-E-10 订单退款流程 E2E 测试（用户端退款状态展示部分）
 * <p>
 * 测试场景：订单列表展示退款中/已退款订单 → 订单详情页显示退款状态 → 退款状态订单不显示进度条。
 * 本文件只测试用户端的退款状态展示，前端没有"申请退款"按钮（退款由后端流程触发），
 * 商家审核部分不在本测试范围内。
 * 由于后端服务可能未启动，所有测试都通过 page.route() mock 接口数据。
 * 运行前需要先启动用户端 dev server（npm run dev:user），默认地址 http://localhost:3000。
 * </p>
 */
import { test, expect } from '@playwright/test'
import {
  mockGet,
  mockUserCommonGetApis,
  mockOrderList,
  mockRefundOrderDetail,
  mockRefundedOrderDetail,
  injectAuthToken,
  mockUserInfo,
} from '../helpers/api-mock'

/**
 * 含"退款中"状态订单的订单列表 mock 数据
 * 小白理解：mockOrderList 默认 status=2（待发货），
 * 这里改成 status=6（退款中），订单列表页会显示"退款中"标签。
 */
const mockRefundingOrderList = {
  records: [
    {
      ...mockOrderList.records[0],
      status: 6, // 6=REFUNDING 退款中
    },
  ],
  total: 1,
  current: 1,
  size: 10,
}

/**
 * 含"已退款"状态订单的订单列表 mock 数据
 * 小白理解：status=7（已退款），订单列表页会显示"已退款"标签。
 */
const mockRefundedOrderList = {
  records: [
    {
      ...mockOrderList.records[0],
      status: 7, // 7=REFUNDED 已退款
    },
  ],
  total: 1,
  current: 1,
  size: 10,
}

test.describe('F-E-10 订单退款流程', () => {
  /**
   * 每个测试前先注入登录态并 mock 用户信息接口
   * <p>
   * 小白理解：订单列表 /order/list 和 订单详情 /order/:id 页面都配置了
   * 路由 meta.requiresAuth=true，路由守卫会检查 localStorage 中的 token，
   * 没有就会取消导航并弹出登录弹窗，导致 body 不可见、测试失败。
   * 所以这里在每次测试前先用公共 injectAuthToken 注入 Base64 编码的假 token，
   * 同时 mock /user/info 接口，让前端以为用户已登录。
   * </p>
   */
  test.beforeEach(async ({ page }) => {
    await mockUserCommonGetApis(page)
    await injectAuthToken(page)
    await mockGet(page, '**/user/info**', mockUserInfo)
  })

  /**
   * 测试1：订单列表页应正常加载
   * 小白理解：打开 /order/list 页面，验证 body 可见，说明页面加载成功。
   */
  test('订单列表页应正常加载', async ({ page }) => {
    // beforeEach 已注入登录态并 mock 公共接口，这里直接跳转
    await page.goto('/order/list')
    await page.waitForTimeout(500)
    await expect(page.locator('body')).toBeVisible()
  })

  /**
   * 测试2：订单列表应显示订单
   * 小白理解：mock GET /order/list 返回待发货订单列表（status=2），
   * 打开订单列表后，应能看到订单中的商品名称。
   */
  test('订单列表应显示订单', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock 订单列表接口，返回待发货状态的订单
    await mockGet(page, '**/order/list**', mockOrderList)
    await page.goto('/order/list')
    await page.waitForTimeout(500)
    // 验证订单中第一件商品名称在页面上显示
    await expect(page.getByText('测试商品A - 无线蓝牙耳机').first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试3：退款中订单在列表应显示"退款中"状态标签
   * 小白理解：mock 订单列表返回退款中订单（status=6），
   * 订单卡片右上角应显示"退款中"文字标签（红色）。
   */
  test('退款中订单在列表应显示"退款中"状态标签', async ({ page }) => {
    await mockUserCommonGetApis(page)
    await mockGet(page, '**/order/list**', mockRefundingOrderList)
    await page.goto('/order/list')
    await page.waitForTimeout(500)
    // 验证"退款中"状态标签可见
    await expect(page.getByText('退款中').first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试4：已退款订单在列表应显示"已退款"状态标签
   * 小白理解：mock 订单列表返回已退款订单（status=7），
   * 订单卡片右上角应显示"已退款"文字标签（灰色）。
   */
  test('已退款订单在列表应显示"已退款"状态标签', async ({ page }) => {
    await mockUserCommonGetApis(page)
    await mockGet(page, '**/order/list**', mockRefundedOrderList)
    await page.goto('/order/list')
    await page.waitForTimeout(500)
    // 验证"已退款"状态标签可见
    await expect(page.getByText('已退款').first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试5：点击订单卡片应跳转到订单详情页
   * 小白理解：在订单列表点击订单商品区域（order-body），
   * 应跳转到 /order/:id 详情页。
   */
  test('点击订单卡片应跳转到订单详情页', async ({ page }) => {
    await mockUserCommonGetApis(page)
    await mockGet(page, '**/order/list**', mockOrderList)
    // mock 订单详情接口，返回订单详情数据
    await mockGet(page, '**/order/10001**', mockOrderList.records[0])
    await page.goto('/order/list')
    await page.waitForTimeout(500)
    // 点击订单商品区域（order-body 类）跳转到详情页
    await page.locator('.order-body').first().click()
    await page.waitForTimeout(500)
    // 验证URL跳转到了订单详情页（/order/10001）
    await expect(page).toHaveURL(/\/order\/10001/, { timeout: 5000 })
  })

  /**
   * 测试6：退款中订单详情页应显示退款状态描述
   * 小白理解：mock 订单详情接口返回退款中订单（status=6），
   * 订单详情页顶部应显示"退款中"状态和"退款申请处理中"描述。
   */
  test('退款中订单详情页应显示退款状态描述', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock 订单详情接口，返回退款中订单
    await mockGet(page, '**/order/10001**', mockRefundOrderDetail)
    // 注意：路由是 /order/:id，不是 /order/detail/:id
    await page.goto('/order/10001')
    await page.waitForTimeout(500)
    // 验证"退款中"状态文字可见
    await expect(page.getByText('退款中').first()).toBeVisible({ timeout: 5000 })
    // 验证退款状态描述可见（DetailView 中 statusDesc 返回"退款申请处理中，请耐心等待"）
    await expect(page.getByText(/退款申请处理中|退款状态|退款审核中/).first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试7：退款状态订单详情页不显示进度条
   * 小白理解：DetailView.vue 中 showProgress 计算属性对 REFUNDING(6) 和 REFUNDED(7) 状态返回 false，
   * 所以退款中和已退款订单详情页不显示进度条（progress-bar 元素不存在）。
   */
  test('退款状态订单详情页不显示进度条', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // 使用退款中订单详情
    await mockGet(page, '**/order/10001**', mockRefundOrderDetail)
    await page.goto('/order/10001')
    await page.waitForTimeout(500)
    // 验证进度条元素不存在（status=6 时 showProgress=false）
    await expect(page.locator('.progress-bar')).toHaveCount(0)
  })
})
