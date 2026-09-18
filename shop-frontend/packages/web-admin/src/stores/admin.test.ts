/**
 * admin.ts 管理员状态管理测试
 * <p>
 * 这是结构最复杂的 store，包含登录、获取信息、动态路由、权限检查、登出等。
 * 需要 mock vue-router、@shop/shared/utils/auth 和 @shop/shared/api/modules/admin。
 * </p>
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// ============ mock 依赖模块 ============
// 1. mock vue-router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

// 2. mock @shop/shared/utils/auth
const mockGetSharedToken = vi.fn()
const mockSetSharedToken = vi.fn()
const mockClearSharedToken = vi.fn()
vi.mock('@shop/shared/utils/auth', () => ({
  getToken: (...args: any[]) => mockGetSharedToken(...args),
  setToken: (...args: any[]) => mockSetSharedToken(...args),
  clearToken: (...args: any[]) => mockClearSharedToken(...args),
}))

// 3. mock @shop/shared/api/modules/admin
const mockAdminLogin = vi.fn()
const mockAdminLogout = vi.fn()
const mockGetAdminInfo = vi.fn()
vi.mock('@shop/shared/api/modules/admin', () => ({
  adminLogin: (...args: any[]) => mockAdminLogin(...args),
  adminLogout: (...args: any[]) => mockAdminLogout(...args),
  getAdminInfo: (...args: any[]) => mockGetAdminInfo(...args),
}))

import { useAdminStore } from './admin'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // 默认 Token 为空字符串（未登录）
  mockGetSharedToken.mockReturnValue('')
  // 清空 localStorage
  localStorage.clear()
})

/**
 * 构造假的登录响应
 */
function makeFakeLoginResponse() {
  return { data: { data: { token: 'admin-token-xxx' } } }
}

/**
 * 构造假的管理员信息响应
 */
function makeFakeAdminInfoResponse() {
  return {
    data: {
      data: {
        id: 1,
        username: 'admin',
        nickname: '超级管理员',
        avatar: 'https://example.com/avatar.png',
        roles: ['admin'],
        permissions: ['admin:user:list', 'admin:order:list'],
      },
    },
  }
}

describe('useAdminStore 初始化', () => {
  it('Token 应从 shared auth 工具获取', () => {
    mockGetSharedToken.mockReturnValue('saved-token')
    const store = useAdminStore()
    expect(store.token).toBe('saved-token')
  })

  it('无 Token 时 isLoggedIn 应为 false', () => {
    mockGetSharedToken.mockReturnValue('')
    const store = useAdminStore()
    expect(store.isLoggedIn).toBe(false)
  })

  it('有 Token 时 isLoggedIn 应为 true', () => {
    mockGetSharedToken.mockReturnValue('some-token')
    const store = useAdminStore()
    expect(store.isLoggedIn).toBe(true)
  })

  it('初始 dynamicRoutesLoaded 应为 false', () => {
    const store = useAdminStore()
    expect(store.dynamicRoutesLoaded).toBe(false)
  })

  it('初始 adminInfo 应从 localStorage 恢复', () => {
    const savedInfo = {
      id: 1,
      username: 'admin',
      nickname: '管理员',
      avatar: '',
      roles: ['admin'],
      permissions: ['admin:user:list'],
    }
    localStorage.setItem('admin_info', JSON.stringify(savedInfo))
    const store = useAdminStore()
    expect(store.adminInfo).toEqual(savedInfo)
  })
})

describe('计算属性', () => {
  it('displayName 应优先显示 nickname', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '昵称', avatar: '',
      roles: [], permissions: [],
    }))
    const store = useAdminStore()
    expect(store.displayName).toBe('昵称')
  })

  it('displayName 无 nickname 时应显示 username', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: [], permissions: [],
    }))
    const store = useAdminStore()
    expect(store.displayName).toBe('admin')
  })

  it('displayName 无 adminInfo 时应为空字符串', () => {
    const store = useAdminStore()
    expect(store.displayName).toBe('')
  })

  it('permissions 应从 adminInfo 获取', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: [], permissions: ['admin:user:list'],
    }))
    const store = useAdminStore()
    expect(store.permissions).toEqual(['admin:user:list'])
  })

  it('roles 应从 adminInfo 获取', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: ['admin', 'editor'], permissions: [],
    }))
    const store = useAdminStore()
    expect(store.roles).toEqual(['admin', 'editor'])
  })

  it('isSuperAdmin 当 roles 包含 admin 时应为 true', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: ['admin'], permissions: [],
    }))
    const store = useAdminStore()
    expect(store.isSuperAdmin).toBe(true)
  })

  it('isSuperAdmin 当 roles 不包含 admin 时应为 false', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: ['editor'], permissions: [],
    }))
    const store = useAdminStore()
    expect(store.isSuperAdmin).toBe(false)
  })
})

