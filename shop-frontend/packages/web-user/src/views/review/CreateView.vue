<template>
  <!-- 发表评价页 - 用户对已完成订单中的商品进行评价 -->
  <div class="review-create">
    <!-- 返回链接 -->
    <div class="back-link" @click="handleCancel">← 返回订单详情</div>

    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 参数缺失提示 -->
    <div v-else-if="!orderItem" class="empty-wrapper">
      <el-empty description="未找到订单商品信息" />
    </div>

    <!-- 评价表单 -->
    <template v-else>
      <h1 class="page-title">发表评价</h1>

      <!-- 商品信息卡片 -->
      <div class="goods-card">
        <img :src="orderItem.productImage" :alt="orderItem.productName" class="goods-image" />
        <div class="goods-info">
          <p class="goods-name">{{ orderItem.productName }}</p>
          <p class="goods-sku">{{ orderItem.skuName }}</p>
          <p class="goods-price">{{ formatPriceWithSymbol(orderItem.price) }} × {{ orderItem.quantity }}</p>
        </div>
      </div>

      <!-- 评价表单区域 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="review-form"
      >
        <!-- 评分 -->
        <el-form-item label="商品评分" prop="score">
          <div class="rate-wrapper">
            <el-rate
              v-model="form.score"
              :max="5"
              :colors="['#F7BA0A', '#F7BA0A', '#C9A961']"
              show-text
              :texts="['很差', '较差', '一般', '满意', '非常满意']"
            />
          </div>
        </el-form-item>

        <!-- 评价内容 -->
        <el-form-item label="评价内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="500"
            show-word-limit
            minlength="10"
            placeholder="说说商品怎么样吧，至少10个字哦"
          />
        </el-form-item>

        <!-- 评价图片 -->
        <el-form-item label="添加图片（最多5张，可选）">
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

        <!-- 匿名评价 -->
        <el-form-item>
          <div class="anonymous-wrapper">
            <span class="anonymous-label">匿名评价</span>
            <el-switch v-model="form.isAnonymous" />
            <span class="anonymous-hint">开启后他人将看到"匿名用户"</span>
          </div>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <div class="form-actions">
            <button type="button" class="btn-outline" @click="handleCancel">取消</button>
            <button type="button" class="btn-primary" :disabled="submitting" @click="handleSubmit">
              {{ submitting ? '提交中...' : '提交评价' }}
            </button>
          </div>
        </el-form-item>
      </el-form>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 发表评价页
 * 用户对已完成订单中的商品进行评价
 *
 * 功能说明（小白版）：
 * 1. 从路由参数获取 orderId、orderItemId、productId
 * 2. 调用订单详情接口获取商品信息（图片、名称、规格）
 * 3. 用户填写评分、内容、上传图片、选择是否匿名
 * 4. 提交后调用 addComment 接口，成功后跳回订单详情页
 * 5. 未登录用户会被路由守卫拦截，弹出 AuthModal 登录弹窗
 */

import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile, UploadFiles } from 'element-plus'
import { getOrderDetail, addComment } from '@shop/shared'
import { formatPriceWithSymbol } from '@shop/shared'
import type { OrderItem, CommentCreateParams } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 表单引用 */
const formRef = ref<FormInstance>()
/** 是否正在加载订单商品信息 */
const loading = ref(true)
/** 是否正在提交评价 */
const submitting = ref(false)
/** 当前要评价的订单项 */
const orderItem = ref<OrderItem | null>(null)
/** 上传的图片列表（用于 el-upload 展示） */
const imageFileList = ref<UploadFiles>([])

/** 评价表单数据 */
const form = reactive<CommentCreateParams & { isAnonymous: boolean }>({
  productId: 0,
  orderId: 0,
  orderItemId: 0,
  score: 0,
  content: '',
  images: [],
  isAnonymous: false,
})

/** 表单校验规则 */
const rules: FormRules = {
  score: [
    {
      required: true,
      validator: (_rule, value: number, callback) => {
        if (!value || value < 1) {
          callback(new Error('请选择商品评分'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  content: [
    { required: true, message: '请输入评价内容', trigger: 'blur' },
    { min: 10, max: 500, message: '评价内容需要 10-500 字', trigger: 'blur' },
  ],
}

/**
 * 从订单详情中找到对应 orderItemId 的订单项
 * 因为后端没有"按订单项查商品"的接口，所以拉取整个订单详情
 */
const fetchOrderItem = async () => {
  loading.value = true
  const orderIdNum = Number(route.query.orderId)
  const orderItemIdNum = Number(route.query.orderItemId)
  const productIdNum = Number(route.query.productId)

  // 参数缺失直接报错
  if (!orderIdNum || !orderItemIdNum || !productIdNum) {
    loading.value = false
    return
  }

  try {
    const res = await getOrderDetail(orderIdNum)
    const item = res.data.items.find(it => it.id === orderItemIdNum)
    if (!item) {
      ElMessage.error('未找到对应的订单商品')
      loading.value = false
      return
    }
    orderItem.value = item
    // 把参数回填到表单里，提交时一起发送给后端
    form.productId = productIdNum
    form.orderId = orderIdNum
    form.orderItemId = orderItemIdNum
  } catch (error) {
    const msg = error instanceof Error ? error.message : '加载订单信息失败'
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
  // 校验新文件的格式
  const rawFile = uploadFile.raw
  if (rawFile) {
    const isJpgOrPng = ['image/jpeg', 'image/png'].includes(rawFile.type)
    if (!isJpgOrPng) {
      ElMessage.error('只支持 jpg/png 格式图片')
      // 从列表中移除不合规的文件
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
  // 更新图片列表为本地预览 URL（实际项目应调用上传接口）
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
 * 提交评价
 * 先校验表单，再调用接口提交
 */
const handleSubmit = async () => {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await addComment({
      productId: form.productId,
      orderId: form.orderId,
      orderItemId: form.orderItemId,
      score: form.score,
      content: form.content,
      images: form.images,
      isAnonymous: form.isAnonymous,
    })
    ElMessage.success('评价提交成功')
    // 提交成功后跳回订单详情页
    router.push({ name: 'OrderDetail', params: { id: String(form.orderId) } })
  } catch (error) {
    const msg = error instanceof Error ? error.message : '提交评价失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

/**
 * 取消按钮：返回订单详情页
 */
const handleCancel = () => {
  const orderId = route.query.orderId
  if (orderId) {
    router.push({ name: 'OrderDetail', params: { id: String(orderId) } })
  } else {
    router.back()
  }
}

onMounted(() => {
  fetchOrderItem()
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.review-create {
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

/* ==================== 商品信息卡片 ==================== */
.goods-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: var(--color-bg-secondary);
  margin-bottom: 40px;
  align-items: center;
}

.goods-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-name {
  font-size: 14px;
  color: var(--color-text);
  margin: 0 0 8px;
  letter-spacing: 0.01em;
}

.goods-sku {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0 0 6px;
  letter-spacing: 0.02em;
}

.goods-price {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
  font-variant-numeric: tabular-nums;
}

/* ==================== 评价表单 ==================== */
.review-form {
  background: transparent;
}

.rate-wrapper {
  display: flex;
  align-items: center;
  padding: 4px 0;
}

.upload-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 8px;
  letter-spacing: 0.02em;
}

/* 匿名评价区 */
.anonymous-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.anonymous-label {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.anonymous-hint {
  font-size: 12px;
  color: var(--color-text-muted);
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
  .review-create {
    padding: 24px 0 40px;
  }

  .page-title {
    font-size: 22px;
    margin-bottom: 24px;
  }

  .goods-image {
    width: 64px;
    height: 64px;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>
