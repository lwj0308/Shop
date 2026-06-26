/**
 * 优惠券相关类型定义
 * 对齐后端 com.shop.model.coupon 包下的实体、DTO、VO 和枚举
 */

/** 优惠券类型：1满减 2折扣 3立减 */
export enum CouponType {
  /** 满减（满 threshold 元减 amount 元） */
  FULL_REDUCTION = 1,
  /** 折扣（打 amount 折，如 0.85 表示 85 折） */
  DISCOUNT = 2,
  /** 立减（无门槛减 amount 元） */
  DIRECT_DISCOUNT = 3,
}

/** 优惠券类型选项（用于下拉筛选） */
export const couponTypeOptions = [
  { label: '满减', value: CouponType.FULL_REDUCTION },
  { label: '折扣', value: CouponType.DISCOUNT },
  { label: '立减', value: CouponType.DIRECT_DISCOUNT },
]

/** 优惠券类型对应的中文描述 */
export const couponTypeTextMap: Record<number, string> = {
  [CouponType.FULL_REDUCTION]: '满减',
  [CouponType.DISCOUNT]: '折扣',
  [CouponType.DIRECT_DISCOUNT]: '立减',
}

/** 优惠券类型对应的标签颜色（Element Plus 的 type） */
export const couponTypeTagMap: Record<number, string> = {
  [CouponType.FULL_REDUCTION]: 'warning',
  [CouponType.DISCOUNT]: 'success',
  [CouponType.DIRECT_DISCOUNT]: 'primary',
}

/** 优惠券模板状态：0待生效 1进行中 2已结束 3已下架 */
export enum CouponStatus {
  /** 待生效（还没到领取时间） */
  PENDING = 0,
  /** 进行中（在领取时间窗口内） */
  ACTIVE = 1,
  /** 已结束（领取时间已过） */
  ENDED = 2,
  /** 已下架（商家/管理员手动下架） */
  OFFLINE = 3,
}

/** 优惠券状态选项（用于下拉筛选） */
export const couponStatusOptions = [
  { label: '待生效', value: CouponStatus.PENDING },
  { label: '进行中', value: CouponStatus.ACTIVE },
  { label: '已结束', value: CouponStatus.ENDED },
  { label: '已下架', value: CouponStatus.OFFLINE },
]

/** 优惠券状态对应的中文描述 */
export const couponStatusTextMap: Record<number, string> = {
  [CouponStatus.PENDING]: '待生效',
  [CouponStatus.ACTIVE]: '进行中',
  [CouponStatus.ENDED]: '已结束',
  [CouponStatus.OFFLINE]: '已下架',
}

/** 优惠券状态对应的标签颜色（Element Plus 的 type） */
export const couponStatusTagMap: Record<number, string> = {
  [CouponStatus.PENDING]: 'info',
  [CouponStatus.ACTIVE]: 'success',
  [CouponStatus.ENDED]: 'warning',
  [CouponStatus.OFFLINE]: 'danger',
}

/** 用户优惠券状态：0未使用 1已使用 2已过期 */
export enum UserCouponStatus {
  /** 未使用 */
  UNUSED = 0,
  /** 已使用 */
  USED = 1,
  /** 已过期 */
  EXPIRED = 2,
}

/** 用户优惠券状态选项（用于下拉筛选） */
export const userCouponStatusOptions = [
  { label: '未使用', value: UserCouponStatus.UNUSED },
  { label: '已使用', value: UserCouponStatus.USED },
  { label: '已过期', value: UserCouponStatus.EXPIRED },
]

/** 用户优惠券状态对应的中文描述 */
export const userCouponStatusTextMap: Record<number, string> = {
  [UserCouponStatus.UNUSED]: '未使用',
  [UserCouponStatus.USED]: '已使用',
  [UserCouponStatus.EXPIRED]: '已过期',
}

