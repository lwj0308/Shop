<template>
  <!-- 追加评价页 - 用户对已发表的评价进行追加评论 -->
  <div class="review-append">
    <!-- 返回链接 -->
    <div class="back-link" @click="handleCancel">← 返回商品详情</div>

    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 参数缺失或评价不存在 -->
    <div v-else-if="!parentComment" class="empty-wrapper">
      <el-empty description="未找到原始评价" />
    </div>

    <!-- 追评表单 -->
    <template v-else>
      <h1 class="page-title">追加评价</h1>

      <!-- 原始评价展示区 -->
      <div class="original-comment">
        <h3 class="block-title">我的初始评价</h3>
        <div class="comment-card">
          <!-- 评分行 -->
          <div class="comment-header">
            <el-rate
              :model-value="parentComment.score"
              disabled
              :colors="['#F7BA0A', '#F7BA0A', '#C9A961']"
            />
            <span class="comment-date">{{ formatDate(parentComment.createTime) }}</span>
          </div>
          <!-- 评价内容 -->
          <p class="comment-content">{{ parentComment.content }}</p>
          <!-- 评价图片 -->
          <div v-if="parentComment.images && parentComment.images.length" class="comment-images">
            <el-image
              v-for="(img, index) in parentComment.images"
              :key="index"
              :src="img"
              :preview-src-list="parentComment.images"
              :initial-index="index"
              fit="cover"
              class="comment-image"
              preview-teleported
            />
          </div>
        </div>
      </div>

      <!-- 追评表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="append-form"
      >
        <el-form-item label="追加内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="500"
            show-word-limit
            minlength="5"
            placeholder="使用一段时间后，再说说商品怎么样吧"
          />
        </el-form-item>

        <el-form-item label="追评图片（最多5张，可选）">
          <el-upload
            v-model:file-list="imageFileList"
            action=""
            :auto-upload="false"
            list-type="picture-card"
            accept=".jpg,.jpeg,.png"
            :limit="5"
            :on-change="handleImageChange"
            :on-exceed="handleImageExceed"
            :before-upload="() => false"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">支持 jpg/png 格式，最多上传 5 张</div>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <div class="form-actions">
            <button type="button" class="btn-outline" @click="handleCancel">取消</button>
            <button type="button" class="btn-primary" :disabled="submitting" @click="handleSubmit">
              {{ submitting ? '提交中...' : '提交追评' }}
            </button>
          </div>
        </el-form-item>
      </el-form>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 追加评价页
 * 用户对已发表过的评价进行追加评论（如使用一段时间后再补充感受）
 *
 * 功能说明（小白版）：
 * 1. 从路由参数获取 parentId（初始评价的 ID）
 * 2. 调用评价列表接口按 parentId 查询出原始评价内容
 * 3. 用户填写追评内容、上传图片
 * 4. 提交后调用 appendComment 接口，成功后跳回商品详情页
 * 5. 未登录用户会被路由守卫拦截，弹出 AuthModal 登录弹窗
 */

import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile, UploadFiles } from 'element-plus'
import { appendComment, getCommentList } from '@shop/shared'
import { formatDate } from '@shop/shared'
import type { CommentInfo, CommentAppendParams } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 表单引用 */
const formRef = ref<FormInstance>()
/** 是否正在加载原始评价 */
const loading = ref(true)
/** 是否正在提交追评 */
const submitting = ref(false)
/** 原始评价数据 */
const parentComment = ref<CommentInfo | null>(null)
/** 上传的图片列表（用于 el-upload 展示） */
const imageFileList = ref<UploadFiles>([])

/** 追评表单数据 */
const form = reactive<CommentAppendParams>({
  parentId: 0,
  content: '',
  images: [],
})

/** 表单校验规则 */
const rules: FormRules = {
  content: [
    { required: true, message: '请输入追评内容', trigger: 'blur' },
    { min: 5, max: 500, message: '追评内容需要 5-500 字', trigger: 'blur' },
  ],
}

/**
 * 查询初始评价内容
 * 这里利用 getCommentList 接口，按 parentId 过滤出原始评价
 * 由于后端没有"按 parentId 查单条评价"的接口，采用列表查询方式
 */
