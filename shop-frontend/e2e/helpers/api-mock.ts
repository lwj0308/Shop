/**
 * E2E 测试公共 API Mock 工具
 * <p>
 * 小白理解：E2E 测试需要真实浏览器加载页面，但后端服务可能没启动。
 * 这个工具用 Playwright 的 page.route() 拦截所有 HTTP API 请求，
 * 返回我们准备好的假数据，让前端以为自己在和真实后端通信。
 * </p>
 * <p>
 * 好处：
 * 1. 不依赖真实后端，测试稳定
 * 2. 可以模拟各种场景（成功、失败、空数据）
 * 3. 测试运行速度快
 * </p>
 */
import type { Page, Route } from '@playwright/test'

/**
 * 统一的 API 响应格式（和后端 Result 类对应）
 * 后端所有接口都返回 { code, message, data } 结构
 * code=200 表示成功
 */
export interface MockApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/**
 * 给 URL 模式自动加上 /api 前缀
 * <p>
 * 小白理解：前端所有接口请求都带 /api 前缀（配置在 .env.development 的 VITE_API_BASE_URL=/api）。
 * 如果 mock 的 URL 模式不带 /api，会有两个问题：
 * 1. 拦截不到真正的 API 请求（因为真实请求是 /api/xxx）
 * 2. 更严重的是会拦截页面导航！比如通配符模式 product/1 会拦截 /product/1 页面跳转，
 *    导致页面显示 JSON 文本而不是真正的组件。
 * 所以这个函数把通配符模式自动变成带 /api 前缀，只拦截 API 请求，不拦截页面导航。
 * </p>
 */
function withApiPrefix(urlPattern: string | RegExp): string | RegExp {
  // 正则模式不处理（调用方自己保证带 /api）
  if (urlPattern instanceof RegExp) return urlPattern
  // 已经带 /api 的不再加
  if (urlPattern.includes('/api/')) return urlPattern
  // 把 **/xxx 变成 **/api/xxx
  if (urlPattern.startsWith('**/')) return '**/api/' + urlPattern.slice(3)
  // 其他字符串形式直接加前缀
  return '**/api/' + urlPattern
}

/**
 * 创建一个成功的 mock 响应
 * @param data 响应数据
 * @param message 提示消息
 */
export function successResponse<T>(data: T, message: string = '操作成功'): MockApiResponse<T> {
  return { code: 200, message, data }
}

/**
 * 创建一个失败的 mock 响应
 * @param code 错误码（非200）
 * @param message 错误消息
 */
export function failResponse(code: number = 500, message: string = '操作失败'): MockApiResponse<null> {
  return { code, message, data: null }
}

/**
 * 拦截指定 URL 模式的 GET 请求并返回 mock 数据
 * <p>
 * 小白理解：当前端发 GET 请求到匹配的地址时，不真正发到后端，
 * 而是直接返回我们提供的假数据。
 * </p>
 *
 * @param page Playwright 页面对象
 * @param urlPattern 要拦截的 URL 模式（字符串或正则）
 * @param data 要返回的数据（会自动包装成成功响应）
 */
export async function mockGet<T>(
  page: Page,
  urlPattern: string | RegExp,
  data: T,
): Promise<void> {
  await page.route(withApiPrefix(urlPattern), (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(successResponse(data)),
    })
  })
}

/**
 * 拦截指定 URL 模式的 POST 请求并返回 mock 数据
 */
export async function mockPost<T>(
  page: Page,
  urlPattern: string | RegExp,
  data: T,
): Promise<void> {
  await page.route(withApiPrefix(urlPattern), (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(successResponse(data)),
    })
  })
}

/**
 * 拦截指定 URL 模式的 PUT 请求并返回 mock 数据
 */
export async function mockPut<T>(
  page: Page,
  urlPattern: string | RegExp,
  data: T,
): Promise<void> {
  await page.route(withApiPrefix(urlPattern), (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(successResponse(data)),
    })
  })
}

/**
 * 拦截指定 URL 模式的 DELETE 请求并返回 mock 数据
 */
