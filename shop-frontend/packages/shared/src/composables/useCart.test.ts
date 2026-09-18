/**
 * useCart.ts 购物车逻辑组合式函数测试
 * <p>
 * 测试添加购物车、获取购物车数量/列表、更新/删除/清空购物车等操作。
 * useCart 内部用了模块级全局变量（globalCartCount 等）实现多组件共享状态，
 * 所以每个测试前需要通过 vi.resetModules() 重置模块缓存，避免状态污染。
 * </p>
 */
// @vitest-environment happy-dom
// 上面这行告诉 Vitest：这个测试文件需要在 happy-dom 环境运行
// 因为 useCart 依赖 vue 的响应式系统（ref/computed），需要浏览器环境
import { describe, it, expect, beforeEach, vi } from 'vitest'

// ============ mock 依赖模块 ============
// mock ../api：模拟购物车相关的所有接口
const mockAddToCart = vi.fn()
const mockGetCartCount = vi.fn()
const mockGetCartList = vi.fn()
const mockRemoveCartItem = vi.fn()
const mockUpdateCartItem = vi.fn()
const mockClearCart = vi.fn()

vi.mock('../api', () => ({
  addToCart: (...args: any[]) => mockAddToCart(...args),
  getCartCount: (...args: any[]) => mockGetCartCount(...args),
  getCartList: (...args: any[]) => mockGetCartList(...args),
  removeCartItem: (...args: any[]) => mockRemoveCartItem(...args),
  updateCartItem: (...args: any[]) => mockUpdateCartItem(...args),
  clearCart: (...args: any[]) => mockClearCart(...args),
}))

/**
 * 构造一个假的购物车项，供测试使用
 * @param overrides - 可选的覆盖字段
 */
function makeFakeCartItem(overrides: Partial<{
  id: number
  productId: number
  productName: string
  price: number
  quantity: number
  checked: boolean
  subtotal: number
}> = {}) {
  return {
    id: 1,
    userId: 100,
    productId: 1001,
    skuId: 2001,
    productName: '测试商品',
    productImage: 'https://example.com/img.png',
    skuName: '默认规格',
    price: 9900, // 99元（单位：分）
    quantity: 1,
    stock: 100,
    checked: true,
    subtotal: 9900,
    ...overrides,
  }
}

/**
 * 构造一个假的购物车汇总数据
 */
function makeFakeCartSummary(overrides: Partial<{
  items: any[]
  checkedCount: number
  checkedTotal: number
}> = {}) {
  return {
    items: [makeFakeCartItem()],
    checkedCount: 1,
    checkedTotal: 9900,
    ...overrides,
  }
}

// 每个测试前重置所有 mock
beforeEach(() => {
  vi.clearAllMocks()
})

describe('useCart 初始化', () => {
  it('初始 cartCount 应为 0', async () => {
    // 用动态 import 重新加载模块，确保拿到全新的全局变量
    vi.resetModules()
    const { useCart } = await import('./useCart')
    const { cartCount } = useCart()
    expect(cartCount.value).toBe(0)
  })

  it('初始 cartList 应为空数组', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    const { cartList } = useCart()
    expect(cartList.value).toEqual([])
  })

  it('初始 checkedTotal 应为 0，hasCheckedItems 应为 false', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    const { checkedTotal, hasCheckedItems } = useCart()
    expect(checkedTotal.value).toBe(0)
    expect(hasCheckedItems.value).toBe(false)
  })
})

describe('addToCart 添加购物车', () => {
  it('应调用添加接口并刷新购物车数量', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    // 添加接口直接返回成功
    mockAddToCart.mockResolvedValue({})
    // 刷新数量接口返回 3
    mockGetCartCount.mockResolvedValue({ data: 3 })

    const { addToCart, cartCount } = useCart()
    expect(cartCount.value).toBe(0)

    await addToCart(1001, 2001, 2)

    // 断言：添加接口被调用，参数是商品ID、SKU ID、数量
    expect(mockAddToCart).toHaveBeenCalledWith({ productId: 1001, skuId: 2001, quantity: 2 })
    // 断言：添加后自动调用了刷新数量
    expect(mockGetCartCount).toHaveBeenCalledTimes(1)
    // 断言：购物车数量被更新为 3
    expect(cartCount.value).toBe(3)
  })

  it('添加接口失败时应向上抛出异常', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockAddToCart.mockRejectedValue(new Error('库存不足'))

    const { addToCart } = useCart()
    await expect(addToCart(1001, 2001, 1)).rejects.toThrow('库存不足')
  })
})

describe('fetchCartCount 获取购物车数量', () => {
  it('应调用接口并更新 cartCount', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockGetCartCount.mockResolvedValue({ data: 5 })

    const { fetchCartCount, cartCount } = useCart()
    await fetchCartCount()

    expect(mockGetCartCount).toHaveBeenCalledTimes(1)
    expect(cartCount.value).toBe(5)
  })

  it('接口失败时 cartCount 应设为 0', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockGetCartCount.mockRejectedValue(new Error('未登录'))

    const { fetchCartCount, cartCount } = useCart()
    // 先把数量设成非 0，再测试失败后是否被重置
    // 这里通过先成功一次再失败一次来验证
    mockGetCartCount.mockResolvedValueOnce({ data: 5 })
    await fetchCartCount()
    expect(cartCount.value).toBe(5)

    await fetchCartCount()
    expect(cartCount.value).toBe(0)
  })
})

