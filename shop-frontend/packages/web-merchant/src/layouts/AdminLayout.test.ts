/**
 * AdminLayout 商家后台布局组件测试
 * <p>
 * 验证商家后台布局的侧边栏菜单、面包屑、通知铃铛、用户下拉菜单（退出登录）、
 * 菜单激活态特殊路由处理等核心行为。
 * </p>
 *
 * 小白讲解：
 * 这个布局是商家后台的"整体页面框架"，包含：
 * - 左侧：深色玻璃侧边栏（Logo + 功能菜单）
 * - 右侧上：顶部导航栏（面包屑 + 通知铃铛 + 用户头像/退出登录）
 * - 右侧下：内容区（子路由页面渲染在这里）
 * 我们要测的核心是：
 * 1. 菜单项都能正确渲染（工作台、商品管理、订单管理 等）
 * 2. 菜单激活态根据路由自动高亮，且对编辑页/订单详情页有特殊处理
 * 3. 面包屑根据路由 meta.title 和 parentTitle 自动生成
 * 4. 通知铃铛徽章数量正确，点击跳转通知页
 * 5. 退出登录需要 ElMessageBox 确认后调用 merchantStore.logout
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminLayout from './AdminLayout.vue'

// ==================== mock 外部依赖 ====================

/**
 * 用 vi.hoisted 把 mock 函数和响应式容器提升到顶部
 * 这样 vi.mock 工厂函数里就能安全引用它们
 */
const {
  mockPush,
  routeState,
  merchantMocks,
  mockGetMerchantUnreadCount,
  mockMerchantLogout,
  mockMessageBoxConfirm,
} = vi.hoisted(() => ({
  // router.push 的 mock
  mockPush: vi.fn(),
  // 路由状态容器（reactive 对象在 mock factory 中创建并赋值）
  routeState: { value: null as any },
  // merchantStore 的响应式容器（ref 在 mock factory 中赋值）
  merchantMocks: {
    merchantInfo: null as any,
    logout: vi.fn(),
  },
  // 获取商家未读通知数 API 的 mock
  mockGetMerchantUnreadCount: vi.fn(),
  // merchantStore.logout 的 mock（与 merchantMocks.logout 是同一个）
  mockMerchantLogout: vi.fn(),
  // ElMessageBox.confirm 的 mock
  mockMessageBoxConfirm: vi.fn(),
}))

// mock vue-router —— useRoute 返回 reactive 路由对象，useRouter 返回 push
// 用 reactive 包装是为了让组件内的 computed/watch 能正常响应
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  routeState.value = reactive({
    path: '/',
    fullPath: '/',
    meta: {} as Record<string, any>,
  })
  return {
    useRoute: () => routeState.value,
    useRouter: () => ({ push: mockPush }),
  }
})

// mock @/stores/merchant —— merchantInfo 是 ref，logout 是函数
//
// 小白讲解：
// 真实的 pinia store 里，state 是自动解包 ref 的，组件代码可以直接写
// merchantStore.merchantInfo?.name 来拿 name。
// 但我们 mock 时返回的是普通对象，普通对象不会自动解包 ref，
// 所以 merchantStore.merchantInfo 拿到的是 ref 对象本身 { value: ... }，
// 访问 .name 会得到 undefined。
// 解决办法：用 getter 让 merchantInfo 访问时自动返回 ref 的 value，
// 这样组件里的 merchantStore.merchantInfo?.name 就能正确拿到 name，
// 而且 computed 也能追踪 ref 的变化（getter 内部访问了 ref.value）。
vi.mock('@/stores/merchant', async () => {
  const { ref } = await import('vue')
  merchantMocks.merchantInfo = ref(null)
  return {
    useMerchantStore: () => ({
      get merchantInfo() {
        return merchantMocks.merchantInfo.value
      },
      logout: mockMerchantLogout,
    }),
  }
})

// mock @shop/shared —— getMerchantUnreadCount
vi.mock('@shop/shared', () => ({
  getMerchantUnreadCount: (...args: any[]) => mockGetMerchantUnreadCount(...args),
}))

