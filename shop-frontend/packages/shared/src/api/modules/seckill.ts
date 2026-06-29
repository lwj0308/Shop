/**
 * 秒杀活动相关API
 * 包含商家端、管理端和用户端接口
 *
 * 商家端：/seckill/*（直接调用 shop-seckill）
 * 管理端：/admin/manage/seckill/*（通过 shop-admin 转发）
 * 用户端：/order/seckill/*（调用 shop-order 秒杀接口）
 */

import { get, post, put } from '../request'
import type {
  SeckillInfo,
  SeckillCreateParams,
  SeckillQueryParams,
} from '../../types'
import type { PageResult } from '../../types/api'

// ==================== 商家端秒杀活动接口 ====================

/**
 * 商家创建秒杀活动
 * @param data 秒杀活动参数
 */
export function createMerchantSeckill(data: SeckillCreateParams) {
  return post<number>('/seckill', data)
}

/**
 * 商家下架秒杀活动
 * @param seckillId 秒杀活动ID
 */
export function offlineMerchantSeckill(seckillId: number) {
  return put<null>(`/seckill/${seckillId}/offline`)
}

/**
 * 查询商家自己的秒杀活动列表
 */
export function getMerchantSeckillList(params: SeckillQueryParams) {
  return get<PageResult<SeckillInfo>>('/seckill/list', params)
}

/**
 * 查询秒杀活动详情
 */
export function getMerchantSeckillDetail(seckillId: number) {
  return get<SeckillInfo>(`/seckill/${seckillId}`)
}

// ==================== 管理端秒杀活动接口 ====================

/**
 * 管理员创建平台秒杀活动
 */
export function createAdminSeckill(data: SeckillCreateParams) {
  return post<number>('/admin/manage/seckill', data)
}

/**
 * 查询全平台秒杀活动列表
 */
export function getAdminSeckillList(params: SeckillQueryParams) {
  return get<PageResult<SeckillInfo>>('/admin/manage/seckill/list', params)
}

/**
 * 管理员下架秒杀活动
 */
export function offlineAdminSeckill(seckillId: number) {
  return put<null>(`/admin/manage/seckill/${seckillId}/offline`)
}

// ==================== 用户端秒杀接口 ====================

/**
 * 用户端查询进行中的秒杀活动列表（不需要登录）
 * 返回所有进行中的秒杀活动
 */
export function getPublicSeckillList() {
  return get<SeckillInfo[]>('/seckill/public/list')
}

/**
 * 用户端查询秒杀活动详情（不需要登录）
 * @param seckillId 秒杀活动ID
 */
export function getPublicSeckillDetail(seckillId: number) {
  return get<SeckillInfo>(`/seckill/public/${seckillId}`)
}

/**
 * 用户秒杀抢购
 * 调用 shop-order 的秒杀接口，执行 Redis Lua 脚本扣减库存
 * 成功后返回"抢购成功，正在创建订单"，订单异步创建
 * @param seckillId 秒杀活动ID
 */
export function executeSeckill(seckillId: number) {
  return post<string>(`/order/seckill/${seckillId}`)
}
