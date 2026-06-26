<template>
  <!-- 登录/注册页 - 左右分栏，左侧品牌展示，右侧表单切换 -->
  <div class="auth-page">
    <!-- 左侧：品牌展示区 -->
    <div class="brand-section">
      <div class="brand-content">
        <h1 class="brand-title">Shop商城</h1>
        <p class="brand-slogan">品质生活，从这里开始</p>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-icon">🛒</span>
            <span class="feature-text">海量好物，一键下单</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🚚</span>
            <span class="feature-text">极速配送，当日送达</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🔒</span>
            <span class="feature-text">正品保障，放心购物</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：表单区域 -->
    <div class="form-section">
      <div class="form-card">
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
            <div class="form-group">
              <el-form-item prop="phone">
                <el-input
                  v-model="loginForm.phone"
                  placeholder="请输入手机号"
                  maxlength="11"
                  size="large"
                />
              </el-form-item>
            </div>

            <!-- 密码 -->
            <div class="form-group">
              <el-form-item prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                  size="large"
                />
              </el-form-item>
            </div>

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
            <div class="form-group">
              <el-form-item prop="phone">
                <el-input
                  v-model="registerForm.phone"
                  placeholder="请输入手机号"
                  maxlength="11"
                  size="large"
                />
              </el-form-item>
            </div>

            <!-- 验证码 -->
            <div class="form-group">
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
            </div>

            <!-- 密码 -->
            <div class="form-group">
              <el-form-item prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="请设置密码（6-20位，含字母和数字）"
                  show-password
                  size="large"
                />
              </el-form-item>
            </div>

            <!-- 确认密码 -->
            <div class="form-group">
              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                  size="large"
                />
              </el-form-item>
            </div>

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
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录/注册页
 * 左右分栏布局，左侧品牌展示，右侧登录/注册Tab切换
 */

import { ref, reactive, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuth } from '@shop/shared'
import { isValidPhone, isValidPassword } from '@shop/shared'
import { userRegister, sendVerifyCode } from '@shop/shared'

const route = useRoute()
const { login } = useAuth()

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
 * @param tab - 要切换到的Tab
 */
const switchTab = (tab: 'login' | 'register') => {
  activeTab.value = tab
}

/**
 * 处理登录
 * 校验表单后调用登录接口，成功后跳转到redirect路径或首页
 */
const handleLogin = async () => {
  if (submitLoading.value) return
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    const redirect = (route.query.redirect as string) || undefined
    await login(loginForm.phone, loginForm.password, redirect)
    ElMessage.success('登录成功')
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

/** 页面加载时，如果URL带了tab=register参数，自动切换到注册Tab */
if (route.query.tab === 'register') {
  activeTab.value = 'register'
}

/** 组件卸载时清除倒计时定时器，防止内存泄漏 */
onUnmounted(() => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
  }
})
</script>

<style scoped>
/* 整体布局：左右分栏 */
.auth-page {
  display: flex;
  min-height: 100vh;
}

/* 左侧品牌展示区 */
.brand-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E4393C 0%, #FF6B6E 100%);
  color: #fff;
}

.brand-content {
  text-align: center;
  padding: 40px;
}

.brand-title {
  font-size: 42px;
  font-weight: 700;
  margin: 0 0 12px;
}

.brand-slogan {
  font-size: 18px;
  opacity: 0.9;
  margin: 0 0 48px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
  align-items: flex-start;
  display: inline-flex;
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  opacity: 0.95;
}

.feature-icon {
  font-size: 24px;
}

/* 右侧表单区域 */
.form-section {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.form-card {
  width: 100%;
  max-width: 380px;
  padding: 40px;
}

/* Tab切换 */
.auth-tabs {
  display: flex;
  margin-bottom: 32px;
}

.auth-tab {
  flex: 1;
  padding: 12px 0;
  text-align: center;
  font-size: 18px;
  color: #999;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.auth-tab:hover {
  color: #E4393C;
}

.auth-tab.active {
  color: #E4393C;
  font-weight: 600;
  border-bottom-color: #E4393C;
}

/* 表单区域 */
.form-area {
  width: 100%;
}

.form-group {
  margin-bottom: 20px;
}

/* 记住我 & 忘记密码 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  font-size: 14px;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #666;
  cursor: pointer;
}

.remember-me input {
  accent-color: #E4393C;
}

.forgot-link {
  color: #E4393C;
  text-decoration: none;
  cursor: pointer;
}

.forgot-link:hover {
  text-decoration: underline;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 8px;
  font-size: 16px;
  background: #E4393C;
  border-color: #E4393C;
}

.submit-btn:hover {
  background: #f05052;
  border-color: #f05052;
}

/* 验证码行 */
.code-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-btn {
  width: 120px;
  height: 40px;
  border: 1px solid #E4393C;
  color: #E4393C;
  background: #fff;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background 0.2s;
}

.code-btn:hover:not(:disabled) {
  background: #FFF5F5;
}

.code-btn:disabled {
  color: #ccc;
  border-color: #ddd;
  cursor: not-allowed;
}

/* 用户协议 */
.form-agreement {
  margin-bottom: 24px;
}

.agreement-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  flex-wrap: wrap;
}

.agreement-label input {
  accent-color: #E4393C;
}

.agreement-link {
  color: #E4393C;
  text-decoration: none;
}

.agreement-link:hover {
  text-decoration: underline;
}

/* 响应式：小屏幕时隐藏左侧品牌区 */
@media (max-width: 768px) {
  .auth-page {
    flex-direction: column;
  }

  .brand-section {
    display: none;
  }

  .form-section {
    width: 100%;
  }
}
</style>