// mock @element-plus/icons-vue —— 保留真实图标（element-plus 内部依赖），
// 用到的图标简化为 i 标签
vi.mock('@element-plus/icons-vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@element-plus/icons-vue')>()
  return {
    ...actual,
    DataBoard: { template: '<i class="icon-data-board"></i>' },
    Goods: { template: '<i class="icon-goods"></i>' },
    Document: { template: '<i class="icon-document"></i>' },
    TrendCharts: { template: '<i class="icon-trend"></i>' },
    Setting: { template: '<i class="icon-setting"></i>' },
    ArrowDown: { template: '<i class="icon-arrow-down"></i>' },
    ChatDotRound: { template: '<i class="icon-chat"></i>' },
    Wallet: { template: '<i class="icon-wallet"></i>' },
    Bell: { template: '<i class="icon-bell"></i>' },
    Ticket: { template: '<i class="icon-ticket"></i>' },
    Discount: { template: '<i class="icon-discount"></i>' },
    AlarmClock: { template: '<i class="icon-alarm"></i>' },
  }
})

// mock element-plus —— ElMessageBox.confirm 可控制确认/取消，ElMessage 成功提示
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessageBox: {
      confirm: (...args: any[]) => mockMessageBoxConfirm(...args),
    },
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  }
})

/**
 * 辅助函数：挂载组件
 * stub 掉 EP 的 menu/breadcrumb/badge/dropdown/icon，避免渲染复杂 DOM
 *
 * 小白讲解：
 * 因为 AdminLayout.vue 第113行模板里写了 <router-view :key="$route.fullPath" />，
 * 这里直接用了 $route 这个全局属性（而不是通过 useRoute() 获取），
 * 所以光 mock useRoute() 不够，还得在 global.mocks 里提供 $route，
 * 否则 Vue 渲染时会报 "Property $route was accessed during render but is not defined"。
 * 这和 CartView 模板里用 $router.push() 需要在 mocks 提供 $router 是同一个道理。
 */
const mountComponent = () => {
  return mount(AdminLayout, {
    global: {
      // 提供 $route 全局属性（模板里直接用了 $route.fullPath）
      mocks: { $route: routeState.value },
      stubs: {
        RouterView: { template: '<div class="router-view-stub"></div>' },
        ElMenu: {
          name: 'ElMenu',
          template: `<div class="el-menu-stub" :data-default-active="defaultActive"><slot/></div>`,
          props: ['defaultActive', 'router', 'backgroundColor', 'textColor', 'activeTextColor'],
        },
        ElMenuItem: {
          name: 'ElMenuItem',
          template: '<div class="el-menu-item-stub" :data-index="index"><slot/><slot name="title"/></div>',
          props: ['index'],
        },
        ElSubMenu: {
          name: 'ElSubMenu',
          template: '<div class="el-sub-menu-stub" :data-index="index"><slot name="title"/><div class="el-sub-menu-children"><slot/></div></div>',
          props: ['index'],
        },
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
        },
        ElDropdownMenu: { template: '<div class="el-dropdown-menu-stub"><slot/></div>' },
        ElDropdownItem: {
          template: '<div class="el-dropdown-item-stub" @click="$emit(\'click\')"><slot/></div>',
          emits: ['click'],
        },
        ElIcon: { template: '<i class="el-icon-stub"><slot/></i>' },
      },
    },
  })
}

