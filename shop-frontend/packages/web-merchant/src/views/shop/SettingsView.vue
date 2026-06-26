<template>
  <!-- 店铺设置页：表单卡片 + 图片上传 -->
  <div class="shop-settings">
    <el-card style="max-width: 720px;">
      <template #header>
        <span class="card-title">店铺信息</span>
      </template>

      <el-form
        ref="settingsFormRef"
        :model="settingsForm"
        :rules="settingsRules"
        label-width="100px"
        v-loading="loading"
      >
        <el-form-item label="店铺名称" prop="name">
          <el-input v-model="settingsForm.name" placeholder="请输入店铺名称" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="店铺Logo" prop="logo">
          <div class="logo-upload-row">
            <el-upload
              class="logo-uploader"
              action=""
              :auto-upload="false"
              :show-file-list="false"
              accept=".jpg,.jpeg,.png"
              :on-change="handleLogoChange"
            >
              <img v-if="logoPreview" :src="logoPreview" class="logo-preview" alt="Logo预览" />
              <el-icon v-else class="logo-uploader-icon"><Plus /></el-icon>
            </el-upload>
            <el-upload
              class="logo-uploader-small"
              action=""
              :auto-upload="false"
              :show-file-list="false"
              accept=".jpg,.jpeg,.png"
              :on-change="handleLogoChange"
            >
              <span class="upload-icon-small">+</span>
              <span class="upload-text-small">更换Logo</span>
            </el-upload>
          </div>
          <div class="upload-tip">建议尺寸 200×200，支持 JPG、PNG 格式</div>
        </el-form-item>

        <el-form-item label="店铺描述" prop="description">
          <el-input
            v-model="settingsForm.description"
            type="textarea"
            placeholder="请输入店铺描述"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="settingsForm.contactPhone" placeholder="请输入联系电话" maxlength="11" />
        </el-form-item>

        <el-form-item>
          <div class="form-actions">
            <el-button @click="loadSettings">取消</el-button>
            <el-button type="primary" :loading="saveLoading" @click="handleSave">
              {{ saveLoading ? '保存中...' : '保存设置' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 店铺设置页
 * 修改店铺名称、Logo、描述等基本信息
 * 优化点：图片上传预览、保存成功提示、表单校验
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { updateMerchantSettings } from '@shop/shared'
import { isValidPhone } from '@shop/shared'
import type { MerchantSettingsParams } from '@shop/shared'
import { useMerchantStore } from '@/stores/merchant'

const merchantStore = useMerchantStore()

/** 表单引用 */
const settingsFormRef = ref<FormInstance>()

/** 加载状态 */
const loading = ref(false)

/** 保存按钮loading */
const saveLoading = ref(false)

/** Logo预览地址 */
const logoPreview = ref('')

/** 图片大小限制：2MB */
const MAX_IMAGE_SIZE = 2 * 1024 * 1024

/** 允许的图片格式 */
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png']

/** 设置表单数据 */
const settingsForm = reactive<MerchantSettingsParams>({
  name: '',
  logo: '',
  contactName: '',
  contactPhone: '',
  address: '',
  description: '',
})

/** 表单校验规则 */
const settingsRules: FormRules = {
  name: [
    { required: true, message: '请输入商家名称', trigger: 'blur' },
    { min: 2, max: 50, message: '商家名称长度为2-50个字符', trigger: 'blur' },
  ],
  contactPhone: [
    { validator: (_rule, value, callback) => {
      if (value && !isValidPhone(value)) {
        callback(new Error('请输入正确的手机号格式'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
}

/**
 * 处理Logo图片选择
 * 校验格式和大小，生成预览
 */
const handleLogoChange = (uploadFile: UploadFile) => {
  const rawFile = uploadFile.raw
  if (!rawFile) return

  if (!ALLOWED_IMAGE_TYPES.includes(rawFile.type)) {
    ElMessage.error('Logo仅支持 jpg/png 格式')
    return
  }

  if (rawFile.size > MAX_IMAGE_SIZE) {
    ElMessage.error('Logo图片大小不能超过 2MB')
    return
  }

  logoPreview.value = URL.createObjectURL(rawFile)
  settingsForm.logo = logoPreview.value
}

/**
 * 加载当前商家信息到表单
 */
const loadSettings = () => {
  loading.value = true
  try {
    const info = merchantStore.merchantInfo
    if (info) {
      settingsForm.name = info.name
      settingsForm.logo = info.logo
      settingsForm.contactName = info.contactName
      settingsForm.contactPhone = info.contactPhone
      settingsForm.address = info.address
      settingsForm.description = info.description
      logoPreview.value = info.logo
    }
  } finally {
    loading.value = false
  }
}

/**
 * 保存设置
 * 校验表单后调用API，成功后刷新商家信息
 */
const handleSave = async () => {
  const valid = await settingsFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (saveLoading.value) return
  saveLoading.value = true

  try {
    await updateMerchantSettings(settingsForm)
    // 刷新store中的商家信息
    await merchantStore.fetchMerchantInfo()
    ElMessage.success('保存成功')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '保存失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    saveLoading.value = false
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.shop-settings {
  padding: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

/* Logo上传行 */
.logo-upload-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

/* Logo上传区域 */
.logo-uploader {
  display: inline-block;
}

.logo-uploader :deep(.el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}

.logo-uploader :deep(.el-upload:hover) {
  border-color: var(--color-primary);
}

.logo-preview {
  width: 80px;
  height: 80px;
  object-fit: cover;
}

.logo-uploader-icon {
  font-size: 28px;
  color: var(--color-text-muted);
}

/* 小尺寸上传按钮 */
.logo-uploader-small {
  display: inline-block;
}

.logo-uploader-small :deep(.el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  cursor: pointer;
  width: 80px;
  height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}

.logo-uploader-small :deep(.el-upload:hover) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.upload-icon-small {
  font-size: 20px;
  color: var(--color-text-muted);
}

.upload-text-small {
  font-size: 10px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.upload-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 8px;
}

/* 表单底部操作区 */
.form-actions {
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}
</style>