describe('fetchCartList 获取购物车列表', () => {
  it('应调用接口并更新 cartList、checkedTotal、cartCount', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    const fakeSummary = makeFakeCartSummary({
      items: [makeFakeCartItem({ id: 1, productName: '商品A', subtotal: 5000 }),
              makeFakeCartItem({ id: 2, productName: '商品B', subtotal: 3000, checked: false })],
      checkedCount: 2,
      checkedTotal: 8000,
    })
    mockGetCartList.mockResolvedValue({ data: fakeSummary })

    const { fetchCartList, cartList, checkedTotal, cartCount, hasCheckedItems } = useCart()
    await fetchCartList()

    expect(mockGetCartList).toHaveBeenCalledTimes(1)
    expect(cartList.value).toHaveLength(2)
    expect(cartList.value[0].productName).toBe('商品A')
    expect(checkedTotal.value).toBe(8000)
    expect(cartCount.value).toBe(2)
    expect(hasCheckedItems.value).toBe(true)
  })

  it('接口失败时应清空购物车状态', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockGetCartList.mockRejectedValue(new Error('网络错误'))

    const { fetchCartList, cartList, checkedTotal } = useCart()
    await fetchCartList()

    expect(cartList.value).toEqual([])
    expect(checkedTotal.value).toBe(0)
  })
})

describe('updateCartItem 更新购物车项', () => {
  it('应调用更新接口并刷新购物车列表', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockUpdateCartItem.mockResolvedValue({})
    mockGetCartList.mockResolvedValue({ data: makeFakeCartSummary() })

    const { updateCartItem } = useCart()
    await updateCartItem({ id: 1, quantity: 3 })

    expect(mockUpdateCartItem).toHaveBeenCalledWith({ id: 1, quantity: 3 })
    // 更新后应自动刷新列表
    expect(mockGetCartList).toHaveBeenCalledTimes(1)
  })

  it('更新接口失败时应向上抛出异常', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockUpdateCartItem.mockRejectedValue(new Error('操作失败'))

    const { updateCartItem } = useCart()
    await expect(updateCartItem({ id: 1, quantity: 3 })).rejects.toThrow('操作失败')
  })
})

describe('removeCartItem 删除购物车项', () => {
  it('应调用删除接口并刷新购物车列表', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockRemoveCartItem.mockResolvedValue({})
    mockGetCartList.mockResolvedValue({ data: makeFakeCartSummary() })

    const { removeCartItem } = useCart()
    await removeCartItem(1)

    expect(mockRemoveCartItem).toHaveBeenCalledWith(1)
    expect(mockGetCartList).toHaveBeenCalledTimes(1)
  })

  it('删除接口失败时应向上抛出异常', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockRemoveCartItem.mockRejectedValue(new Error('删除失败'))

    const { removeCartItem } = useCart()
    await expect(removeCartItem(1)).rejects.toThrow('删除失败')
  })
})

describe('clearCart 清空购物车', () => {
  it('应调用清空接口并重置所有全局状态', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    // 先填充一些数据
    mockGetCartList.mockResolvedValue({
      data: makeFakeCartSummary({
        items: [makeFakeCartItem()],
        checkedCount: 3,
        checkedTotal: 9900,
      }),
    })
    const cart = useCart()
    await cart.fetchCartList()
    expect(cart.cartCount.value).toBe(3)
    expect(cart.checkedTotal.value).toBe(9900)

    // 再清空
    mockClearCart.mockResolvedValue({})
    await cart.clearCart()

    expect(mockClearCart).toHaveBeenCalledTimes(1)
    expect(cart.cartList.value).toEqual([])
    expect(cart.checkedTotal.value).toBe(0)
    expect(cart.cartCount.value).toBe(0)
    expect(cart.hasCheckedItems.value).toBe(false)
  })

  it('清空接口失败时应向上抛出异常（不清空本地状态）', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockClearCart.mockRejectedValue(new Error('清空失败'))

    const { clearCart } = useCart()
    await expect(clearCart()).rejects.toThrow('清空失败')
  })
})

describe('多实例共享状态', () => {
  it('多个 useCart() 实例应共享同一份购物车数据', async () => {
    vi.resetModules()
    const { useCart } = await import('./useCart')
    mockGetCartCount.mockResolvedValue({ data: 10 })

    const cart1 = useCart()
    const cart2 = useCart()

    // 初始两个实例的 cartCount 都是 0
    expect(cart1.cartCount.value).toBe(0)
    expect(cart2.cartCount.value).toBe(0)

    // 在 cart1 上获取数量
    await cart1.fetchCartCount()

    // cart2 的 cartCount 也应该同步更新为 10（因为是共享的全局变量）
    expect(cart1.cartCount.value).toBe(10)
    expect(cart2.cartCount.value).toBe(10)
  })
})
