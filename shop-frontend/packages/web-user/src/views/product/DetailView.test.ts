/**
 * DetailView 商品详情页组件测试
 * <p>
 * 验证商品详情页的渲染、SKU选择、数量调整、加购/购买/收藏交互、
 * 评价列表展示与筛选、相关推荐等核心功能。
 * </p>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import DetailView from './DetailView.vue'

// ==================== mock 外部依赖 ====================

// mock vue-router —— 提供 route.params.id 和 router.push
const mockPush = vi.fn()
const mockRoute = { params: { id: '1' }, meta: {} }
vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: mockPush }),
  RouterLink: { template: '<a><slot/></a>' },
}))

// mock @/stores/authModal —— 登录弹窗 store
const mockOpenAuthModal = vi.fn()
vi.mock('@/stores/authModal', () => ({
  useAuthModalStore: () => ({
    openAuthModal: mockOpenAuthModal,
  }),
}))

// mock @shop/shared —— API、工具函数、composable
// 使用 vi.hoisted 确保 mock 工厂中引用的变量在 hoisting 后也能正确访问
const {
  mockGetProductDetail,
  mockGetCommentList,
  mockGetRelatedProducts,
  mockAddFavorite,
  mockIsAuthenticated,
  mockAddToCart,
  mockFormatDate,
  CommentScoreType,
  commentScoreTypeOptions,
} = vi.hoisted(() => ({
  mockGetProductDetail: vi.fn(),
  mockGetCommentList: vi.fn(),
  mockGetRelatedProducts: vi.fn(),
  mockAddFavorite: vi.fn(),
  mockIsAuthenticated: vi.fn(),
  mockAddToCart: vi.fn(),
  mockFormatDate: vi.fn((date: string) => date?.substring(0, 10) || ''),
  // CommentScoreType 枚举（需要提供真实值，组件内部用 === 比较）
  CommentScoreType: {
    ALL: 'all',
    GOOD: 'good',
    MEDIUM: 'medium',
    BAD: 'bad',
  } as const,
  commentScoreTypeOptions: [
    { label: '全部', value: 'all' as const },
    { label: '好评', value: 'good' as const },
    { label: '中评', value: 'medium' as const },
    { label: '差评', value: 'bad' as const },
  ],
}))

vi.mock('@shop/shared', async () => {
  // 在 mock 工厂内部获取 vue 的 ref，避免 hoisting 顺序问题
  const { ref } = await import('vue')
  return {
    getProductDetail: (...args: any[]) => mockGetProductDetail(...args),
    getCommentList: (...args: any[]) => mockGetCommentList(...args),
    getRelatedProducts: (...args: any[]) => mockGetRelatedProducts(...args),
    addFavorite: (...args: any[]) => mockAddFavorite(...args),
    isAuthenticated: (...args: any[]) => mockIsAuthenticated(...args),
    addToCart: (...args: any[]) => mockAddToCart(...args),
    formatDate: (...args: any[]) => mockFormatDate(...args),
    commentScoreTypeOptions,
    CommentScoreType,
    // useCart 返回 addToCart 方法
    useCart: () => ({ addToCart: mockAddToCart, cartCount: ref(0), fetchCartCount: vi.fn() }),
    // useDebounce 返回 loading 和 run，run 直接执行传入的异步函数
    useDebounce: () => ({
      loading: ref(false),
      run: async (fn: () => Promise<any>) => fn(),
    }),
  }
})

// mock element-plus —— ElMessage 部分 mock，其余组件保留真实实现
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  }
})

// ==================== 测试数据 ====================

/** 构造完整的商品 mock 数据 */
const mockProduct = {
  id: 1,
  shopId: 1,
  shopName: 'ShopMall旗舰店',
  name: '测试手机Pro',
  subtitle: '高性能旗舰手机',
  detail: '<p>商品详情</p>',
  mainImage: 'https://example.com/main.jpg',
  images: ['https://example.com/1.jpg', 'https://example.com/2.jpg', 'https://example.com/3.jpg'],
  categoryId: 10,
  categoryName: '手机数码',
  brandId: 1,
  brandName: 'SHOPMALL',
  minPrice: 2999,
  status: 1,
  sales: 500,
  viewCount: 2000,
  totalStock: 100,
  skus: [
    {
      id: 101,
      productId: 1,
      specValues: { '颜色': '黑色', '存储': '128GB' },
      price: 2999,
      originalPrice: 3749,
      stock: 50,
      image: 'https://example.com/sku1.jpg',
      status: 1,
    },
    {
      id: 102,
      productId: 1,
      specValues: { '颜色': '白色', '存储': '256GB' },
      price: 3499,
      originalPrice: 4374,
      stock: 30,
      image: 'https://example.com/sku2.jpg',
      status: 1,
    },
  ],
  specs: [
    { name: '颜色', values: ['黑色', '白色'] },
    { name: '存储', values: ['128GB', '256GB'] },
  ],
  commentSummary: { avgScore: 4.5, goodRate: 92, totalCount: 128 },
  createTime: '2026-01-01 00:00:00',
}

