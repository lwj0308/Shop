/**
 * 管理后台API接口
 *
 * 所有与管理后台相关的API请求都在这里定义。
 * 包括登录认证、管理员管理、角色权限、内容管理等。
 */

import request from '../request'
import type { ApiResponse, PageParams, PageResult } from '../../types/api'

// ========== 认证相关 ==========

/** 管理员登录请求参数 */
export interface AdminLoginParams {
  username: string
  password: string
  captchaKey: string
  captchaCode: string
}

/** 管理员登录响应数据 */
export interface AdminLoginResult {
  token: string
  adminUserId: number
  username: string
  nickname: string
}

/** 验证码响应数据 */
export interface CaptchaResult {
  key: string
  image: string
}

/**
 * 获取验证码
 * @returns 验证码图片（Base64）和Key
 */
export function getCaptcha() {
  return request.post<ApiResponse<CaptchaResult>>('/admin/auth/captcha')
}

/**
 * 管理员登录
 * @param data 登录参数
 * @returns Token和管理员基本信息
 */
export function adminLogin(data: AdminLoginParams) {
  return request.post<ApiResponse<AdminLoginResult>>('/admin/auth/login', data)
}

/**
 * 管理员登出
 */
export function adminLogout() {
  return request.post<ApiResponse<void>>('/admin/auth/logout')
}

/**
 * 获取当前管理员信息
 * @returns 管理员详情（含角色和权限列表）
 */
export function getAdminInfo() {
  return request.get<ApiResponse<any>>('/admin/user/current')
}

// ========== 管理员管理 ==========

/**
 * 获取管理员列表
 */
export function getAdminUserList(params: PageParams & { keyword?: string; status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/user/list', { params })
}

/**
 * 创建管理员
 */
export function createAdminUser(data: any) {
  return request.post<ApiResponse<void>>('/admin/user', data)
}

/**
 * 更新管理员
 */
export function updateAdminUser(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/user/${id}`, data)
}

/**
 * 删除管理员
 */
export function deleteAdminUser(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/user/${id}`)
}

/**
 * 修改管理员状态（启用/禁用）
 */
export function updateAdminUserStatus(id: number, status: number) {
  return request.put<ApiResponse<void>>(`/admin/user/${id}/status`, null, { params: { status } })
}

/**
 * 重置管理员密码（重置为默认密码 123456）
 * @param id 管理员ID
 */
export function resetAdminUserPassword(id: number) {
  return request.put<ApiResponse<void>>(`/admin/user/${id}/reset-password`)
}

// ========== 角色管理 ==========

export function getAdminRoleList(params: PageParams & { keyword?: string }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/role/list', { params })
}

export function getAllRoles() {
  return request.get<ApiResponse<any[]>>('/admin/role/all')
}

export function createAdminRole(data: any) {
  return request.post<ApiResponse<void>>('/admin/role', data)
}

export function updateAdminRole(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/role/${id}`, data)
}

export function deleteAdminRole(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/role/${id}`)
}

// ========== 权限管理 ==========

export function getPermissionTree() {
  return request.get<ApiResponse<any[]>>('/admin/permission/tree')
}

export function createPermission(data: any) {
  return request.post<ApiResponse<void>>('/admin/permission', data)
}

export function updatePermission(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/permission/${id}`, data)
}

export function deletePermission(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/permission/${id}`)
}

// ========== 部门管理 ==========

export function getDeptTree() {
  return request.get<ApiResponse<any[]>>('/admin/dept/tree')
}

export function createDept(data: any) {
  return request.post<ApiResponse<void>>('/admin/dept', data)
}

export function updateDept(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/dept/${id}`, data)
}

export function deleteDept(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/dept/${id}`)
}

// ========== 仪表盘 ==========

export function getDashboardOverview() {
  return request.get<ApiResponse<any>>('/admin/dashboard/overview')
}

// ========== 内容管理 ==========

export function getBannerList() {
  return request.get<ApiResponse<any[]>>('/admin/content/banner/list')
}

export function createBanner(data: any) {
  return request.post<ApiResponse<void>>('/admin/content/banner', data)
}

export function updateBanner(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/content/banner/${id}`, data)
}

export function deleteBanner(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/content/banner/${id}`)
}

export function getNoticeList(params: PageParams & { type?: number; status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/content/notice/list', { params })
}

export function createNotice(data: any) {
  return request.post<ApiResponse<void>>('/admin/content/notice', data)
}

export function updateNotice(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/content/notice/${id}`, data)
}

export function deleteNotice(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/content/notice/${id}`)
}

