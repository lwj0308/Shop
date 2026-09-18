/**
 * HeaderBar 顶部导航栏组件测试
 * <p>
 * 验证管理后台顶部导航栏的折叠按钮、面包屑、通知铃铛、主题切换、
 * 全屏按钮、用户下拉菜单（退出登录）等核心交互。
 * </p>
 *
 * 小白讲解：
 * 顶栏是管理后台"顶部那一行"，包含：
 * - 左边：折叠按钮（控制侧边栏收起/展开）+ 面包屑（显示当前页面路径）
 * - 右边：通知铃铛 + 主题切换 + 全屏 + 用户头像/退出登录
 * 我们要测的核心是：
 * 1. 折叠按钮点击会触发 toggleCollapse 事件给父组件
 * 2. 面包屑根据路由 matched 自动生成
 * 3. 通知铃铛徽章数量正确，点击跳转通知页
 * 4. 主题切换按钮点击调用 toggleTheme
 * 5. 全屏按钮调用 Fullscreen API
 * 6. 退出登录需要 ElMessageBox 确认后调用 adminStore.logout
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HeaderBar from './HeaderBar.vue'

// ==================== mock 外部依赖 ====================

/**
 * 用 vi.hoisted 把 mock 函数和响应式容器提升到顶部
 * 这样 vi.mock 工厂函数里就能安全引用它们
 */
const {
  mockPush,
  routeState,
  mockToggleTheme,
  isDarkRef,
  mockGetAdminUnreadCount,
  mockAdminLogout,
  mockMessageBoxConfirm,
  adminStoreState,
} = vi.hoisted(() => ({
  // router.push 的 mock
  mockPush: vi.fn(),
  // 路由状态容器（reactive 对象在 mock factory 中创建并赋值）
  routeState: { value: null as any },
  // toggleTheme 函数的 mock
  mockToggleTheme: vi.fn(),
  // isDark 的 ref（在 mock factory 中创建）
  isDarkRef: { value: null as any },
  // 获取管理员未读通知数 API 的 mock
  mockGetAdminUnreadCount: vi.fn(),
  // adminStore.logout 的 mock
  mockAdminLogout: vi.fn(),
  // ElMessageBox.confirm 的 mock
  mockMessageBoxConfirm: vi.fn(),
  // adminStore 状态（displayName 等）
  adminStoreState: {
    displayName: 'admin',
  },
}))

// mock vue-router —— useRoute 返回 reactive 路由对象，useRouter 返回 push
// 用 reactive 包装是为了让组件内的 computed/watch 能正常响应
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  routeState.value = reactive({
    path: '/dashboard',
    fullPath: '/dashboard',
    matched: [
      { path: '/dashboard', meta: { title: '仪表盘' } },
    ],
  })
  return {
    useRoute: () => routeState.value,
    useRouter: () => ({ push: mockPush }),
  }
})

// mock @/stores/admin —— useAdminStore 返回带 displayName 和 logout 的对象
vi.mock('@/stores/admin', () => ({
  useAdminStore: () => ({
    displayName: adminStoreState.displayName,
    logout: mockAdminLogout,
  }),
}))

// mock @/composables/useTheme —— isDark 是 ref，toggleTheme 是函数
vi.mock('@/composables/useTheme', async () => {
  const { ref } = await import('vue')
  isDarkRef.value = ref(false)
  return {
    isDark: isDarkRef.value,
    toggleTheme: mockToggleTheme,
  }
})

// mock @shop/shared —— getAdminUnreadCount
vi.mock('@shop/shared', () => ({
  getAdminUnreadCount: (...args: any[]) => mockGetAdminUnreadCount(...args),
}))

// mock @element-plus/icons-vue —— 保留真实图标（element-plus 内部依赖），
// 用到的图标简化为 i 标签
vi.mock('@element-plus/icons-vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@element-plus/icons-vue')>()
  return {
    ...actual,
    Fold: { template: '<i class="icon-fold"></i>' },
    Expand: { template: '<i class="icon-expand"></i>' },
    Sunny: { template: '<i class="icon-sunny"></i>' },
    Moon: { template: '<i class="icon-moon"></i>' },
    FullScreen: { template: '<i class="icon-fullscreen"></i>' },
    UserFilled: { template: '<i class="icon-user-filled"></i>' },
    ArrowDown: { template: '<i class="icon-arrow-down"></i>' },
    SwitchButton: { template: '<i class="icon-switch"></i>' },
    Bell: { template: '<i class="icon-bell"></i>' },
  }
})

