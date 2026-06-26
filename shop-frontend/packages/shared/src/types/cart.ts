/**
 * 购物车相关类型定义
 * 对齐后端 Cart 相关实体和 VO
 */

/** 购物车项信息 */
export interface CartItem {
  /** 购物车项ID */
  id: number
  /** 用户ID */
  userId: number
  /** 商品ID */
  productId: number
  /** SKU ID */
  skuId: number
  /** 商品名称 */
  productName: string
  /** 商品主图 */
  productImage: string
  /** SKU名称 */
  skuName: string
  /** 单价（分） */
  price: number
  /** 购买数量 */
  quantity: number
  /** 库存数量 */
  stock: number
  /** 是否选中 */
  checked: boolean
  /** 小计金额（分）= price * quantity */
  subtotal: number
}

/** 添加购物车参数 */
export interface AddCartParams {
  /** 商品ID */
  productId: number
  /** SKU ID */
  skuId: number
  /** 购买数量 */
  quantity: number
}

/** 更新购物车项参数 */
export interface UpdateCartParams {
  /** 购物车项ID */
  id: number
  /** 购买数量 */
  quantity?: number
  /** 是否选中 */
  checked?: boolean
}

/** 购物车汇总信息 */
export interface CartSummary {
  /** 购物车项列表 */
  items: CartItem[]
  /** 选中商品总数 */
  checkedCount: number
  /** 选中商品总金额（分） */
  checkedTotal: number
}
