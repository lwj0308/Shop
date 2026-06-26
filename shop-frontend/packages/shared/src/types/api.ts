/**
 * 通用API响应类型
 * 后端统一返回格式，所有接口都遵循这个结构
 * 对齐后端 Result<T> 类：code=200表示成功，其他为失败
 */

/** 后端统一响应格式 */
export interface ApiResponse<T = unknown> {
  /** 业务状态码，200表示成功（和后端Result.success的code保持一致） */
  code: number
  /** 提示信息 */
  message: string
  /** 响应数据 */
  data: T
  /** 服务器时间戳 */
  timestamp: number
}

/** 分页请求参数 */
export interface PageParams {
  /** 当前页码，从1开始 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}

/** 分页响应结果 */
export interface PageResult<T> {
  /** 数据列表 */
  records: T[]
  /** 总记录数 */
  total: number
  /** 当前页码 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
  /** 总页数 */
  pages: number
}