/** 用户优惠券状态对应的标签颜色（Element Plus 的 type） */
export const userCouponStatusTagMap: Record<number, string> = {
  [UserCouponStatus.UNUSED]: 'success',
  [UserCouponStatus.USED]: 'info',
  [UserCouponStatus.EXPIRED]: 'danger',
}

/**
 * 优惠券模板信息（对应后端 CouponVO）
 * 商家端和管理端展示优惠券列表时用
 */
export interface CouponInfo {
  /** 优惠券ID */
  id: number
  /** 商家ID（0表示平台券） */
  merchantId: number
  /** 优惠券名称 */
  name: string
  /** 类型：1满减 2折扣 3立减 */
  type: number
  /** 类型描述（后端翻译好的中文，如"满减"） */
  typeDesc?: string
  /**
   * 面额
   * - 满减/立减：金额（如 20.00 表示减20元）
   * - 折扣：折扣率（如 0.85 表示85折）
   */
  amount: number
  /** 使用门槛金额（满减用；立减和折扣为0） */
  threshold: number
  /** 发放总量（0表示不限量） */
  totalCount: number
  /** 已领取数量 */
  receivedCount: number
  /** 已使用数量 */
  usedCount: number
  /** 每人限领数量 */
  perLimit: number
  /** 领取开始时间 */
  receiveStartTime: string
  /** 领取结束时间 */
  receiveEndTime: string
  /** 有效期开始时间 */
  validStartTime: string
  /** 有效期结束时间 */
  validEndTime: string
  /** 状态：0待生效 1进行中 2已结束 3已下架 */
  status: number
  /** 状态描述（后端翻译好的中文） */
  statusDesc?: string
  /** 描述说明 */
  description?: string
  /** 创建时间 */
  createTime?: string
  /** 剩余可领取数量（totalCount=0 表示不限量返回 -1） */
  remainCount?: number
}

/**
 * 用户优惠券信息（对应后端 UserCouponVO）
 * 用户端"我的优惠券"列表展示用
 */
export interface UserCouponInfo {
  /** 用户券ID（user_coupon 表的主键） */
  id: number
  /** 用户ID */
  userId: number
  /** 优惠券模板ID */
  couponId: number
  /** 商家ID（0表示平台券） */
  merchantId: number
  /** 优惠券名称 */
  couponName: string
  /** 优惠券类型：1满减 2折扣 3立减 */
  couponType: number
  /** 优惠券类型描述（后端翻译好的中文） */
  couponTypeDesc?: string
  /** 面额 */
  amount: number
  /** 使用门槛 */
  threshold: number
  /** 有效期开始时间 */
  validStartTime: string
  /** 有效期结束时间 */
  validEndTime: string
  /** 状态：0未使用 1已使用 2已过期 */
  status: number
  /** 状态描述（后端翻译好的中文） */
  statusDesc?: string
  /** 使用的订单号 */
  orderNo?: string
  /** 领取时间 */
  getTime: string
  /** 使用时间 */
  useTime?: string
  /** 计算的优惠金额（下单选券时返回） */
  discountAmount?: number
}

/** 创建优惠券参数（对应后端 CouponCreateDTO） */
export interface CouponCreateParams {
  /** 优惠券名称 */
  name: string
  /** 类型：1满减 2折扣 3立减 */
  type: number
  /** 面额（满减/立减为金额，折扣为折扣率如0.85） */
  amount: number
  /** 使用门槛金额（满减用，立减和折扣传0） */
  threshold: number
  /** 发放总量（0表示不限量） */
  totalCount: number
  /** 每人限领数量 */
  perLimit: number
  /** 领取开始时间 */
  receiveStartTime: string
  /** 领取结束时间 */
  receiveEndTime: string
  /** 有效期开始时间 */
  validStartTime: string
  /** 有效期结束时间 */
  validEndTime: string
  /** 描述说明 */
  description?: string
}

/** 优惠券查询参数 */
export interface CouponQueryParams {
  /** 优惠券状态筛选（可选） */
  status?: number
  /** 优惠券类型筛选（可选） */
  type?: number
  /** 页码（从1开始） */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}
