/**
 * 订单相关类型定义
 * 对齐后端 OrderInfo、OrderItem、OrderVO 等实体
 */

/** 订单状态枚举值类型（对齐后端 OrderStatusEnum） */
export type OrderStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7

/** 订单信息（详情页使用，包含完整信息） */
export interface OrderInfo {
  /** 订单ID */
  id: number
  /** 订单编号 */
  orderNo: string
  /** 用户ID */
  userId: number
  /** 商家ID */
  merchantId: number
  /** 商家名称 */
  merchantName: string
  /** 订单状态：参考 ORDER_STATUS 常量 */
  status: OrderStatus
  /** 订单项列表 */
  items: OrderItem[]
  /** 商品总金额（分） */
  totalAmount: number
  /** 实付金额（分） */
  payAmount: number
  /** 运费（分） */
  freightAmount: number
  /** 优惠金额（分） */
  discountAmount: number
  /** 收货地址 */
  address: OrderAddress
  /** 备注 */
  remark: string
  /** 取消原因 */
  cancelReason: string | null
  /** 支付时间 */
  payTime: string | null
  /** 发货时间 */
  deliveryTime: string | null
  /** 收货时间 */
  receiveTime: string | null
  /** 完成时间 */
  finishTime: string | null
  /** 取消时间 */
  cancelTime: string | null
  /** 创建时间 */
  createTime: string
}

/** 订单项信息 */
export interface OrderItem {
  /** 订单项ID */
  id: number
  /** 商品ID */
  productId: number
  /** SKU ID */
  skuId: number
  /** 商品名称 */
  productName: string
  /** 商品主图 */
  productImage: string
  /** SKU规格名称 */
  skuName: string
  /** 单价（分） */
  price: number
  /** 购买数量 */
  quantity: number
  /** 小计金额（分） */
  subtotal: number
  /** 是否已评价：0未评价 1已评价（订单完成后用于显示"去评价"按钮） */
  isReviewed: 0 | 1
}

/** 订单收货地址 */
export interface OrderAddress {
  /** 收货人姓名 */
  name: string
  /** 联系电话 */
  phone: string
  /** 省份 */
  province: string
  /** 城市 */
  city: string
  /** 区县 */
  district: string
  /** 详细地址 */
  detail: string
}

/** 创建订单参数 */
export interface CreateOrderParams {
  /** 收货地址ID */
  addressId: number
  /** 购物车项ID列表 */
  cartItemIds: number[]
  /** 备注 */
  remark?: string
  /** 用户优惠券ID（可选，用户下单时选择的优惠券） */
  userCouponId?: number
}

/** 订单列表查询参数 */
export interface OrderListParams {
  /** 订单状态筛选 */
  status?: OrderStatus
}

/** 订单列表项（对齐后端 OrderVO，列表页使用） */
export interface OrderListItem {
  /** 订单ID */
  id: number
  /** 订单号 */
  orderNo: string
  /** 订单总金额（分） */
  totalAmount: number
  /** 实付金额（分） */
  payAmount: number
  /** 订单状态 */
  status: OrderStatus
  /** 订单状态描述（后端直接返回中文） */
  statusDesc: string
  /** 第一件商品图片（列表展示用） */
  firstItemImage: string
  /** 商品总数量 */
  totalQuantity: number
  /** 创建时间 */
  createTime: string
  /** 支付时间 */
  payTime: string | null
}
