/**
 * F-E-03 秒杀抢购流程 E2E 测试
 * <p>
 * 测试场景：秒杀列表 → 秒杀详情 → 未登录弹窗 → 登录后抢购
 * 所有接口均通过 page.route() mock，不依赖真实后端。
 * 运行前提：已启动用户端 dev server（http://localhost:3000）
 */
import { test, expect } from '@playwright/test'
import {
  mockGet,
  mockPost,
  mockUserCommonGetApis,
  injectAuthToken,
} from '../helpers/api-mock'

/**
 * 秒杀活动列表 mock 数据
 * <p>
 * 小白理解：秒杀列表页用这个数据展示活动卡片。
 * 关键字段说明：
 * - status=1 表示"进行中"（SeckillStatus.ACTIVE）
 * - availableCount>0 表示还有库存，按钮显示"立即抢购"
 * - endTime 设为未来时间，避免活动被判定为已结束
 * </p>
 */
const mockSeckillListData = [
  {
    id: 1,
    merchantId: 0,
    productId: 1,
    skuId: 1,
    seckillPrice: 9900,
    originalPrice: 19900,
    availableCount: 50,
    totalCount: 100,
    limitCount: 1,
    startTime: '2026-07-16 08:00:00',
    endTime: '2026-12-31 23:59:59',
    status: 1,
  },
]

/**
 * 秒杀活动详情 mock 数据
 * <p>
 * 小白理解：秒杀详情页用这个数据展示商品信息和抢购按钮。
 * 关键点：
 * - endTime 必须是未来时间，否则页面判定"活动已结束"按钮变灰
 * - availableCount>0 确保按钮显示"立即抢购"而非"已抢光"
 * - limitCount 是每人限购数量（不是 limitPerUser）
 * </p>
 */
const mockSeckillDetailData = {
  id: 1,
  merchantId: 0,
  productId: 1,
  skuId: 1,
  seckillPrice: 9900,
  originalPrice: 19900,
  availableCount: 50,
  totalCount: 100,
  limitCount: 1,
  startTime: '2026-07-16 08:00:00',
  endTime: '2026-12-31 23:59:59',
  status: 1,
  description: '限时秒杀活动，超低折扣，数量有限，先到先得！',
}

