<template>
  <!-- 商家入驻申请页：左右分屏布局，与登录页保持一致 -->
  <div class="apply-page">
    <!-- 左侧：品牌展示区 -->
    <aside class="brand-panel">
      <div class="glow glow--1"></div>
      <div class="glow glow--2"></div>
      <div class="grid-overlay"></div>

      <div class="brand-content">
        <div class="brand-logo">
          <span class="logo-mark">S</span>
          <span class="logo-text">ShopMall</span>
        </div>
        <h1 class="brand-headline">
          加入<em>万千商家</em><br />
          开启增长引擎
        </h1>
        <p class="brand-tagline">
          从申请到开店，最快1个工作日完成审核。享受平台流量扶持与专业运营指导。
        </p>

        <!-- 入驻流程指引 -->
        <div class="process-guide">
          <div class="guide-item">
            <span class="guide-num">01</span>
            <div class="guide-text">
              <span class="guide-title">填写信息</span>
              <span class="guide-desc">商家基本资料</span>
            </div>
          </div>
          <div class="guide-line"></div>
          <div class="guide-item">
            <span class="guide-num">02</span>
            <div class="guide-text">
              <span class="guide-title">上传资质</span>
              <span class="guide-desc">营业执照验证</span>
            </div>
          </div>
          <div class="guide-line"></div>
          <div class="guide-item">
            <span class="guide-num">03</span>
            <div class="guide-text">
              <span class="guide-title">提交审核</span>
              <span class="guide-desc">等待平台确认</span>
            </div>
          </div>
        </div>
      </div>

      <footer class="brand-footer">
        <span>&copy; 2026 ShopMall Inc.</span>
        <span class="dot">·</span>
        <span>All rights reserved.</span>
      </footer>
    </aside>

    <!-- 右侧：表单区 -->
    <main class="form-panel">
      <div class="mobile-logo">
        <span class="logo-mark">S</span>
        <span class="logo-text">ShopMall</span>
      </div>

      <div class="form-wrapper">
        <!-- 标题 + 返回登录 -->
        <header class="form-header">
          <div class="header-top">
            <h2 class="form-title">商家入驻</h2>
            <router-link to="/login" class="back-link">
              <el-icon><ArrowLeft /></el-icon>
              返回登录
            </router-link>
          </div>
          <p class="form-subtitle">填写以下信息，审核通过后即可开店经营</p>
        </header>

        <!-- 步骤指示器 -->
        <div class="step-indicator">
          <div
            v-for="(step, index) in steps"
            :key="index"
            class="step-item"
            :class="{
              'step-item--active': currentStep === index,
              'step-item--done': currentStep > index,
            }"
          >
            <span class="step-circle">
              <el-icon v-if="currentStep > index"><Check /></el-icon>
              <span v-else>{{ index + 1 }}</span>
            </span>
            <span class="step-label">{{ step }}</span>
            <span v-if="index < steps.length - 1" class="step-connector"></span>
          </div>
        </div>

        <!-- 第一步：基本信息 -->
        <Transition name="step-fade" mode="out-in">
          <el-form
            v-if="currentStep === 0"
            ref="basicFormRef"
            :model="applyForm"
            :rules="basicRules"
            label-width="0"
            class="apply-form"
            key="step0"
          >
            <div class="field-group">
              <label class="field-label">商家名称</label>
              <el-form-item prop="name">
                <el-input v-model="applyForm.name" placeholder="请输入商家名称" maxlength="50" show-word-limit />
              </el-form-item>
            </div>

            <div class="field-row">
              <div class="field-group">
                <label class="field-label">联系人</label>
                <el-form-item prop="contactName">
                  <el-input v-model="applyForm.contactName" placeholder="联系人姓名" maxlength="20" />
                </el-form-item>
              </div>
              <div class="field-group">
                <label class="field-label">联系电话</label>
                <el-form-item prop="contactPhone">
                  <el-input v-model="applyForm.contactPhone" placeholder="手机号码" maxlength="11" />
                </el-form-item>
              </div>
            </div>

            <div class="field-group">
              <label class="field-label">商家地址</label>
              <el-form-item prop="address">
                <el-input v-model="applyForm.address" placeholder="请输入商家地址" maxlength="200" />
              </el-form-item>
            </div>

            <div class="field-group">
              <label class="field-label">商家描述</label>
              <el-form-item prop="description">
                <el-input
                  v-model="applyForm.description"
                  type="textarea"
                  placeholder="简要介绍你的商家和主营产品"
                  :rows="4"
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>
            </div>
          </el-form>

          <!-- 第二步：资质上传 -->
          <el-form
            v-else-if="currentStep === 1"
            ref="licenseFormRef"
            :model="applyForm"
            :rules="licenseRules"
            label-width="0"
            class="apply-form"
            key="step1"
          >
            <div class="field-group">
              <label class="field-label">营业执照</label>
              <p class="field-hint">支持 JPG、PNG 格式，大小不超过 2MB</p>
              <el-form-item prop="licenseUrl">
                <el-upload
                  class="license-uploader"
                  action=""
                  :auto-upload="false"
                  :show-file-list="false"
                  accept=".jpg,.jpeg,.png"
                  :on-change="handleLicenseChange"
                >
                  <img v-if="licensePreview" :src="licensePreview" class="license-preview" alt="营业执照预览" />
                  <div v-else class="upload-placeholder">
                    <el-icon class="upload-icon"><Plus /></el-icon>
                    <span class="upload-text">点击上传</span>
                  </div>
                </el-upload>
              </el-form-item>
            </div>
          </el-form>

          <!-- 第三步：确认提交 -->
          <div v-else class="confirm-section" key="step2">
            <p class="confirm-tip">请确认以下信息无误后提交</p>
            <div class="confirm-list">
              <div class="confirm-row">
                <span class="confirm-label">商家名称</span>
                <span class="confirm-value">{{ applyForm.name }}</span>
              </div>
              <div class="confirm-row">
                <span class="confirm-label">联系人</span>
                <span class="confirm-value">{{ applyForm.contactName }}</span>
              </div>
              <div class="confirm-row">
                <span class="confirm-label">联系电话</span>
                <span class="confirm-value">{{ applyForm.contactPhone }}</span>
              </div>
              <div class="confirm-row">
                <span class="confirm-label">商家地址</span>
                <span class="confirm-value">{{ applyForm.address }}</span>
              </div>
              <div class="confirm-row">
                <span class="confirm-label">商家描述</span>
                <span class="confirm-value">{{ applyForm.description }}</span>
              </div>
              <div class="confirm-row">
                <span class="confirm-label">营业执照</span>
                <div class="confirm-value">
                  <img
                    v-if="applyForm.licenseUrl"
                    :src="applyForm.licenseUrl"
                    class="license-thumb"
                    alt="营业执照"
                  />
                  <span v-else class="confirm-empty">未上传</span>
                </div>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 底部操作按钮 -->
        <div class="form-actions">
          <button v-if="currentStep === 0" class="btn btn--ghost" @click="router.push('/login')">
            取消
          </button>
          <button v-if="currentStep > 0" class="btn btn--ghost" @click="prevStep">
            上一步
          </button>
          <button v-if="currentStep < 2" class="btn btn--primary" @click="nextStep">
            下一步
          </button>
          <button
            v-if="currentStep === 2"
            class="btn btn--primary"
            :disabled="submitLoading"
            @click="handleSubmit"
          >
            <span v-if="!submitLoading">提交审核</span>
            <span v-else class="loading-spinner"></span>
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 商家入驻申请页
 * 左右分屏布局，与登录页保持统一设计语言
 * 3步骤流程：基本信息 → 资质上传 → 确认提交
 */

