<template>
  <!-- 商家登录页：左右分屏布局 -->
  <div class="login-page">
    <!-- 左侧：品牌展示区（深色背景 + 动态光效） -->
    <aside class="brand-panel">
      <!-- 装饰性光斑 -->
      <div class="glow glow--1"></div>
      <div class="glow glow--2"></div>
      <div class="glow glow--3"></div>

      <!-- 网格背景纹理 -->
      <div class="grid-overlay"></div>

      <!-- 品牌内容 -->
      <div class="brand-content">
        <div class="brand-logo">
          <span class="logo-mark">S</span>
          <span class="logo-text">ShopMall</span>
        </div>
        <h1 class="brand-headline">
          开启你的<br />
          <em>数字商业</em>之旅
        </h1>
        <p class="brand-tagline">
          一站式商家管理平台，让每一笔交易都高效透明
        </p>

        <!-- 数据亮点 -->
        <div class="brand-stats">
          <div class="stat-item">
            <span class="stat-num">12K+</span>
            <span class="stat-label">入驻商家</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-num">98%</span>
            <span class="stat-label">好评率</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-num">24h</span>
            <span class="stat-label">技术支持</span>
          </div>
        </div>
      </div>

      <!-- 底部版权 -->
      <footer class="brand-footer">
        <span>&copy; 2026 ShopMall Inc.</span>
        <span class="dot">·</span>
        <span>All rights reserved.</span>
      </footer>
    </aside>

    <!-- 右侧：表单区 -->
    <main class="form-panel">
      <!-- 移动端 Logo（大屏隐藏） -->
      <div class="mobile-logo">
        <span class="logo-mark">S</span>
        <span class="logo-text">ShopMall</span>
      </div>

      <!-- 表单卡片 -->
      <div class="form-wrapper">
        <!-- 标题 -->
        <header class="form-header">
          <h2 class="form-title">商家登录</h2>
          <p class="form-subtitle">欢迎回来，请输入你的账号信息</p>
        </header>

        <!-- 登录表单 -->
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-width="0"
          size="large"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <!-- 联系电话 -->
          <el-form-item prop="contactPhone">
            <el-input
              v-model="loginForm.contactPhone"
              placeholder="联系电话"
              :prefix-icon="User"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <!-- 密码 -->
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              :prefix-icon="Lock"
              @keyup.enter="handleLogin"
            >
              <template #suffix>
                <el-icon class="password-toggle" @click="showPassword = !showPassword">
                  <View v-if="showPassword" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <!-- 记住我 & 忘记密码 -->
          <div class="form-options">
            <label class="remember-me">
              <el-checkbox v-model="rememberMe" />
              <span>记住我</span>
            </label>
            <a href="#" class="forgot-link">忘记密码？</a>
          </div>

          <!-- 登录按钮 -->
          <el-form-item>
            <button type="submit" class="submit-btn" :disabled="loginLoading">
              <span v-if="!loginLoading">登 录</span>
              <span v-else class="loading-spinner"></span>
            </button>
          </el-form-item>
        </el-form>

        <!-- 底部链接 -->
        <div class="form-footer">
          <span>还没有商家账号？</span>
          <router-link to="/apply" class="apply-link">立即入驻 &rarr;</router-link>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 商家登录页
 * 左右分屏布局：左侧深色品牌展示区，右侧浅色表单区
 * 优化点：防重复提交、密码显示/隐藏、回车键提交、表单校验
 */

import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, View, Hide } from '@element-plus/icons-vue'
import { merchantLogin } from '@shop/shared'
import { useMerchantStore } from '@/stores/merchant'

const router = useRouter()
const route = useRoute()
const merchantStore = useMerchantStore()

/** 表单引用，用于调用校验方法 */
const loginFormRef = ref<FormInstance>()

/** 是否显示密码（默认隐藏） */
const showPassword = ref(false)

/** 是否记住我 */
const rememberMe = ref(true)

/** 登录按钮loading状态，防止重复提交 */
const loginLoading = ref(false)

/** 登录表单数据 */
const loginForm = reactive({
  contactPhone: '',
  password: '',
})

/** 表单校验规则 */
const loginRules: FormRules = {
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { min: 2, max: 20, message: '联系电话长度为2-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
}

/**
 * 处理登录
 * 1. 先校验表单
 * 2. 设置loading防止重复提交
 * 3. 调用登录API
 * 4. 成功后存储Token并跳转
 */
const handleLogin = async () => {
  // 校验表单，不通过就不继续
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  // 防止重复提交：如果正在登录中就不允许再次点击
  if (loginLoading.value) return
  loginLoading.value = true

  try {
    // 调用登录接口
    const res = await merchantLogin({
      contactPhone: loginForm.contactPhone,
      password: loginForm.password,
    })

    // 登录成功，存储Token（后端使用Sa-Token，只有token没有refreshToken）
    merchantStore.setAuth(res.data.token, '')

    ElMessage.success('登录成功')

    // 跳转到登录前的页面，如果没有则跳转工作台
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (error) {
    // 登录失败，显示错误信息
    const msg = error instanceof Error ? error.message : '登录失败，请检查用户名和密码'
    ElMessage.error(msg)
  } finally {
    // 不管成功还是失败，都关闭loading
    loginLoading.value = false
  }
}
</script>

<style scoped>
/* ========== 页面容器：左右分屏 ========== */
.login-page {
  display: flex;
  min-height: 100vh;
  background: #fafafa;
}

/* ========== 左侧品牌展示区 ========== */
.brand-panel {
  position: relative;
  flex: 0 0 42%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  background: linear-gradient(160deg, #0f1923 0%, #1a2332 50%, #0d1520 100%);
  overflow: hidden;
}

/* 装饰性光斑 - 营造氛围感 */
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  pointer-events: none;
}

.glow--1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -80px;
  background: radial-gradient(circle, rgba(212, 165, 116, 0.35) 0%, transparent 70%);
  animation: float-glow 12s ease-in-out infinite;
}

.glow--2 {
  width: 300px;
  height: 300px;
  bottom: 10%;
  left: -60px;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.2) 0%, transparent 70%);
  animation: float-glow 15s ease-in-out infinite reverse;
}

