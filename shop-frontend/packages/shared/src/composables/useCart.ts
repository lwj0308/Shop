/**
 * 购物车逻辑组合式函数
 * 把添加购物车、获取购物车数量这些跟"购物车"相关的操作封装在一起
 * 购物车数量是响应式的，变化时页面会自动更新
 * 支持全局角标管理，多个组件共享同一个购物车数量
 */

import { ref, computed } from 'vue'
import {
  addToCart as addToCartApi,
  getCartCount as getCartCountApi,
  getCartList as getCartListApi,
  removeCartItem as removeCartItemApi,
  updateCartItem as updateCartItemApi,
  clearCart as clearCartApi,
} from '../api'
import type { CartItem, CartSummary, AddCartParams, UpdateCartParams } from '../types'

/** 全局共享的购物车数量（模块级变量，所有useCart()实例共享） */
const globalCartCount = ref(0)

/** 全局共享的购物车列表数据 */
const globalCartList = ref<CartItem[]>([])

/** 全局共享的购物车选中总金额 */
const globalCheckedTotal = ref(0)

/**
 * 购物车逻辑组合式函数
 * 提供添加商品到购物车、获取购物车商品数量、购物车角标管理等功能
 * 多个组件调用useCart()时，共享同一份购物车数据
 */
export function useCart() {
  /** 购物车商品数量（响应式），用于顶部导航栏的角标显示 */
  const cartCount = computed(() => globalCartCount.value)

  /** 购物车列表 */
  const cartList = computed(() => globalCartList.value)

  /** 选中商品总金额（分） */
  const checkedTotal = computed(() => globalCheckedTotal.value)

  /** 是否有选中的商品 */
  const hasCheckedItems = computed(() => globalCheckedTotal.value > 0)

  /**
   * 添加商品到购物车
   * @param productId - 商品ID
   * @param skuId - SKU ID（选择的具体规格）
   * @param quantity - 购买数量
   */
  const addToCart = async (productId: number, skuId: number, quantity: number) => {
    await addToCartApi({ productId, skuId, quantity })
    // 添加成功后，刷新购物车数量
    await fetchCartCount()
  }

  /**
   * 获取购物车商品数量
   * 页面初始化时调用，更新顶部导航栏的角标
   */
  const fetchCartCount = async () => {
    try {
      const res = await getCartCountApi()
      globalCartCount.value = res.data
    } catch {
      // 获取失败（比如未登录），数量设为0
      globalCartCount.value = 0
    }
  }

  /**
   * 获取购物车列表
   * 购物车页面打开时调用，获取完整的购物车数据
   */
  const fetchCartList = async () => {
    try {
      const res = await getCartListApi()
      const data = res.data as CartSummary
      globalCartList.value = data.items
      globalCheckedTotal.value = data.checkedTotal
      globalCartCount.value = data.checkedCount
    } catch {
      globalCartList.value = []
      globalCheckedTotal.value = 0
    }
  }

  /**
   * 更新购物车项（修改数量、选中状态）
   * @param data - 更新参数
   */
  const updateCartItem = async (data: UpdateCartParams) => {
    await updateCartItemApi(data)
    // 更新后刷新购物车列表
    await fetchCartList()
  }

  /**
   * 删除购物车项
   * @param id - 购物车项ID
   */
  const removeCartItem = async (id: number) => {
    await removeCartItemApi(id)
    // 删除后刷新购物车数据
    await fetchCartList()
  }

  /**
   * 清空购物车
   */
  const clearCart = async () => {
    await clearCartApi()
    globalCartList.value = []
    globalCheckedTotal.value = 0
    globalCartCount.value = 0
  }

  return {
    cartCount,
    cartList,
    checkedTotal,
    hasCheckedItems,
    addToCart,
    fetchCartCount,
    fetchCartList,
    updateCartItem,
    removeCartItem,
    clearCart,
  }
}
