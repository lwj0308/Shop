/**
 * ConfirmView 下单确认页组件测试
 * <p>
 * 验证结算页的加载状态、空状态、地址选择、商品清单、
 * 优惠券选择、费用计算、提交订单等核心交互。
 * </p>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConfirmView from './ConfirmView.vue'

// ==================== mock 外部依赖 ====================

// mock vue-router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

// 使用 vi.hoisted 创建 mock 容器
const {
  cartMocks,
  mockGetAddressList,
  mockCreateOrder,
  mockGetUsableCoupons,
} = vi.hoisted(() => ({
  cartMocks: {
    cartList: null as any,
    fetchCartList: vi.fn(),
  },
  mockGetAddressList: vi.fn(),
  mockCreateOrder: vi.fn(),
  mockGetUsableCoupons: vi.fn(),
}))

// mock @shop/shared —— useCart、useDebounce、地址/优惠券/下单 API
vi.mock('@shop/shared', async () => {
  const { ref } = await import('vue')
  // 创建响应式 cartList 并暴露到 hoisted 容器
  cartMocks.cartList = ref([])
  return {
    useCart: () => ({
      cartList: cartMocks.cartList,
      fetchCartList: cartMocks.fetchCartList,
    }),
    // useDebounce 返回 loading 和 run，run 直接执行传入的函数
    useDebounce: () => ({
      loading: ref(false),
      run: async (fn: () => Promise<any>) => fn(),
    }),
    getAddressList: (...args: any[]) => mockGetAddressList(...args),
    createOrder: (...args: any[]) => mockCreateOrder(...args),
    getUsableCoupons: (...args: any[]) => mockGetUsableCoupons(...args),
    formatPriceWithSymbol: (price: number) => `¥${(price / 100).toFixed(2)}`,
  }
})

// mock element-plus —— ElMessage
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

/** 地址列表 mock 数据 */
const mockAddresses = [
  {
    id: 1,
    name: '张三',
    phone: '13812345678',
    province: '广东省',
    city: '深圳市',
    district: '南山区',
    detail: '科技园路1号',
    isDefault: 1,
  },
  {
    id: 2,
    name: '李四',
    phone: '13987654321',
    province: '北京市',
    city: '北京市',
    district: '海淀区',
    detail: '中关村大街2号',
    isDefault: 0,
  },
]

/**
 * 优惠券列表 mock 数据
 * 注意：UserCouponInfo.id 就是用户券表的主键（user_coupon 表主键），
 * 下单时直接作为 userCouponId 传给后端，不存在单独的 userCouponId 字段。
 */
const mockCoupons = [
  {
    id: 10,
    couponName: '满2000减100',
    couponType: 1,
    amount: 100,
    discountAmount: 100,
    validStartTime: '2026-01-01T00:00:00',
    validEndTime: '2026-12-31T23:59:59',
  },
  {
    id: 11,
    couponName: '满3000减200',
    couponType: 1,
    amount: 200,
    discountAmount: 200,
    validStartTime: '2026-01-01T00:00:00',
    validEndTime: '2026-12-31T23:59:59',
  },
]

/** 购物车已选中商品 mock 数据 */
const mockCheckedItems = [
  {
    id: 1,
    productId: 101,
    productName: '测试手机Pro',
    productImage: 'https://example.com/phone.jpg',
    skuId: 1001,
    skuName: '黑色 128GB',
    price: 299900,
    quantity: 2,
    subtotal: 599800,
    stock: 50,
    checked: true,
  },
]

/**
 * 辅助函数：挂载组件
 * stub EP 组件避免渲染问题
 */
const mountComponent = () => {
  return mount(ConfirmView, {
    global: {
      stubs: {
        ElSkeleton: { template: '<div class="el-skeleton-stub"><slot/></div>' },
        // ElEmpty stub 需要渲染 <slot/>，否则 el-empty 标签内的"返回购物车"按钮会被丢弃
        ElEmpty: { template: '<div class="el-empty-stub">{{ description }}<slot/></div>', props: ['description'] },
        ElDialog: {
          name: 'ElDialog',
          template: '<div v-if="modelValue" class="el-dialog-stub"><slot/></div>',
          props: ['modelValue', 'title', 'width'],
        },
      },
    },
  })
}