describe('AdminLayout 商家后台布局组件', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 重置路由到首页
    routeState.value.path = '/'
    routeState.value.fullPath = '/'
    routeState.value.meta = {}
    // 重置商家信息为 null
    merchantMocks.merchantInfo.value = null
    // 默认未读数 API 返回 0
    mockGetMerchantUnreadCount.mockResolvedValue({ data: 0 })
    // 默认 ElMessageBox.confirm 返回 resolved（用户点确定）
    mockMessageBoxConfirm.mockResolvedValue('confirm')
  })

  // ==================== Logo 区域 ====================

  it('应渲染 Logo 图标和文本', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.logo-icon').text()).toBe('🏪')
    expect(wrapper.find('.logo-text').text()).toBe('商家后台')
  })

  // ==================== 侧边栏菜单 ====================

  it('应渲染所有顶级菜单项（无子菜单 + 有子菜单）', () => {
    const wrapper = mountComponent()
    const topItems = wrapper.findAll('.el-menu-stub > .el-menu-item-stub, .el-menu-stub > .el-sub-menu-stub')
    // 工作台 / 商品管理(sub) / 订单管理(sub) / 评价管理(sub) / 数据中心 / 结算管理
    // 优惠券管理 / 满减活动 / 秒杀活动 / 店铺设置 / 消息通知 = 共 11 项
    expect(topItems).toHaveLength(11)
  })

  it('应渲染工作台菜单项', () => {
    const wrapper = mountComponent()
    const dashboard = wrapper.find('.el-menu-item-stub[data-index="/"]')
    expect(dashboard.exists()).toBe(true)
    expect(dashboard.text()).toContain('工作台')
  })

  it('应渲染数据中心菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/data"]').text()).toContain('数据中心')
  })

  it('应渲染结算管理菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/settlement"]').text()).toContain('结算管理')
  })

  it('应渲染优惠券管理菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/coupon"]').text()).toContain('优惠券管理')
  })

  it('应渲染满减活动菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/promotion"]').text()).toContain('满减活动')
  })

  it('应渲染秒杀活动菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/seckill"]').text()).toContain('秒杀活动')
  })

  it('应渲染店铺设置菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/shop/settings"]').text()).toContain('店铺设置')
  })

  it('应渲染消息通知菜单项', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/notification"]').text()).toContain('消息通知')
  })

  // ==================== 子菜单 ====================

  it('商品管理应渲染为子菜单并包含 2 个子项', () => {
    const wrapper = mountComponent()
    const productMenu = wrapper.find('.el-sub-menu-stub[data-index="product"]')
    expect(productMenu.exists()).toBe(true)
    expect(productMenu.text()).toContain('商品管理')
    const children = productMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    // 商品列表 / 发布商品
    expect(children).toHaveLength(2)
    expect(children[0].text()).toBe('商品列表')
    expect(children[1].text()).toBe('发布商品')
  })

  it('订单管理应渲染为子菜单并包含 1 个子项', () => {
    const wrapper = mountComponent()
    const orderMenu = wrapper.find('.el-sub-menu-stub[data-index="order"]')
    expect(orderMenu.exists()).toBe(true)
    const children = orderMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    expect(children).toHaveLength(1)
    expect(children[0].text()).toBe('订单列表')
  })

  it('评价管理应渲染为子菜单并包含 1 个子项', () => {
    const wrapper = mountComponent()
    const commentMenu = wrapper.find('.el-sub-menu-stub[data-index="comment"]')
    expect(commentMenu.exists()).toBe(true)
    const children = commentMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    expect(children).toHaveLength(1)
    expect(children[0].text()).toBe('评价列表')
  })

  // ==================== 菜单激活态 ====================

  it('应把当前路由路径作为 default-active 传递给 el-menu', () => {
    routeState.value.path = '/product/list'
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/product/list')
  })

  it('编辑商品页（带id）应高亮"发布商品"菜单', () => {
    // 特殊处理：/product/edit/123 → /product/edit
    routeState.value.path = '/product/edit/123'
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/product/edit')
  })

  it('订单详情页（纯数字id）应高亮"订单列表"菜单', () => {
    // 特殊处理：/order/123 → /order/list
    routeState.value.path = '/order/123'
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/order/list')
  })

  it('切换路由后 default-active 应更新', async () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/')

    routeState.value.path = '/coupon'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/coupon')
  })

  // ==================== 面包屑 ====================

  it('应始终显示"首页"面包屑', () => {
    const wrapper = mountComponent()
    const breadcrumbs = wrapper.findAll('.el-breadcrumb-item-stub')
    expect(breadcrumbs.length).toBeGreaterThanOrEqual(1)
    expect(breadcrumbs[0].text()).toBe('首页')
  })

  it('有 parentTitle 和 title 时应显示多级面包屑', () => {
    routeState.value.meta = { title: '商品列表', parentTitle: '商品管理' }
    const wrapper = mountComponent()
    const breadcrumbs = wrapper.findAll('.el-breadcrumb-item-stub')
    // 首页 / 商品管理 / 商品列表
    expect(breadcrumbs).toHaveLength(3)
    expect(breadcrumbs[1].text()).toBe('商品管理')
    expect(breadcrumbs[2].text()).toBe('商品列表')
  })

  it('title 为"工作台"时不应重复显示', () => {
    routeState.value.meta = { title: '工作台' }
    const wrapper = mountComponent()
    const breadcrumbs = wrapper.findAll('.el-breadcrumb-item-stub')
    // 只显示"首页"，不显示"工作台"（因为 currentTitle === '工作台' 被过滤）
    expect(breadcrumbs).toHaveLength(1)
    expect(breadcrumbs[0].text()).toBe('首页')
  })

  it('无 title 时只显示"首页"', () => {
    routeState.value.meta = {}
    const wrapper = mountComponent()
    const breadcrumbs = wrapper.findAll('.el-breadcrumb-item-stub')
    expect(breadcrumbs).toHaveLength(1)
  })

  // ==================== 通知铃铛 ====================

  it('未读数为 0 时徽章应隐藏', async () => {
    mockGetMerchantUnreadCount.mockResolvedValue({ data: 0 })
    const wrapper = mountComponent()
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-hidden')).toBe('true')
  })

  it('未读数大于 0 时徽章应显示对应数值', async () => {
    mockGetMerchantUnreadCount.mockResolvedValue({ data: 5 })
    const wrapper = mountComponent()
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-hidden')).toBe('false')
    expect(badge.attributes('data-value')).toBe('5')
  })

  it('点击通知铃铛应跳转通知页', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.notification-bell').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/notification')
  })

  it('onMounted 应获取未读通知数量', async () => {
    mockGetMerchantUnreadCount.mockResolvedValue({ data: 3 })
    mountComponent()
    await flushPromises()

    expect(mockGetMerchantUnreadCount).toHaveBeenCalled()
  })

  it('API 失败时未读数应保持 0 且不报错', async () => {
    mockGetMerchantUnreadCount.mockRejectedValue(new Error('network error'))
    const wrapper = mountComponent()
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-hidden')).toBe('true')
  })

  // ==================== 用户信息 ====================

  it('无商家信息时应显示默认商家名称', () => {
    merchantMocks.merchantInfo.value = null
    const wrapper = mountComponent()
    expect(wrapper.find('.user-name').text()).toBe('商家管理员')
  })

  it('有商家信息时应显示商家名称', () => {
    merchantMocks.merchantInfo.value = { name: '测试店铺' }
    const wrapper = mountComponent()
    expect(wrapper.find('.user-name').text()).toBe('测试店铺')
  })

  it('商家头像应显示名称首字符', () => {
    merchantMocks.merchantInfo.value = { name: '测试店铺' }
    const wrapper = mountComponent()
    expect(wrapper.find('.user-avatar').text()).toBe('测')
  })

  it('无商家信息时头像应显示"商"字', () => {
    merchantMocks.merchantInfo.value = null
    const wrapper = mountComponent()
    // merchantName = '商家管理员'，首字符是'商'
    expect(wrapper.find('.user-avatar').text()).toBe('商')
  })

  // ==================== 退出登录 ====================

  it('下拉菜单应包含退出登录选项', () => {
    const wrapper = mountComponent()
    const logoutItem = wrapper.find('.el-dropdown-item-stub')
    expect(logoutItem.exists()).toBe(true)
    expect(logoutItem.text()).toContain('退出登录')
  })

  it('确认退出后应调用 merchantStore.logout 并跳转登录页', async () => {
    mockMessageBoxConfirm.mockResolvedValue('confirm')
    mockMerchantLogout.mockResolvedValue(undefined)
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.el-dropdown-item-stub').trigger('click')
    await flushPromises()

    expect(mockMessageBoxConfirm).toHaveBeenCalledWith('确定要退出登录吗？', '提示', expect.anything())
    expect(mockMerchantLogout).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/login')

    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.success)).toHaveBeenCalledWith('已退出登录')
  })

  it('取消退出时不应调用 logout', async () => {
    // 模拟用户点取消
    mockMessageBoxConfirm.mockRejectedValue('cancel')
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('.el-dropdown-item-stub').trigger('click')
    await flushPromises()

    expect(mockMessageBoxConfirm).toHaveBeenCalled()
    expect(mockMerchantLogout).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  // ==================== 路由变化刷新未读数 ====================

  it('路由变化时应刷新未读通知数量', async () => {
    mockGetMerchantUnreadCount.mockResolvedValue({ data: 2 })
    const wrapper = mountComponent()
    await flushPromises()

    // 重置 mock 计数
    mockGetMerchantUnreadCount.mockClear()
    // 模拟路由变化（watch route.fullPath 触发）
    routeState.value.fullPath = '/product/list'
    await flushPromises()

    expect(mockGetMerchantUnreadCount).toHaveBeenCalled()
  })
})
