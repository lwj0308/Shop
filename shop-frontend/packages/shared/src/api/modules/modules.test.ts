/**
 * api/modules 请求函数测试
 * <p>
 * shared 包的 api/modules 下的函数都是简单的请求封装，
 * 每个函数只是调用 request 模块的 get/post/put/del 方法，传入 URL 和参数。
 * 测试重点：验证 URL 是否正确、HTTP 方法是否正确、参数是否正确传递。
 * 这里以 user.ts 和 cart.ts 为示例，其他模块可按相同模式补全。
 * </p>
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

// ============ mock request 模块 ============
// 把 request 模块的 get/post/put/del 全部 mock 掉，
// 这样测试不会真正发请求，只会记录调用参数
const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()
const mockDel = vi.fn()

vi.mock('../request', () => ({
  get: (...args: any[]) => mockGet(...args),
  post: (...args: any[]) => mockPost(...args),
  put: (...args: any[]) => mockPut(...args),
  del: (...args: any[]) => mockDel(...args),
}))

// 在 mock 定义之后导入被测模块
import {
  userLogin,
  userRegister,
  getUserInfo,
  updateUserInfo,
  userLogout,
  sendVerifyCode,
  getAddressList,
  addAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
  addFavorite,
  removeFavorite,
  getFavoriteList,
  getFootprintList,
} from './user'
import {
  getCartList,
  addToCart,
  updateCartItem,
  removeCartItem,
  clearCart,
  toggleCartAllChecked,
  getCartCount,
} from './cart'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('user.ts 用户API', () => {
  describe('认证相关接口', () => {
    it('userLogin 应以 POST 调用 /user/auth/login', () => {
      const params = { phone: '13812345678', password: '123456' }
      userLogin(params)
      expect(mockPost).toHaveBeenCalledWith('/user/auth/login', params)
    })

    it('userRegister 应以 POST 调用 /user/auth/register', () => {
      const params = {
        phone: '13812345678',
        password: '123456',
        confirmPassword: '123456',
        verifyCode: '123456',
      }
      userRegister(params)
      expect(mockPost).toHaveBeenCalledWith('/user/auth/register', params)
    })

    it('getUserInfo 应以 GET 调用 /user/info', () => {
      getUserInfo()
      expect(mockGet).toHaveBeenCalledWith('/user/info')
    })

    it('updateUserInfo 应以 PUT 调用 /user/info', () => {
      const data = { nickname: '新昵称' }
      updateUserInfo(data)
      expect(mockPut).toHaveBeenCalledWith('/user/info', data)
    })

    it('userLogout 应以 POST 调用 /user/auth/logout（无参数）', () => {
      userLogout()
      // post 无第二参数时，只传 URL
      expect(mockPost).toHaveBeenCalledWith('/user/auth/logout')
    })

    it('sendVerifyCode 应以 POST 调用 /user/auth/send-code 并传 phone', () => {
      sendVerifyCode('13812345678')
      expect(mockPost).toHaveBeenCalledWith('/user/auth/send-code', { phone: '13812345678' })
    })
  })

  describe('收货地址接口', () => {
    it('getAddressList 应以 GET 调用 /user/address/list', () => {
      getAddressList()
      expect(mockGet).toHaveBeenCalledWith('/user/address/list')
    })

    it('addAddress 应以 POST 调用 /user/address', () => {
      const data = {
        name: '张三',
        phone: '13812345678',
        province: '北京市',
        city: '北京市',
        district: '朝阳区',
        detail: 'xxx路xxx号',
      }
      addAddress(data)
      expect(mockPost).toHaveBeenCalledWith('/user/address', data)
    })

    it('updateAddress 应以 PUT 调用 /user/address/{id}', () => {
      const data = { name: '李四', phone: '13912345678', province: '上海市', city: '上海市', district: '浦东新区', detail: 'xxx路' }
      updateAddress(123, data)
      expect(mockPut).toHaveBeenCalledWith('/user/address/123', data)
    })

    it('deleteAddress 应以 DELETE 调用 /user/address/{id}', () => {
      deleteAddress(123)
      expect(mockDel).toHaveBeenCalledWith('/user/address/123')
    })

    it('setDefaultAddress 应以 PUT 调用 /user/address/{id}/default', () => {
      setDefaultAddress(123)
      expect(mockPut).toHaveBeenCalledWith('/user/address/123/default')
    })
  })

  describe('收藏和足迹接口', () => {
    it('addFavorite 应以 POST 调用 /user/favorite/{productId}', () => {
      addFavorite(1001)
      expect(mockPost).toHaveBeenCalledWith('/user/favorite/1001')
    })

    it('removeFavorite 应以 DELETE 调用 /user/favorite/{productId}', () => {
      removeFavorite(1001)
      expect(mockDel).toHaveBeenCalledWith('/user/favorite/1001')
    })

    it('getFavoriteList 应以 GET 调用 /user/favorite/list 并传分页参数', () => {
      const params = { pageNum: 1, pageSize: 10 }
      getFavoriteList(params)
      expect(mockGet).toHaveBeenCalledWith('/user/favorite/list', params)
    })

    it('getFootprintList 应以 GET 调用 /user/footprint/list 并传分页参数', () => {
      const params = { pageNum: 2, pageSize: 20 }
      getFootprintList(params)
      expect(mockGet).toHaveBeenCalledWith('/user/footprint/list', params)
    })
  })
})

describe('cart.ts 购物车API', () => {
  it('getCartList 应以 GET 调用 /cart/list', () => {
    getCartList()
    expect(mockGet).toHaveBeenCalledWith('/cart/list')
  })

  it('addToCart 应以 POST 调用 /cart/add 并传商品参数', () => {
    const data = { productId: 1001, skuId: 2001, quantity: 2 }
    addToCart(data)
    expect(mockPost).toHaveBeenCalledWith('/cart/add', data)
  })

  it('updateCartItem 应以 PUT 调用 /cart/update 并传更新参数', () => {
    const data = { id: 1, quantity: 3 }
    updateCartItem(data)
    expect(mockPut).toHaveBeenCalledWith('/cart/update', data)
  })

  it('removeCartItem 应以 DELETE 调用 /cart/remove/{id}', () => {
    removeCartItem(123)
    expect(mockDel).toHaveBeenCalledWith('/cart/remove/123')
  })

  it('clearCart 应以 DELETE 调用 /cart/clear', () => {
    clearCart()
    expect(mockDel).toHaveBeenCalledWith('/cart/clear')
  })

  it('toggleCartAllChecked 应以 PUT 调用 /cart/checkAll 并传 checked', () => {
    toggleCartAllChecked(true)
    expect(mockPut).toHaveBeenCalledWith('/cart/checkAll', { checked: true })

    toggleCartAllChecked(false)
    expect(mockPut).toHaveBeenCalledWith('/cart/checkAll', { checked: false })
  })

  it('getCartCount 应以 GET 调用 /cart/count', () => {
    getCartCount()
    expect(mockGet).toHaveBeenCalledWith('/cart/count')
  })
})