describe('ConfirmView 下单确认页组件', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 重置响应式数据
    cartMocks.cartList.value = []
    // 默认 mock 返回值
    cartMocks.fetchCartList.mockResolvedValue({})
    mockGetAddressList.mockResolvedValue({ data: mockAddresses })
    mockGetUsableCoupons.mockResolvedValue({ data: mockCoupons })
    mockCreateOrder.mockResolvedValue({ data: { orderNo: 'ORD202607160001' } })
  })

  // ==================== 加载状态 ====================

  it('加载中时应显示骨架屏', () => {
    // fetchCartList 不 resolve，让 loading 保持 true
    cartMocks.fetchCartList.mockReturnValue(new Promise(() => {}))
    const wrapper = mountComponent()
    expect(wrapper.find('.loading-wrapper').exists()).toBe(true)
  })

  // ==================== 空状态 ====================

  it('无选中商品时应显示空状态', async () => {
    // 购物车有商品但未选中
    cartMocks.cartList.value = [{ ...mockCheckedItems[0], checked: false }]
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.empty-wrapper').exists()).toBe(true)
    expect(wrapper.find('.el-empty-stub').text()).toContain('没有可结算的商品')
  })

  it('空状态应显示返回购物车按钮', async () => {
    cartMocks.cartList.value = []
    const wrapper = mountComponent()
    await flushPromises()

    const backBtn = wrapper.find('.btn-back')
    expect(backBtn.exists()).toBe(true)
    expect(backBtn.text()).toBe('返回购物车')
  })

  // ==================== 地址展示 ====================

  it('应默认选中默认地址', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // isDefault=1 的地址应被选中
    const addressInfo = wrapper.find('.address-info')
    expect(addressInfo.exists()).toBe(true)
    expect(addressInfo.find('.user-name').text()).toBe('张三')
    expect(addressInfo.find('.user-phone').text()).toBe('13812345678')
    // 应显示默认标签
    expect(addressInfo.find('.tag-green').exists()).toBe(true)
  })

  it('应显示完整地址', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const addressText = wrapper.find('.address-text').text()
    expect(addressText).toContain('广东省')
    expect(addressText).toContain('深圳市')
    expect(addressText).toContain('南山区')
    expect(addressText).toContain('科技园路1号')
  })

  it('有多个地址时应显示切换按钮', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.switch-btn').exists()).toBe(true)
  })

  it('无地址时应显示添加地址提示', async () => {
    cartMocks.cartList.value = mockCheckedItems
    mockGetAddressList.mockResolvedValue({ data: [] })
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.address-empty').exists()).toBe(true)
    expect(wrapper.find('.address-empty-text').text()).toContain('暂无收货地址')
  })

  // ==================== 商品清单 ====================

  it('应显示已选商品的清单', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const goodsItems = wrapper.findAll('.goods-item')
    expect(goodsItems).toHaveLength(1)
    expect(goodsItems[0].find('.goods-name').text()).toBe('测试手机Pro')
    expect(goodsItems[0].find('.goods-sku').text()).toBe('黑色 128GB')
  })

  it('应显示商品图片', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const img = wrapper.find('.goods-image')
    expect(img.attributes('src')).toBe('https://example.com/phone.jpg')
    expect(img.attributes('alt')).toBe('测试手机Pro')
  })

  it('应显示商品数量和单价', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const priceQty = wrapper.find('.goods-price-qty').text()
    // 单价 299900 分 = ¥2999.00，数量 2
    expect(priceQty).toContain('¥2999.00')
    expect(priceQty).toContain('2')
  })

  it('应显示商品小计', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 小计 599800 分 = ¥5998.00
    const subtotal = wrapper.find('.goods-subtotal').text()
    expect(subtotal).toContain('¥5998.00')
  })

  // ==================== 费用明细 ====================

  it('应显示运费为免运费', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const feeItems = wrapper.findAll('.fee-item')
    // 第一个 fee-item 是运费
    expect(feeItems[0].text()).toContain('免运费')
  })

  it('应显示商品总件数', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const feeItems = wrapper.findAll('.fee-item')
    // 第二个 fee-item 是商品件数
    expect(feeItems[1].text()).toContain('共 2 件')
  })

  it('应显示支付方式为在线支付', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    const feeItems = wrapper.findAll('.fee-item')
    // 第三个 fee-item 是支付方式
    expect(feeItems[2].text()).toContain('在线支付')
  })

  it('有可用优惠券时应显示可用数量', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 未选优惠券时应显示"2张可用"
    const couponItem = wrapper.find('.coupon-item')
    expect(couponItem.text()).toContain('2张可用')
  })

  // ==================== 优惠券选择 ====================

  it('点击优惠券区域应打开选择弹窗', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.coupon-item').trigger('click')
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(true)
  })

  it('选择优惠券后应显示优惠金额', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 打开优惠券弹窗
    await wrapper.find('.coupon-item').trigger('click')

    // 选择第一张优惠券（不使用优惠券选项之后的第一个）
    const couponItems = wrapper.findAll('.coupon-dialog-item')
    // couponItems[0] 是"不使用优惠券"，couponItems[1] 是第一张券
    await couponItems[1].trigger('click')
    await flushPromises()

    // 应显示优惠金额 -¥100.00（discountAmount=100 元 = 10000 分）
    const discountSummary = wrapper.find('.discount-summary')
    expect(discountSummary.exists()).toBe(true)
    expect(discountSummary.text()).toContain('¥100.00')
  })

  it('选择"不使用优惠券"后不应显示优惠金额', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 打开优惠券弹窗
    await wrapper.find('.coupon-item').trigger('click')

    // 选择"不使用优惠券"
    const couponItems = wrapper.findAll('.coupon-dialog-item')
    await couponItems[0].trigger('click') // 不使用优惠券
    await flushPromises()

    // 不应显示优惠金额
    expect(wrapper.find('.discount-summary').exists()).toBe(false)
  })

  // ==================== 提交订单 ====================

  it('无地址时提交按钮应禁用', async () => {
    cartMocks.cartList.value = mockCheckedItems
    mockGetAddressList.mockResolvedValue({ data: [] })
    const wrapper = mountComponent()
    await flushPromises()

    const submitBtn = wrapper.find('.submit-btn')
    expect(submitBtn.attributes('disabled')).toBeDefined()
  })

  it('有地址时点击提交应调用createOrder并跳转收银台', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.submit-btn').trigger('click')
    await flushPromises()

    // 验证调用 createOrder
    expect(mockCreateOrder).toHaveBeenCalledWith({
      addressId: 1, // 默认地址 ID
      cartItemIds: [1], // 购物车项 ID 列表
      userCouponId: undefined, // 未选优惠券
    })

    // 验证跳转收银台
    expect(mockPush).toHaveBeenCalledWith({
      name: 'PaymentPay',
      query: { orderNo: 'ORD202607160001' },
    })

    // 验证成功提示
    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.success)).toHaveBeenCalledWith(
      expect.stringContaining('下单成功'),
    )
  })

  it('选择优惠券后提交应传入userCouponId', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 选择第一张优惠券
    await wrapper.find('.coupon-item').trigger('click')
    const couponItems = wrapper.findAll('.coupon-dialog-item')
    await couponItems[1].trigger('click')
    await flushPromises()

    // 提交订单
    await wrapper.find('.submit-btn').trigger('click')
    await flushPromises()

    // 验证传入了 userCouponId（UserCouponInfo.id 即用户券主键）
    expect(mockCreateOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        userCouponId: 10, // 第一张券的 id
      }),
    )
  })

  it('下单失败应显示错误消息', async () => {
    cartMocks.cartList.value = mockCheckedItems
    mockCreateOrder.mockRejectedValue(new Error('库存不足'))
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.submit-btn').trigger('click')
    await flushPromises()

    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.error)).toHaveBeenCalledWith('库存不足')
    // 不应跳转
    expect(mockPush).not.toHaveBeenCalled()
  })

  // ==================== 实付金额计算 ====================

  it('未选优惠券时实付金额等于商品总额', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 总额 599800 分 = ¥5998.00
    const totalPrice = wrapper.find('.total-price').text()
    expect(totalPrice).toContain('¥5998.00')
  })

  it('选择优惠券后实付金额应扣减优惠', async () => {
    cartMocks.cartList.value = mockCheckedItems
    const wrapper = mountComponent()
    await flushPromises()

    // 选择第一张优惠券（减100元 = 10000分）
    await wrapper.find('.coupon-item').trigger('click')
    const couponItems = wrapper.findAll('.coupon-dialog-item')
    await couponItems[1].trigger('click')
    await flushPromises()

    // 实付 = 599800 - 10000 = 589800 分 = ¥5898.00
    const totalPrice = wrapper.find('.total-price').text()
    expect(totalPrice).toContain('¥5898.00')
  })
})
