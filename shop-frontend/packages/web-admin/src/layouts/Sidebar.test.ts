/**
 * Sidebar 侧边栏组件测试
 * <p>
 * 验证管理后台侧边栏的 Logo 渲染（展开/折叠态）、菜单项渲染、
 * 子菜单展开、激活态高亮等核心行为。
 * </p>
 *
 * 小白讲解：
 * 侧边栏是管理后台的"功能导航树"，左边一列列出所有功能模块。
 * 我们要测的核心是：
 * 1. Logo 区在展开/折叠两种状态下显示是否正确
 * 2. 菜单项数量、文本是否正确
 * 3. 有子菜单的项（如"业务管理"）能正确展开显示子项
 * 4. 当前路由对应的菜单项应该高亮（is-active）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import Sidebar from './Sidebar.vue'

// ==================== mock 外部依赖 ====================

/**
 * 用 vi.hoisted 把路由状态容器提升到顶部
 * 这样 vi.mock 工厂函数里就能安全引用
 */
const { routeState } = vi.hoisted(() => ({
  // 路由状态容器（reactive 对象在 mock factory 中创建并赋值）
  routeState: { value: null as any },
}))

// mock vue-router —— useRoute 返回 reactive 路由对象
// 用 reactive 包装是为了让组件内的 computed(() => route.path) 能正常响应
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  routeState.value = reactive({ path: '/dashboard' })
  return {
    useRoute: () => routeState.value,
  }
})

// mock @element-plus/icons-vue —— 保留真实图标（el-menu 内部可能依赖），
// 只是避免图标组件渲染复杂 SVG 影响测试性能
vi.mock('@element-plus/icons-vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@element-plus/icons-vue')>()
  return {
    ...actual,
    // 把用到的图标简化为 i 标签
    Odometer: { template: '<i class="icon-odometer"></i>' },
    User: { template: '<i class="icon-user"></i>' },
    OfficeBuilding: { template: '<i class="icon-building"></i>' },
    Goods: { template: '<i class="icon-goods"></i>' },
    ShoppingCart: { template: '<i class="icon-cart"></i>' },
    Tickets: { template: '<i class="icon-tickets"></i>' },
    PictureFilled: { template: '<i class="icon-picture"></i>' },
    Setting: { template: '<i class="icon-setting"></i>' },
    Lock: { template: '<i class="icon-lock"></i>' },
    Document: { template: '<i class="icon-document"></i>' },
    Money: { template: '<i class="icon-money"></i>' },
    Bell: { template: '<i class="icon-bell"></i>' },
    Ticket: { template: '<i class="icon-ticket"></i>' },
    Discount: { template: '<i class="icon-discount"></i>' },
    AlarmClock: { template: '<i class="icon-alarm"></i>' },
    ChatDotRound: { template: '<i class="icon-chat"></i>' },
  }
})

/**
 * 辅助函数：挂载组件
 * @param collapse - 是否折叠
 *
 * stub 掉 EP 的 el-menu / el-menu-item / el-sub-menu / el-scrollbar，
 * 避免渲染复杂 DOM，同时保留关键属性（index、default-active）方便断言
 */
const mountComponent = (collapse = false) => {
  return mount(Sidebar, {
    props: { collapse },
    global: {
      stubs: {
        ElScrollbar: { template: '<div class="el-scrollbar-stub"><slot/></div>' },
        // el-menu stub：渲染 default-active 和 collapse 到 data 属性，方便断言
        ElMenu: {
          name: 'ElMenu',
          template: `<div class="el-menu-stub" :data-default-active="defaultActive" :data-collapse="collapse"><slot/></div>`,
          props: ['defaultActive', 'collapse', 'backgroundColor', 'textColor', 'activeTextColor', 'router', 'collapseTransition'],
        },
        // el-menu-item stub：渲染 index 到 data 属性，渲染插槽内容
        ElMenuItem: {
          name: 'ElMenuItem',
          template: '<div class="el-menu-item-stub" :data-index="index" :class="{ \'is-active\': isActive }"><slot/><slot name="title"/></div>',
          props: ['index'],
          data() {
            return { isActive: false }
          },
        },
        // el-sub-menu stub：渲染 index 到 data 属性，渲染 title 插槽和默认插槽
        ElSubMenu: {
          name: 'ElSubMenu',
          template: '<div class="el-sub-menu-stub" :data-index="index"><slot name="title"/><div class="el-sub-menu-children"><slot/></div></div>',
          props: ['index'],
        },
        ElIcon: { template: '<i class="el-icon-stub"><slot/></i>' },
      },
    },
  })
}

