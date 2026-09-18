/**
 * useAuth.ts 认证逻辑组合式函数测试
 * <p>
 * 测试登录、登出、获取用户信息三大核心流程。
 * 因为 useAuth 内部依赖 vue-router、API 请求和 Token 工具，
 * 所以需要把这三部分全部 mock 掉，只测试 useAuth 本身的逻辑是否正确。
 * </p>
 */
// @vitest-environment happy-dom
// 上面这行告诉 Vitest：这个测试文件需要在 happy-dom 环境运行
// 因为 useAuth 内部用到的 vue-router 依赖浏览器环境
import { describe, it, expect, beforeEach, vi } from 'vitest'

// ============ mock 依赖模块 ============
// 1. mock vue-router：模拟 useRouter 返回一个带 push 方法的假 router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

// 2. mock ../api：模拟登录、登出、获取用户信息三个接口
// 这样测试不会真正发请求，只会调用我们提供的 mock 函数
const mockUserLogin = vi.fn()
const mockUserLogout = vi.fn()
const mockGetUserInfo = vi.fn()
vi.mock('../api', () => ({
  userLogin: (...args: any[]) => mockUserLogin(...args),
  userLogout: (...args: any[]) => mockUserLogout(...args),
  getUserInfo: (...args: any[]) => mockGetUserInfo(...args),
}))

// 3. mock ../utils/auth：模拟 Token 的存储和读取
// 这样测试不依赖真实的 localStorage，可以精确控制登录状态
const mockSetToken = vi.fn()
const mockClearToken = vi.fn()
// isAuthenticated 用一个变量控制返回值，方便测试不同登录状态
let mockIsAuthenticatedReturn = false
vi.mock('../utils/auth', () => ({
  setToken: (...args: any[]) => mockSetToken(...args),
  clearToken: (...args: any[]) => mockClearToken(...args),
  isAuthenticated: () => mockIsAuthenticatedReturn,
}))

// 在所有 mock 定义之后再导入被测模块
import { useAuth } from './useAuth'

// 每个测试前重置所有 mock 和状态
beforeEach(() => {
  vi.clearAllMocks()
  mockIsAuthenticatedReturn = false
})

/**
 * 构造一个假的用户信息对象，供测试使用
 */
function makeFakeUser() {
  return {
    id: 100,
    phone: '13812345678',
    nickname: '测试用户',
    avatar: 'https://example.com/avatar.png',
    gender: 1 as 0 | 1 | 2,
    createTime: '2024-01-01 10:00:00',
  }
}

describe('useAuth 初始化', () => {
  it('未登录时 isLoggedIn 应为 false', () => {
    mockIsAuthenticatedReturn = false
    const { isLoggedIn } = useAuth()
    expect(isLoggedIn.value).toBe(false)
  })

  it('已登录时 isLoggedIn 应为 true', () => {
    mockIsAuthenticatedReturn = true
    const { isLoggedIn } = useAuth()
    expect(isLoggedIn.value).toBe(true)
  })

  it('初始 userInfo 应为 null', () => {
    const { userInfo } = useAuth()
    expect(userInfo.value).toBeNull()
  })

  it('初始 loginLoading 应为 false', () => {
    const { loginLoading } = useAuth()
    expect(loginLoading.value).toBe(false)
  })
})

