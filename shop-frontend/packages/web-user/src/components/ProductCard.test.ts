/**
 * ProductCard 组件测试
 * <p>
 * 验证商品卡片组件的渲染、交互和边界情况。
 * 这是 web-user 端的核心展示组件，被首页、分类页、搜索页、相关推荐等场景复用。
 * </p>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ProductCard from './ProductCard.vue'
import type { ProductInfo } from '@shop/shared'

// mock vue-router，因为组件里用了 useRouter().push() 跳转商品详情页
// 用 mockPush 捕获跳转调用，方便后续断言
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}))

// 构造一个测试用的商品数据（含完整字段）
const mockProduct: ProductInfo = {
  id: 1001,
  name: '测试商品',
  subtitle: '这是一个测试商品',
  mainImage: 'https://example.com/test.jpg',
  minPrice: 99.5,
  brandName: '测试品牌',
  commentSummary: {
    totalCount: 1500,
    avgScore: 4.5,
  },
} as ProductInfo

describe('ProductCard 商品卡片组件', () => {
  // 每个测试前清空 mock 调用记录，避免相互影响
  beforeEach(() => {
    mockPush.mockClear()
  })

  // ==================== 渲染验证 ====================
  it('应正确渲染商品名称', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    expect(wrapper.find('.card-title').text()).toContain('测试商品')
  })

  it('应正确渲染品牌标签', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    expect(wrapper.find('.card-brand').text()).toBe('测试品牌')
  })

  it('没有品牌名时不显示品牌标签', () => {
    const noBrandProduct = { ...mockProduct, brandName: '' }
    const wrapper = mount(ProductCard, {
      props: { product: noBrandProduct },
    })
    expect(wrapper.find('.card-brand').exists()).toBe(false)
  })

  it('应正确渲染价格', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    // formatPriceValue(99.5) = '99.50'（toFixed(2)保留两位，只去掉.00后缀）
    expect(wrapper.find('.price-value').text()).toBe('99.50')
  })

  it('有评论时应显示评分', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    expect(wrapper.find('.card-rating').exists()).toBe(true)
    // 1500条评论应显示为1.5k
    expect(wrapper.find('.rating-count').text()).toContain('1.5k')
  })

  it('无评论时不显示评分区域', () => {
    const noCommentProduct = {
      ...mockProduct,
      commentSummary: { totalCount: 0, avgScore: 0 },
    }
    const wrapper = mount(ProductCard, {
      props: { product: noCommentProduct },
    })
    expect(wrapper.find('.card-rating').exists()).toBe(false)
  })

  it('价格为undefined时显示0', () => {
    const noPriceProduct = { ...mockProduct, minPrice: undefined }
    const wrapper = mount(ProductCard, {
      props: { product: noPriceProduct },
    })
    expect(wrapper.find('.price-value').text()).toBe('0')
  })

  it('整百价格应去掉.00后缀', () => {
    const wholePriceProduct = { ...mockProduct, minPrice: 100 }
    const wrapper = mount(ProductCard, {
      props: { product: wholePriceProduct },
    })
    // 100.00 → 100
    expect(wrapper.find('.price-value').text()).toBe('100')
  })

  // ==================== 价格格式化边界值 ====================
  it('小数价格.99应保留两位小数', () => {
    // 19.99 不应该被截断为 19，因为只有 .00 后缀才会被去掉
    const wrapper = mount(ProductCard, {
      props: { product: { ...mockProduct, minPrice: 19.99 } },
    })
    expect(wrapper.find('.price-value').text()).toBe('19.99')
  })

  it('小数价格.50应保留两位小数', () => {
    // 0.50 只有 .00 后缀才会被去掉，.50 应保留
    const wrapper = mount(ProductCard, {
      props: { product: { ...mockProduct, minPrice: 0.5 } },
    })
    expect(wrapper.find('.price-value').text()).toBe('0.50')
  })

  it('整千价格应去掉.00后缀', () => {
    // 1000.00 → 1000
    const wrapper = mount(ProductCard, {
      props: { product: { ...mockProduct, minPrice: 1000 } },
    })
    expect(wrapper.find('.price-value').text()).toBe('1000')
  })

  // ==================== 评论数格式化边界值 ====================
  it('评论数小于1000应直接显示原数', () => {
    const wrapper = mount(ProductCard, {
      props: {
        product: {
          ...mockProduct,
          commentSummary: { totalCount: 999, avgScore: 4.0 },
        },
      },
    })
    expect(wrapper.find('.rating-count').text()).toContain('999')
  })

  it('评论数正好1000应显示为1k', () => {
    // formatReviewCount(1000) = (1000/1000).toFixed(1) = '1.0' → replace(/\.0$/, '') → '1' → '1k'
    const wrapper = mount(ProductCard, {
      props: {
        product: {
          ...mockProduct,
          commentSummary: { totalCount: 1000, avgScore: 4.0 },
        },
      },
    })
    expect(wrapper.find('.rating-count').text()).toContain('1k')
  })

  it('评论数1500应显示为1.5k', () => {
    const wrapper = mount(ProductCard, {
      props: {
        product: {
          ...mockProduct,
          commentSummary: { totalCount: 1500, avgScore: 4.0 },
        },
      },
    })
    expect(wrapper.find('.rating-count').text()).toContain('1.5k')
  })

  it('评论数9999应显示为9.9k+', () => {
    // 注意：源码实现是 (count/1000).toFixed(1).replace(/\.0$/, '') + 'k'
    // 9999 / 1000 = 9.999 → toFixed(1) = '10.0' → replace → '10' → '10k'
    // 实际显示是 10k 而不是 9.9k+（与常见实现略有差异，以源码为准）
    const wrapper = mount(ProductCard, {
      props: {
        product: {
          ...mockProduct,
          commentSummary: { totalCount: 9999, avgScore: 4.0 },
        },
      },
    })
    expect(wrapper.find('.rating-count').text()).toContain('10k')
  })

  // ==================== 星级评分渲染 ====================
  it('avgScore为4.5时应填充5颗星（Math.round(4.5)=5）', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct }, // avgScore=4.5
    })
    const filledStars = wrapper.findAll('.star.filled')
    expect(filledStars).toHaveLength(5)
  })

  it('avgScore为3.2时应填充3颗星（Math.round(3.2)=3）', () => {
    const wrapper = mount(ProductCard, {
      props: {
        product: {
          ...mockProduct,
          commentSummary: { totalCount: 100, avgScore: 3.2 },
        },
      },
    })
    const filledStars = wrapper.findAll('.star.filled')
    expect(filledStars).toHaveLength(3)
  })

  it('应渲染5个星星元素（无论评分如何）', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    // v-for="n in 5" 会生成5个星星 span
    const allStars = wrapper.findAll('.star')
    expect(allStars).toHaveLength(5)
  })

  // ==================== 图片渲染 ====================
  it('应正确渲染商品图片src和alt', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    const img = wrapper.find('.card-image')
    expect(img.attributes('src')).toBe('https://example.com/test.jpg')
    expect(img.attributes('alt')).toBe('测试商品')
  })

  // ==================== v-html 渲染（搜索高亮） ====================
  it('商品名称支持v-html渲染（搜索高亮em标签）', () => {
    // 搜索结果场景：商品名带有 <em>高亮标签</em>
    const highlightProduct = {
      ...mockProduct,
      name: '红色<em>手机</em>壳',
    }
    const wrapper = mount(ProductCard, {
      props: { product: highlightProduct },
    })
    // v-html 渲染后应该能找到 em 元素
    const em = wrapper.find('.card-title em')
    expect(em.exists()).toBe(true)
    expect(em.text()).toBe('手机')
  })

  // ==================== 副标题渲染 ====================
  it('有副标题时应渲染副标题', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    expect(wrapper.find('.card-subtitle').text()).toBe('这是一个测试商品')
  })

  it('无副标题时用占位符保持等高', () => {
    // 源码用 product.subtitle || '\u00A0'（&nbsp;）占位保证卡片等高
    // 注意：happy-dom 的 text() 会把 \u00A0 normalize 为空字符串，所以用 html 验证
    const noSubtitleProduct = { ...mockProduct, subtitle: '' }
    const wrapper = mount(ProductCard, {
      props: { product: noSubtitleProduct },
    })
    // 副标题元素应存在（CSS height:17px 保证占位，不依赖文本内容）
    const subtitle = wrapper.find('.card-subtitle')
    expect(subtitle.exists()).toBe(true)
    // html 中应包含不间断空格（&nbsp; 或 \u00A0）
    const html = subtitle.html()
    expect(html.includes('&nbsp;') || html.includes('\u00A0') || html.includes('&#160;')).toBe(true)
  })

  // ==================== 点击交互 ====================
  it('点击卡片应跳转到商品详情页', async () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    await wrapper.find('.product-card').trigger('click')
    // 应调用 router.push 跳转到 /product/1001
    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/product/1001')
  })

  it('点击浮动加购按钮应跳转到商品详情页（选SKU）', async () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    await wrapper.find('.add-btn').trigger('click')
    // 加购按钮也是跳转到详情页，让用户选择SKU
    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/product/1001')
  })

  it('点击加购按钮不应触发卡片点击事件（阻止冒泡）', async () => {
    // 源码用 @click.stop="handleQuickAdd" 阻止事件冒泡到卡片
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    await wrapper.find('.add-btn').trigger('click')
    // 阻止冒泡后，卡片外层点击不应被触发，push 只应被调用1次（来自加购按钮）
    expect(mockPush).toHaveBeenCalledTimes(1)
  })

  it('加购按钮应有aria-label无障碍标签', () => {
    const wrapper = mount(ProductCard, {
      props: { product: mockProduct },
    })
    expect(wrapper.find('.add-btn').attributes('aria-label')).toBe('加入购物车')
  })
})
