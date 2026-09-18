/**
 * TagsView 标签页导航组件测试
 * <p>
 * 验证标签页导航的核心行为：默认仪表盘标签、路由变化自动添加标签、
 * 标签激活态、点击跳转、关闭标签（含关闭当前标签时自动跳转）。
 * </p>
 *
 * 小白讲解：
 * TagsView 是管理后台顶部的一排"标签页"，类似浏览器的标签页：
 * - 每访问一个页面就自动新增一个标签，方便快速切换
 * - 仪表盘标签是固定的（affix），不能关闭
 * - 其他标签可以点 X 关闭，关闭当前标签时会自动跳到最后一个标签
 * 我们要测的核心是：
 * 1. 默认有一个"仪表盘"标签且不可关闭
 * 2. 路由变化时自动添加新标签（有 title 且非 hidden 才加）
 * 3. 当前路由对应的标签高亮（active class）
 * 4. 点击标签跳转、点击 X 关闭标签
 * 5. 关闭当前标签时自动跳转到最后一个标签
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TagsView from './TagsView.vue'

// ==================== mock 外部依赖 ====================

/**
 * 用 vi.hoisted 把 mock 函数和响应式容器提升到顶部
 * 这样 vi.mock 工厂函数里就能安全引用它们
 */
const { mockPush, routeState } = vi.hoisted(() => ({
  // router.push 的 mock
  mockPush: vi.fn(),
  // 路由状态容器（reactive 对象在 mock factory 中创建并赋值）
  // 用容器模式是为了让组件内的 watch(() => route.path) 能响应变化
  routeState: { value: null as any },
}))

// mock vue-router —— useRoute 返回 reactive 路由对象，useRouter 返回 push
// 用 reactive 包装是为了让组件内的 watch/computed 能正常响应路由变化
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  routeState.value = reactive({
    path: '/dashboard',
    meta: { title: '仪表盘' } as Record<string, any>,
  })
  return {
    useRoute: () => routeState.value,
    useRouter: () => ({ push: mockPush }),
  }
})

// mock @element-plus/icons-vue —— 保留真实图标（element-plus 内部依赖），
// 只把用到的 Close 图标简化为 i 标签
vi.mock('@element-plus/icons-vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@element-plus/icons-vue')>()
  return {
    ...actual,
    Close: { template: '<i class="icon-close"></i>' },
  }
})

/**
 * 辅助函数：挂载组件
 * stub 掉 ElScrollbar 和 ElIcon，避免渲染复杂 DOM
 */
const mountComponent = () => {
  return mount(TagsView, {
    global: {
      stubs: {
        ElScrollbar: {
          template: '<div class="el-scrollbar-stub"><slot/></div>',
        },
        ElIcon: {
          template: '<i class="el-icon-stub"><slot/></i>',
        },
      },
    },
  })
}

