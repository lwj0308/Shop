/**
 * errorCode.ts 错误码常量测试
 * <p>
 * 验证错误码映射和 getErrorMessage 函数的三级 fallback 逻辑。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import {
  SUCCESS_CODE,
  ERROR_CODE_MAP,
  getErrorMessage,
} from './errorCode'

describe('SUCCESS_CODE', () => {
  it('成功码应为200', () => {
    expect(SUCCESS_CODE).toBe(200)
  })
})

describe('ERROR_CODE_MAP 错误码映射', () => {
  it('应包含通用HTTP错误码', () => {
    expect(ERROR_CODE_MAP[400]).toBe('请求参数错误')
    expect(ERROR_CODE_MAP[401]).toBe('未登录或登录已过期')
    expect(ERROR_CODE_MAP[403]).toBe('没有权限访问')
    expect(ERROR_CODE_MAP[404]).toBe('请求的资源不存在')
    expect(ERROR_CODE_MAP[429]).toBe('请求太频繁，请稍后再试')
    expect(ERROR_CODE_MAP[500]).toBe('服务器内部错误，请稍后重试')
  })

  it('应包含用户模块错误码', () => {
    expect(ERROR_CODE_MAP[11001]).toBe('用户不存在')
    expect(ERROR_CODE_MAP[11002]).toBe('密码错误')
    expect(ERROR_CODE_MAP[11004]).toBe('账号已被禁用')
  })

  it('应包含商品模块错误码', () => {
    expect(ERROR_CODE_MAP[30001]).toBe('商品不存在')
    expect(ERROR_CODE_MAP[30002]).toBe('商品已下架')
    expect(ERROR_CODE_MAP[30003]).toBe('库存不足')
  })

  it('应包含订单模块错误码', () => {
    expect(ERROR_CODE_MAP[40001]).toBe('订单不存在')
    expect(ERROR_CODE_MAP[40002]).toBe('订单状态异常')
  })

  it('应包含购物车模块错误码', () => {
    expect(ERROR_CODE_MAP[60001]).toBe('购物车商品不存在')
    expect(ERROR_CODE_MAP[60004]).toBe('购物车为空')
  })
})

describe('getErrorMessage 三级fallback', () => {
  it('映射表中存在的错误码应返回对应中文提示', () => {
    expect(getErrorMessage(11001)).toBe('用户不存在')
    expect(getErrorMessage(30003)).toBe('库存不足')
  })

  it('映射表中不存在的错误码应返回defaultMessage', () => {
    expect(getErrorMessage(99999, '自定义错误')).toBe('自定义错误')
  })

  it('映射表中不存在且未传defaultMessage应返回兜底文案', () => {
    expect(getErrorMessage(99999)).toBe('操作失败，请稍后重试')
  })

  it('映射表中不存在且defaultMessage为空字符串应返回兜底文案', () => {
    // 空字符串是 falsy，会走兜底
    expect(getErrorMessage(99999, '')).toBe('操作失败，请稍后重试')
  })
})