import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { Plus, Check, ArrowLeft } from '@element-plus/icons-vue'
import { merchantApply } from '@shop/shared'
import { setStorage, getStorage, removeStorage, isValidPhone } from '@shop/shared'
import type { MerchantApplyParams } from '@shop/shared'

const router = useRouter()

/** 表单暂存在localStorage中的键名 */
const APPLY_FORM_KEY = 'shop_merchant_apply_form'
const APPLY_STEP_KEY = 'shop_merchant_apply_step'

/** 步骤名称（用于步骤指示器显示） */
const steps = ['填写信息', '上传资质', '确认提交']

/** 当前步骤（0-基本信息 1-资质上传 2-确认提交） */
const currentStep = ref(0)

/** 提交按钮loading状态 */
const submitLoading = ref(false)

/** 营业执照预览地址 */
const licensePreview = ref('')

/** 基本信息表单引用 */
const basicFormRef = ref<FormInstance>()
/** 资质上传表单引用 */
const licenseFormRef = ref<FormInstance>()

/** 入驻申请表单数据 */
const applyForm = reactive<MerchantApplyParams>({
  name: '',
  contactName: '',
  contactPhone: '',
  address: '',
  licenseUrl: '',
  description: '',
})

/** 基本信息校验规则 */
const basicRules: FormRules = {
  name: [
    { required: true, message: '请输入商家名称', trigger: 'blur' },
    { min: 2, max: 50, message: '商家名称长度为2-50个字符', trigger: 'blur' },
  ],
  contactName: [
    { required: true, message: '请输入联系人姓名', trigger: 'blur' },
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      if (value && !isValidPhone(value)) {
        callback(new Error('请输入正确的手机号格式'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
  address: [
    { required: true, message: '请输入商家地址', trigger: 'blur' },
  ],
}

/** 资质上传校验规则 */
const licenseRules: FormRules = {
  licenseUrl: [
    { required: true, message: '请上传营业执照', trigger: 'change' },
  ],
}

/** 图片大小限制：2MB */
const MAX_IMAGE_SIZE = 2 * 1024 * 1024

/** 允许的图片格式 */
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png']

/**
 * 处理营业执照图片选择
 * 校验图片格式和大小，生成预览
 */
const handleLicenseChange = (uploadFile: UploadFile) => {
  const rawFile = uploadFile.raw
  if (!rawFile) return

  // 校验图片格式
  if (!ALLOWED_IMAGE_TYPES.includes(rawFile.type)) {
    ElMessage.error('营业执照仅支持 jpg/png 格式')
    return
  }

  // 校验图片大小
  if (rawFile.size > MAX_IMAGE_SIZE) {
    ElMessage.error('营业执照图片大小不能超过 2MB')
    return
  }

  // 生成预览URL
  licensePreview.value = URL.createObjectURL(rawFile)

  // 这里实际项目中应该调用上传接口获取URL，目前用预览URL模拟
  applyForm.licenseUrl = licensePreview.value

  // 触发表单校验
  licenseFormRef.value?.validateField('licenseUrl')
}

/**
 * 下一步
 * 校验当前步骤的表单，通过后才允许进入下一步
 */
const nextStep = async () => {
  const formRef = currentStep.value === 0 ? basicFormRef.value : licenseFormRef.value
  const valid = await formRef?.validate().catch(() => false)
  if (!valid) return

  currentStep.value++
  saveFormToStorage()
}

/** 上一步 */
const prevStep = () => {
  currentStep.value--
  saveFormToStorage()
}

/**
 * 提交入驻申请
 * 弹出二次确认弹窗，确认后调用API
 */
const handleSubmit = async () => {
  try {
    // 二次确认，防止误操作
    await ElMessageBox.confirm(
      '请确认填写的信息无误，提交后需等待管理员审核。确定提交吗？',
      '提交确认',
      { confirmButtonText: '确定提交', cancelButtonText: '再看看', type: 'info' },
    )
  } catch {
    // 用户点击取消
    return
  }

  if (submitLoading.value) return
  submitLoading.value = true

  try {
    await merchantApply(applyForm)
    ElMessage.success('入驻申请已提交，请等待审核')
    // 提交成功后清除暂存数据
    clearFormStorage()
    router.push('/login')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '提交失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    submitLoading.value = false
  }
}

/**
 * 保存表单数据到localStorage
 * 防止刷新页面后数据丢失
 */
const saveFormToStorage = () => {
  setStorage(APPLY_FORM_KEY, { ...applyForm })
  setStorage(APPLY_STEP_KEY, currentStep.value)
}

/** 清除暂存的表单数据 */
const clearFormStorage = () => {
  removeStorage(APPLY_FORM_KEY)
  removeStorage(APPLY_STEP_KEY)
}

/**
 * 从localStorage恢复表单数据
 * 页面加载时自动恢复之前填写的内容
 */
const restoreFormFromStorage = () => {
  const savedForm = getStorage<MerchantApplyParams>(APPLY_FORM_KEY)
  const savedStep = getStorage<number>(APPLY_STEP_KEY)

  if (savedForm) {
    Object.assign(applyForm, savedForm)
    // 如果有营业执照URL，恢复预览
    if (savedForm.licenseUrl) {
      licensePreview.value = savedForm.licenseUrl
    }
  }

  if (savedStep !== null && savedStep !== undefined) {
    currentStep.value = savedStep
  }
}

// 页面加载时恢复暂存数据
onMounted(() => {
  restoreFormFromStorage()
})

// 页面卸载前保存数据
onUnmounted(() => {
  saveFormToStorage()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.apply-page {
  display: flex;
  min-height: 100vh;
  background: #fafafa;
}

/* ========== 左侧品牌展示区（与登录页统一） ========== */
.brand-panel {
  position: relative;
  flex: 0 0 38%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  background: linear-gradient(160deg, #0f1923 0%, #1a2332 50%, #0d1520 100%);
  overflow: hidden;
}

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

@keyframes float-glow {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, -30px) scale(1.05); }
  66% { transform: translate(-15px, 20px) scale(0.95); }
}

.grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
}

.brand-content {
  position: relative;
  z-index: 1;
  margin-top: auto;
  margin-bottom: auto;
}

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

.brand-headline {
  font-size: 38px;
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

.brand-tagline {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.7;
  max-width: 340px;
  margin-bottom: 40px;
}

/* 入驻流程指引 */
.process-guide {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.guide-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
}

.guide-num {
  font-size: 14px;
  font-weight: 700;
  color: #d4a574;
  font-family: 'Georgia', serif;
  letter-spacing: 1px;
}

.guide-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.guide-title {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 500;
}

.guide-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
}

.guide-line {
  width: 1px;
  height: 20px;
  background: rgba(255, 255, 255, 0.1);
  margin-left: 14px;
}

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
  padding: 40px;
  overflow-y: auto;
  max-height: 100vh;
}

.mobile-logo {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
}

.form-wrapper {
  width: 100%;
  max-width: 480px;
  animation: slide-up 0.5s ease-out;
}

@keyframes slide-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 表单头部 */
.form-header {
  margin-bottom: 32px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a2332;
  letter-spacing: -0.3px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #8c9196;
  text-decoration: none;
  transition: color 0.2s;
}

.back-link:hover {
  color: #d4a574;
}

.form-subtitle {
  font-size: 14px;
  color: #8c9196;
}

/* 步骤指示器 */
.step-indicator {
  display: flex;
  align-items: center;
  margin-bottom: 32px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid #e4e7ed;
  font-size: 13px;
  font-weight: 600;
  color: #c0c4cc;
  transition: all 0.3s ease;
}

.step-item--active .step-circle {
  border-color: #1a2332;
  color: #1a2332;
  background: #ffffff;
}

.step-item--done .step-circle {
  border-color: #d4a574;
  background: #d4a574;
  color: #ffffff;
}

.step-label {
  font-size: 13px;
  color: #8c9196;
  font-weight: 500;
}

.step-item--active .step-label {
  color: #1a2332;
}

.step-item--done .step-label {
  color: #d4a574;
}

.step-connector {
  width: 32px;
  height: 2px;
  background: #e4e7ed;
  margin: 0 8px;
  transition: background 0.3s ease;
}

.step-item--done .step-connector {
  background: #d4a574;
}

/* 表单字段 */
.apply-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 4px 14px;
  box-shadow: 0 0 0 1px #e4e7ed;
  transition: box-shadow 0.25s ease;
}

.apply-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c0c4cc;
}

.apply-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #d4a574;
}

