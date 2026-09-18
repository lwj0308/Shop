/**
 * CartView 购物车页组件测试
 * <p>
 * 验证购物车页的加载状态、空状态、商品列表渲染、
 * 选中/全选、数量修改、删除确认、结算跳转等核心交互。
 * </p>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CartView from './IndexView.vue'

// ==================== mock 外部依赖 ====================

// mock vue-router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

// 使用 vi.hoisted 创建 mock 容器，使测试代码可以修改响应式数据
const { cartMocks, mockMessageBoxConfirm } = vi.hoisted(() => ({
  cartMocks: {
    // ref 引用，在 mock 工厂中被赋值
    cartList: null as any,
    checkedTotal: null as any,
    // mock 函数
    fetchCartList: vi.fn(),
    updateCartItem: vi.fn(),
    removeCartItem: vi.fn(),
  },
  mockMessageBoxConfirm: vi.fn(),
}))

// mock @shop/shared —— useCart 返回响应式数据，formatPriceWithSymbol 格式化价格
vi.mock('@shop/shared', async () => {
  const { ref, computed } = await import('vue')
  // 创建响应式 ref 并暴露到 hoisted 容器，方便测试代码修改
  cartMocks.cartList = ref([])
  cartMocks.checkedTotal = ref(0)
  return {
    useCart: () => ({
      cartList: cartMocks.cartList,
      checkedTotal: cartMocks.checkedTotal,
      hasCheckedItems: computed(() => cartMocks.checkedTotal.value > 0),
      fetchCartList: cartMocks.fetchCartList,
      updateCartItem: cartMocks.updateCartItem,
      removeCartItem: cartMocks.removeCartItem,
    }),
    // formatPriceWithSymbol：分转元，加¥符号
    formatPriceWithSymbol: (price: number) => `¥${(price / 100).toFixed(2)}`,
  }
})

// mock element-plus —— ElMessage 和 ElMessageBox
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
    ElMessageBox: {
      confirm: mockMessageBoxConfirm,
    },
  }
})

// ==================== 测试数据 ====================

/** 购物车商品 mock 数据 */
const mockCartItems = [
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
  {
    id: 2,
    productId: 102,
    productName: '无线耳机',
    productImage: 'https://example.com/earphone.jpg',
    skuId: 2001,
    skuName: '白色',
    price: 29900,
    quantity: 1,
    subtotal: 29900,
    stock: 100,
    checked: false,
  },
]

/**
 * 辅助函数：挂载组件
 * stub EP 组件避免渲染问题
 */
const mountComponent = () => {
  return mount(CartView, {
    global: {
      // 模板中用了 $router.push，需要通过 mocks 提供
      mocks: {
        $router: { push: mockPush },
      },
      stubs: {
        ElSkeleton: { template: '<div class="el-skeleton-stub"><slot/></div>' },
        ElEmpty: { template: '<div class="el-empty-stub">{{ description }}</div>', props: ['description'] },
        ElCheckbox: {
          template: '<input type="checkbox" class="el-checkbox-stub" :checked="modelValue" @change="$emit(\'change\', $event.target.checked)" />',
          props: ['modelValue'],
          emits: ['change'],
        },
        ElInputNumber: {
          template: '<div class="el-input-number-stub"></div>',
          props: ['modelValue', 'min', 'max', 'size'],
          emits: ['change'],
        },
      },
    },
  })
}

