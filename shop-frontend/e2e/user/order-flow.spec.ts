/**
 * F-E-02 商品浏览下单全流程 E2E 测试
 * <p>
 * 测试场景：首页 → 商品详情 → 加购物车 → 购物车 → 确认订单 → 提交订单 → 支付页
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
 * 带规格和SKU的完整商品详情 mock 数据
 * <p>
 * 小白理解：商品详情页的"加入购物车"按钮需要选中SKU才能点击，
 * 所以 mock 数据必须包含 specs（规格选项）和 skus（具体SKU），
 * 页面加载后会自动选中第一个规格值，按钮才能变成可点击状态。
 * </p>
 */
const mockProductDetailFull = {
  id: 1,
  name: '测试商品A - 无线蓝牙耳机',
  mainImage: '/placeholder.svg',
  images: ['/placeholder.svg'],
  minPrice: 199,
  originalPrice: 299,
  totalStock: 100,
  sales: 1200,
  description: '高品质无线蓝牙耳机，降噪效果好，续航持久。',
  categoryId: 1,
  brandId: 1,
  status: 1,
  // 规格列表：用户可以从每个规格里选一个值
  specs: [
    { name: '颜色', values: ['黑色', '白色'] },
  ],
  // SKU列表：每个SKU对应一组规格组合，有独立的价格和库存
  skus: [
    {
      id: 1,
      specValues: { '颜色': '黑色' },
      price: 199,
      originalPrice: 299,
      stock: 50,
    },
    {
      id: 2,
      specValues: { '颜色': '白色' },
      price: 209,
      originalPrice: 299,
      stock: 50,
    },
  ],
}

/**
 * 购物车列表 mock 数据（CartSummary 格式）
 * <p>
 * 小白理解：购物车接口返回的不只是一个商品数组，
 * 而是一个包含 items（商品列表）、checkedCount（已选数量）、checkedTotal（已选总金额）的对象。
 * item 里的 checked 字段表示是否勾选，只有勾选的商品才能结算。
 * </p>
 */
const mockCartSummary = {
  items: [
    {
      id: 1,
      productId: 1,
      productName: '测试商品A - 无线蓝牙耳机',
      productImage: '/placeholder.svg',
      price: 19900,
      quantity: 2,
      skuId: 1,
      skuName: '黑色',
      subtotal: 39800,
      checked: true,
    },
  ],
  checkedCount: 2,
  checkedTotal: 39800,
}

/**
 * 收货地址列表 mock 数据
 * <p>
 * 小白理解：注意这里用的是 name 字段（不是 receiver），
 * 因为前端的 AddressInfo 类型定义的字段名是 name。
 * isDefault=1 表示是默认地址，页面会自动选中默认地址。
 * </p>
 */
const mockAddressListData = [
  {
    id: 1,
    name: '张三',
    phone: '13812345678',
    province: '北京市',
    city: '北京市',
    district: '朝阳区',
    detail: '建国路88号SOHO现代城A座1001室',
    isDefault: 1,
  },
]