export async function mockDelete<T>(
  page: Page,
  urlPattern: string | RegExp,
  data: T,
): Promise<void> {
  await page.route(withApiPrefix(urlPattern), (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(successResponse(data)),
    })
  })
}

/**
 * 拦截请求并返回失败响应（用于测试错误场景）
 */
export async function mockError(
  page: Page,
  urlPattern: string | RegExp,
  code: number = 500,
  message: string = '操作失败',
): Promise<void> {
  await page.route(withApiPrefix(urlPattern), (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(failResponse(code, message)),
    })
  })
}

// ==================== 通用 Mock 数据 ====================

/** 模拟的用户信息 */
export const mockUserInfo = {
  id: 1001,
  phone: '13812345678',
  nickname: '测试用户',
  avatar: '',
  email: '',
  gender: 0,
  birthday: '',
  createTime: '2026-01-01 10:00:00',
}

/** 模拟的登录响应（包含 token） */
export const mockLoginResult = {
  accessToken: 'mock-access-token-for-e2e-test-123456',
  refreshToken: 'mock-refresh-token-for-e2e-test-789012',
  expiresIn: 7200,
}

/** 模拟的商品列表（首页推荐用） */
export const mockProductList = [
  {
    id: 1,
    name: '测试商品A - 无线蓝牙耳机',
    mainImage: '/placeholder.svg',
    price: 19900,
    originalPrice: 29900,
    sales: 1200,
    status: 1,
  },
  {
    id: 2,
    name: '测试商品B - 智能手表',
    mainImage: '/placeholder.svg',
    price: 89900,
    originalPrice: 99900,
    sales: 800,
    status: 1,
  },
  {
    id: 3,
    name: '测试商品C - 保温杯',
    mainImage: '/placeholder.svg',
    price: 9900,
    originalPrice: 12900,
    sales: 2300,
    status: 1,
  },
]

/** 模拟的商品详情 */
export const mockProductDetail = {
  id: 1,
  name: '测试商品A - 无线蓝牙耳机',
  mainImage: '/placeholder.svg',
  images: ['/placeholder.svg'],
  price: 19900,
  originalPrice: 29900,
  sales: 1200,
  stock: 100,
  description: '高品质无线蓝牙耳机，降噪效果好，续航持久。',
  categoryId: 1,
  brandId: 1,
  status: 1,
}

/**
 * 模拟的收货地址列表（对应 AddressInfo[] 类型）
 * <p>
 * 小白理解：getAddressList() 返回 AddressInfo[] 数组。
 * 注意字段名是 name（收货人姓名），不是 receiver。
 * isDefault: 0否 1是
 * </p>
 */