describe('Sidebar 侧边栏组件', () => {
  beforeEach(() => {
    // 重置路由到 /dashboard
    routeState.value.path = '/dashboard'
  })

  // ==================== Logo 区域 ====================

  it('应渲染 Logo 图标 S', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.logo-icon').text()).toBe('S')
  })

  it('展开时应显示 Logo 文本 ShopMall 管理后台', () => {
    const wrapper = mountComponent(false)
    const logoText = wrapper.find('.logo-text')
    expect(logoText.exists()).toBe(true)
    expect(logoText.find('.gradient-text').text()).toBe('ShopMall')
    expect(logoText.find('.sub').text()).toBe('管理后台')
  })

  it('折叠时应隐藏 Logo 文本', () => {
    const wrapper = mountComponent(true)
    expect(wrapper.find('.logo-text').exists()).toBe(false)
  })

  it('折叠时 Logo 区应有 collapse class', () => {
    const wrapper = mountComponent(true)
    expect(wrapper.find('.sidebar-logo').classes()).toContain('collapse')
  })

  it('展开时 Logo 区不应有 collapse class', () => {
    const wrapper = mountComponent(false)
    expect(wrapper.find('.sidebar-logo').classes()).not.toContain('collapse')
  })

  // ==================== 菜单传递 ====================

  it('应把 collapse 属性传递给 el-menu', () => {
    const wrapper = mountComponent(true)
    const menu = wrapper.find('.el-menu-stub')
    expect(menu.attributes('data-collapse')).toBe('true')
  })

  it('应把当前路由路径作为 default-active 传递给 el-menu', () => {
    routeState.value.path = '/business/user'
    const wrapper = mountComponent()
    const menu = wrapper.find('.el-menu-stub')
    expect(menu.attributes('data-default-active')).toBe('/business/user')
  })

  // ==================== 顶级菜单项 ====================

  it('应渲染所有顶级菜单项（无子菜单 + 有子菜单）', () => {
    const wrapper = mountComponent()
    // 顶级菜单项 = el-menu-item-stub + el-sub-menu-stub
    const topItems = wrapper.findAll('.el-menu-stub > .el-menu-item-stub, .el-menu-stub > .el-sub-menu-stub')
    // 仪表盘 / 消息通知 / 优惠券管理 / 满减活动 / 秒杀活动 / 评价管理
    // 业务管理 / 内容管理 / 系统管理 / 日志管理 / 安全审计 = 共 11 项
    expect(topItems).toHaveLength(11)
  })

  it('应渲染无子菜单的菜单项（仪表盘）', () => {
    const wrapper = mountComponent()
    const dashboard = wrapper.find('.el-menu-item-stub[data-index="/dashboard"]')
    expect(dashboard.exists()).toBe(true)
    expect(dashboard.text()).toContain('仪表盘')
  })

  it('应渲染无子菜单的菜单项（消息通知）', () => {
    const wrapper = mountComponent()
    const notification = wrapper.find('.el-menu-item-stub[data-index="/notification"]')
    expect(notification.exists()).toBe(true)
    expect(notification.text()).toContain('消息通知')
  })

  it('应渲染无子菜单的菜单项（优惠券管理）', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/coupon"]').text()).toContain('优惠券管理')
  })

  it('应渲染无子菜单的菜单项（满减活动）', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/promotion"]').text()).toContain('满减活动')
  })

  it('应渲染无子菜单的菜单项（秒杀活动）', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/seckill"]').text()).toContain('秒杀活动')
  })

  it('应渲染无子菜单的菜单项（评价管理）', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/comment"]').text()).toContain('评价管理')
  })

  // ==================== 子菜单 ====================

  it('业务管理应渲染为子菜单', () => {
    const wrapper = mountComponent()
    const businessMenu = wrapper.find('.el-sub-menu-stub[data-index="/business"]')
    expect(businessMenu.exists()).toBe(true)
    expect(businessMenu.text()).toContain('业务管理')
  })

  it('业务管理子菜单应包含 8 个子项', () => {
    const wrapper = mountComponent()
    const businessMenu = wrapper.find('.el-sub-menu-stub[data-index="/business"]')
    const children = businessMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    // 用户管理 / 商家管理 / 商品管理 / 分类管理 / 品牌管理 / 订单管理 / 退款管理 / 提现审核
    expect(children).toHaveLength(8)
  })

  it('业务管理子菜单应包含用户管理', () => {
    const wrapper = mountComponent()
    const userItem = wrapper.find('.el-menu-item-stub[data-index="/business/user"]')
    expect(userItem.exists()).toBe(true)
    expect(userItem.text()).toContain('用户管理')
  })

  it('业务管理子菜单应包含商家管理', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/business/merchant"]').text()).toContain('商家管理')
  })

  it('业务管理子菜单应包含订单管理', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-item-stub[data-index="/business/order"]').text()).toContain('订单管理')
  })

  it('内容管理应渲染为子菜单并包含 2 个子项', () => {
    const wrapper = mountComponent()
    const contentMenu = wrapper.find('.el-sub-menu-stub[data-index="/content"]')
    expect(contentMenu.exists()).toBe(true)
    const children = contentMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    // Banner管理 / 公告管理
    expect(children).toHaveLength(2)
  })

  it('系统管理应渲染为子菜单并包含 4 个子项', () => {
    const wrapper = mountComponent()
    const systemMenu = wrapper.find('.el-sub-menu-stub[data-index="/system"]')
    expect(systemMenu.exists()).toBe(true)
    const children = systemMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    // 管理员管理 / 角色管理 / 权限管理 / 部门管理
    expect(children).toHaveLength(4)
  })

  it('日志管理应渲染为子菜单并包含 2 个子项', () => {
    const wrapper = mountComponent()
    const logMenu = wrapper.find('.el-sub-menu-stub[data-index="/log"]')
    expect(logMenu.exists()).toBe(true)
    const children = logMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    // 操作日志 / 登录日志
    expect(children).toHaveLength(2)
  })

  it('安全审计应渲染为子菜单并包含 1 个子项', () => {
    const wrapper = mountComponent()
    const securityMenu = wrapper.find('.el-sub-menu-stub[data-index="/security"]')
    expect(securityMenu.exists()).toBe(true)
    const children = securityMenu.findAll('.el-sub-menu-children .el-menu-item-stub')
    expect(children).toHaveLength(1)
    expect(children[0].text()).toContain('安全事件')
  })

  // ==================== 激活态响应路由变化 ====================

  it('切换路由后 default-active 应更新', async () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/dashboard')

    // 模拟路由变化
    routeState.value.path = '/business/user'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/business/user')
  })

  it('切换到子菜单路径后 default-active 应更新', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/system/role'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-menu-stub').attributes('data-default-active')).toBe('/system/role')
  })
})