test.describe('F-E-03 秒杀抢购流程', () => {
  /**
   * 每个测试前先 mock 公共 GET 接口
   * 小白理解：页面框架（导航栏等）会请求商品推荐、分类等接口，
   * 提前 mock 好避免页面白屏。
   */
  test.beforeEach(async ({ page }) => {
    await mockUserCommonGetApis(page)
  })

  /**
   * 测试1：秒杀列表页应正常加载
   * 小白理解：访问秒杀列表页，mock 秒杀接口返回数据，
   * 验证页面顶部的"限时秒杀"大标题正确展示。
   */
  test('秒杀列表页应正常加载', async ({ page }) => {
    // mock 秒杀列表接口（公开接口，不需要登录）
    await mockGet(page, '**/seckill/public/list**', mockSeckillListData)

    await page.goto('/seckill')

    // 验证顶部 Banner 的"限时秒杀"标题可见
    await expect(page.locator('.banner-title')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.banner-title')).toHaveText('限时秒杀')
  })

  /**
   * 测试2：秒杀列表应显示秒杀活动卡片
   * 小白理解：mock 秒杀列表接口返回一条活动数据，
   * 验证页面上渲染出了秒杀卡片，且卡片上的抢购按钮显示"立即抢购"。
   */
  test('秒杀列表应显示秒杀活动卡片', async ({ page }) => {
    await mockGet(page, '**/seckill/public/list**', mockSeckillListData)

    await page.goto('/seckill')

    // 验证秒杀卡片可见
    await expect(page.locator('.seckill-card').first()).toBeVisible({ timeout: 10000 })

    // 验证秒杀价格区域可见
    await expect(page.locator('.price-seckill').first()).toBeVisible()

    // 验证抢购按钮显示"立即抢购"（库存大于0时）
    await expect(page.locator('.card-btn').first()).toHaveText('立即抢购')
  })

  /**
   * 测试3：点击秒杀活动应跳转到秒杀详情页
   * 小白理解：在秒杀列表页点击活动卡片，验证URL跳转到了 /seckill/ 开头的详情页。
   */
  test('点击秒杀活动应跳转到秒杀详情页', async ({ page }) => {
    await mockGet(page, '**/seckill/public/list**', mockSeckillListData)
    // mock 秒杀详情接口（点击卡片后会跳转到详情页）
    await mockGet(page, '**/seckill/public/1**', mockSeckillDetailData)

    await page.goto('/seckill')

    // 等待秒杀卡片出现并点击
    await page.locator('.seckill-card').first().click()

    // 验证URL跳转到了秒杀详情页（URL包含 /seckill/ 且后面跟着数字ID）
    await expect(page).toHaveURL(/\/seckill\/\d+/, { timeout: 5000 })
  })

  /**
   * 测试4：秒杀详情页应显示秒杀价格和"立即抢购"按钮
   * 小白理解：直接访问秒杀详情页URL，检查秒杀价格和抢购按钮是否正确展示。
   * 按钮显示"立即抢购"需要：活动未结束 + 库存大于0 + 未在提交中 + 未在排队中。
   */
  test('秒杀详情页应显示秒杀价格和立即抢购按钮', async ({ page }) => {
    await mockGet(page, '**/seckill/public/1**', mockSeckillDetailData)

    await page.goto('/seckill/1')

    // 验证商品名称可见（格式为"商品 #productId"）
    await expect(page.locator('.product-name')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.product-name')).toHaveText('商品 #1')

    // 验证秒杀价格区域可见
    await expect(page.locator('.price-seckill')).toBeVisible()

    // 验证抢购按钮可见且显示"立即抢购"
    await expect(page.locator('.btn-seckill')).toBeVisible()
    await expect(page.locator('.btn-seckill')).toHaveText('立即抢购')
  })

  /**
   * 测试5：点击立即抢购（未登录）应弹出登录弹窗
   * 小白理解：未登录状态下点击"立即抢购"按钮，
   * 前端会检测到未登录，弹出登录弹窗让用户先登录。
   */
  test('点击立即抢购（未登录）应弹出登录弹窗', async ({ page }) => {
    await mockGet(page, '**/seckill/public/1**', mockSeckillDetailData)

    await page.goto('/seckill/1')

    // 等待详情页加载完成
    await expect(page.locator('.btn-seckill')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.btn-seckill')).toHaveText('立即抢购')

    // 点击"立即抢购"按钮（未登录状态下会弹出登录弹窗）
    await page.locator('.btn-seckill').click()

    // 验证登录弹窗出现
    await expect(page.locator('.auth-modal')).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试6：登录后点击立即抢购应创建秒杀订单
   * 小白理解：先注入登录态，mock 抢购接口返回成功，
   * 点击"立即抢购"后验证出现成功提示并跳转到订单列表页。
   */
  test('登录后点击立即抢购应创建秒杀订单', async ({ page }) => {
    // 注入登录态
    await injectAuthToken(page)
    // mock 用户信息接口（已登录后会请求）
    await mockGet(page, '**/user/info**', { id: 1001, phone: '13812345678', nickname: '测试用户' })

    await mockGet(page, '**/seckill/public/1**', mockSeckillDetailData)
    // mock 秒杀抢购接口返回成功（code=200，返回提示字符串）
    // 小白理解：executeSeckill 返回 code=200 表示抢购成功，前端会跳转到订单列表
    await mockPost(page, '**/order/seckill/1**', '抢购成功，正在创建订单')

    await page.goto('/seckill/1')

    // 等待详情页加载完成
    await expect(page.locator('.btn-seckill')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.btn-seckill')).toHaveText('立即抢购')

    // 点击"立即抢购"按钮
    await page.locator('.btn-seckill').click()

    // 验证出现成功提示"抢购成功"
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })

    // 验证跳转到订单列表页（URL包含 /order/list）
    await expect(page).toHaveURL(/\/order\/list/, { timeout: 5000 })
  })
})
