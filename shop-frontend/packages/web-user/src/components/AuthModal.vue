<template>
  <!-- 登录/注册弹窗 - 极简独立站风格 -->
  <el-dialog
    v-model="dialogVisible"
    width="440px"
    :show-close="true"
    :close-on-click-modal="false"
    align-center
    class="auth-modal"
    @close="handleClose"
  >
    <div class="auth-modal-content">
      <!-- 品牌Logo -->
      <div class="brand-logo">
        <span class="logo-text">SHOPMALL</span>
      </div>

      <!-- 待执行操作提示 -->
      <p v-if="authModalStore.hasPendingAction" class="pending-hint">
        登录后将继续您的操作
      </p>

      <!-- Tab切换：登录/注册 -->
      <div class="auth-tabs">
        <span
          :class="['auth-tab', { active: activeTab === 'login' }]"
          @click="switchTab('login')"
        >登录</span>
        <span
          :class="['auth-tab', { active: activeTab === 'register' }]"
          @click="switchTab('register')"
        >注册</span>
      </div>

      <!-- 登录表单 -->
      <div v-show="activeTab === 'login'" class="form-area">
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-width="0"
          @submit.prevent="handleLogin"
        >
          <!-- 手机号 -->
          <el-form-item prop="phone">
            <el-input
              v-model="loginForm.phone"
              placeholder="请输入手机号"
              maxlength="11"
              size="large"
            />
          </el-form-item>

          <!-- 密码 -->
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              size="large"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <!-- 记住我 & 忘记密码 -->
          <div class="form-options">
            <label class="remember-me">
              <input type="checkbox" /> 记住我
            </label>
            <a class="forgot-link" href="javascript:void(0)">忘记密码？</a>
          </div>

          <!-- 登录按钮 -->
          <el-form-item>
            <el-button
              type="primary"
              :loading="submitLoading"
              class="submit-btn"
              @click="handleLogin"
            >
              {{ submitLoading ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 注册表单 -->
      <div v-show="activeTab === 'register'" class="form-area">
        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-width="0"
          @submit.prevent="handleRegister"
        >
          <!-- 手机号 -->
          <el-form-item prop="phone">
            <el-input
              v-model="registerForm.phone"
              placeholder="请输入手机号"
              maxlength="11"
              size="large"
            />
          </el-form-item>

          <!-- 验证码 -->
          <el-form-item prop="verifyCode">
            <div class="code-row">
              <el-input
                v-model="registerForm.verifyCode"
                placeholder="请输入验证码"
                maxlength="6"
                size="large"
              />
              <button
                type="button"
                class="code-btn"
                :disabled="codeCooldown > 0"
                @click="handleSendCode"
              >
                {{ codeCooldown > 0 ? `${codeCooldown}s` : '获取验证码' }}
              </button>
            </div>
          </el-form-item>

          <!-- 密码 -->
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请设置密码（6-20位，含字母和数字）"
              show-password
              size="large"
            />
          </el-form-item>

          <!-- 确认密码 -->
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              show-password
              size="large"
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <!-- 用户协议 -->
          <div class="form-agreement">
            <label class="agreement-label">
              <input v-model="agreed" type="checkbox" /> 我已阅读并同意
              <a class="agreement-link" href="javascript:void(0)">《用户协议》</a>和<a class="agreement-link" href="javascript:void(0)">《隐私政策》</a>
            </label>
          </div>

          <!-- 注册按钮 -->
          <el-form-item>
            <el-button
              type="primary"
              :loading="submitLoading"
              class="submit-btn"
              @click="handleRegister"
            >
              {{ submitLoading ? '注册中...' : '注册' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 登录/注册弹窗组件
 * 全局模态框，由 authModalStore 控制显示/隐藏
 * 登录成功后自动执行 pendingAction（如加购、跳转等）
 */

import { ref, reactive, computed, watch, onUnmounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthModalStore } from '@/stores/authModal'
import { isValidPhone, isValidPassword, userRegister, sendVerifyCode, userLogin, getUserInfo, setToken } from '@shop/shared'

const authModalStore = useAuthModalStore()

/** 弹窗显示状态（双向绑定，同步store） */
const dialogVisible = computed({
  get: () => authModalStore.showAuthModal,
  set: (val: boolean) => {
    if (!val) {
      authModalStore.closeAuthModal()
    }
  },
})

/** 当前激活的Tab：login登录 / register注册 */
const activeTab = ref<'login' | 'register'>('login')

/** 表单引用 */
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

/** 是否正在提交（登录或注册） */
const submitLoading = ref(false)

/** 是否同意用户协议 */
const agreed = ref(false)

/** 验证码倒计时（秒），0表示可发送 */
const codeCooldown = ref(0)

/** 倒计时定时器 */
let cooldownTimer: ReturnType<typeof setInterval> | null = null

/** 登录表单数据 */
const loginForm = reactive({
  phone: '',
  password: '',
})

/** 注册表单数据 */
const registerForm = reactive({
  phone: '',
  verifyCode: '',
  password: '',
  confirmPassword: '',
})

/** 登录表单校验规则 */
const loginRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value && !isValidPhone(value)) {
        callback(new Error('请输入正确的手机号格式'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value && !isValidPassword(value)) {
        callback(new Error('密码需6-20位，包含字母和数字'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
}

/** 注册表单校验规则 */
const registerRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value && !isValidPhone(value)) {
        callback(new Error('请输入正确的手机号格式'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
  verifyCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value && !isValidPassword(value)) {
        callback(new Error('密码需6-20位，包含字母和数字'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value && value !== registerForm.password) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
}

/**
 * 切换Tab（登录/注册）
 */
const switchTab = (tab: 'login' | 'register') => {
  activeTab.value = tab
}

/**
 * 处理登录
 * 校验表单后调用登录接口
 * 登录成功后：执行pendingAction + 关闭弹窗 + Toast提示
 * 注意：不使用 useAuth().login()，因为它会强制跳转，弹窗模式需要留在当前页
 */
const handleLogin = async () => {
  if (submitLoading.value) return
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    // 直接调用登录API，不跳转
    const res = await userLogin({ phone: loginForm.phone, password: loginForm.password })
    // 存储 Token
    setToken(res.data.accessToken, res.data.refreshToken)
    // 获取用户信息（更新全局状态）
    await getUserInfo()
    ElMessage.success('登录成功')
    // 执行待执行操作（如加购、跳转等）
    await authModalStore.executePendingAction()
    // 重置表单
    loginForm.phone = ''
    loginForm.password = ''
  } catch (error) {
    const msg = error instanceof Error ? error.message : '登录失败'
    ElMessage.error(msg)
  } finally {
    submitLoading.value = false
  }
}

/**
 * 发送短信验证码
 * 点击后开始60秒倒计时，防止频繁发送
 */
const handleSendCode = async () => {
  if (!registerForm.phone) {
    ElMessage.warning('请先输入手机号')
    return
  }
  if (!isValidPhone(registerForm.phone)) {
    ElMessage.warning('请输入正确的手机号格式')
    return
  }

  try {
    await sendVerifyCode(registerForm.phone)
    ElMessage.success('验证码已发送')
    // 开始60秒倒计时
    codeCooldown.value = 60
    cooldownTimer = setInterval(() => {
      codeCooldown.value--
      if (codeCooldown.value <= 0) {
        if (cooldownTimer) {
          clearInterval(cooldownTimer)
          cooldownTimer = null
        }
      }
    }, 1000)
  } catch (error) {
    const msg = error instanceof Error ? error.message : '验证码发送失败'
    ElMessage.error(msg)
  }
}

/**
 * 处理注册
 * 校验表单后调用注册接口，成功后切换到登录Tab并填充手机号
 */
const handleRegister = async () => {
  if (submitLoading.value) return
  if (!agreed.value) {
    ElMessage.warning('请先同意用户协议')
    return
  }
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    await userRegister({
      phone: registerForm.phone,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      verifyCode: registerForm.verifyCode,
    })
    ElMessage.success('注册成功，请登录')
    // 注册成功后切换到登录Tab，并填充手机号方便登录
    loginForm.phone = registerForm.phone
    loginForm.password = ''
    switchTab('login')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '注册失败'
    ElMessage.error(msg)
  } finally {
    submitLoading.value = false
  }
}

/**
 * 关闭弹窗时清理状态
 */
const handleClose = () => {
  authModalStore.closeAuthModal()
}

/** 弹窗打开时重置Tab为登录 */
watch(() => authModalStore.showAuthModal, (val) => {
  if (val) {
    activeTab.value = 'login'
  }
})

/** 组件卸载时清除倒计时定时器，防止内存泄漏 */
onUnmounted(() => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
  }
})
</script>

<style scoped>
/* 弹窗内容区 */
.auth-modal-content {
  padding: var(--space-lg) var(--space-lg) var(--space-md);
}

/* 品牌Logo */
.brand-logo {
  text-align: center;
  margin-bottom: var(--space-md);
}

.logo-text {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 0.05em;
}

/* 待执行操作提示 */
.pending-hint {
  text-align: center;
  font-size: var(--font-size-small);
  color: var(--color-accent);
  margin-bottom: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  background: rgba(201, 169, 97, 0.08);
  border-radius: var(--radius-sm);
}

/* Tab切换 */
.auth-tabs {
  display: flex;
  margin-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.auth-tab {
  flex: 1;
  padding: var(--space-sm) 0;
  text-align: center;
  font-size: var(--font-size-body);
  color: var(--color-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: var(--transition-base);
  margin-bottom: -1px;
}

.auth-tab:hover {
  color: var(--color-primary);
}

.auth-tab.active {
  color: var(--color-primary);
  font-weight: 600;
  border-bottom-color: var(--color-primary);
}

/* 表单区域 */
.form-area {
  width: 100%;
}

/* 记住我 & 忘记密码 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
  font-size: var(--font-size-small);
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.remember-me input {
  accent-color: var(--color-primary);
}

.forgot-link {
  color: var(--color-text-secondary);
  text-decoration: none;
  cursor: pointer;
  transition: var(--transition-base);
}

.forgot-link:hover {
  color: var(--color-primary);
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-body);
  background: var(--color-primary);
  border-color: var(--color-primary);
  letter-spacing: 0.05em;
}

.submit-btn:hover {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

/* 验证码行 */
.code-row {
  display: flex;
  gap: var(--space-sm);
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-btn {
  width: 120px;
  height: 40px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  background: #fff;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-small);
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: var(--transition-base);
}

.code-btn:hover:not(:disabled) {
  background: var(--color-bg-secondary);
}

.code-btn:disabled {
  color: var(--color-text-muted);
  border-color: var(--color-border);
  cursor: not-allowed;
}

/* 用户协议 */
.form-agreement {
  margin-bottom: var(--space-md);
}

.agreement-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  cursor: pointer;
  flex-wrap: wrap;
}

.agreement-label input {
  accent-color: var(--color-primary);
}

.agreement-link {
  color: var(--color-primary);
  text-decoration: none;
}

.agreement-link:hover {
  text-decoration: underline;
}
</style>

<style>
/* 全局样式：覆盖 Element Plus Dialog 样式 */
.auth-modal .el-dialog__header {
  padding: 0;
  margin: 0;
}

.auth-modal .el-dialog__body {
  padding: 0;
}

.auth-modal .el-dialog__headerbtn {
  top: 16px;
  right: 16px;
  z-index: 10;
}

.auth-modal .el-dialog__headerbtn .el-dialog__close {
  font-size: 20px;
  color: var(--color-text-muted);
}

.auth-modal .el-dialog__headerbtn:hover .el-dialog__close {
  color: var(--color-primary);
}
</style>
