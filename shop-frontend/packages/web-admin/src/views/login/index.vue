<template>
  <!-- 管理后台登录页 -->
  <div class="login-container">
    <div class="login-card">
      <!-- 左侧装饰区 -->
      <div class="login-banner">
        <h1>ShopMall</h1>
        <p>管理后台</p>
      </div>
      <!-- 右侧登录表单 -->
      <div class="login-form-wrapper">
        <h2 class="login-title">账号登录</h2>
        <el-form ref="formRef" :model="loginForm" :rules="loginRules" size="large">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item prop="captchaCode">
            <div class="captcha-row">
              <el-input
                v-model="loginForm.captchaCode"
                placeholder="请输入验证码"
                :prefix-icon="Key"
                @keyup.enter="handleLogin"
              />
              <div class="captcha-img" @click="refreshCaptcha">
                <img v-if="captchaImg" :src="captchaImg" alt="验证码" />
                <span v-else>加载中</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理后台登录页
 * 包含账号密码登录和验证码校验
 */

import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAdminStore } from '@/stores/admin'
import { getCaptcha } from '@shop/shared/api/modules/admin'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

/** 验证码图片（Base64） */
const captchaImg = ref('')
/** 验证码Key（后端返回，用于校验） */
const captchaKey = ref('')

/** 登录表单数据 */
const loginForm = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

/** 表单校验规则 */
const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

/**
 * 获取验证码
 * 调用后端接口获取验证码图片和Key
 */
async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    captchaImg.value = res.data.data.image
    captchaKey.value = res.data.data.key
  } catch {
    ElMessage.error('获取验证码失败')
  }
}

/**
 * 处理登录
 * 1. 校验表单
 * 2. 调用登录API
 * 3. 保存Token和管理员信息
 * 4. 跳转到首页或之前被拦截的页面
 */
async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await adminStore.login({
      username: loginForm.username,
      password: loginForm.password,
      captchaKey: captchaKey.value,
      captchaCode: loginForm.captchaCode,
    })

    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
    ElMessage.success('登录成功')
  } catch (error: any) {
    refreshCaptcha()
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  display: flex;
  width: 800px;
  min-height: 460px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-banner {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #304156 0%, #1a2332 100%);
  color: #fff;
  padding: 40px;
}

.login-banner h1 {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 8px;
}

.login-banner p {
  font-size: 18px;
  opacity: 0.8;
}

.login-form-wrapper {
  flex: 1;
  padding: 60px 40px;
  background: var(--color-card);
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 32px;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-row .el-input {
  flex: 1;
}

.captcha-img {
  width: 120px;
  height: 40px;
  cursor: pointer;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-img span {
  font-size: 12px;
  color: var(--color-text-muted);
}

.login-btn {
  width: 100%;
}
</style>