describe('TagsView 标签页导航组件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 重置路由到仪表盘（默认标签）
    routeState.value.path = '/dashboard'
    routeState.value.meta = { title: '仪表盘' }
  })

  // ==================== 默认渲染 ====================

  it('应默认渲染仪表盘标签', () => {
    const wrapper = mountComponent()
    const tags = wrapper.findAll('.tag-item')
    expect(tags).toHaveLength(1)
    expect(tags[0].text()).toContain('仪表盘')
  })

  it('仪表盘标签应标记为 affix（无关闭按钮）', () => {
    const wrapper = mountComponent()
    // affix 标签不渲染关闭按钮（v-if="!tag.affix"）
    expect(wrapper.find('.tag-item .tag-close').exists()).toBe(false)
  })

  // ==================== 路由变化自动添加标签 ====================

  it('路由变化到有 title 的页面应自动添加标签', async () => {
    const wrapper = mountComponent()
    // 初始只有仪表盘
    expect(wrapper.findAll('.tag-item')).toHaveLength(1)

    // 切换到用户管理页
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    // 应新增一个标签
    expect(wrapper.findAll('.tag-item')).toHaveLength(2)
    expect(wrapper.findAll('.tag-item')[1].text()).toContain('用户管理')
  })

  it('路由变化到无 title 的页面不应添加标签', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/no-title-page'
    routeState.value.meta = {}
    await flushPromises()

    // 仍然只有仪表盘
    expect(wrapper.findAll('.tag-item')).toHaveLength(1)
  })

  it('路由变化到 hidden 页面不应添加标签', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/hidden-page'
    routeState.value.meta = { title: '隐藏页', hidden: true }
    await flushPromises()

    // hidden 页面不添加标签
    expect(wrapper.findAll('.tag-item')).toHaveLength(1)
  })

  it('重复访问相同路由不应重复添加标签', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()
    expect(wrapper.findAll('.tag-item')).toHaveLength(2)

    // 再次触发路由变化（路径不变，meta 变化）
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    // 仍然只有 2 个标签，不会重复添加
    expect(wrapper.findAll('.tag-item')).toHaveLength(2)
  })

  it('连续访问多个页面应依次添加标签', async () => {
    const wrapper = mountComponent()
    // 访问用户管理
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()
    // 访问订单管理
    routeState.value.path = '/business/order'
    routeState.value.meta = { title: '订单管理' }
    await flushPromises()
    // 访问商品管理
    routeState.value.path = '/business/product'
    routeState.value.meta = { title: '商品管理' }
    await flushPromises()

    // 仪表盘 + 用户管理 + 订单管理 + 商品管理 = 4 个
    const tags = wrapper.findAll('.tag-item')
    expect(tags).toHaveLength(4)
    expect(tags[1].text()).toContain('用户管理')
    expect(tags[2].text()).toContain('订单管理')
    expect(tags[3].text()).toContain('商品管理')
  })

  // ==================== 标签激活态 ====================

  it('当前路由对应的标签应有 active class', async () => {
    const wrapper = mountComponent()
    // 仪表盘是当前路由，应有 active
    expect(wrapper.findAll('.tag-item')[0].classes()).toContain('active')

    // 切换到用户管理
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    const tags = wrapper.findAll('.tag-item')
    // 仪表盘不再 active
    expect(tags[0].classes()).not.toContain('active')
    // 用户管理 active
    expect(tags[1].classes()).toContain('active')
  })

  it('无对应标签的路由不应有任何标签 active', async () => {
    const wrapper = mountComponent()
    // 切换到无 title 的页面（不会添加标签）
    routeState.value.path = '/no-title'
    routeState.value.meta = {}
    await flushPromises()

    // 仪表盘标签也不 active（因为当前路由不是 /dashboard）
    expect(wrapper.findAll('.tag-item')[0].classes()).not.toContain('active')
  })

  // ==================== 点击标签跳转 ====================

  it('点击标签应调用 router.push 跳转', async () => {
    const wrapper = mountComponent()
    // 先添加一个标签
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    // 点击仪表盘标签
    await wrapper.findAll('.tag-item')[0].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/dashboard')
  })

  it('点击非激活标签应跳转到对应路径', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    // 点击用户管理标签
    await wrapper.findAll('.tag-item')[1].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/business/user')
  })

  // ==================== 关闭标签 ====================

  it('非 affix 标签应显示关闭按钮', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    // 用户管理标签（非 affix）应有关闭按钮
    const userTag = wrapper.findAll('.tag-item')[1]
    expect(userTag.find('.tag-close').exists()).toBe(true)
  })

  it('点击关闭按钮应删除对应标签', async () => {
    const wrapper = mountComponent()
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()
    expect(wrapper.findAll('.tag-item')).toHaveLength(2)

    // 点击用户管理标签的关闭按钮
    await wrapper.findAll('.tag-item')[1].find('.tag-close').trigger('click')
    // 点击关闭按钮不应触发标签跳转（@click.stop）
    expect(mockPush).not.toHaveBeenCalledWith('/business/user')

    // 标签被删除
    expect(wrapper.findAll('.tag-item')).toHaveLength(1)
    expect(wrapper.findAll('.tag-item')[0].text()).toContain('仪表盘')
  })

  it('关闭非当前标签时不应跳转', async () => {
    const wrapper = mountComponent()
    // 当前在用户管理页
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    // 再添加一个订单管理标签
    routeState.value.path = '/business/order'
    routeState.value.meta = { title: '订单管理' }
    await flushPromises()

    // mockPush 重置，方便断言
    mockPush.mockClear()

    // 当前在订单管理页，关闭用户管理标签（非当前）
    const userTag = wrapper.findAll('.tag-item')[1]
    await userTag.find('.tag-close').trigger('click')

    // 关闭非当前标签不应触发跳转
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('关闭当前标签应跳转到最后一个标签', async () => {
    const wrapper = mountComponent()
    // 添加用户管理
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()
    // 添加订单管理（当前页）
    routeState.value.path = '/business/order'
    routeState.value.meta = { title: '订单管理' }
    await flushPromises()

    mockPush.mockClear()

    // 关闭当前标签（订单管理）
    const orderTag = wrapper.findAll('.tag-item')[2]
    await orderTag.find('.tag-close').trigger('click')

    // 应跳转到最后一个标签（用户管理）
    expect(mockPush).toHaveBeenCalledWith('/business/user')
  })

  it('关闭当前标签后只剩仪表盘时应跳转到仪表盘', async () => {
    const wrapper = mountComponent()
    // 添加用户管理（当前页）
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    mockPush.mockClear()

    // 关闭当前标签（用户管理）
    const userTag = wrapper.findAll('.tag-item')[1]
    await userTag.find('.tag-close').trigger('click')

    // 应跳转到最后一个标签（仪表盘）
    expect(mockPush).toHaveBeenCalledWith('/dashboard')
  })

  it('关闭按钮点击应 stop 阻止冒泡（不触发被关闭标签的跳转）', async () => {
    // 小白讲解：
    // 关闭按钮上有 @click.stop，作用是阻止点击事件冒泡到外层的 .tag-item，
    // 否则点击 X 会同时触发"关闭标签"和"跳转到该标签"两个动作。
    // 但如果关闭的是当前激活标签，closeTag 内部会主动跳转到最后一个标签，
    // 所以 mockPush 仍会被调用——关键是参数应该是"最后一个标签的路径"，
    // 而不是"被关闭标签的路径"（后者说明冒泡了）。
    const wrapper = mountComponent()
    // 当前在仪表盘页（仪表盘是激活标签，但 affix 不可关闭）
    // 添加用户管理标签，并切到用户管理页（使其成为激活标签）
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    await flushPromises()

    mockPush.mockClear()

    // 点击用户管理标签的关闭按钮（关闭的是当前激活标签）
    await wrapper.findAll('.tag-item')[1].find('.tag-close').trigger('click')

    // closeTag 内部会跳转到最后一个标签（仪表盘），参数是 '/dashboard'
    // 如果冒泡了，外层 @click 会 push '/business/user'（被关闭标签的路径）
    // 所以这里断言：push 的参数不能是 '/business/user'
    expect(mockPush).not.toHaveBeenCalledWith('/business/user')
    // 且 push 的参数应该是 '/dashboard'（closeTag 内部跳转到最后一个标签）
    expect(mockPush).toHaveBeenCalledWith('/dashboard')
  })

  // ==================== immediate watch ====================

  it('挂载时如果当前路由有 title 应立即添加标签', async () => {
    // 重置路由为非仪表盘的有 title 页面
    routeState.value.path = '/business/user'
    routeState.value.meta = { title: '用户管理' }
    const wrapper = mountComponent()
    await flushPromises()

    // 仪表盘（默认） + 用户管理（immediate watch 添加）
    expect(wrapper.findAll('.tag-item')).toHaveLength(2)
  })

  it('挂载时如果当前路由是仪表盘不应重复添加', async () => {
    // 默认路由就是 /dashboard
    const wrapper = mountComponent()
    await flushPromises()

    // 不会重复添加仪表盘
    expect(wrapper.findAll('.tag-item')).toHaveLength(1)
  })
})