// mock element-plus —— ElMessageBox.confirm 可控制确认/取消
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessageBox: {
      confirm: (...args: any[]) => mockMessageBoxConfirm(...args),
    },
  }
})

/**
 * 辅助函数：挂载组件
 * @param collapse - 是否折叠
 *
 * stub 掉 EP 的 tooltip / breadcrumb / badge / dropdown / avatar / icon，
 * 同时把 dropdown 的 command 事件暴露出来方便测试
 */
const mountComponent = (collapse = false) => {
  return mount(HeaderBar, {
    props: { collapse },
    global: {
      stubs: {
        ElTooltip: { template: '<div class="el-tooltip-stub"><slot/></div>' },
        ElBreadcrumb: { template: '<div class="el-breadcrumb-stub"><slot/></div>' },
        ElBreadcrumbItem: {
          template: '<div class="el-breadcrumb-item-stub"><slot/></div>',
          props: ['to'],
        },
        ElBadge: {
          template: '<div class="el-badge-stub" :data-value="value" :data-hidden="hidden"><slot/></div>',
          props: ['value', 'hidden', 'max'],
        },
        ElDropdown: {
          name: 'ElDropdown',
          template: '<div class="el-dropdown-stub"><slot/><div class="el-dropdown-dropdown"><slot name="dropdown"/></div></div>',
          props: ['trigger'],
          methods: {
            // 暴露一个方法模拟点击 dropdown item 触发 command 事件
            emitCommand(cmd: string) {
              ;(this as any).$emit('command', cmd)
            },
          },
        },
        ElDropdownMenu: { template: '<div class="el-dropdown-menu-stub"><slot/></div>' },
        ElDropdownItem: {
          template: '<div class="el-dropdown-item-stub" :data-command="command" @click="$emit(\'click\')"><slot/></div>',
          props: ['command', 'divided'],
          emits: ['click'],
        },
        ElAvatar: {
          template: '<div class="el-avatar-stub"><slot/></div>',
          props: ['size', 'icon'],
        },
        ElIcon: { template: '<i class="el-icon-stub"><slot/></i>' },
      },
    },
  })
}

