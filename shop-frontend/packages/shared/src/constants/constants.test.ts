/**
 * constants 常量文件统一测试
 * <p>
 * shared 包的 constants 目录下有 5 个状态常量文件：
 * order.ts、payment.ts、product.ts、refund.ts、merchant.ts
 * 这些文件都是纯常量定义，没有逻辑函数，所以测试重点是：
 * 1. 枚举值和后端定义一致（防止改错）
 * 2. 状态映射表（XX_MAP）覆盖了所有枚举值（防止漏配）
 * 3. 映射表每项都有 label 和 color 字段（防止缺字段）
 * </p>
 */
import { describe, it, expect } from 'vitest'
import { ORDER_STATUS, ORDER_STATUS_MAP } from './order'
import { PAY_STATUS, PAY_STATUS_MAP, PAY_METHOD, PAY_METHOD_MAP } from './payment'
import { PRODUCT_STATUS, PRODUCT_STATUS_MAP } from './product'
import { REFUND_STATUS, REFUND_STATUS_MAP } from './refund'
import { MERCHANT_STATUS, MERCHANT_STATUS_MAP } from './merchant'

/**
 * 通用工具：验证一个状态枚举和它的映射表是否一致
 * @param statusEnum - 状态枚举对象（如 ORDER_STATUS）
 * @param statusMap - 状态映射表（如 ORDER_STATUS_MAP）
 * @param expectedValues - 期望的枚举值列表（如 [0, 1, 2, 3, 4, 5, 6, 7]）
 */
function assertEnumAndMapConsistent(
  statusEnum: Record<string, number>,
  statusMap: Record<number, { label: string; color: string }>,
  expectedValues: number[],
): void {
  // 1. 验证枚举值和期望一致
  const actualValues = Object.values(statusEnum).sort((a, b) => a - b)
  expect(actualValues).toEqual(expectedValues)

  // 2. 验证映射表覆盖了所有枚举值
  expectedValues.forEach(value => {
    expect(statusMap[value]).toBeDefined()
  })

  // 3. 验证映射表每项都有 label 和 color 字段
  expectedValues.forEach(value => {
    expect(statusMap[value].label).toBeTruthy()
    expect(statusMap[value].color).toMatch(/^#[0-9A-Fa-f]{6}$/)
  })
}

describe('ORDER_STATUS 订单状态常量', () => {
  it('枚举值应和后端 OrderStatusEnum 一致（0-7）', () => {
    expect(ORDER_STATUS.UNPAID).toBe(0)
    expect(ORDER_STATUS.CANCELLED).toBe(1)
    expect(ORDER_STATUS.PENDING_DELIVERY).toBe(2)
    expect(ORDER_STATUS.SHIPPING).toBe(3)
    expect(ORDER_STATUS.RECEIVED).toBe(4)
    expect(ORDER_STATUS.COMPLETED).toBe(5)
    expect(ORDER_STATUS.REFUNDING).toBe(6)
    expect(ORDER_STATUS.REFUNDED).toBe(7)
  })

  it('ORDER_STATUS_MAP 应覆盖所有状态且每项有 label 和 color', () => {
    assertEnumAndMapConsistent(ORDER_STATUS, ORDER_STATUS_MAP, [0, 1, 2, 3, 4, 5, 6, 7])
  })

  it('待付款状态描述应为黄色', () => {
    expect(ORDER_STATUS_MAP[0].label).toBe('待付款')
    expect(ORDER_STATUS_MAP[0].color).toBe('#E6A23C')
  })
})

describe('PAY_STATUS 支付状态常量', () => {
  it('枚举值应和后端 PayStatusEnum 一致（0-6）', () => {
    expect(PAY_STATUS.WAIT).toBe(0)
    expect(PAY_STATUS.PAYING).toBe(1)
    expect(PAY_STATUS.PAID).toBe(2)
    expect(PAY_STATUS.CLOSED).toBe(3)
    expect(PAY_STATUS.FAILED).toBe(4)
    expect(PAY_STATUS.REFUNDING).toBe(5)
    expect(PAY_STATUS.REFUNDED).toBe(6)
  })

  it('PAY_STATUS_MAP 应覆盖所有状态且每项有 label 和 color', () => {
    assertEnumAndMapConsistent(PAY_STATUS, PAY_STATUS_MAP, [0, 1, 2, 3, 4, 5, 6])
  })
})

describe('PAY_METHOD 支付方式常量', () => {
  it('枚举值应为 1-3', () => {
    expect(PAY_METHOD.MOCK).toBe(1)
    expect(PAY_METHOD.WECHAT).toBe(2)
    expect(PAY_METHOD.ALIPAY).toBe(3)
  })

  it('PAY_METHOD_MAP 应覆盖所有支付方式', () => {
    expect(PAY_METHOD_MAP[1]).toBe('模拟支付')
    expect(PAY_METHOD_MAP[2]).toBe('微信支付')
    expect(PAY_METHOD_MAP[3]).toBe('支付宝')
  })
})

describe('PRODUCT_STATUS 商品状态常量', () => {
  it('枚举值应为 0 和 1', () => {
    expect(PRODUCT_STATUS.OFF_SHELF).toBe(0)
    expect(PRODUCT_STATUS.ON_SHELF).toBe(1)
  })

  it('PRODUCT_STATUS_MAP 应覆盖所有状态且每项有 label 和 color', () => {
    assertEnumAndMapConsistent(PRODUCT_STATUS, PRODUCT_STATUS_MAP, [0, 1])
  })
})

describe('REFUND_STATUS 退款状态常量', () => {
  it('枚举值应和后端 RefundStatusEnum 一致（0-4）', () => {
    expect(REFUND_STATUS.PENDING).toBe(0)
    expect(REFUND_STATUS.APPROVED).toBe(1)
    expect(REFUND_STATUS.REJECTED).toBe(2)
    expect(REFUND_STATUS.REFUNDING).toBe(3)
    expect(REFUND_STATUS.REFUNDED).toBe(4)
  })

  it('REFUND_STATUS_MAP 应覆盖所有状态且每项有 label 和 color', () => {
    assertEnumAndMapConsistent(REFUND_STATUS, REFUND_STATUS_MAP, [0, 1, 2, 3, 4])
  })
})

describe('MERCHANT_STATUS 商家状态常量', () => {
  it('枚举值应和后端 MerchantStatusEnum 一致（0-3）', () => {
    expect(MERCHANT_STATUS.PENDING).toBe(0)
    expect(MERCHANT_STATUS.APPROVED).toBe(1)
    expect(MERCHANT_STATUS.REJECTED).toBe(2)
    expect(MERCHANT_STATUS.DISABLED).toBe(3)
  })

  it('MERCHANT_STATUS_MAP 应覆盖所有状态且每项有 label 和 color', () => {
    assertEnumAndMapConsistent(MERCHANT_STATUS, MERCHANT_STATUS_MAP, [0, 1, 2, 3])
  })
})