test.describe('F-E-02 商品浏览下单全流程', () => {
  /**
   * 每个测试前先 mock 公共 GET 接口
   * 小白理解：首页和详情页都会请求商品推荐、分类等接口，
   * 提前 mock 好这些接口，页面才不会白屏。
   */
  test.beforeEach(async ({ page }) => {
    await mockUserCommonGetApis(page)
  })

  /**
   * 测试1：首页应显示精选商品列表
   * 小白理解：打开首页，mock 商品推荐接口返回商品数据，
   * 检查页面上的商品卡片是否正确渲染出来。
   */
  test('首页应显示精选商品列表', async ({ page }) => {
    // mockUserCommonGetApis 已经 mock 了 /product/recommend/guess 等接口
    await page.goto('/')

    // 等待商品网格区域出现（首页的精选好物区域）
    await expect(page.locator('.products-grid')).toBeVisible({ timeout: 10000 })

    // 验证商品卡片数量大于0（至少有一张商品卡片）
    const cards = page.locator('.product-card')
    await expect(cards.first()).toBeVisible({ timeout: 10000 })
    const count = await cards.count()
    expect(count).toBeGreaterThan(0)
  })

  /**
   * 测试2：点击商品卡片应跳转到商品详情页
   * 小白理解：在首页点击第一个商品卡片，验证浏览器URL跳转到了 /product/ 开头的详情页。
   */
  test('点击商品卡片应跳转到商品详情页', async ({ page }) => {
    // mock 商品详情接口，返回带规格的完整商品数据
    await mockGet(page, '**/product/1', mockProductDetailFull)
    // mock 评论列表接口（详情页会请求评论）
    await mockGet(page, '**/product/comment/list**', { records: [], total: 0, current: 1, size: 5 })

    await page.goto('/')

    // 等待商品卡片出现并点击第一张
    await page.locator('.product-card').first().click()

    // 验证URL跳转到了商品详情页（URL包含 /product/）
    await expect(page).toHaveURL(/\/product\//, { timeout: 5000 })
  })

  /**
   * 测试3：商品详情页应显示商品名称、价格、加入购物车按钮
   * 小白理解：直接访问商品详情页URL，检查页面上的关键元素是否都展示了。
   */
  test('商品详情页应显示商品名称、价格、加入购物车按钮', async ({ page }) => {
    await mockGet(page, '**/product/1', mockProductDetailFull)
    await mockGet(page, '**/product/comment/list**', { records: [], total: 0, current: 1, size: 5 })

    await page.goto('/product/1')

    // 验证商品名称可见
    await expect(page.locator('.detail-name')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.detail-name')).toHaveText('测试商品A - 无线蓝牙耳机')

    // 验证当前价格可见
    await expect(page.locator('.current-price')).toBeVisible()

    // 验证"加入购物车"按钮可见
    await expect(page.locator('.btn-cart')).toBeVisible()
    await expect(page.locator('.btn-cart')).toHaveText('加入购物车')
  })

  /**
   * 测试4：点击加入购物车应显示成功提示
   * 小白理解：在商品详情页点击"加入购物车"按钮，
   * mock 加购接口返回成功，验证页面出现"已加入购物车"的成功提示。
   * 注意：需要先注入登录态，因为加购需要登录。
   */
  test('点击加入购物车应显示成功提示', async ({ page }) => {
    // 注入登录态（加购操作需要登录）
    await injectAuthToken(page)
    // mock 用户信息接口（已登录后会请求）
    await mockGet(page, '**/user/info**', { id: 1001, phone: '13812345678', nickname: '测试用户' })

    await mockGet(page, '**/product/1', mockProductDetailFull)
    await mockGet(page, '**/product/comment/list**', { records: [], total: 0, current: 1, size: 5 })
    // mock 加购接口返回成功
    await mockPost(page, '**/cart/add', { id: 1, productId: 1, skuId: 1, quantity: 1 })
    // mock 购物车数量接口（加购成功后会刷新角标数量）
    await mockGet(page, '**/cart/count**', 1)

    await page.goto('/product/1')

    // 等待详情页加载完成（商品名称出现表示加载完毕）
    await expect(page.locator('.detail-name')).toBeVisible({ timeout: 10000 })
    // 等待一下让 initDefaultSelection 自动选中规格（默认选中第一个规格值）
    await page.waitForTimeout(500)

    // 点击"加入购物车"按钮
    await page.locator('.btn-cart').click()

    // 验证出现成功提示"已加入购物车"
    await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试5：购物车页面应显示商品列表
   * 小白理解：访问购物车页，mock 购物车接口返回商品数据，
   * 验证购物车页面正确展示了商品项。
   * 注意：购物车页需要登录，所以要先注入登录态。
   */
  test('购物车页面应显示商品列表', async ({ page }) => {
    // 注入登录态
    await injectAuthToken(page)
    await mockGet(page, '**/user/info**', { id: 1001, phone: '13812345678', nickname: '测试用户' })
    // mock 购物车列表接口，返回 CartSummary 格式数据
    await mockGet(page, '**/cart/list**', mockCartSummary)
    // mock 购物车数量接口
    await mockGet(page, '**/cart/count**', mockCartSummary.checkedCount)

    await page.goto('/cart')

    // 验证页面标题"购物车"可见
    await expect(page.locator('.page-title')).toHaveText('购物车', { timeout: 10000 })

    // 验证购物车中至少有一个商品项
    await expect(page.locator('.cart-item').first()).toBeVisible({ timeout: 10000 })

    // 验证商品名称正确展示
    await expect(page.locator('.item-name').first()).toHaveText('测试商品A - 无线蓝牙耳机')
  })

  /**
   * 测试6：购物车结算按钮应跳转到确认订单页
   * 小白理解：在购物车页点击"去结算"按钮，验证URL跳转到了确认订单页。
   * 注意：结算按钮在有勾选商品时才可点击，mockCartSummary 里的商品 checked=true。
   */
  test('购物车结算按钮应跳转到确认订单页', async ({ page }) => {
    await injectAuthToken(page)
    await mockGet(page, '**/user/info**', { id: 1001, phone: '13812345678', nickname: '测试用户' })
    await mockGet(page, '**/cart/list**', mockCartSummary)
    await mockGet(page, '**/cart/count**', mockCartSummary.checkedCount)

    await page.goto('/cart')

    // 等待购物车列表加载完成
    await expect(page.locator('.cart-item').first()).toBeVisible({ timeout: 10000 })

    // 点击"去结算"按钮
    await page.locator('.checkout-btn').click()

    // 验证URL跳转到了确认订单页
    await expect(page).toHaveURL(/\/order\/confirm/, { timeout: 5000 })
  })

  /**
   * 测试7：确认订单页应显示收货地址和商品清单
   * 小白理解：访问确认订单页，mock 购物车、地址、优惠券接口，
   * 验证页面同时展示了收货地址卡片和商品清单。
   */
  test('确认订单页应显示收货地址和商品清单', async ({ page }) => {
    await injectAuthToken(page)
    await mockGet(page, '**/user/info**', { id: 1001, phone: '13812345678', nickname: '测试用户' })
    // mock 购物车列表（确认订单页会重新获取已勾选的商品）
    await mockGet(page, '**/cart/list**', mockCartSummary)
    await mockGet(page, '**/cart/count**', mockCartSummary.checkedCount)
    // mock 收货地址列表
    await mockGet(page, '**/user/address/list**', mockAddressListData)
    // mock 可用优惠券（返回空数组，表示没有可用券）
    await mockGet(page, '**/user/coupon/usable**', [])

    await page.goto('/order/confirm')

    // 验证页面标题"确认订单"可见
    await expect(page.locator('.page-title')).toHaveText('确认订单', { timeout: 10000 })

    // 验证收货地址卡片可见
    await expect(page.locator('.address-card')).toBeVisible()
    // 验证地址中有收货人姓名"张三"
    await expect(page.locator('.address-card .user-name')).toHaveText('张三')

    // 验证商品清单可见
    await expect(page.locator('.goods-card')).toBeVisible()
    // 验证商品清单中有商品名称
    await expect(page.locator('.goods-name').first()).toHaveText('测试商品A - 无线蓝牙耳机')
  })

  /**
   * 测试8：提交订单应跳转到支付页
   * 小白理解：在确认订单页点击"提交订单"按钮，
   * mock 创建订单接口返回订单号，验证URL跳转到了支付页。
   */
  test('提交订单应跳转到支付页', async ({ page }) => {
    await injectAuthToken(page)
    await mockGet(page, '**/user/info**', { id: 1001, phone: '13812345678', nickname: '测试用户' })
    await mockGet(page, '**/cart/list**', mockCartSummary)
    await mockGet(page, '**/cart/count**', mockCartSummary.checkedCount)
    await mockGet(page, '**/user/address/list**', mockAddressListData)
    await mockGet(page, '**/user/coupon/usable**', [])
    // mock 创建订单接口，返回订单号
    await mockPost(page, '**/order', { orderNo: 'E2E20260716100001', orderId: 10001, totalAmount: 39800 })

    await page.goto('/order/confirm')

    // 等待页面加载完成（确认订单标题出现表示加载完毕）
    await expect(page.locator('.page-title')).toHaveText('确认订单', { timeout: 10000 })

    // 等待地址和商品加载完成
    await expect(page.locator('.address-card')).toBeVisible()
    await expect(page.locator('.goods-card')).toBeVisible()

    // 点击"提交订单"按钮
    await page.locator('.submit-btn').click()

    // 验证URL跳转到了支付页（URL包含 /payment/pay）
    await expect(page).toHaveURL(/\/payment\/pay/, { timeout: 5000 })
  })
})
