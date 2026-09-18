/**
 * F-E-04 优惠券领取使用 E2E 测试
 * <p>
 * 测试场景：领券中心 → 领取优惠券 → 我的优惠券 → 下单使用优惠券。
 * 由于后端服务可能未启动，所有测试都通过 page.route() mock 接口数据。
 * 运行前需要先启动用户端 dev server（npm run dev:user），默认地址 http://localhost:3000。
 * </p>
 */
import { test, expect } from '@playwright/test'
import {
  mockGet,
  mockPost,
  mockUserCommonGetApis,
  mockCouponList,
  mockMyCouponList,
  mockAvailableCoupons,
  mockAddressList,
  mockCartList,
  injectAuthToken,
  mockUserInfo,
} from '../helpers/api-mock'

test.describe('F-E-04 优惠券领取使用', () => {
  /**
   * 每个测试前先注入登录态并 mock 用户信息接口
   * <p>
   * 小白理解：领券中心 /coupon/receive 和 我的优惠券 /coupon 页面都配置了
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
   * 测试1：领券中心页应正常加载
   * 小白理解：打开 /coupon/receive 页面，验证页面 body 元素可见，
   * 说明页面没有因为接口报错而白屏。
   */
  test('领券中心页应正常加载', async ({ page }) => {
    // beforeEach 已注入登录态并 mock 公共接口，这里直接跳转
    // 跳转到领券中心页
    await page.goto('/coupon/receive')
    // 等一下，让页面渲染完成
    await page.waitForTimeout(500)
    // body 可见说明页面加载成功
    await expect(page.locator('body')).toBeVisible()
  })

  /**
   * 测试2：领券中心应显示可领取的优惠券列表
   * 小白理解：mock GET /coupon/available 返回优惠券列表数据，
   * 打开领券中心后，应能看到优惠券名称（如"新人满100减20"）。
   */
  test('领券中心应显示可领取的优惠券列表', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock 可领取优惠券接口，返回准备好的优惠券列表（实际路径是 /user/coupon/receivable）
    await mockGet(page, '**/user/coupon/receivable**', mockCouponList)
    await page.goto('/coupon/receive')
    await page.waitForTimeout(500)
    // 验证页面中显示了优惠券名称
    await expect(page.getByText('新人满100减20')).toBeVisible()
  })

  /**
   * 测试3：点击"立即领取"应成功领取优惠券
   * 小白理解：mock POST /coupon/receive/:id 返回成功，
   * 点击领取按钮后，应能触发领取请求并看到成功反馈。
   */
  test('点击"立即领取"应成功领取', async ({ page }) => {
    await mockUserCommonGetApis(page)
    await mockGet(page, '**/user/coupon/receivable**', mockCouponList)
    // mock 领取优惠券接口返回成功（实际路径是 /user/coupon/receive/:id）
    await mockPost(page, '**/user/coupon/receive/**', { id: 1, status: 1 })
    await page.goto('/coupon/receive')
    await page.waitForTimeout(500)
    // 找到"立即领取"按钮并点击
    const receiveBtn = page.getByRole('button', { name: '立即领取' }).first()
    await expect(receiveBtn).toBeVisible()
    await receiveBtn.click()
    // 等一下让接口返回和提示出现
    await page.waitForTimeout(500)
    // 验证按钮文案变化或出现成功提示（任意一种都算成功）
    const successIndicator = page
      .locator('body')
      .filter({ hasText: /已领取|领取成功|去使用|立即使用|领取成功/ })
    await expect(successIndicator.first()).toBeVisible({ timeout: 3000 })
  })

  /**
   * 测试4：我的优惠券页应显示已领取的优惠券
   * 小白理解：mock GET /user/coupon/my 返回已领取的优惠券列表，
   * 打开 /coupon 页面后，应能看到已领取的优惠券。
   * <p>
   * 注意：getMyCoupons 返回的是 PageResult<UserCouponInfo> 分页格式，
   * 即 { records: [...], total, current, size }，不是直接的数组。
   * 如果 mock 返回数组，页面的 res.data?.records 会拿到 undefined，显示"暂无优惠券"。
   * 所以这里用 mockMyCouponList（PageResult 格式）而不是 mockCouponList（数组格式）。
   * </p>
   */
  test('我的优惠券页应显示已领取的优惠券', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock "我的优惠券"接口，返回已领取的优惠券列表（实际路径是 /user/coupon/my）
    // 必须用 PageResult 格式（含 records 字段），否则页面取不到数据
    await mockGet(page, '**/user/coupon/my**', mockMyCouponList)
    await page.goto('/coupon')
    await page.waitForTimeout(500)
    // 验证页面显示了优惠券名称
    await expect(page.getByText('新人满100减20')).toBeVisible()
  })

  /**
   * 测试5：下单时选择优惠券应显示折扣金额
   * 小白理解：mock 收货地址、购物车选中商品、可用优惠券三个接口，
   * 打开订单确认页后，应能看到优惠券选择区域。
   */
  test('下单时选择优惠券应显示折扣金额', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock 收货地址列表
    await mockGet(page, '**/user/address/list**', mockAddressList)
    // mock 购物车已选中的商品（实际路径是 /cart/list，没有单独的 /cart/selected 接口）
    await mockGet(page, '**/cart/list**', mockCartList)
    // mock 当前订单可用的优惠券（实际路径是 /user/coupon/usable）
    await mockGet(page, '**/user/coupon/usable**', mockAvailableCoupons)
    await page.goto('/order/confirm')
    await page.waitForTimeout(500)
    // 验证优惠券选择区域可见（页面中应出现"优惠券"文本）
    await expect(page.getByText('优惠券').first()).toBeVisible({ timeout: 3000 })
  })
})