export const mockAddressList = [
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

/**
 * 模拟的购物车列表（对应 CartSummary 类型）
 * <p>
 * 小白理解：getCartList() 返回的是 CartSummary 汇总对象，不是直接的数组。
 * 结构是 { items: CartItem[], checkedCount, checkedTotal }。
 * 如果 mock 返回数组，页面的 res.data.items 会拿到 undefined，导致渲染失败。
 * CartItem 的字段：
 * - checked: boolean（不是 selected: number）
 * - skuName: string（不是 skuSpec）
 * - subtotal: number（小计金额，分）
 * </p>
 */
export const mockCartList = {
  items: [
    {
      id: 1,
      userId: 1001,
      productId: 1,
      skuId: 1,
      productName: '测试商品A - 无线蓝牙耳机',
      productImage: '/placeholder.svg',
      skuName: '标准版',
      price: 19900,
      quantity: 2,
      stock: 100,
      checked: true,
      subtotal: 39800,
    },
  ],
  checkedCount: 2,
  checkedTotal: 39800,
}

/** 模拟的订单创建结果 */
export const mockOrderResult = {
  orderNo: 'E2E20260716100001',
  orderId: 10001,
  totalAmount: 39800,
}

/**
 * 模拟的订单列表（PageResult<OrderInfo> 格式）
 * <p>
 * 小白理解：getOrderList() 返回 PageResult<OrderInfo>，结构是 { records, total, current, size }。
 * OrderInfo 类型的关键字段：
 * - items: OrderItem[]（订单项列表，字段名是 items，不是 orderItems！）
 * - address: OrderAddress（收货地址对象，订单详情页会读 order.address.name）
 * - status: 订单状态码（参考 ORDER_STATUS 常量，1=已取消，2=待发货，6=退款中，7=已退款）
 * OrderItem 必须包含 id（订单项ID，评价页用它匹配 orderItemId）、skuName、isReviewed 等字段。
 * </p>
 */
export const mockOrderList = {
  records: [
    {
      id: 10001,
      orderNo: 'E2E20260716100001',
      userId: 1001,
      merchantId: 1,
      merchantName: 'ShopMall旗舰店',
      status: 2, // 2=待发货（已支付），符合 ORDER_STATUS.PENDING_DELIVERY
      totalAmount: 39800,
      payAmount: 39800,
      freightAmount: 0,
      discountAmount: 0,
      remark: '',
      cancelReason: null,
      payTime: '2026-07-16 10:01:00',
      deliveryTime: null,
      receiveTime: null,
      finishTime: null,
      cancelTime: null,
      createTime: '2026-07-16 10:00:00',
      // 订单项列表：字段名必须是 items（不是 orderItems），否则前端 order.items 会是 undefined
      items: [
        {
          id: 1, // 订单项ID，发表评价页用它匹配 route.query.orderItemId
          productId: 1,
          skuId: 1,
          productName: '测试商品A - 无线蓝牙耳机',
          productImage: '/placeholder.svg',
          skuName: '标准版',
          price: 19900,
          quantity: 2,
          subtotal: 39800,
          isReviewed: 0, // 0=未评价，订单详情页据此显示"去评价"按钮
        },
      ],
      // 收货地址：订单详情页会读 order.address.name，缺失会报错
      address: {
        name: '张三',
        phone: '13812345678',
        province: '北京市',
        city: '北京市',
        district: '朝阳区',
        detail: '建国路88号SOHO现代城A座1001室',
      },
    },
  ],
  total: 1,
  current: 1,
  size: 10,
}

/**
 * 模拟的"退款中"订单详情（status=6 REFUNDING）
 * <p>
 * 小白理解：前端没有"申请退款"按钮，但能展示退款状态。
 * 订单详情页对 status=6 的订单会显示"退款中"标签、退款状态描述，并不显示进度条。
 * </p>
 */
export const mockRefundOrderDetail = {
  ...mockOrderList.records[0],
  id: 10001,
  status: 6, // 6=REFUNDING 退款中
  statusDesc: '退款中',
}

/**
 * 模拟的"已退款"订单详情（status=7 REFUNDED）
 * <p>
 * 小白理解：status=7 表示退款已完成，订单详情页会显示"已退款"标签。
 * </p>
 */
export const mockRefundedOrderDetail = {
  ...mockOrderList.records[0],
  id: 10001,
  status: 7, // 7=REFUNDED 已退款
  statusDesc: '已退款',
}

/** 模拟的秒杀活动列表 */
export const mockSeckillList = [
  {
    id: 1,
    productId: 1,
    productName: '测试商品A - 无线蓝牙耳机',
    productImage: '/placeholder.svg',
    seckillPrice: 9900,
    originalPrice: 19900,
    availableCount: 50,
    totalCount: 100,
    startTime: '2026-07-16 08:00:00',
    endTime: '2026-07-16 23:59:59',
    status: 1,
  },
]

/** 模拟的秒杀活动详情 */
export const mockSeckillDetail = {
  id: 1,
  productId: 1,
  productName: '测试商品A - 无线蓝牙耳机',
  productImage: '/placeholder.svg',
  seckillPrice: 9900,
  originalPrice: 19900,
  availableCount: 50,
  totalCount: 100,
  startTime: '2026-07-16 08:00:00',
  endTime: '2026-07-16 23:59:59',
  status: 1,
  limitPerUser: 1,
}

/**
 * 模拟的优惠券列表（领券中心用，对应 CouponInfo 类型）
 * <p>
 * 小白理解：领券中心页面调用 getReceivableCouponList()，返回 CouponInfo[] 数组。
 * CouponInfo 字段较多，这里补全了页面渲染需要的关键字段：
 * - typeDesc: 类型描述，页面显示"满减"等文字
 * - threshold: 使用门槛（满减用）
 * - receiveStartTime/receiveEndTime: 领取时间窗口
 * - validStartTime/validEndTime: 使用时间窗口
 * </p>
 */
export const mockCouponList = [
  {
    id: 1,
    merchantId: 0,
    name: '新人满100减20',
    type: 1,
    typeDesc: '满减',
    amount: 20,
    threshold: 100,
    totalCount: 1000,
    receivedCount: 120,
    usedCount: 80,
    perLimit: 1,
    receiveStartTime: '2026-07-01 00:00:00',
    receiveEndTime: '2026-07-31 23:59:59',
    validStartTime: '2026-07-01 00:00:00',
    validEndTime: '2026-08-31 23:59:59',
    status: 1,
    statusDesc: '进行中',
    remainCount: 880,
  },
]

/**
 * 模拟的"我的优惠券"列表（对应 PageResult<UserCouponInfo> 类型）
 * <p>
 * 小白理解：getMyCoupons() 返回的是分页结果 PageResult<UserCouponInfo>，
 * 结构是 { records: [...], total, current, size }，不是直接的数组。
 * 如果 mock 返回数组，页面的 res.data?.records 会拿到 undefined，显示"暂无优惠券"。
 * UserCouponInfo 和 CouponInfo 字段名不同（couponName vs name，couponType vs type），
 * 必须用 UserCouponInfo 的字段名，否则页面渲染会取不到值。
 * </p>
 */
export const mockMyCouponList = {
  records: [
    {
      id: 1,
      userId: 1001,
      couponId: 1,
      merchantId: 0,
      couponName: '新人满100减20',
      couponType: 1,
      couponTypeDesc: '满减',
      amount: 20,
      threshold: 100,
      validStartTime: '2026-07-01 00:00:00',
      validEndTime: '2026-08-31 23:59:59',
      status: 0,
      statusDesc: '未使用',
      getTime: '2026-07-16 10:00:00',
    },
  ],
  total: 1,
  current: 1,
  size: 10,
}

/**
 * 模拟的可用优惠券（下单时使用，对应 UserCouponInfo[] 类型）
 * <p>
 * 小白理解：getUsableCoupons() 返回 UserCouponInfo[] 数组，
 * 字段名和 UserCouponInfo 一致（couponName、couponType 等）。
 * </p>
 */
export const mockAvailableCoupons = [
  {
    id: 1,
    userId: 1001,
    couponId: 1,
    merchantId: 0,
    couponName: '新人满100减20',
    couponType: 1,
    couponTypeDesc: '满减',
    amount: 20,
    threshold: 100,
    validStartTime: '2026-07-01 00:00:00',
    validEndTime: '2026-08-31 23:59:59',
    status: 0,
    statusDesc: '未使用',
    getTime: '2026-07-16 10:00:00',
    discountAmount: 2000,
  },
]

/** 模拟的支付结果 */
export const mockPaymentResult = {
  paymentNo: 'PAY20260716100001',
  payUrl: 'about:blank',
}

/** 模拟的评论列表 */
export const mockCommentList = {
  records: [
    {
      id: 1,
      productId: 1,
      userId: 1001,
      userNickname: '测试用户',
      userAvatar: '',
      score: 5,
      content: '商品质量很好，物流也快！',
      images: [],
      isAnonymous: 0,
      commentType: 0,
      createTime: '2026-07-15 10:00:00',
      replyList: [],
    },
  ],
  total: 1,
  current: 1,
  size: 10,
}

/** 模拟的分类列表 */
export const mockCategoryList = [
  { id: 1, name: '数码电子', icon: 'Cellphone', keyword: 'digital' },
  { id: 2, name: '家居生活', icon: 'House', keyword: 'home' },
  { id: 3, name: '服饰鞋包', icon: 'ShoppingBag', keyword: 'fashion' },
]

/** 模拟的通知列表 */
export const mockNotificationList = {
  records: [
    {
      id: 1,
      title: '订单发货通知',
      content: '您的订单已发货，请注意查收。',
      type: 1,
      isRead: 0,
      createTime: '2026-07-16 09:00:00',
    },
  ],
  total: 1,
  current: 1,
  size: 10,
}

/**
 * 一键 mock 用户端所有常用 GET 接口
 * <p>
 * 小白理解：用户端首页打开时会请求多个接口（商品、分类等），
 * 这个方法一次性把所有常用的 GET 接口都 mock 好，
 * 这样页面打开就不会因为接口报错而白屏。
 * </p>
 */
export async function mockUserCommonGetApis(page: Page): Promise<void> {
  // 首页推荐商品（猜你喜欢）
  await mockGet(page, '**/product/recommend/guess**', mockProductList)
  // 热门商品
  await mockGet(page, '**/product/recommend/hot**', mockProductList)
  // 新品
  await mockGet(page, '**/product/recommend/new**', mockProductList)
  // 相关商品
  await mockGet(page, '**/product/recommend/related**', mockProductList)
  // 分类列表（实际接口路径是 /product/category/tree）
  await mockGet(page, '**/product/category/tree**', mockCategoryList)
  // 分类树（实际接口路径是 /product/category/tree）
  await mockGet(page, '**/product/category/tree**', mockCategoryList)
  // 通知未读数（实际接口路径是 /user/notification/unread-count）
  await mockGet(page, '**/user/notification/unread-count**', { count: 0 })
  // 购物车数量（DefaultLayout onMounted 时调用，未 mock 会请求失败触发 401 处理）
  await mockGet(page, '**/cart/count**', 0)
  // 热门搜索词（DefaultLayout onMounted 时调用，未 mock 会请求失败）
  await mockGet(page, '**/product/hot-keywords**', ['手机', '电脑', '耳机'])
}

/**
 * 一键 mock 商家端所有常用 GET 接口
 */
export async function mockMerchantCommonGetApis(page: Page): Promise<void> {
  await mockGet(page, '**/merchant/info**', {
    id: 1,
    shopName: '测试店铺',
    status: 1,
    contactPhone: '13812345678',
  })
  await mockGet(page, '**/merchant/stats/dashboard**', {
    todayOrders: 10,
    todaySales: 50000,
    totalProducts: 50,
  })
}

/**
 * 一键 mock 管理后台所有常用 GET 接口
 */
export async function mockAdminCommonGetApis(page: Page): Promise<void> {
  await mockGet(page, '**/admin/user/current**', {
    id: 1,
    username: 'admin',
    nickname: '超级管理员',
    avatar: '',
    roles: ['admin'],
    permissions: ['*'],
  })
  await mockGet(page, '**/admin/dashboard/overview**', {
    todayOrders: 100,
    todaySales: 500000,
    totalUsers: 1000,
    totalMerchants: 50,
  })
  // 通知未读数（HeaderBar onMounted 时调用，未 mock 会触发 401 → redirectToLogin）
  await mockGet(page, '**/admin/notification/unread-count**', { count: 0 })
}

/**
 * 注入登录态到 localStorage
 * <p>
 * 小白理解：很多页面（购物车、订单、优惠券、评价等）需要登录才能访问，
 * 路由守卫会检查 localStorage 里有没有 token，没有就弹出登录弹窗拦截访问。
 * 这个函数在页面加载前往 localStorage 写入一个 Base64 编码的假 token，
 * 让前端以为用户已登录，这样就不会弹登录弹窗了。
 * </p>
 * <p>
 * 编码方式必须和前端 shared/utils/auth.ts 的 encode 函数保持一致：
 * btoa(encodeURIComponent(token))，否则 getToken() 解码会失败。
 * </p>
 *
 * @param page Playwright 页面对象
 */
export async function injectAuthToken(page: Page): Promise<void> {
  await page.addInitScript(() => {
    // 和前端 auth.ts 的 encode 函数保持一致：btoa(encodeURIComponent(str))
    localStorage.setItem('shop_access_token', btoa(encodeURIComponent('mock-access-token-for-e2e')))
    localStorage.setItem('shop_refresh_token', btoa(encodeURIComponent('mock-refresh-token-for-e2e')))
  })
}
