/**
 * AuthModal 组件测试
 * <p>
 * 验证登录/注册弹窗组件的渲染、Tab切换、表单提交和错误处理。
 * 该组件由 authModalStore 控制显隐，登录成功后自动执行 pendingAction。
 * </p>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AuthModal from './AuthModal.vue'

// ==================== mock 外部依赖 ====================

// mock @/stores/authModal —— 弹窗状态由 store 控制
const mockOpenAuthModal = vi.fn()
const mockCloseAuthModal = vi.fn()
const mockExecutePendingAction = vi.fn()
// 用一个可变对象保存 store 状态，方便每个测试单独控制
const storeState = {
  showAuthModal: false,
  hasPendingAction: false,
}
vi.mock('@/stores/authModal', () => ({
  useAuthModalStore: () => ({
    showAuthModal: storeState.showAuthModal,
    hasPendingAction: storeState.hasPendingAction,
    openAuthModal: mockOpenAuthModal,
    closeAuthModal: mockCloseAuthModal,
    executePendingAction: mockExecutePendingAction,
  }),
}))

// mock @shop/shared —— 校验函数和 API
const mockIsValidPhone = vi.fn()
const mockIsValidPassword = vi.fn()
const mockUserLogin = vi.fn()
const mockUserRegister = vi.fn()
const mockSendVerifyCode = vi.fn()
const mockGetUserInfo = vi.fn()
const mockSetToken = vi.fn()
vi.mock('@shop/shared', () => ({
  isValidPhone: (...args: any[]) => mockIsValidPhone(...args),
  isValidPassword: (...args: any[]) => mockIsValidPassword(...args),
  userLogin: (...args: any[]) => mockUserLogin(...args),
  userRegister: (...args: any[]) => mockUserRegister(...args),
  sendVerifyCode: (...args: any[]) => mockSendVerifyCode(...args),
  getUserInfo: (...args: any[]) => mockGetUserInfo(...args),
  setToken: (...args: any[]) => mockSetToken(...args),
}))

// mock ElMessage —— 避免真实 DOM 操作
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  }
})

// ==================== 测试用商品数据 ====================
const mockLoginResult = {
  data: {
    accessToken: 'access-token-123',
    refreshToken: 'refresh-token-456',
  },
}

describe('AuthModal 登录/注册弹窗组件', () => {
  beforeEach(() => {
    // 每个测试前重置 Pinia 和所有 mock
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 重置 store 状态
    storeState.showAuthModal = false
    storeState.hasPendingAction = false
    // 默认让校验函数返回 true，需要测校验失败时再单独覆盖
    mockIsValidPhone.mockReturnValue(true)
    mockIsValidPassword.mockReturnValue(true)
  })

  /**
   * 辅助函数：挂载组件
   * 使用 global.stubs 把 Element Plus 组件 stub 掉，避免渲染复杂 DOM
   * 关键点：
   * 1. ElForm stub 需要暴露 validate 方法（AuthModal 内部调用 loginFormRef.value.validate()）
   * 2. ElForm stub 需要 @submit.prevent 阻止原生表单提交（否则 button click 会触发 form submit 重复调用 handleLogin）
   * 3. ElDialog stub 需要 name 属性，方便 findComponent 定位
   */
  const mountComponent = () => {
    return mount(AuthModal, {
      global: {
        stubs: {
          ElDialog: {
            name: 'ElDialog',
            template: '<div v-if="modelValue" class="el-dialog-stub"><slot/></div>',
            props: ['modelValue'],
          },
          // ElForm stub：用 <div> 而非 <form>，避免 Vue 3 fallthrough attributes 把
          // AuthModal 的 @submit.prevent="handleLogin" 透传到原生 form 上导致 handleLogin 被重复调用
          ElForm: {
            name: 'ElForm',
            template: '<div class="el-form-stub"><slot/></div>',
            props: ['model', 'rules'],
            methods: {
              validate: () => Promise.resolve(true),
            },
          },
          ElFormItem: { template: '<div class="el-form-item-stub"><slot/></div>', props: ['prop'] },
          // ElInput stub：声明 emits 防止 Vue 3 把 @update:modelValue 当作 fallthrough 原生事件重复触发
          ElInput: {
            template: '<input class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
            props: ['modelValue', 'placeholder', 'type', 'maxlength', 'size', 'showPassword'],
            emits: ['update:modelValue'],
          },
          // ElButton stub：声明 emits 防止 Vue 3 把 @click 当作 fallthrough 原生事件重复触发（否则 handleLogin 被调用 2 次）
          ElButton: {
            template: '<button type="button" class="el-button-stub" :disabled="loading" @click="$emit(\'click\')"><slot/></button>',
            props: ['type', 'loading'],
            emits: ['click'],
          },
        },
      },
    })
  }

  /**
   * 辅助函数：设置指定表单的输入框值
   * 注意：v-show 不会卸载组件，登录表单和注册表单同时存在于 DOM 中
   * 需要通过 formType 参数指定要操作哪个表单
   * @param wrapper - 组件包装器
   * @param formType - 'login' 或 'register'
   * @param values - 按顺序设置的输入框值数组
   */
  const setInputs = async (
    wrapper: ReturnType<typeof mountComponent>,
    formType: 'login' | 'register',
    values: string[],
  ) => {
    // 登录表单是第一个 .form-area，注册表单是第二个
    const formIndex = formType === 'login' ? 0 : 1
    const formArea = wrapper.findAll('.form-area')[formIndex]
    const inputs = formArea.findAll('input.el-input-stub')
    for (let i = 0; i < values.length && i < inputs.length; i++) {
      await inputs[i].setValue(values[i])
    }
  }

  // ==================== 渲染验证 ====================
  it('弹窗关闭时不应渲染内容', () => {
    storeState.showAuthModal = false
    const wrapper = mountComponent()
    // ElDialog v-model=false 时不显示
    expect(wrapper.find('.auth-modal-content').exists()).toBe(false)
  })

  it('弹窗打开时应显示品牌Logo和Tab切换', () => {
    storeState.showAuthModal = true
    const wrapper = mountComponent()
    expect(wrapper.find('.logo-text').text()).toBe('SHOPMALL')
    // 默认显示登录和注册两个Tab
    const tabs = wrapper.findAll('.auth-tab')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toBe('登录')
    expect(tabs[1].text()).toBe('注册')
  })

  it('默认应显示登录Tab', () => {
    storeState.showAuthModal = true
    const wrapper = mountComponent()
    // activeTab 默认为 'login'
    expect(wrapper.find('.auth-tab.active').text()).toBe('登录')
  })

  it('有pendingAction时应显示提示文案', () => {
    storeState.showAuthModal = true
    storeState.hasPendingAction = true
    const wrapper = mountComponent()
    expect(wrapper.find('.pending-hint').text()).toContain('登录后将继续您的操作')
  })

  it('无pendingAction时不应显示提示文案', () => {
    storeState.showAuthModal = true
    storeState.hasPendingAction = false
    const wrapper = mountComponent()
    expect(wrapper.find('.pending-hint').exists()).toBe(false)
  })

  // ==================== Tab 切换 ====================
  it('点击注册Tab应切换到注册表单', async () => {
    storeState.showAuthModal = true
    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')
    // activeTab 应切换为 'register'
    expect(wrapper.find('.auth-tab.active').text()).toBe('注册')
  })

  it('点击登录Tab应切换回登录表单', async () => {
    storeState.showAuthModal = true
    const wrapper = mountComponent()
    // 先切到注册
    await wrapper.findAll('.auth-tab')[1].trigger('click')
    // 再切回登录
    await wrapper.findAll('.auth-tab')[0].trigger('click')
    expect(wrapper.find('.auth-tab.active').text()).toBe('登录')
  })

  // ==================== 关闭弹窗 ====================
  it('关闭弹窗时应调用store.closeAuthModal', async () => {
    storeState.showAuthModal = true
    const wrapper = mountComponent()
    // ElDialog 触发 close 事件
    await wrapper.findComponent({ name: 'ElDialog' }).vm.$emit('close')
    expect(mockCloseAuthModal).toHaveBeenCalledTimes(1)
  })

  // ==================== 登录流程 ====================
  it('登录成功应调用API、存储Token、获取用户信息、执行pendingAction', async () => {
    storeState.showAuthModal = true
    mockUserLogin.mockResolvedValue(mockLoginResult)
    mockGetUserInfo.mockResolvedValue({ data: { id: 1, phone: '13812345678' } })
    mockExecutePendingAction.mockResolvedValue(undefined)

    const wrapper = mountComponent()
    // 模拟用户输入手机号和密码
    await setInputs(wrapper, 'login', ['13812345678', 'abc123456'])
    // 点击登录按钮
    const buttons = wrapper.findAll('.el-button-stub')
    const loginBtn = buttons.find(b => b.text().includes('登录'))!
    await loginBtn.trigger('click')
    await flushPromises()

    // 验证调用链
    expect(mockUserLogin).toHaveBeenCalledWith({
      phone: '13812345678',
      password: 'abc123456',
    })
    expect(mockSetToken).toHaveBeenCalledWith('access-token-123', 'refresh-token-456')
    expect(mockGetUserInfo).toHaveBeenCalledTimes(1)
    expect(mockExecutePendingAction).toHaveBeenCalledTimes(1)
  })

  it('登录失败应显示错误消息且不执行pendingAction', async () => {
    storeState.showAuthModal = true
    mockUserLogin.mockRejectedValue(new Error('账号或密码错误'))

    const wrapper = mountComponent()
    await setInputs(wrapper, 'login', ['13812345678', 'wrongpass'])
    const buttons = wrapper.findAll('.el-button-stub')
    const loginBtn = buttons.find(b => b.text().includes('登录'))!
    await loginBtn.trigger('click')
    await flushPromises()

    // 验证错误处理
    expect(mockSetToken).not.toHaveBeenCalled()
    expect(mockExecutePendingAction).not.toHaveBeenCalled()
    // ElMessage.error 应被调用（错误消息）
    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.error)).toHaveBeenCalledWith('账号或密码错误')
  })

  it('登录中按钮应显示loading状态', async () => {
    storeState.showAuthModal = true
    // 用一个永不 resolve 的 promise 让登录一直处于 loading
    mockUserLogin.mockReturnValue(new Promise(() => {}))

    const wrapper = mountComponent()
    await setInputs(wrapper, 'login', ['13812345678', 'abc123456'])
    const buttons = wrapper.findAll('.el-button-stub')
    const loginBtn = buttons.find(b => b.text().includes('登录'))!
    await loginBtn.trigger('click')
    await flushPromises()

    // submitLoading=true 时按钮文本应变成"登录中..."
    expect(wrapper.find('.el-button-stub').text()).toContain('登录中')
  })

  // ==================== 注册流程 ====================
  it('注册前未同意用户协议应提示且不调用API', async () => {
    storeState.showAuthModal = true
    // 切换到注册Tab
    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')

    // agreed 默认为 false
    const buttons = wrapper.findAll('.el-button-stub')
    const registerBtn = buttons.find(b => b.text().includes('注册'))!
    await registerBtn.trigger('click')
    await flushPromises()

    expect(mockUserRegister).not.toHaveBeenCalled()
    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.warning)).toHaveBeenCalledWith('请先同意用户协议')
  })

  it('注册成功应调用API并切换到登录Tab且填充手机号', async () => {
    storeState.showAuthModal = true
    mockUserRegister.mockResolvedValue({ data: null })

    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')

    // 注册表单有4个输入框：手机号、验证码、密码、确认密码
    await setInputs(wrapper, 'register', ['13812345678', '123456', 'abc123456', 'abc123456'])

    // 勾选用户协议（agreed checkbox）
    // 注意：登录表单也有 checkbox（"记住我"），必须用 .form-agreement 精确定位注册表单的用户协议 checkbox
    const agreementCheckbox = wrapper.find('.form-agreement input[type="checkbox"]')
    await agreementCheckbox.setValue(true)

    const buttons = wrapper.findAll('.el-button-stub')
    const registerBtn = buttons.find(b => b.text().includes('注册'))!
    await registerBtn.trigger('click')
    await flushPromises()

    expect(mockUserRegister).toHaveBeenCalledWith({
      phone: '13812345678',
      password: 'abc123456',
      confirmPassword: 'abc123456',
      verifyCode: '123456',
    })
    // 注册成功后应自动切回登录Tab
    expect(wrapper.find('.auth-tab.active').text()).toBe('登录')
  })

  // ==================== 发送验证码 ====================
  it('未输入手机号点发送验证码应提示', async () => {
    storeState.showAuthModal = true
    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')

    // 不输入手机号直接点发送验证码
    const codeBtn = wrapper.find('.code-btn')
    await codeBtn.trigger('click')

    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.warning)).toHaveBeenCalledWith('请先输入手机号')
    expect(mockSendVerifyCode).not.toHaveBeenCalled()
  })

  it('手机号格式错误点发送验证码应提示且不调用API', async () => {
    storeState.showAuthModal = true
    mockIsValidPhone.mockReturnValue(false)

    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')
    await setInputs(wrapper, 'register', ['12345'])

    const codeBtn = wrapper.find('.code-btn')
    await codeBtn.trigger('click')

    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.warning)).toHaveBeenCalledWith('请输入正确的手机号格式')
    expect(mockSendVerifyCode).not.toHaveBeenCalled()
  })

  it('发送验证码成功应开始60秒倒计时', async () => {
    storeState.showAuthModal = true
    mockSendVerifyCode.mockResolvedValue({ data: null })

    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')
    await setInputs(wrapper, 'register', ['13812345678'])

    const codeBtn = wrapper.find('.code-btn')
    await codeBtn.trigger('click')
    await flushPromises()

    expect(mockSendVerifyCode).toHaveBeenCalledWith('13812345678')
    // 倒计时开始，按钮应显示 60s
    expect(wrapper.find('.code-btn').text()).toContain('60s')
    // 按钮应被禁用（disabled 属性）
    expect(wrapper.find('.code-btn').attributes('disabled')).toBeDefined()
  })

  it('发送验证码失败应显示错误消息', async () => {
    storeState.showAuthModal = true
    mockSendVerifyCode.mockRejectedValue(new Error('发送失败，请稍后重试'))

    const wrapper = mountComponent()
    await wrapper.findAll('.auth-tab')[1].trigger('click')
    await setInputs(wrapper, 'register', ['13812345678'])

    const codeBtn = wrapper.find('.code-btn')
    await codeBtn.trigger('click')
    await flushPromises()

    const { ElMessage } = await import('element-plus')
    expect(vi.mocked(ElMessage.error)).toHaveBeenCalledWith('发送失败，请稍后重试')
    // 失败时不应进入倒计时
    expect(wrapper.find('.code-btn').text()).toBe('获取验证码')
  })
})