.glow--3 {
  width: 200px;
  height: 200px;
  top: 50%;
  right: 20%;
  background: radial-gradient(circle, rgba(212, 165, 116, 0.15) 0%, transparent 70%);
  animation: float-glow 10s ease-in-out infinite 2s;
}

/* 光斑浮动动画 */
@keyframes float-glow {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, -30px) scale(1.05); }
  66% { transform: translate(-15px, 20px) scale(0.95); }
}

/* 网格背景纹理 - 增加层次感 */
.grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
}

/* 品牌内容区域 */
.brand-content {
  position: relative;
  z-index: 1;
  margin-top: auto;
  margin-bottom: auto;
}

/* Logo */
.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
}

.logo-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #d4a574 0%, #c4956a 100%);
  border-radius: 10px;
  font-size: 22px;
  font-weight: 800;
  color: #0f1923;
  font-family: 'Georgia', serif;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.5px;
}

/* 主标题 */
.brand-headline {
  font-size: 42px;
  font-weight: 700;
  line-height: 1.25;
  color: #ffffff;
  margin-bottom: 20px;
  letter-spacing: -0.5px;
}

.brand-headline em {
  font-style: italic;
  font-family: 'Georgia', 'Noto Serif SC', serif;
  color: #d4a574;
  font-weight: 600;
}

/* 副标题 */
.brand-tagline {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.7;
  max-width: 360px;
  margin-bottom: 48px;
}

/* 数据亮点 */
.brand-stats {
  display: flex;
  align-items: center;
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #d4a574;
  font-family: 'Georgia', serif;
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 0.5px;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: rgba(255, 255, 255, 0.1);
}

/* 底部版权 */
.brand-footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
}

.brand-footer .dot {
  opacity: 0.5;
}

/* ========== 右侧表单区 ========== */
.form-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
}

/* 移动端 Logo（大屏隐藏） */
.mobile-logo {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;
}

/* 表单容器 */
.form-wrapper {
  width: 100%;
  max-width: 380px;
  animation: slide-up 0.6s ease-out;
}

/* 表单入场动画 */
@keyframes slide-up {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 表单标题 */
.form-header {
  margin-bottom: 32px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a2332;
  margin-bottom: 8px;
  letter-spacing: -0.3px;
}

.form-subtitle {
  font-size: 14px;
  color: #8c9196;
}

/* 表单样式调整 */
.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 4px 14px;
  box-shadow: 0 0 0 1px #e4e7ed;
  transition: box-shadow 0.25s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #d4a574;
}

.login-form :deep(.el-input__inner) {
  height: 44px;
  font-size: 14px;
}

/* 密码显示/隐藏 */
.password-toggle {
  cursor: pointer;
  color: #c0c4cc;
  transition: color 0.2s;
}

.password-toggle:hover {
  color: #d4a574;
}

/* 记住我 & 忘记密码 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #8c9196;
  cursor: pointer;
  user-select: none;
}

.remember-me :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #1a2332;
  border-color: #1a2332;
}

.forgot-link {
  font-size: 13px;
  color: #1a2332;
  text-decoration: none;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: #d4a574;
}

/* 自定义提交按钮 */
.submit-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #1a2332 0%, #2d3a4f 100%);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.submit-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(212, 165, 116, 0.3), transparent);
  transition: left 0.5s ease;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(26, 35, 50, 0.3);
}

.submit-btn:hover:not(:disabled)::before {
  left: 100%;
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Loading 动画 */
.loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 底部链接 */
.form-footer {
  text-align: center;
  font-size: 13px;
  color: #8c9196;
  margin-top: 32px;
}

.apply-link {
  color: #d4a574;
  text-decoration: none;
  font-weight: 600;
  transition: color 0.2s;
}

.apply-link:hover {
  color: #c4956a;
}

/* ========== 响应式：小屏隐藏左侧品牌区 ========== */
@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    padding: 24px;
  }

  .mobile-logo {
    display: flex;
  }

  .mobile-logo .logo-mark {
    width: 36px;
    height: 36px;
    font-size: 20px;
  }

  .mobile-logo .logo-text {
    font-size: 18px;
  }
}
</style>
