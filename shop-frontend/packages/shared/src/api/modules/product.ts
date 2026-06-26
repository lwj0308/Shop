/**
 * 商品相关API
 * 包含商品列表、详情、搜索、分类等接口
 */

import { get, post, put, del } from '../request'
import type {
  ProductInfo,
  ProductSearchParams,
  ProductEditParams,
  CategoryInfo,
  CommentInfo,
  CommentReplyParams,
  CommentCreateParams,
  CommentAppendParams,
  PageParams,
  PageResult,
} from '../../types'

/** 获取商品列表（用户端，只看上架商品） */
export function getProductList(params: PageParams & ProductSearchParams) {
  return get<PageResult<ProductInfo>>('/product/list', params)
}

/** 获取商品详情 */
export function getProductDetail(id: number) {
  return get<ProductInfo>(`/product/${id}`)
}

/** 搜索商品 */
export function searchProducts(params: PageParams & ProductSearchParams) {
  return get<PageResult<ProductInfo>>('/product/search', params)
}

/** 获取商品分类树 */
export function getCategoryTree() {
  return get<CategoryInfo[]>('/product/category/tree')
}

/** 获取热门搜索词（基于Redis ZSet，按搜索次数排序） */
export function getHotKeywords() {
  return get<string[]>('/product/hot-keywords')
}

/** 搜索建议（用户输入时实时返回建议词） */
export function getSuggest(keyword: string) {
  return get<string[]>('/product/suggest', { keyword })
}

/** 商家 - 获取自己的商品列表（含下架商品） */
export function getMerchantProductList(params: PageParams & { status?: number }) {
  return get<PageResult<ProductInfo>>('/merchant/product/list', params)
}

/** 商家 - 创建商品 */
export function createProduct(data: ProductEditParams) {
  return post<ProductInfo>('/merchant/product/create', data)
}

/** 商家 - 更新商品 */
export function updateProduct(id: number, data: ProductEditParams) {
  return put<ProductInfo>(`/merchant/product/update/${id}`, data)
}

/** 商家 - 上架/下架商品 */
export function toggleProductStatus(id: number, status: 0 | 1) {
  return put<null>(`/merchant/product/status/${id}`, { status })
}

/** 商家 - 删除商品 */
export function deleteProduct(id: number) {
  return del<null>(`/merchant/product/delete/${id}`)
}

/** 商家 - 获取评价列表（支持按是否已回复筛选） */
export function getMerchantCommentList(params: PageParams & { hasReply?: boolean }) {
  return get<PageResult<CommentInfo>>('/merchant/comment/list', params)
}

/** 商家 - 回复评价 */
export function replyComment(data: CommentReplyParams) {
  return post<null>('/merchant/comment/reply', data)
}

// ==================== 用户端评价接口 ====================

/**
 * 用户发表评价
 * @param data 评价参数（评分+内容+图片+匿名选项）
 */
export function addComment(data: CommentCreateParams) {
  return post<null>('/product/comment', data)
}

/**
 * 用户追加评价（追评）
 * @param data 追评参数（父评价ID+内容+图片）
 */
export function appendComment(data: CommentAppendParams) {
  return post<null>('/product/comment/append', data)
}

/**
 * 查询商品评价列表（带评分类型筛选）
 * @param productId 商品ID
 * @param params 分页参数 + scoreType评分类型
 */
export function getCommentList(
  productId: number,
  params: PageParams & { scoreType?: string },
) {
  return get<PageResult<CommentInfo>>(`/product/comment/list`, { productId, ...params })
}

// ==================== 管理端评价接口 ====================

/**
 * 管理端-查询全平台评价列表
 */
export function getAdminCommentList(params: PageParams & { scoreType?: string }) {
  return get<PageResult<CommentInfo>>('/admin/manage/comment/list', params)
}

/**
 * 管理端-删除评价
 * @param commentId 评价ID
 */
export function deleteComment(commentId: number) {
  return del<null>(`/admin/manage/comment/${commentId}`)
}

/**
 * 管理端-管理员回复评价
 * @param commentId 评价ID
 * @param reply 回复内容
 */
export function adminReplyComment(commentId: number, reply: string) {
  return post<null>(`/admin/manage/comment/${commentId}/reply`, { reply })
}

// ==================== 商品推荐接口 ====================

/**
 * 热销推荐（按销量降序）
 * <p>用于首页"热销推荐"区域，返回销量最高的商品</p>
 * @param limit 返回数量，默认10
 */
export function getHotProducts(limit = 10) {
  return get<ProductInfo[]>('/product/recommend/hot', { limit })
}

/**
 * 新品推荐（按创建时间降序）
 * <p>用于首页"新品推荐"区域，返回最新上架的商品</p>
 * @param limit 返回数量，默认10
 */
export function getNewProducts(limit = 10) {
  return get<ProductInfo[]>('/product/recommend/new', { limit })
}

/**
 * 相关推荐（同分类商品，用于详情页"看了又看"）
 * <p>查询与指定商品同分类的其他商品，按销量降序</p>
 * @param productId 当前商品ID
 * @param limit 返回数量，默认10
 */
export function getRelatedProducts(productId: number, limit = 10) {
  return get<ProductInfo[]>(`/product/recommend/related/${productId}`, { limit })
}

/**
 * 猜你喜欢（基于浏览足迹的个性化推荐）
 * <p>登录用户基于足迹分类推荐，未登录降级为全站热销</p>
 * @param limit 返回数量，默认10
 */
export function getGuessProducts(limit = 10) {
  return get<ProductInfo[]>('/product/recommend/guess', { limit })
}
