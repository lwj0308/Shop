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
 * 被限流时返回 code=202 + 排队号（RL-13 改造），前端需处理 202 响应进入排队轮询
 * @param seckillId 秒杀活动ID
 * @returns code=200 抢购成功；code=202 排队中（data 是排队号）
 */
export function executeSeckill(seckillId: number) {
  return post<string>(`/order/seckill/${seckillId}`)
}

/**
 * 查询秒杀排队位置（RL-13 引入）
 * 前端收到 202 响应后，用排队号轮询这个接口查询当前位置
 * @param seckillId 秒杀活动ID
 * @param queueNo   排队号（executeSeckill 返回的 202 响应中的 data）
 * @returns 排队状态：{ position: 当前位置(1-based, 0表示不在队列中), total: 队列总人数 }
 */
export function getSeckillQueueStatus(seckillId: number, queueNo: string) {
  return get<{ position: number; total: number }>(`/order/seckill/queue/${seckillId}/${queueNo}`)
}