describe('HeaderBar 顶部导航栏组件', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 重置路由到 /dashboard，带一个 matched 项
    routeState.value.path = '/dashboard'
    routeState.value.fullPath = '/dashboard'
    routeState.value.matched = [{ path: '/dashboard', meta: { title: '仪表盘' } }]
    // 重置 isDark 为 false（亮色模式）
    isDarkRef.value.value = false
    // 重置 admin 显示名
    adminStoreState.displayName = 'admin'
    // 默认未读数 API 返回 0
    mockGetAdminUnreadCount.mockResolvedValue({ data: 0 })
    // 默认 ElMessageBox.confirm 返回 resolved（用户点确定）
    mockMessageBoxConfirm.mockResolvedValue('confirm')
  })

  // ==================== 折叠按钮 ====================

  it('展开时折叠按钮应显示 Fold 图标', () => {
    const wrapper = mountComponent(false)
    expect(wrapper.find('.collapse-btn .icon-fold').exists()).toBe(true)
    expect(wrapper.find('.collapse-btn .icon-expand').exists()).toBe(false)
  })

  it('折叠时折叠按钮应显示 Expand 图标', () => {
    const wrapper = mountComponent(true)
    expect(wrapper.find('.collapse-btn .icon-expand').exists()).toBe(true)
    expect(wrapper.find('.collapse-btn .icon-fold').exists()).toBe(false)
  })

  it('点击折叠按钮应触发 toggleCollapse 事件', async () => {
    const wrapper = mountComponent()
    await wrapper.find('.collapse-btn').trigger('click')
    expect(wrapper.emitted('toggleCollapse')).toBeTruthy()
    expect(wrapper.emitted('toggleCollapse')).toHaveLength(1)
  })

  // ==================== 面包屑 ====================

  it('应根据路由 matched 渲染面包屑', () => {
    // 设置多级路由匹配
    routeState.value.matched = [
      { path: '/business', meta: { title: '业务管理' } },
      { path: '/business/user', meta: { title: '用户管理' } },
    ]
    const wrapper = mountComponent()
    const breadcrumbs = wrapper.findAll('.el-breadcrumb-item-stub')
    expect(breadcrumbs).toHaveLength(2)
    expect(breadcrumbs[0].text()).toBe('业务管理')
    expect(breadcrumbs[1].text()).toBe('用户管理')
  })

  it('没有 matched 的路由应显示空面包屑', () => {
    routeState.value.matched = []
    const wrapper = mountComponent()
    expect(wrapper.findAll('.el-breadcrumb-item-stub')).toHaveLength(0)
  })

  it('matched 项无 title meta 时应被过滤', () => {
    routeState.value.matched = [
      { path: '/business', meta: { title: '业务管理' } },
      { path: '/business/user', meta: {} }, // 无 title
    ]
    const wrapper = mountComponent()
    expect(wrapper.findAll('.el-breadcrumb-item-stub')).toHaveLength(1)
    expect(wrapper.findAll('.el-breadcrumb-item-stub')[0].text()).toBe('业务管理')
  })

  // ==================== 通知铃铛 ====================

  it('未读数为 0 时徽章应隐藏', async () => {
    mockGetAdminUnreadCount.mockResolvedValue({ data: 0 })
    const wrapper = mountComponent()
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-hidden')).toBe('true')
  })

  it('未读数大于 0 时徽章应显示对应数值', async () => {
    mockGetAdminUnreadCount.mockResolvedValue({ data: 5 })
    const wrapper = mountComponent()
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-hidden')).toBe('false')
    expect(badge.attributes('data-value')).toBe('5')
  })

  it('未读数超过 99 时徽章应显示 99', async () => {
    mockGetAdminUnreadCount.mockResolvedValue({ data: 150 })
    const wrapper = mountComponent()
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-value')).toBe('150')
    // max=99 由 EP 内部处理显示为 99+，这里只验证传入值
  })

  it('点击通知铃铛应跳转通知页', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.notification-badge .header-action').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/notification')
  })

  it('onMounted 应获取未读通知数量', async () => {
    mockGetAdminUnreadCount.mockResolvedValue({ data: 3 })
    mountComponent()
    await flushPromises()

    expect(mockGetAdminUnreadCount).toHaveBeenCalled()
  })

  it('API 失败时未读数应保持 0 且不报错', async () => {
    mockGetAdminUnreadCount.mockRejectedValue(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()

    // 不应抛错，徽章保持隐藏
    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-hidden')).toBe('true')
  })

  // ==================== 主题切换 ====================

  it('亮色模式时应显示 Moon 图标', () => {
    isDarkRef.value.value = false
    const wrapper = mountComponent()
    expect(wrapper.find('.header-action .icon-moon').exists()).toBe(true)
    expect(wrapper.find('.header-action .icon-sunny').exists()).toBe(false)
  })

  it('暗黑模式时应显示 Sunny 图标', () => {
    isDarkRef.value.value = true
    const wrapper = mountComponent()
    expect(wrapper.find('.header-action .icon-sunny').exists()).toBe(true)
    expect(wrapper.find('.header-action .icon-moon').exists()).toBe(false)
  })

  it('点击主题切换按钮应调用 toggleTheme', async () => {
    const wrapper = mountComponent()
    // 主题切换按钮是 .header-right 下第二个 .header-action（第一个是通知）
    // 通过 tooltip 的 content 区分：主题切换 tooltip content 为 "切换暗黑模式" 或 "切换亮色模式"
    const themeAction = wrapper.findAll('.header-action')[0] // 第一个 header-action 是通知？不对
    // 实际上 .header-action 在 tooltip 内部，通知在 .notification-badge 里
    // 主题切换在 .header-right 直接子级 tooltip 中
    // 我们用 icon-moon 定位
    await wrapper.find('.icon-moon').trigger('click')
    expect(mockToggleTheme).toHaveBeenCalled()
  })

  // ==================== 全屏按钮 ====================

  it('点击全屏按钮在非全屏时应调用 requestFullscreen', async () => {
    // mock document.fullscreenElement 为 null（非全屏）
    Object.defineProperty(document, 'fullscreenElement', { value: null, configurable: true })
    const mockRequestFullscreen = vi.fn()
    Object.defineProperty(document.documentElement, 'requestFullscreen', {
      value: mockRequestFullscreen,
      configurable: true,
    })
    const wrapper = mountComponent()
    await wrapper.find('.icon-fullscreen').trigger('click')
    expect(mockRequestFullscreen).toHaveBeenCalled()
  })

  it('点击全屏按钮在全屏时应调用 exitFullscreen', async () => {
    // mock document.fullscreenElement 为非 null（已全屏）
    Object.defineProperty(document, 'fullscreenElement', { value: document.documentElement, configurable: true })
    const mockExitFullscreen = vi.fn()
    Object.defineProperty(document, 'exitFullscreen', {
      value: mockExitFullscreen,
      configurable: true,
    })
    const wrapper = mountComponent()
    await wrapper.find('.icon-fullscreen').trigger('click')
    expect(mockExitFullscreen).toHaveBeenCalled()
  })

  // ==================== 用户下拉菜单 ====================

  it('应显示当前管理员用户名', () => {
    adminStoreState.displayName = '超级管理员'
    const wrapper = mountComponent()
    expect(wrapper.find('.username').text()).toBe('超级管理员')
  })

  it('用户名为空时应显示空字符串', () => {
    adminStoreState.displayName = ''
    const wrapper = mountComponent()
    expect(wrapper.find('.username').text()).toBe('')
  })

  it('下拉菜单应包含退出登录选项', () => {
    const wrapper = mountComponent()
    const logoutItem = wrapper.find('.el-dropdown-item-stub[data-command="logout"]')
    expect(logoutItem.exists()).toBe(true)
    expect(logoutItem.text()).toContain('退出登录')
  })

  it('确认退出后应调用 adminStore.logout', async () => {
    mockMessageBoxConfirm.mockResolvedValue('confirm')
    const wrapper = mountComponent()
    // 模拟 dropdown 触发 command 事件
    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    dropdown.vm.$emit('command', 'logout')
    await flushPromises()

    expect(mockMessageBoxConfirm).toHaveBeenCalledWith('确定要退出登录吗？', '提示', expect.anything())
    expect(mockAdminLogout).toHaveBeenCalled()
  })

  it('取消退出时不应调用 logout', async () => {
    // 模拟用户点取消
    mockMessageBoxConfirm.mockRejectedValue('cancel')
    const wrapper = mountComponent()
    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    dropdown.vm.$emit('command', 'logout')
    await flushPromises()

    expect(mockMessageBoxConfirm).toHaveBeenCalled()
    expect(mockAdminLogout).not.toHaveBeenCalled()
  })

  it('未知 command 时不应执行任何操作', async () => {
    const wrapper = mountComponent()
    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    dropdown.vm.$emit('command', 'unknown')
    await flushPromises()

    expect(mockMessageBoxConfirm).not.toHaveBeenCalled()
    expect(mockAdminLogout).not.toHaveBeenCalled()
  })

  // ==================== 路由变化刷新未读数 ====================

  it('路由变化时应刷新未读通知数量', async () => {
    mockGetAdminUnreadCount.mockResolvedValue({ data: 2 })
    const wrapper = mountComponent()
    await flushPromises()

    // 重置 mock 计数
    mockGetAdminUnreadCount.mockClear()
    // 模拟路由变化（watch route.fullPath 触发）
    routeState.value.fullPath = '/business/user'
    await flushPromises()

    expect(mockGetAdminUnreadCount).toHaveBeenCalled()
  })
})
