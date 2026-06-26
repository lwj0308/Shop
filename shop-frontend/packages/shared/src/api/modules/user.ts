/**
 * 用户相关API
 * 包含登录、注册、获取用户信息、收货地址、收藏、足迹等接口
 */

import { get, post, put, del } from '../request'
import type { LoginParams, LoginResult, RegisterParams, UserInfo, AddressInfo, AddressParams, PageParams, PageResult } from '../../types'

/** 收藏项（用户收藏的商品记录） */
export interface FavoriteItem {
  /** 记录ID */
  id: number
  /** 用户ID */
  userId: number
  /** 商品ID */
  productId: number
  /** 收藏时间 */
  createTime: string
}

/** 足迹项（用户浏览过的商品记录） */
export interface FootprintItem {
  /** 记录ID */
  id: number
  /** 用户ID */
  userId: number
  /** 商品ID */
  productId: number
  /** 商品分类ID */
  categoryId?: number
  /** 浏览时间 */
  createTime: string
}

/** 用户登录 */
export function userLogin(data: LoginParams) {
  return post<LoginResult>('/user/auth/login', data)
}

/** 用户注册 */
export function userRegister(data: RegisterParams) {
  return post<null>('/user/auth/register', data)
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return get<UserInfo>('/user/info')
}

/** 修改用户信息 */
export function updateUserInfo(data: Partial<UserInfo>) {
  return put<UserInfo>('/user/info', data)
}

/** 退出登录（通知后端使Token失效） */
export function userLogout() {
  return post<null>('/user/auth/logout')
}

/** 发送短信验证码 */
export function sendVerifyCode(phone: string) {
  return post<null>('/user/auth/send-code', { phone })
}

/** 获取收货地址列表（默认地址排前面） */
export function getAddressList() {
  return get<AddressInfo[]>('/user/address/list')
}

/** 添加收货地址 */
export function addAddress(data: AddressParams) {
  return post<AddressInfo>('/user/address', data)
}

/** 修改收货地址 */
export function updateAddress(id: number, data: AddressParams) {
  return put<AddressInfo>(`/user/address/${id}`, data)
}

/** 删除收货地址 */
export function deleteAddress(id: number) {
  return del<null>(`/user/address/${id}`)
}

/** 设为默认地址 */
export function setDefaultAddress(id: number) {
  return put<null>(`/user/address/${id}/default`)
}

// ==================== 收藏接口 ====================

/**
 * 添加收藏
 * <p>收藏指定商品，后端通过唯一索引保证不能重复收藏</p>
 * @param productId 商品ID
 */
export function addFavorite(productId: number) {
  return post<null>(`/user/favorite/${productId}`)
}

/**
 * 取消收藏
 * @param productId 商品ID
 */
export function removeFavorite(productId: number) {
  return del<null>(`/user/favorite/${productId}`)
}

/**
 * 获取收藏列表（分页）
 * <p>按收藏时间倒序，最新收藏排在最前面</p>
 * @param params 分页参数
 */
export function getFavoriteList(params: PageParams) {
  return get<PageResult<FavoriteItem>>('/user/favorite/list', params)
}

// ==================== 足迹接口 ====================

/**
 * 获取浏览足迹列表（分页）
 * <p>按浏览时间倒序，最近浏览的排在最前面</p>
 * @param params 分页参数
 */
export function getFootprintList(params: PageParams) {
  return get<PageResult<FootprintItem>>('/user/footprint/list', params)
}
