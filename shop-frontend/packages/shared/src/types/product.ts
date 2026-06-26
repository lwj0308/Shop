/**
 * 商品相关类型定义
 * 包含商品信息、SKU、分类等类型
 * 对齐后端 Product 相关实体和 VO
 */

/** 商品状态枚举 */
export type ProductStatus = 0 | 1

/** 商品信息 */
export interface ProductInfo {
  /** 商品ID */
  id: number
  /** 店铺ID */
  shopId: number
  /** 店铺名称 */
  shopName: string | null
  /** 商品名称 */
  name: string
  /** 副标题 */
  subtitle: string
  /** 商品详情（HTML格式） */
  detail: string | null
  /** 商品主图URL */
  mainImage: string
  /** 商品图片列表 */
  images: string[]
  /** 商品分类ID */
  categoryId: number
  /** 商品分类名称 */
  categoryName: string
  /** 品牌ID */
  brandId: number
  /** 品牌名称 */
  brandName: string
  /** 最低价格 */
  minPrice: number
  /** 商品状态：0-下架 1-上架 */
  status: ProductStatus
  /** 销量（用于热销推荐排序，值越大越热门） */
  sales?: number
  /** 浏览量（用于热度统计） */
  viewCount?: number
  /** 总库存 */
  totalStock: number
  /** SKU列表 */
  skus: ProductSku[]
  /** 规格列表（按规格名分组，如颜色、存储） */
  specs: ProductSpec[]
  /** 评论摘要 */
  commentSummary: {
    /** 平均评分 */
    avgScore: number
    /** 好评率 */
    goodRate: number
    /** 评论总数 */
    totalCount: number
  } | null
  /** 创建时间 */
  createTime: string
}

/** 商品规格（如颜色、存储等） */
export interface ProductSpec {
  /** 规格名称（如：颜色、存储） */
  name: string
  /** 规格可选值列表（如：["红色", "蓝色"]） */
  values: string[]
}

/** 商品SKU信息 */
export interface ProductSku {
  /** SKU ID */
  id: number
  /** 商品ID */
  productId: number
  /** 规格值（如：{颜色: 红色, 存储: 256GB}） */
  specValues: Record<string, string>
  /** 价格 */
  price: number
  /** 原价 */
  originalPrice: number
  /** 库存数量 */
  stock: number
  /** SKU图片URL */
  image: string
  /** SKU状态：0-禁用 1-正常 */
  status: number
}

/** 商品分类 */
export interface CategoryInfo {
  /** 分类ID */
  id: number
  /** 分类名称 */
  name: string
  /** 父分类ID，0表示顶级分类 */
  parentId: number
  /** 分类图标 */
  icon: string
  /** 排序值 */
  sort: number
  /** 子分类列表 */
  children?: CategoryInfo[]
}

/** 商品搜索参数 */
export interface ProductSearchParams {
  /** 搜索关键词 */
  keyword?: string
  /** 分类ID */
  categoryId?: number
  /** 最低价格（分） */
  minPrice?: number
  /** 最高价格（分） */
  maxPrice?: number
  /** 排序字段：price/sales/createTime */
  sortBy?: string
  /** 排序方式：asc/desc */
  sortOrder?: 'asc' | 'desc'
}

/** 商品创建/编辑参数 */
export interface ProductEditParams {
  /** 商品名称 */
  name: string
  /** 商品描述 */
  description: string
  /** 商品主图URL */
  mainImage: string
  /** 商品图片列表 */
  images: string[]
  /** 商品分类ID */
  categoryId: number
  /** SKU列表 */
  skus: Omit<ProductSku, 'id' | 'productId'>[]
}

/** 商品评价信息 */
export interface CommentInfo {
  /** 评价ID */
  id: number
  /** 商品ID */
  productId: number
  /** 商品名称（商家端评价管理列表展示用） */
  productName?: string
  /** 用户ID */
  userId: number
  /** 用户昵称 */
  userNickname?: string
  /** 用户头像 */
  userAvatar?: string
  /** 评价内容 */
  content: string
  /** 评价图片列表 */
  images: string[]
  /** 评分：1-5分 */
  score: number
  /** 商家回复内容 */
  reply?: string | null
  /** 是否匿名评价：0否 1是 */
  isAnonymous?: number
  /** 评价类型：0初始评价 1追评 */
  commentType?: number
  /** 父评价ID（追评时指向初始评价ID） */
  parentId?: number | null
  /** 追评列表（查询初始评价时附带其追评） */
  replyList?: CommentInfo[]
  /** 评价时间 */
  createTime: string
}

/** 评分类型筛选 */
export enum CommentScoreType {
  /** 全部评价 */
  ALL = 'all',
  /** 好评（4-5分） */
  GOOD = 'good',
  /** 中评（3分） */
  MEDIUM = 'medium',
  /** 差评（1-2分） */
  BAD = 'bad',
}

/** 评分类型选项 */
export const commentScoreTypeOptions = [
  { label: '全部', value: CommentScoreType.ALL },
  { label: '好评', value: CommentScoreType.GOOD },
  { label: '中评', value: CommentScoreType.MEDIUM },
  { label: '差评', value: CommentScoreType.BAD },
]

/** 商家回复评价参数 */
export interface CommentReplyParams {
  /** 评价ID */
  commentId: number
  /** 回复内容 */
  reply: string
}

/** 发表评价参数 */
export interface CommentCreateParams {
  /** 商品ID */
  productId: number
  /** 订单ID */
  orderId: number
  /** 订单明细ID */
  orderItemId: number
  /** 评分：1-5分 */
  score: number
  /** 评价内容 */
  content: string
  /** 评价图片列表 */
  images?: string[]
  /** 是否匿名评价 */
  isAnonymous?: boolean
}

/** 追评参数 */
export interface CommentAppendParams {
  /** 父评价ID（初始评价的ID） */
  parentId: number
  /** 追评内容 */
  content: string
  /** 追评图片列表 */
  images?: string[]
}