describe('login 管理员登录', () => {
  it('登录成功应保存 Token 并获取管理员信息', async () => {
    mockAdminLogin.mockResolvedValue(makeFakeLoginResponse())
    mockGetAdminInfo.mockResolvedValue(makeFakeAdminInfoResponse())

    const store = useAdminStore()
    await store.login({ username: 'admin', password: '123456', captcha: 'ABCD' })

    // 断言：登录接口被调用
    expect(mockAdminLogin).toHaveBeenCalledWith({ username: 'admin', password: '123456', captcha: 'ABCD' })
    // 断言：Token 被保存
    expect(store.token).toBe('admin-token-xxx')
    // 断言：获取了管理员信息
    expect(mockGetAdminInfo).toHaveBeenCalledTimes(1)
    // 断言：adminInfo 被更新
    expect(store.adminInfo).not.toBeNull()
    expect(store.adminInfo?.username).toBe('admin')
  })

  it('登录接口失败时应向上抛出异常', async () => {
    mockAdminLogin.mockRejectedValue(new Error('密码错误'))

    const store = useAdminStore()
    await expect(store.login({ username: 'admin', password: 'wrong', captcha: '' })).rejects.toThrow('密码错误')
  })
})

describe('fetchAdminInfo 获取管理员信息', () => {
  it('应调用接口并更新 adminInfo', async () => {
    mockGetAdminInfo.mockResolvedValue(makeFakeAdminInfoResponse())
    const store = useAdminStore()

    await store.fetchAdminInfo()

    expect(mockGetAdminInfo).toHaveBeenCalledTimes(1)
    expect(store.adminInfo?.id).toBe(1)
    expect(store.adminInfo?.roles).toEqual(['admin'])
    expect(store.adminInfo?.permissions).toEqual(['admin:user:list', 'admin:order:list'])
  })

  it('接口返回空 roles 和 permissions 时应默认为空数组', async () => {
    mockGetAdminInfo.mockResolvedValue({
      data: {
        data: {
          id: 2, username: 'editor', nickname: '编辑', avatar: '',
          roles: null as any, permissions: null as any,
        },
      },
    })
    const store = useAdminStore()

    await store.fetchAdminInfo()

    expect(store.adminInfo?.roles).toEqual([])
    expect(store.adminInfo?.permissions).toEqual([])
  })
})

describe('loadDynamicRoutes 加载动态路由', () => {
  it('应将 dynamicRoutesLoaded 设为 true', async () => {
    const store = useAdminStore()
    expect(store.dynamicRoutesLoaded).toBe(false)

    await store.loadDynamicRoutes()

    expect(store.dynamicRoutesLoaded).toBe(true)
  })
})

describe('hasPermission 权限检查', () => {
  it('超级管理员应拥有所有权限', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: ['admin'], permissions: [],
    }))
    const store = useAdminStore()

    expect(store.hasPermission('any:permission')).toBe(true)
    expect(store.hasPermission('admin:user:list')).toBe(true)
  })

  it('非超级管理员有该权限时应返回 true', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'editor', nickname: '', avatar: '',
      roles: ['editor'], permissions: ['admin:user:list'],
    }))
    const store = useAdminStore()

    expect(store.hasPermission('admin:user:list')).toBe(true)
  })

  it('非超级管理员无该权限时应返回 false', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'editor', nickname: '', avatar: '',
      roles: ['editor'], permissions: ['admin:user:list'],
    }))
    const store = useAdminStore()

    expect(store.hasPermission('admin:order:list')).toBe(false)
  })
})

describe('hasAnyPermission 任意权限检查', () => {
  it('超级管理员应返回 true', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: ['admin'], permissions: [],
    }))
    const store = useAdminStore()

    expect(store.hasAnyPermission(['any:perm1', 'any:perm2'])).toBe(true)
  })

  it('拥有其中一个权限时应返回 true', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'editor', nickname: '', avatar: '',
      roles: ['editor'], permissions: ['admin:user:list'],
    }))
    const store = useAdminStore()

    expect(store.hasAnyPermission(['admin:user:list', 'admin:order:list'])).toBe(true)
  })

  it('都不拥有时应返回 false', () => {
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'editor', nickname: '', avatar: '',
      roles: ['editor'], permissions: ['admin:user:list'],
    }))
    const store = useAdminStore()

    expect(store.hasAnyPermission(['admin:order:list', 'admin:product:list'])).toBe(false)
  })
})

describe('logout 登出', () => {
  it('登出应清除所有状态并跳转登录页', async () => {
    mockAdminLogout.mockResolvedValue({})
    mockGetSharedToken.mockReturnValue('token')
    localStorage.setItem('admin_info', JSON.stringify({
      id: 1, username: 'admin', nickname: '', avatar: '',
      roles: ['admin'], permissions: [],
    }))
    const store = useAdminStore()
    store.dynamicRoutesLoaded = true

    await store.logout()

    // 断言：登出接口被调用
    expect(mockAdminLogout).toHaveBeenCalledTimes(1)
    // 断言：Token 被清除
    expect(store.token).toBe('')
    // 断言：adminInfo 被清空
    expect(store.adminInfo).toBeNull()
    // 断言：dynamicRoutesLoaded 被重置
    expect(store.dynamicRoutesLoaded).toBe(false)
    // 断言：跳转到登录页
    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('登出接口失败时也应清除本地状态', async () => {
    // admin.logout 用了 try...catch，会吞掉错误，不会向上抛
    mockAdminLogout.mockRejectedValue(new Error('网络错误'))
    mockGetSharedToken.mockReturnValue('token')
    const store = useAdminStore()

    // logout 不会抛错（内部 catch 了）
    await expect(store.logout()).resolves.toBeUndefined()

    // 即使接口失败，本地状态也应被清除
    expect(store.token).toBe('')
    expect(store.adminInfo).toBeNull()
    expect(store.dynamicRoutesLoaded).toBe(false)
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