const fetchParentComment = async () => {
  loading.value = true
  const parentId = Number(route.query.parentId)
  if (!parentId) {
    loading.value = false
    return
  }
  form.parentId = parentId

  try {
    // 第 1 页拉取足够多的记录来定位父评价（实际后端通常会直接返回该 ID 的评价）
    const res = await getCommentList(parentId, { pageNum: 1, pageSize: 1 })
    const records = res.data.records || []
    if (records.length === 0) {
      ElMessage.error('未找到原始评价')
      loading.value = false
      return
    }
    parentComment.value = records[0]
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载原始评价失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

/**
 * 图片选择变化时的处理
 * 校验格式和大小后，把图片转成本地预览 URL（实际项目中应上传到服务器获取真实 URL）
 * @param uploadFile - 当前选择的文件
 * @param uploadFiles - 当前所有文件列表
 */
const handleImageChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  const rawFile = uploadFile.raw
  if (rawFile) {
    // 校验格式
    const isJpgOrPng = ['image/jpeg', 'image/png'].includes(rawFile.type)
    if (!isJpgOrPng) {
      ElMessage.error('只支持 jpg/png 格式图片')
      const index = uploadFiles.findIndex(f => f.uid === uploadFile.uid)
      if (index > -1) uploadFiles.splice(index, 1)
      return
    }
    // 校验大小：最大 5MB
    if (rawFile.size > 5 * 1024 * 1024) {
      ElMessage.error('单张图片不能超过 5MB')
      const index = uploadFiles.findIndex(f => f.uid === uploadFile.uid)
      if (index > -1) uploadFiles.splice(index, 1)
      return
    }
  }
  imageFileList.value = uploadFiles
  form.images = uploadFiles.map(f => f.url || (f.raw ? URL.createObjectURL(f.raw) : ''))
}

/**
 * 超出图片数量限制时提示
 */
const handleImageExceed = () => {
  ElMessage.warning('最多只能上传 5 张图片')
}

/**
 * 提交追评
 * 先校验表单，再调用接口提交
 */
const handleSubmit = async () => {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await appendComment({
      parentId: form.parentId,
      content: form.content,
      images: form.images,
    })
    ElMessage.success('追评提交成功')
    // 提交成功后跳回商品详情页
    if (parentComment.value?.productId) {
      router.push({ name: 'ProductDetail', params: { id: String(parentComment.value.productId) } })
    } else {
      router.back()
    }
  } catch (error) {
    const msg = error instanceof Error ? error.message : '提交追评失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

/**
 * 取消按钮：返回商品详情页
 */
const handleCancel = () => {
  if (parentComment.value?.productId) {
    router.push({ name: 'ProductDetail', params: { id: String(parentComment.value.productId) } })
  } else {
    router.back()
  }
}

onMounted(() => {
  fetchParentComment()
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.review-append {
  max-width: 800px;
  margin: 0 auto;
  padding: 48px 0 80px;
}

.back-link {
  font-size: 13px;
  color: var(--color-text-muted);
  cursor: pointer;
  margin-bottom: 32px;
  transition: color var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  display: inline-block;
}

.back-link:hover {
  color: var(--color-primary);
}

/* 加载中 */
.loading-wrapper {
  padding: 40px 0;
}

/* 空状态 */
.empty-wrapper {
  padding: 120px 0;
}

/* ==================== 页面标题 ==================== */
.page-title {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-text);
  letter-spacing: -0.01em;
  margin: 0 0 32px;
}

/* ==================== 原始评价展示区 ==================== */
.original-comment {
  margin-bottom: 40px;
}

.block-title {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text);
  margin: 0 0 16px;
  letter-spacing: 0.02em;
}

.comment-card {
  padding: 20px;
  background: var(--color-bg-secondary);
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.comment-date {
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.comment-content {
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  margin: 0 0 12px;
}

.comment-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.comment-image {
  width: 80px;
  height: 80px;
  cursor: pointer;
  object-fit: cover;
}

/* ==================== 追评表单 ==================== */
.append-form {
  background: transparent;
}

.upload-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 8px;
  letter-spacing: 0.02em;
}

/* ==================== 操作按钮 ==================== */
.form-actions {
  display: flex;
  gap: 16px;
  width: 100%;
  margin-top: 16px;
}

.btn-outline {
  flex: 1;
  background: transparent;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  padding: 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.btn-primary {
  flex: 1;
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  padding: 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.btn-primary:hover:not(:disabled) {
  background: transparent;
  color: var(--color-primary);
}

.btn-primary:disabled {
  background: var(--color-bg-tertiary);
  border-color: var(--color-bg-tertiary);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .review-append {
    padding: 24px 0 40px;
  }

  .page-title {
    font-size: 22px;
    margin-bottom: 24px;
  }

  .comment-image {
    width: 64px;
    height: 64px;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>