.apply-form :deep(.el-input__inner) {
  height: 42px;
  font-size: 14px;
}

.apply-form :deep(.el-textarea__inner) {
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 14px;
  box-shadow: 0 0 0 1px #e4e7ed;
}

.apply-form :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px #d4a574;
}

/* 字段分组 */
.field-group {
  margin-bottom: 20px;
}

.field-row {
  display: flex;
  gap: 16px;
}

.field-row .field-group {
  flex: 1;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #1a2332;
  margin-bottom: 8px;
}

.field-hint {
  font-size: 12px;
  color: #8c9196;
  margin-bottom: 8px;
}

/* 营业执照上传 */
.license-uploader :deep(.el-upload) {
  border: 2px dashed #e4e7ed;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 200px;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.25s ease;
}

.license-uploader :deep(.el-upload:hover) {
  border-color: #d4a574;
}

.license-preview {
  width: 200px;
  height: 200px;
  object-fit: cover;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #c0c4cc;
}

.upload-icon {
  font-size: 32px;
}

.upload-text {
  font-size: 13px;
}

/* 确认信息 */
.confirm-section {
  animation: slide-up 0.3s ease-out;
}

.confirm-tip {
  font-size: 14px;
  color: #8c9196;
  margin-bottom: 20px;
}

.confirm-list {
  background: #f9fafb;
  border-radius: 10px;
  padding: 8px 20px;
}

.confirm-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.confirm-row:last-child {
  border-bottom: none;
}

.confirm-label {
  font-size: 13px;
  color: #8c9196;
  flex-shrink: 0;
  width: 80px;
}

.confirm-value {
  font-size: 14px;
  color: #1a2332;
  text-align: right;
  word-break: break-all;
}

.license-thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
}

.confirm-empty {
  color: #c0c4cc;
  font-size: 13px;
}

/* 步骤切换动画 */
.step-fade-enter-active,
.step-fade-leave-active {
  transition: all 0.3s ease;
}

.step-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.step-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 底部操作按钮 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
}

.btn {
  padding: 0 28px;
  height: 44px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn--primary {
  background: linear-gradient(135deg, #1a2332 0%, #2d3a4f 100%);
  color: #ffffff;
}

.btn--primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(26, 35, 50, 0.3);
}

.btn--primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn--ghost {
  background: transparent;
  color: #606266;
  border: 1px solid #dcdfe6;
}

.btn--ghost:hover {
  color: #1a2332;
  border-color: #1a2332;
}

.loading-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== 响应式 ========== */
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

  .field-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