/** 评价列表 mock 数据 */
const mockReviews = {
  data: {
    records: [
      {
        id: 1,
        userId: 100,
        userNickname: '张三',
        isAnonymous: 0,
        score: 5,
        content: '手机很好用，拍照清晰！',
        images: ['https://example.com/r1.jpg'],
        reply: '感谢您的支持！',
        replyList: [],
        createTime: '2026-07-01 10:00:00',
      },
      {
        id: 2,
        userId: 200,
        userNickname: '李四',
        isAnonymous: 1,
        score: 4,
        content: '性价比很高，推荐购买',
        images: [],
        reply: null,
        replyList: [],
        createTime: '2026-07-02 12:00:00',
      },
    ],
    total: 2,
  },
}

/** 相关推荐 mock 数据 */
const mockRelated = {
  data: [
    { id: 10, name: '推荐商品1', mainImage: 'https://example.com/r1.jpg', minPrice: 1999, sales: 300 },
    { id: 11, name: '推荐商品2', mainImage: 'https://example.com/r2.jpg', minPrice: 1599, sales: 200 },
  ],
}

/**
 * 辅助函数：挂载组件
 * stub 掉 Element Plus 复杂组件，避免渲染问题
 */
const mountComponent = () => {
  return mount(DetailView, {
    global: {
      stubs: {
        ElSkeleton: { template: '<div class="el-skeleton-stub"><slot/></div>' },
        ElIcon: { template: '<span class="el-icon-stub"><slot/></span>' },
        ElRate: { template: '<div class="el-rate-stub"></div>', props: ['modelValue', 'disabled', 'size', 'colors'] },
        ElImage: { template: '<img class="el-image-stub" :src="src" />', props: ['src', 'previewSrcList', 'initialIndex', 'fit'] },
        ElEmpty: { template: '<div class="el-empty-stub">{{ description }}</div>', props: ['description'] },
        ElPagination: { template: '<div class="el-pagination-stub"></div>', props: ['currentPage', 'pageSize', 'total', 'layout', 'small'] },
        RouterLink: { template: '<a class="router-link-stub"><slot/></a>' },
      },
    },
  })
}