// ========== 日志管理 ==========

export function getOperationLogList(params: PageParams & { module?: string; operationType?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/log/operation/list', { params })
}

export function getLoginLogList(params: PageParams & { username?: string }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/log/login/list', { params })
}

// ========== 安全审计 ==========

export function getSecurityEventList(params: PageParams & { eventType?: number; status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/security/event/list', { params })
}

export function handleSecurityEvent(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/security/event/${id}/handle`, data)
}

// ========== 业务管理（通过 shop-admin Feign 代理调用） ==========

/** 用户管理 */
export function getManageUserList(params: PageParams & { keyword?: string; status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/manage/user/list', { params })
}

export function disableManageUser(userId: number) {
  return request.put<ApiResponse<void>>(`/admin/manage/user/${userId}/disable`)
}

export function enableManageUser(userId: number) {
  return request.put<ApiResponse<void>>(`/admin/manage/user/${userId}/enable`)
}

/** 商家管理 */
export function getManageMerchantList(params: PageParams & { keyword?: string; status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/manage/merchant/list', { params })
}

export function auditMerchant(data: { merchantId: number; approved: boolean; remark?: string }) {
  return request.put<ApiResponse<void>>('/admin/manage/merchant/audit', data)
}

export function disableManageMerchant(id: number) {
  return request.put<ApiResponse<void>>(`/admin/manage/merchant/${id}/disable`)
}

export function enableManageMerchant(id: number) {
  return request.put<ApiResponse<void>>(`/admin/manage/merchant/${id}/enable`)
}

/** 商品管理 */
export function getManageProductList(params: PageParams & { keyword?: string; status?: number; categoryId?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/manage/product/list', { params })
}

export function offShelfManageProduct(id: number) {
  return request.put<ApiResponse<void>>(`/admin/manage/product/${id}/off-shelf`)
}

export function onShelfManageProduct(id: number) {
  return request.put<ApiResponse<void>>(`/admin/manage/product/${id}/on-shelf`)
}

/** 分类管理 */
export function getManageCategoryTree() {
  return request.get<ApiResponse<any[]>>('/admin/manage/category/tree')
}

export function addManageCategory(data: any) {
  return request.post<ApiResponse<void>>('/admin/manage/category', data)
}

export function updateManageCategory(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/manage/category/${id}`, data)
}

export function deleteManageCategory(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/manage/category/${id}`)
}

/** 品牌管理 */
export function getManageBrandList() {
  return request.get<ApiResponse<any[]>>('/admin/manage/brand/list')
}

export function addManageBrand(data: any) {
  return request.post<ApiResponse<void>>('/admin/manage/brand', data)
}

export function updateManageBrand(id: number, data: any) {
  return request.put<ApiResponse<void>>(`/admin/manage/brand/${id}`, data)
}

export function deleteManageBrand(id: number) {
  return request.delete<ApiResponse<void>>(`/admin/manage/brand/${id}`)
}

/** 订单管理 */
export function getManageOrderList(params: PageParams & { orderNo?: string; status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/manage/order/list', { params })
}

export function deliverManageOrder(id: number, logisticsNo: string, logisticsCompany: string) {
  return request.put<ApiResponse<void>>(`/admin/manage/order/${id}/deliver`, null, { params: { logisticsNo, logisticsCompany } })
}

/** 退款管理 */
export function getManageRefundList(params: PageParams & { status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/manage/refund/list', { params })
}

export function auditManageRefund(data: { refundId: number; approved: boolean; remark?: string }) {
  return request.put<ApiResponse<void>>('/admin/manage/refund/audit', data)
}

/** 提现审核 */

/**
 * 查询提现申请列表
 * 管理员查看全平台商家的提现申请，支持按状态筛选
 * @param params 分页参数 + 可选的提现状态筛选
 */
export function getManageWithdrawList(params: PageParams & { status?: number }) {
  return request.get<ApiResponse<PageResult<any>>>('/admin/manage/withdraw/list', { params })
}

/**
 * 审核提现申请
 * @param data 审核参数（提现ID、审核结果、备注）
 * - status=1 通过：冻结金额扣减（钱已打款）
 * - status=2 拒绝：冻结金额转回可用余额（解冻）
 */
export function auditManageWithdraw(data: { id: number; status: number; auditRemark?: string }) {
  return request.put<ApiResponse<void>>('/admin/manage/withdraw/audit', data)
}