describe('login 登录流程', () => {
  it('登录成功应调用API、存Token、获取用户信息、跳转首页', async () => {
    // 安排：让 mock 返回成功结果
    const fakeUser = makeFakeUser()
    mockUserLogin.mockResolvedValue({
      data: { accessToken: 'access-xxx', refreshToken: 'refresh-yyy', expiresIn: 7200 },
    })
    mockGetUserInfo.mockResolvedValue({ data: fakeUser })

    // 执行：调用 login
    const { login, userInfo } = useAuth()
    await login('13812345678', 'password123')

    // 断言：登录接口被调用了一次，参数是手机号和密码
    expect(mockUserLogin).toHaveBeenCalledTimes(1)
    expect(mockUserLogin).toHaveBeenCalledWith({ phone: '13812345678', password: 'password123' })

    // 断言：Token 被存储
    expect(mockSetToken).toHaveBeenCalledWith('access-xxx', 'refresh-yyy')

    // 断言：登录后立即获取了用户信息
    expect(mockGetUserInfo).toHaveBeenCalledTimes(1)

    // 断言：userInfo 被正确更新
    expect(userInfo.value).toEqual(fakeUser)

    // 断言：跳转到了首页
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('带 redirect 参数时应跳转到指定页面', async () => {
    mockUserLogin.mockResolvedValue({
      data: { accessToken: 'token', refreshToken: 'refresh', expiresIn: 7200 },
    })
    mockGetUserInfo.mockResolvedValue({ data: makeFakeUser() })

    const { login } = useAuth()
    await login('13812345678', 'password123', '/cart')

    expect(mockPush).toHaveBeenCalledWith('/cart')
  })

  it('loginLoading 在登录完成后应恢复 false', async () => {
    mockUserLogin.mockResolvedValue({
      data: { accessToken: 'token', refreshToken: 'refresh', expiresIn: 7200 },
    })
    mockGetUserInfo.mockResolvedValue({ data: makeFakeUser() })

    const { login, loginLoading } = useAuth()
    expect(loginLoading.value).toBe(false)

    const promise = login('13812345678', 'password123')
    // 执行期间 loading 应为 true
    expect(loginLoading.value).toBe(true)

    await promise
    // 完成后 loading 应为 false
    expect(loginLoading.value).toBe(false)
  })

  it('登录失败时 loginLoading 也应恢复 false 且不跳转', async () => {
    // 模拟登录接口抛错
    mockUserLogin.mockRejectedValue(new Error('密码错误'))

    const { login, loginLoading } = useAuth()
    // login 会抛错，用 expect 捕获
    await expect(login('13812345678', 'wrong')).rejects.toThrow('密码错误')

    // 即使失败，loading 也应该恢复
    expect(loginLoading.value).toBe(false)
    // 失败时不应跳转
    expect(mockPush).not.toHaveBeenCalled()
    // 失败时不应存 Token
    expect(mockSetToken).not.toHaveBeenCalled()
  })

  it('正在登录中再次调用 login 应被拦截（防重复提交）', async () => {
    // 让登录接口延迟返回，保持 loading 状态
    let resolveLogin!: (value: any) => void
    mockUserLogin.mockReturnValue(
      new Promise(resolve => {
        resolveLogin = resolve
      }),
    )
    mockGetUserInfo.mockResolvedValue({ data: makeFakeUser() })

    const { login } = useAuth()
    const promise1 = login('13812345678', 'password123')

    // 此时 loading=true，再次调用应被拦截（直接返回 undefined）
    const result2 = await login('13812345678', 'password123')
    expect(result2).toBeUndefined()

    // 登录接口应只被调用一次（第二次被拦截）
    expect(mockUserLogin).toHaveBeenCalledTimes(1)

    // 释放第一次的 login
    resolveLogin({ data: { accessToken: 't', refreshToken: 'r', expiresIn: 1 } })
    await promise1
  })
})

describe('logout 登出流程', () => {
  it('登出成功应调用API、清除Token、清空userInfo、跳转登录页', async () => {
    // 先准备一个已登录状态
    mockUserLogout.mockResolvedValue({})
    const { logout, userInfo } = useAuth()
    // 手动设置 userInfo 为非空，验证登出后是否被清空
    userInfo.value = makeFakeUser()

    await logout()

    // 断言：登出接口被调用
    expect(mockUserLogout).toHaveBeenCalledTimes(1)
    // 断言：Token 被清除
    expect(mockClearToken).toHaveBeenCalledTimes(1)
    // 断言：userInfo 被清空
    expect(userInfo.value).toBeNull()
    // 断言：跳转到登录页
    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('登出接口失败时也应清除本地状态（保证用户能退出）', async () => {
    // 模拟登出接口抛错
    mockUserLogout.mockRejectedValue(new Error('网络错误'))

    const { logout, userInfo } = useAuth()
    userInfo.value = makeFakeUser()

    // 注意：logout 用的是 try...finally（没有 catch），
    // 所以错误会被 finally 块处理完后继续向上抛出
    await expect(logout()).rejects.toThrow('网络错误')

    // 即使接口失败，本地状态也应被清除（finally 块的执行保证）
    expect(mockClearToken).toHaveBeenCalledTimes(1)
    expect(userInfo.value).toBeNull()
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})

describe('fetchUserInfo 获取用户信息', () => {
  it('应调用API并更新userInfo', async () => {
    const fakeUser = makeFakeUser()
    mockGetUserInfo.mockResolvedValue({ data: fakeUser })

    const { fetchUserInfo, userInfo } = useAuth()
    expect(userInfo.value).toBeNull()

    const result = await fetchUserInfo()

    expect(mockGetUserInfo).toHaveBeenCalledTimes(1)
    expect(userInfo.value).toEqual(fakeUser)
    // fetchUserInfo 应返回用户信息
    expect(result).toEqual(fakeUser)
  })

  it('API 抛错时应向上传播异常', async () => {
    mockGetUserInfo.mockRejectedValue(new Error('未登录'))

    const { fetchUserInfo } = useAuth()
    await expect(fetchUserInfo()).rejects.toThrow('未登录')
  })
})
