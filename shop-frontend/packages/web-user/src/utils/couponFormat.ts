/**
 * 优惠券展示格式化
 * 用户端"我的优惠券"（UserCouponInfo.couponType）与"领券中心"（CouponInfo.type）共用，
 * 两个列表的展示口径完全一致，只是字段名不同，故门槛文案由各视图传入类型值。
 */
import { CouponType } from '@shop/shared'

/**
 * 格式化金额（去掉小数点末尾的0）
 * 例：20.00 → 20，20.50 → 20.5
 */
export function formatAmount(amount: number): string {
  return Number(amount).toFixed(2).replace(/\.?0+$/, '')
}

/**
 * 格式化折扣率（0.85 → 8.5）
 */
export function formatDiscount(amount: number): string {
  return (Number(amount) * 10).toFixed(1).replace(/\.0$/, '')
}

/**
 * 获取门槛文案：满减券为"满XX元可用"，立减/折扣券无门槛
 * @param couponType - 优惠券类型（UserCouponInfo.couponType 或 CouponInfo.type）
 * @param threshold - 使用门槛金额
 */
export function thresholdText(couponType: number, threshold: number): string {
  if (couponType === CouponType.FULL_REDUCTION) {
    return `满${formatAmount(threshold)}元可用`
  }
  return '无门槛'
}

/**
 * 格式化日期（只取 yyyy-MM-dd）
 * 注意：与 @shop/shared 的 formatDate 不同，这里不做时区解析，只截取字符串
 */
export function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  return dateStr.replace('T', ' ').substring(0, 10)
}