describe('DetailView 商品详情页组件', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 默认 mock 返回值
    mockGetProductDetail.mockResolvedValue({ data: mockProduct })
    mockGetCommentList.mockResolvedValue(mockReviews)
    mockGetRelatedProducts.mockResolvedValue(mockRelated)
    mockIsAuthenticated.mockReturnValue(true)
    mockAddToCart.mockResolvedValue({})
    mockAddFavorite.mockResolvedValue({})
    // 默认 route.params.id = '1'
    mockRoute.params = { id: '1' }
  })

  // ==================== 加载与渲染 ====================

  it('加载中时应显示骨架屏', () => {
    // 不 resolve，让 loading 保持 true
    mockGetProductDetail.mockReturnValue(new Promise(() => {}))
    const wrapper = mountComponent()
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('商品加载成功后应显示商品名称和面包屑', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.skeleton').exists()).toBe(false)
    expect(wrapper.find('.detail-name').text()).toBe('测试手机Pro')
    // 面包屑应显示首页、分类、商品名
    const breadcrumb = wrapper.find('.breadcrumb')
    expect(breadcrumb.exists()).toBe(true)
    expect(breadcrumb.text()).toContain('首页')
    expect(breadcrumb.text()).toContain('手机数码')
    expect(breadcrumb.text()).toContain('测试手机Pro')
  })

  it('商品不存在时应显示空状态', async () => {
    mockGetProductDetail.mockResolvedValue({ data: null })
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
    expect(wrapper.find('.el-empty-stub').text()).toContain('商品不存在或已下架')
  })

  it('商品加载失败时应显示空状态', async () => {
    mockGetProductDetail.mockRejectedValue(new Error('网络错误'))
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  it('无id参数时不应调用商品API', async () => {
    mockRoute.params = { id: '' }
    const wrapper = mountComponent()
    await flushPromises()

    expect(mockGetProductDetail).not.toHaveBeenCalled()
    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  // ==================== 图片切换 ====================

  it('点击缩略图应切换大图', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 默认显示第一张图
    expect(wrapper.find('.main-image').attributes('src')).toBe('https://example.com/1.jpg')

    // 点击第三张缩略图
    const thumbnails = wrapper.findAll('.thumbnail-item')
    expect(thumbnails).toHaveLength(3)
    await thumbnails[2].trigger('click')

    // 大图应切换为第三张
    expect(wrapper.find('.main-image').attributes('src')).toBe('https://example.com/3.jpg')
    // 当前缩略图应有 active class
    expect(thumbnails[2].classes()).toContain('active')
  })

  // ==================== SKU 选择 ====================

  it('应默认选中每个规格的第一个值', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 默认选中"黑色"和"128GB"
    const skuBtns = wrapper.findAll('.sku-btn')
    expect(skuBtns[0].classes()).toContain('active') // 黑色
    expect(skuBtns[2].classes()).toContain('active') // 128GB
  })

  it('点击规格按钮应切换选中状态', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 点击"白色"
    const skuBtns = wrapper.findAll('.sku-btn')
    await skuBtns[1].trigger('click')

    expect(skuBtns[1].classes()).toContain('active') // 白色 active
    expect(skuBtns[0].classes()).not.toContain('active') // 黑色 不再 active
  })

  it('选中全部规格后应匹配到对应SKU', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 默认选中黑色+128GB → SKU 101，价格 2999
    expect(wrapper.find('.current-price').text()).toContain('2999')

    // 切换到白色+256GB → SKU 102，价格 3499
    const skuBtns = wrapper.findAll('.sku-btn')
    await skuBtns[1].trigger('click') // 白色
    await skuBtns[3].trigger('click') // 256GB

    expect(wrapper.find('.current-price').text()).toContain('3499')
  })

  // ==================== 数量选择 ====================

  it('点击减号应减少数量但不能小于1', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const qtyBtns = wrapper.findAll('.qty-btn')
    // qtyBtns[0] 是减号，qtyBtns[1] 是加号
    const minusBtn = qtyBtns[0]

    // 数量已经是1，点击减号不应继续减少
    await minusBtn.trigger('click')
    expect(wrapper.find('.qty-input').element.value).toBe('1')
  })

  it('点击加号应增加数量但不能超过库存', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // 默认选中 SKU 101（库存50），数量为1
    const qtyBtns = wrapper.findAll('.qty-btn')
    const plusBtn = qtyBtns[1]

    // 点击加号，数量变为2
    await plusBtn.trigger('click')
    expect(wrapper.find('.qty-input').element.value).toBe('2')

    // 点击减号，数量回到1
    await qtyBtns[0].trigger('click')
    expect(wrapper.find('.qty-input').element.value).toBe('1')
  })

  // ==================== 加入购物车 ====================

  it('未选SKU时加购按钮应禁用且不调用API', async () => {
    // 构造没有 SKU 的场景：skus 为空数组时 selectedSku 一定为 null
    const noSkuProduct = { ...mockProduct, skus: [] }
    mockGetProductDetail.mockResolvedValue({ data: noSkuProduct })

    const wrapper = mountComponent()
    await flushPromises()

    // selectedSku 为 null 时按钮应被 disabled（disabled 按钮不触发 click 事件）
    const btnCart = wrapper.find('.btn-cart')
    expect(btnCart.attributes('disabled')).toBeDefined()
    expect(mockAddToCart).not.toHaveBeenCalled()
  })

  it('未登录时点加购应弹出登录弹窗', async () => {
    mockIsAuthenticated.mockReturnValue(false)

    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.btn-cart').trigger('click')

    expect(mockOpenAuthModal).toHaveBeenCalledWith(
      expect.objectContaining({
        description: '登录后加入购物车',
      }),
    )
    expect(mockAddToCart).not.toHaveBeenCalled()
  })

  it('已登录时点加购应调用addToCart并提示成功', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.btn-cart').trigger('click')
    await flushPromises()

    // 默认选中 SKU 101，数量 1
    expect(mockAddToCart).toHaveBeenCalledWith(1, 101, 1)
    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.success)).toHaveBeenCalledWith('已加入购物车')
  })

  // ==================== 立即购买 ====================

  it('未选SKU时立即购买按钮应禁用', async () => {
    // 构造没有 SKU 的场景：skus 为空数组时 selectedSku 一定为 null
    const noSkuProduct = { ...mockProduct, skus: [] }
    mockGetProductDetail.mockResolvedValue({ data: noSkuProduct })

    const wrapper = mountComponent()
    await flushPromises()

    // selectedSku 为 null 时按钮应被 disabled
    const btnBuy = wrapper.find('.btn-buy')
    expect(btnBuy.attributes('disabled')).toBeDefined()
  })

  it('未登录时点立即购买应弹出登录弹窗', async () => {
    mockIsAuthenticated.mockReturnValue(false)

    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.btn-buy').trigger('click')

    expect(mockOpenAuthModal).toHaveBeenCalledWith(
      expect.objectContaining({
        description: '登录后立即购买',
      }),
    )
  })

  it('已登录时点立即购买应跳转确认订单页', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.btn-buy').trigger('click')

    expect(mockPush).toHaveBeenCalledWith({ name: 'OrderConfirm' })
  })

  // ==================== 收藏 ====================

  it('未登录时点收藏应弹出登录弹窗', async () => {
    mockIsAuthenticated.mockReturnValue(false)

    const wrapper = mountComponent()
    await flushPromises()

    // 点击收藏按钮（.image-action 的第一个是收藏）
    const favoriteBtn = wrapper.findAll('.image-action')[0]
    await favoriteBtn.trigger('click')

    expect(mockOpenAuthModal).toHaveBeenCalledWith(
      expect.objectContaining({
        description: '登录后收藏商品',
      }),
    )
    expect(mockAddFavorite).not.toHaveBeenCalled()
  })

  it('已登录时点收藏应调用addFavorite并提示成功', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const favoriteBtn = wrapper.findAll('.image-action')[0]
    await favoriteBtn.trigger('click')
    await flushPromises()

    expect(mockAddFavorite).toHaveBeenCalledWith(1)
    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.success)).toHaveBeenCalledWith('已收藏该商品')
  })

  // ==================== 评价列表 ====================

  it('应显示好评率和评价总数', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    // goodRate=92 → 好评率 92%
    expect(wrapper.find('.review-rate').text()).toContain('92')
    // review-rate-label 显示 reviewTotal（评价API返回的 total=2），不是 commentSummary.totalCount
    expect(wrapper.find('.review-rate-label').text()).toContain('2')
  })

  it('应显示评价列表和评价内容', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const reviewItems = wrapper.findAll('.review-item')
    expect(reviewItems).toHaveLength(2)
    expect(reviewItems[0].find('.review-content').text()).toContain('手机很好用')
  })

  it('匿名评价应显示"匿名用户"', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const reviewItems = wrapper.findAll('.review-item')
    // 第二条评价是匿名的
    expect(reviewItems[1].find('.user-name').text()).toBe('匿名用户')
    // 匿名头像首字应为"匿"
    expect(reviewItems[1].find('.user-avatar').text()).toBe('匿')
  })

  it('评价有商家回复时应显示回复内容', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const reviewItems = wrapper.findAll('.review-item')
    expect(reviewItems[0].find('.merchant-reply').exists()).toBe(true)
    expect(reviewItems[0].find('.reply-text').text()).toContain('感谢您的支持')
  })

  it('切换评分Tab应重新拉取评价', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    // 初始加载调用 1 次
    expect(mockGetCommentList).toHaveBeenCalledTimes(1)

    // 点击"好评"Tab
    const reviewTabs = wrapper.findAll('.review-tab')
    await reviewTabs[1].trigger('click') // 好评
    await flushPromises()

    // 应再次调用 getCommentList
    expect(mockGetCommentList).toHaveBeenCalledTimes(2)
  })

  it('评价列表为空时应显示空状态', async () => {
    mockGetCommentList.mockResolvedValue({ data: { records: [], total: 0 } })

    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.review-empty').exists()).toBe(true)
    expect(wrapper.find('.review-empty').text()).toContain('暂无评价')
  })

  // ==================== 相关推荐 ====================

  it('应显示相关推荐商品', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const relatedItems = wrapper.findAll('.related-item')
    expect(relatedItems).toHaveLength(2)
    expect(relatedItems[0].find('.related-name').text()).toBe('推荐商品1')
  })

  it('点击相关推荐应跳转商品详情', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const relatedItems = wrapper.findAll('.related-item')
    await relatedItems[0].trigger('click')

    expect(mockPush).toHaveBeenCalledWith('/product/10')
  })

  it('相关推荐加载失败应静默处理不报错', async () => {
    mockGetRelatedProducts.mockRejectedValue(new Error('网络错误'))

    const wrapper = mountComponent()
    await flushPromises()

    // 推荐区不应显示
    expect(wrapper.find('.related-section').exists()).toBe(false)
  })

  // ==================== 规格参数 ====================

  it('应显示规格参数表', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    const specRows = wrapper.findAll('.spec-row')
    expect(specRows.length).toBeGreaterThanOrEqual(4)
    // 品牌固定为 SHOPMALL
    expect(specRows[0].find('.spec-value').text()).toBe('SHOPMALL')
    // 分类应显示商品 categoryName
    expect(specRows[1].find('.spec-value').text()).toBe('手机数码')
    // 库存应显示总库存
    expect(specRows[2].find('.spec-value').text()).toContain('100')
  })
})
