/**
 * 购物车相关API
 * 包含添加商品、修改数量、删除等接口
 */

import { get, post, put, del } from '../request'
import type { CartItem, AddCartParams, UpdateCartParams, CartSummary } from '../../types'

/** 获取购物车列表 */
export function getCartList() {
  return get<CartSummary>('/cart/list')
}

/** 添加商品到购物车 */
export function addToCart(data: AddCartParams) {
  return post<CartItem>('/cart/add', data)
}

/** 更新购物车项（修改数量、选中状态） */
export function updateCartItem(data: UpdateCartParams) {
  return put<null>('/cart/update', data)
}

/** 删除购物车项 */
export function removeCartItem(id: number) {
  return del<null>(`/cart/remove/${id}`)
}

/** 清空购物车 */
export function clearCart() {
  return del<null>('/cart/clear')
}

/** 全选/取消全选 */
export function toggleCartAllChecked(checked: boolean) {
  return put<null>('/cart/checkAll', { checked })
}

/** 获取购物车商品数量（用于顶部角标显示） */
export function getCartCount() {
  return get<number>('/cart/count')
}
