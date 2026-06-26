/**
 * 秒杀活动相关类型定义
 * 对齐后端 com.shop.model.seckill 包下的实体、DTO、VO 和枚举
 */

/** 秒杀活动状态：0待生效 1进行中 2已结束 3已下架 */
export enum SeckillStatus {
  /** 待生效（还没到秒杀开始时间） */
  PENDING = 0,
  /** 进行中（在秒杀时间窗口内，用户可抢购） */
  ACTIVE = 1,
  /** 已结束（秒杀时间已过） */
  ENDED = 2,
  /** 已下架（手动下架） */
  OFFLINE = 3,
}

/** 秒杀活动状态选项 */
export const seckillStatusOptions = [
  { label: '待生效', value: SeckillStatus.PENDING },
  { label: '进行中', value: SeckillStatus.ACTIVE },
  { label: '已结束', value: SeckillStatus.ENDED },
  { label: '已下架', value: SeckillStatus.OFFLINE },
]

/** 秒杀活动状态中文描述 */
export const seckillStatusTextMap: Record<number, string> = {
  [SeckillStatus.PENDING]: '待生效',
  [SeckillStatus.ACTIVE]: '进行中',
  [SeckillStatus.ENDED]: '已结束',
  [SeckillStatus.OFFLINE]: '已下架',
}

/** 秒杀活动状态标签颜色 */
export const seckillStatusTagMap: Record<number, string> = {
  [SeckillStatus.PENDING]: 'info',
  [SeckillStatus.ACTIVE]: 'success',
  [SeckillStatus.ENDED]: 'warning',
  [SeckillStatus.OFFLINE]: 'danger',
}

/**
 * 秒杀活动信息（对应后端 SeckillVO）
 */
export interface SeckillInfo {
  /** 秒杀活动ID */
  id: number
  /** 商家ID（0表示平台活动） */
  merchantId: number
  /** 商品ID */
  productId: number
  /** SKU ID */
  skuId: number
  /** 秒杀价 */
  seckillPrice: number
  /** 原价 */
  originalPrice: number
  /** 秒杀库存总数 */
  totalCount: number
  /** 剩余库存 */
  availableCount: number
  /** 每人限购数量 */
  limitCount: number
  /** 秒杀开始时间 */
  startTime: string
  /** 秒杀结束时间 */
  endTime: string
  /** 状态 */
  status: number
  /** 状态描述 */
  statusDesc?: string
  /** 活动描述 */
  description?: string
  /** 创建时间 */
  createTime?: string
  /** 秒杀进度百分比（已售/总数*100） */
  progress?: number
}

/** 创建秒杀活动参数 */
export interface SeckillCreateParams {
  /** 商品ID */
  productId: number
  /** SKU ID */
  skuId: number
  /** 秒杀价 */
  seckillPrice: number
  /** 原价 */
  originalPrice: number
  /** 秒杀库存总数 */
  totalCount: number
  /** 每人限购数量 */
  limitCount: number
  /** 秒杀开始时间 */
  startTime: string
  /** 秒杀结束时间 */
  endTime: string
  /** 活动描述 */
  description?: string
}

/** 秒杀活动查询参数 */
export interface SeckillQueryParams {
  /** 状态筛选 */
  status?: number
  /** 页码 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}
