/**
 * 满减活动相关类型定义
 * 对齐后端 com.shop.model.promotion 包下的实体、DTO、VO 和枚举
 */

/** 满减活动状态：0待生效 1进行中 2已结束 3已下架 */
export enum PromotionStatus {
  /** 待生效（还没到活动开始时间） */
  PENDING = 0,
  /** 进行中（在活动时间窗口内） */
  ACTIVE = 1,
  /** 已结束（活动时间已过） */
  ENDED = 2,
  /** 已下架（商家/管理员手动下架） */
  OFFLINE = 3,
}

/** 满减活动状态选项（用于下拉筛选） */
export const promotionStatusOptions = [
  { label: '待生效', value: PromotionStatus.PENDING },
  { label: '进行中', value: PromotionStatus.ACTIVE },
  { label: '已结束', value: PromotionStatus.ENDED },
  { label: '已下架', value: PromotionStatus.OFFLINE },
]

/** 满减活动状态对应的中文描述 */
export const promotionStatusTextMap: Record<number, string> = {
  [PromotionStatus.PENDING]: '待生效',
  [PromotionStatus.ACTIVE]: '进行中',
  [PromotionStatus.ENDED]: '已结束',
  [PromotionStatus.OFFLINE]: '已下架',
}

/** 满减活动状态对应的标签颜色（Element Plus 的 type） */
export const promotionStatusTagMap: Record<number, string> = {
  [PromotionStatus.PENDING]: 'info',
  [PromotionStatus.ACTIVE]: 'success',
  [PromotionStatus.ENDED]: 'warning',
  [PromotionStatus.OFFLINE]: 'danger',
}

/** 满减活动参与范围：1全店 2指定商品 */
export enum PromotionScopeType {
  /** 全店（商家所有商品都参与满减） */
  ALL = 1,
  /** 指定商品（只有关联的商品参与满减） */
  SPECIFIED = 2,
}

/** 满减活动参与范围选项 */
export const promotionScopeTypeOptions = [
  { label: '全店', value: PromotionScopeType.ALL },
  { label: '指定商品', value: PromotionScopeType.SPECIFIED },
]

/** 满减活动参与范围对应的中文描述 */
export const promotionScopeTypeTextMap: Record<number, string> = {
  [PromotionScopeType.ALL]: '全店',
  [PromotionScopeType.SPECIFIED]: '指定商品',
}

/** 满减活动参与范围对应的标签颜色 */
export const promotionScopeTypeTagMap: Record<number, string> = {
  [PromotionScopeType.ALL]: 'primary',
  [PromotionScopeType.SPECIFIED]: 'warning',
}

/**
 * 满减活动信息（对应后端 PromotionVO）
 * 商家端和管理端展示满减活动列表时用
 */
export interface PromotionInfo {
  /** 满减活动ID */
  id: number
  /** 商家ID（0表示平台活动） */
  merchantId: number
  /** 活动名称 */
  name: string
  /** 满减门槛金额（满多少元） */
  threshold: number
  /** 优惠金额（减多少元） */
  discountAmount: number
  /** 参与范围：1全店 2指定商品 */
  scopeType: number
  /** 参与范围描述（后端翻译好的中文，如"全店"） */
  scopeTypeDesc?: string
  /** 活动开始时间 */
  startTime: string
  /** 活动结束时间 */
  endTime: string
  /** 状态：0待生效 1进行中 2已结束 3已下架 */
  status: number
  /** 状态描述（后端翻译好的中文） */
  statusDesc?: string
  /** 活动描述 */
  description?: string
  /** 创建时间 */
  createTime?: string
  /** 参与商品ID列表（仅 scopeType=2 时返回） */
  productIds?: number[]
}

/** 创建满减活动参数（对应后端 PromotionCreateDTO） */
export interface PromotionCreateParams {
  /** 活动名称 */
  name: string
  /** 满减门槛金额 */
  threshold: number
  /** 优惠金额 */
  discountAmount: number
  /** 参与范围：1全店 2指定商品 */
  scopeType: number
  /** 活动开始时间 */
  startTime: string
  /** 活动结束时间 */
  endTime: string
  /** 活动描述 */
  description?: string
  /** 参与商品ID列表（仅 scopeType=2 时需要传） */
  productIds?: number[]
}

/** 满减活动查询参数 */
export interface PromotionQueryParams {
  /** 状态筛选（可选） */
  status?: number
  /** 页码（从1开始） */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}