describe('CartView 购物车页组件', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 重置响应式数据
    cartMocks.cartList.value = []
    cartMocks.checkedTotal.value = 0
    // 默认 mock 返回值
    cartMocks.fetchCartList.mockResolvedValue({})
    cartMocks.updateCartItem.mockResolvedValue({})
    cartMocks.removeCartItem.mockResolvedValue({})
    mockMessageBoxConfirm.mockResolvedValue('confirm')
  })

  // ==================== 加载状态 ====================

  it('加载中时应显示骨架屏', () => {
    // fetchCartList 不 resolve，让 loading 保持 true
    cartMocks.fetchCartList.mockReturnValue(new Promise(() => {}))
    const wrapper = mountComponent()
    expect(wrapper.find('.cart-loading').exists()).toBe(true)
  })

  it('加载完成后应隐藏骨架屏', async () => {
    cartMocks.fetchCartList.mockResolvedValue({})
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.cart-loading').exists()).toBe(false)
  })

  // ==================== 空状态 ====================

  it('购物车为空时应显示空状态', async () => {
    cartMocks.cartList.value = []
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.cart-empty').exists()).toBe(true)
    expect(wrapper.find('.cart-empty').text()).toContain('购物车空空如也')
  })

  it('空购物车应显示继续购物按钮', async () => {
    cartMocks.cartList.value = []
    const wrapper = mountComponent()
    await flushPromises()

    const continueBtn = wrapper.find('.btn-continue')
    expect(continueBtn.exists()).toBe(true)
    expect(continueBtn.text()).toBe('继续购物')
  })

  it('点击继续购物应跳转首页', async () => {
    cartMocks.cartList.value = []
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.btn-continue').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  // ==================== 商品列表渲染 ====================

  it('应正确渲染购物车商品列表', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const cartItems = wrapper.findAll('.cart-item')
    expect(cartItems).toHaveLength(2)
    // 验证第一个商品名称
    expect(cartItems[0].find('.item-name').text()).toBe('测试手机Pro')
    expect(cartItems[0].find('.item-sku').text()).toBe('黑色 128GB')
    // 验证第二个商品名称
    expect(cartItems[1].find('.item-name').text()).toBe('无线耳机')
  })

  it('应显示商品图片', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const images = wrapper.findAll('.item-image')
    expect(images[0].attributes('src')).toBe('https://example.com/phone.jpg')
    expect(images[0].attributes('alt')).toBe('测试手机Pro')
  })

  it('应显示商品数量和总件数', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    // 顶部显示总件数
    expect(wrapper.find('.cart-count').text()).toContain('2')
  })

  it('应显示格式化后的价格', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    // 第一个商品单价 299900 分 = ¥2999.00
    // 注意：用 .cart-item .col-price 精确定位商品行中的价格，排除表头
    const priceEl = wrapper.find('.cart-item .col-price')
    expect(priceEl.text()).toContain('¥2999.00')
    // 小计 599800 分 = ¥5998.00
    const subtotalEl = wrapper.find('.cart-item .col-subtotal')
    expect(subtotalEl.text()).toContain('¥5998.00')
  })

  // ==================== 选中状态 ====================

  it('商品选中状态应正确反映在 checkbox 上', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const checkboxes = wrapper.findAll('.el-checkbox-stub')
    // 第一个商品 checked=true
    expect(checkboxes[0].element.checked).toBe(true)
    // 第二个商品 checked=false
    expect(checkboxes[1].element.checked).toBe(false)
  })

  it('点击商品 checkbox 应调用 updateCartItem', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const checkboxes = wrapper.findAll('.el-checkbox-stub')
    await checkboxes[1].setValue(true)

    expect(cartMocks.updateCartItem).toHaveBeenCalledWith({ id: 2, checked: true })
  })

  it('全选 checkbox 状态应根据商品选中状态计算', async () => {
    // 全部选中 → isAllChecked = true
    cartMocks.cartList.value = mockCartItems.map(item => ({ ...item, checked: true }))
    const wrapper = mountComponent()
    await flushPromises()

    // 底部全选 checkbox 应被选中
    const allCheckbox = wrapper.findAll('.el-checkbox-stub').pop()!
    expect(allCheckbox.element.checked).toBe(true)
  })

  it('非全选时全选 checkbox 应未选中', async () => {
    // 只有部分选中
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const allCheckbox = wrapper.findAll('.el-checkbox-stub').pop()!
    expect(allCheckbox.element.checked).toBe(false)
  })

  // ==================== 删除商品 ====================

  it('点击删除应弹出确认弹窗', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const removeBtn = wrapper.findAll('.remove-btn')[0]
    await removeBtn.trigger('click')

    expect(mockMessageBoxConfirm).toHaveBeenCalled()
    expect(mockMessageBoxConfirm).toHaveBeenCalledWith(
      expect.stringContaining('测试手机Pro'),
      '删除确认',
      expect.any(Object),
    )
  })

  it('确认删除后应调用 removeCartItem 并提示成功', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const removeBtn = wrapper.findAll('.remove-btn')[0]
    await removeBtn.trigger('click')
    await flushPromises()

    expect(cartMocks.removeCartItem).toHaveBeenCalledWith(1)
    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.success)).toHaveBeenCalledWith('已删除')
  })

  it('取消删除时不应调用 removeCartItem', async () => {
    mockMessageBoxConfirm.mockRejectedValue(new Error('cancel'))
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    const removeBtn = wrapper.findAll('.remove-btn')[0]
    await removeBtn.trigger('click')
    await flushPromises()

    expect(cartMocks.removeCartItem).not.toHaveBeenCalled()
  })

  it('删除选中商品时应弹出确认弹窗', async () => {
    cartMocks.cartList.value = mockCartItems
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.delete-selected').trigger('click')

    expect(mockMessageBoxConfirm).toHaveBeenCalledWith(
      expect.stringContaining('1'), // 1件选中商品
      '删除确认',
      expect.any(Object),
    )
  })

  it('无选中商品时点删除选中应提示', async () => {
    cartMocks.cartList.value = mockCartItems.map(item => ({ ...item, checked: false }))
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.delete-selected').trigger('click')

    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.warning)).toHaveBeenCalledWith('请先选择要删除的商品')
    expect(mockMessageBoxConfirm).not.toHaveBeenCalled()
  })

  // ==================== 结算 ====================

  it('无选中商品时结算按钮应禁用', async () => {
    cartMocks.cartList.value = mockCartItems.map(item => ({ ...item, checked: false }))
    cartMocks.checkedTotal.value = 0
    const wrapper = mountComponent()
    await flushPromises()

    const checkoutBtn = wrapper.find('.checkout-btn')
    expect(checkoutBtn.attributes('disabled')).toBeDefined()
  })

  it('有选中商品时点击结算应跳转确认订单页', async () => {
    cartMocks.cartList.value = mockCartItems
    cartMocks.checkedTotal.value = 599800
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.checkout-btn').trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'OrderConfirm' })
  })

  it('底部应显示已选商品数量', async () => {
    cartMocks.cartList.value = mockCartItems
    // 第一个商品 checked=true，数量2；第二个 checked=false
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.selected-count').text()).toBe('2')
  })

  it('底部应显示选中商品合计金额', async () => {
    cartMocks.cartList.value = mockCartItems
    cartMocks.checkedTotal.value = 599800
    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('.total-price').text()).toContain('¥5998.00')
  })
})
