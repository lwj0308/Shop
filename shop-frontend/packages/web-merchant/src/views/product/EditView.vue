<template>
  <!-- 编辑/添加商品页 -->
  <div class="product-edit">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>{{ isEdit ? '编辑商品' : '添加商品' }}</h2>
          <el-button @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-form
        ref="productFormRef"
        :model="productForm"
        :rules="productRules"
        label-width="120px"
      >
        <!-- 基本信息 -->
        <h3 class="section-title">基本信息</h3>
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="productForm.name" placeholder="请输入商品名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="productForm.categoryId" placeholder="请选择商品分类" style="width: 100%">
            <el-option
              v-for="category in categoryList"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input
            v-model="productForm.description"
            type="textarea"
            placeholder="请输入商品描述"
            :rows="4"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <!-- 商品图片 -->
        <h3 class="section-title">商品图片</h3>
        <el-form-item label="商品图片" prop="images">
          <el-upload
            action=""
            :auto-upload="false"
            list-type="picture-card"
            accept=".jpg,.jpeg,.png,.webp"
            :file-list="imageFileList"
            :on-change="handleImageChange"
            :on-remove="handleImageRemove"
            :before-upload="beforeImageUpload"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">支持 jpg/png/webp 格式，单张不超过 2MB，最多上传 9 张</div>
        </el-form-item>

        <!-- SKU规格管理 -->
        <h3 class="section-title">规格与价格</h3>

        <!-- 规格名和规格值 -->
        <div v-for="(spec, specIndex) in specList" :key="specIndex" class="spec-item">
          <el-form-item :label="`规格${specIndex + 1}`">
            <div class="spec-row">
              <el-input
                v-model="spec.name"
                placeholder="规格名（如：颜色）"
                class="spec-name-input"
                @blur="generateSkus"
              />
              <div class="spec-values">
                <el-tag
                  v-for="(val, valIndex) in spec.values"
                  :key="valIndex"
                  closable
                  class="spec-value-tag"
                  @close="removeSpecValue(specIndex, valIndex)"
                >
                  {{ val }}
                </el-tag>
                <el-input
                  v-if="spec.showInput"
                  ref="specInputRefs"
                  v-model="spec.inputValue"
                  size="small"
                  class="spec-value-input"
                  placeholder="输入规格值"
                  @keyup.enter="addSpecValue(specIndex)"
                  @blur="addSpecValue(specIndex)"
                />
                <el-button v-else size="small" @click="showSpecInput(specIndex)">+ 添加规格值</el-button>
              </div>
              <el-button type="danger" text @click="removeSpec(specIndex)">删除规格</el-button>
            </div>
          </el-form-item>
        </div>
        <el-button type="primary" text @click="addSpec" class="add-spec-btn">+ 添加规格</el-button>

        <!-- 批量设置SKU价格/库存 -->
        <div v-if="skuList.length > 0" class="batch-setting">
          <h4>批量设置</h4>
          <div class="batch-row">
            <el-input v-model.number="batchPrice" placeholder="价格（元）" type="number" min="0" step="0.01" />
            <el-button type="primary" size="small" @click="applyBatchPrice">批量设置价格</el-button>
            <el-input v-model.number="batchStock" placeholder="库存" type="number" min="0" step="1" />
            <el-button type="primary" size="small" @click="applyBatchStock">批量设置库存</el-button>
          </div>
        </div>

        <!-- SKU表格 -->
        <el-table v-if="skuList.length > 0" :data="skuList" border class="sku-table">
          <el-table-column v-for="(spec, index) in specList" :key="index" :label="spec.name || `规格${index + 1}`" width="120">
            <template #default="{ row }">
              {{ row.attributes[spec.name] || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="价格（元）" width="160">
            <template #default="{ row }">
              <el-input-number
                v-model="row.priceYuan"
                :min="0.01"
                :max="999999.99"
                :precision="2"
                :step="1"
                size="small"
                controls-position="right"
              />
            </template>
          </el-table-column>
          <el-table-column label="原价（元）" width="160">
            <template #default="{ row }">
              <el-input-number
                v-model="row.originalPriceYuan"
                :min="0"
                :max="999999.99"
                :precision="2"
                :step="1"
                size="small"
                controls-position="right"
              />
            </template>
          </el-table-column>
          <el-table-column label="库存" width="140">
            <template #default="{ row }">
              <el-input-number
                v-model="row.stock"
                :min="0"
                :max="999999"
                :step="1"
                size="small"
                controls-position="right"
              />
            </template>
          </el-table-column>
        </el-table>

        <!-- 提交按钮 -->
        <el-form-item class="submit-row">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
            {{ submitLoading ? '提交中...' : '提交' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 编辑/添加商品页
 * 填写商品信息、设置SKU规格、上传图片等
 * 优化点：SKU规格管理增强、图片上传增强、表单校验、离开页面确认
 */

import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadFiles } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createProduct,
  updateProduct,
  getProductDetail,
  getCategoryTree,
} from '@shop/shared'
import type { CategoryInfo, ProductEditParams, ProductSku } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 是否为编辑模式（有id参数就是编辑，没有就是新增） */
const isEdit = computed(() => !!route.params.id)

/** 表单引用 */
const productFormRef = ref<FormInstance>()

/** 提交按钮loading状态 */
const submitLoading = ref(false)

/** 分类列表 */
const categoryList = ref<CategoryInfo[]>([])

/** 图片文件列表（用于el-upload展示） */
const imageFileList = ref<UploadFiles>([])

/** 批量设置价格（元） */
const batchPrice = ref<number | undefined>(undefined)
/** 批量设置库存 */
const batchStock = ref<number | undefined>(undefined)

/** 是否有未保存的修改 */
const hasUnsavedChanges = ref(false)

/** 规格输入框引用 */
const specInputRefs = ref<InstanceType<typeof HTMLInputElement>[]>([])

/** 商品表单数据 */
const productForm = reactive<ProductEditParams & { mainImage: string }>({
  name: '',
  description: '',
  mainImage: '',
  images: [],
  categoryId: 0,
  skus: [],
})

/** 表单校验规则 */
const productRules: FormRules = {
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { min: 2, max: 100, message: '商品名称长度为2-100个字符', trigger: 'blur' },
  ],
  categoryId: [
    { required: true, message: '请选择商品分类', trigger: 'change' },
    { validator: (_rule, value, callback) => {
      if (!value || value === 0) {
        callback(new Error('请选择商品分类'))
      } else {
        callback()
      }
    }, trigger: 'change' },
  ],
  description: [
    { required: true, message: '请输入商品描述', trigger: 'blur' },
  ],
}

/** 图片大小限制：2MB */
const MAX_IMAGE_SIZE = 2 * 1024 * 1024

/** 允许的图片格式 */
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/** 最多上传图片数 */
const MAX_IMAGE_COUNT = 9

/**
 * 规格项接口
 * 每个规格项包含规格名、规格值列表、输入框状态
 */
interface SpecItem {
  /** 规格名，如"颜色" */
  name: string
  /** 规格值列表，如["红色", "蓝色"] */
  values: string[]
  /** 是否显示输入框 */
  showInput: boolean
  /** 输入框的值 */
  inputValue: string
}

/**
 * SKU项接口
 * 用于表格展示，包含价格（元）和库存
 */
interface SkuItem {
  /** SKU属性，如 {颜色: "红色", 尺码: "XL"} */
  attributes: Record<string, string>
  /** 价格（元），方便输入，提交时转换为分 */
  priceYuan: number
  /** 原价（元） */
  originalPriceYuan: number
  /** 库存 */
  stock: number
  /** SKU图片URL */
  image: string
}

/** 规格列表 */
const specList = ref<SpecItem[]>([])

/** SKU列表（根据规格自动生成） */
const skuList = ref<SkuItem[]>([])

/**
 * 添加规格
 * 新增一个空的规格项
 */
const addSpec = () => {
  specList.value.push({
    name: '',
    values: [],
    showInput: false,
    inputValue: '',
  })
  hasUnsavedChanges.value = true
}

/**
 * 删除规格
 * 删除前弹出确认弹窗，因为已有SKU数据会丢失
 */
const removeSpec = async (index: number) => {
  // 如果已有SKU数据，提示用户确认
  if (skuList.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        '删除规格后，已设置的SKU价格和库存数据将丢失，确定删除吗？',
        '提示',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  specList.value.splice(index, 1)
  generateSkus()
  hasUnsavedChanges.value = true
}

/** 显示规格值输入框 */
const showSpecInput = (specIndex: number) => {
  specList.value[specIndex].showInput = true
}

/**
 * 添加规格值
 * 校验去重后添加到规格值列表
 */
const addSpecValue = (specIndex: number) => {
  const spec = specList.value[specIndex]
  const value = spec.inputValue.trim()

  if (!value) {
    spec.showInput = false
    spec.inputValue = ''
    return
  }

  // 规格值去重校验
  if (spec.values.includes(value)) {
    ElMessage.warning(`规格值"${value}"已存在，请勿重复添加`)
    spec.inputValue = ''
    return
  }

  spec.values.push(value)
  spec.inputValue = ''
  spec.showInput = false
  generateSkus()
  hasUnsavedChanges.value = true
}

/**
 * 删除规格值
 * 删除前确认（如果已有SKU数据）
 */
const removeSpecValue = async (specIndex: number, valIndex: number) => {
  if (skuList.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        '删除规格值后，相关SKU数据将丢失，确定删除吗？',
        '提示',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  specList.value[specIndex].values.splice(valIndex, 1)
  generateSkus()
  hasUnsavedChanges.value = true
}

/**
 * 根据规格自动生成SKU列表
 * 使用笛卡尔积算法：每个规格的值互相组合
 */
const generateSkus = () => {
  // 过滤掉没有规格名或没有规格值的项
  const validSpecs = specList.value.filter(s => s.name && s.values.length > 0)

  if (validSpecs.length === 0) {
    skuList.value = []
    return
  }

  // 笛卡尔积：把所有规格值组合起来
  const cartesian = (arr: string[][]): string[][] => {
    return arr.reduce(
      (acc, cur) => acc.flatMap(a => cur.map(c => [...a, c])),
      [[]] as string[][],
    )
  }

  const valueArrays = validSpecs.map(s => s.values)
  const combinations = cartesian(valueArrays)

  // 保留已有SKU数据（如果规格没变的话）
  const oldSkuMap = new Map<string, SkuItem>()
  skuList.value.forEach(sku => {
    const key = Object.entries(sku.attributes)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => `${k}:${v}`)
      .join('|')
    oldSkuMap.set(key, sku)
  })

  skuList.value = combinations.map(combo => {
    const attributes: Record<string, string> = {}
    validSpecs.forEach((spec, i) => {
      attributes[spec.name] = combo[i]
    })

    // 查找是否已有这个SKU的数据
    const key = Object.entries(attributes)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => `${k}:${v}`)
      .join('|')
    const existing = oldSkuMap.get(key)

    return {
      attributes,
      priceYuan: existing?.priceYuan ?? 0,
      originalPriceYuan: existing?.originalPriceYuan ?? 0,
      stock: existing?.stock ?? 0,
      image: existing?.image ?? '',
    }
  })
}

/**
 * 批量设置价格
 * 把所有SKU的价格统一设置为指定值
 */
const applyBatchPrice = () => {
  if (batchPrice.value === undefined || batchPrice.value <= 0) {
    ElMessage.warning('请输入有效的价格')
    return
  }
  skuList.value.forEach(sku => {
    sku.priceYuan = batchPrice.value!
  })
  ElMessage.success('批量设置价格成功')
  hasUnsavedChanges.value = true
}

/**
 * 批量设置库存
 * 把所有SKU的库存统一设置为指定值
 */
const applyBatchStock = () => {
  if (batchStock.value === undefined || batchStock.value < 0) {
    ElMessage.warning('请输入有效的库存数量')
    return
  }
  skuList.value.forEach(sku => {
    sku.stock = batchStock.value!
  })
  ElMessage.success('批量设置库存成功')
  hasUnsavedChanges.value = true
}

/**
 * 图片上传前校验
 * 校验图片格式和大小
 */
const beforeImageUpload = (rawFile: File) => {
  if (!ALLOWED_IMAGE_TYPES.includes(rawFile.type)) {
    ElMessage.error('图片仅支持 jpg/png/webp 格式')
    return false
  }
  if (rawFile.size > MAX_IMAGE_SIZE) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

/**
 * 图片变更处理
 * 更新图片列表
 */
const handleImageChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  // 校验图片数量
  if (uploadFiles.length > MAX_IMAGE_COUNT) {
    ElMessage.warning(`最多上传 ${MAX_IMAGE_COUNT} 张图片`)
    uploadFiles.splice(MAX_IMAGE_COUNT)
    return
  }

  // 校验新文件
  if (uploadFile.raw && !beforeImageUpload(uploadFile.raw)) {
    // 移除不合规的文件
    const index = uploadFiles.findIndex(f => f.uid === uploadFile.uid)
    if (index > -1) uploadFiles.splice(index, 1)
    return
  }

  imageFileList.value = uploadFiles
  // 更新表单图片数据（实际项目中应调用上传接口获取URL）
  productForm.images = uploadFiles.map(f => f.url || (f.raw ? URL.createObjectURL(f.raw) : ''))
  // 第一张图作为主图
  if (productForm.images.length > 0) {
    productForm.mainImage = productForm.images[0]
  }
  hasUnsavedChanges.value = true
}

/**
 * 图片移除处理
 */
const handleImageRemove = (_uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  imageFileList.value = uploadFiles
  productForm.images = uploadFiles.map(f => f.url || '')
  if (productForm.images.length > 0) {
    productForm.mainImage = productForm.images[0]
  } else {
    productForm.mainImage = ''
  }
  hasUnsavedChanges.value = true
}

/**
 * 提交商品表单
 * 校验所有字段后调用创建/更新API
 */
const handleSubmit = async () => {
  // 校验表单
  const valid = await productFormRef.value?.validate().catch(() => false)
  if (!valid) return

  // 校验图片
  if (productForm.images.length === 0) {
    ElMessage.warning('请至少上传一张商品图片')
    return
  }

  // 校验SKU
  if (skuList.value.length === 0) {
    ElMessage.warning('请至少添加一个规格并设置SKU')
    return
  }

  // 校验每个SKU的价格和库存
  const invalidSku = skuList.value.find(
    sku => sku.priceYuan <= 0 || sku.stock < 0,
  )
  if (invalidSku) {
    ElMessage.warning('请确保所有SKU的价格大于0且库存不为负数')
    return
  }

  if (submitLoading.value) return
  submitLoading.value = true

  try {
    // 转换SKU数据：元 → 分
    const submitData: ProductEditParams = {
      ...productForm,
      skus: skuList.value.map(sku => ({
        skuName: Object.entries(sku.attributes)
          .map(([k, v]) => `${k}:${v}`)
          .join(';'),
        attributes: sku.attributes,
        price: Math.round(sku.priceYuan * 100), // 元转分
        originalPrice: Math.round(sku.originalPriceYuan * 100),
        stock: sku.stock,
        image: sku.image,
      })),
    }

    if (isEdit.value) {
      await updateProduct(Number(route.params.id), submitData)
      ElMessage.success('商品更新成功')
    } else {
      await createProduct(submitData)
      ElMessage.success('商品创建成功')
    }

    hasUnsavedChanges.value = false
    router.push('/product/list')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '操作失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    submitLoading.value = false
  }
}

/** 返回商品列表 */
const goBack = () => {
  router.push('/product/list')
}

/**
 * 加载商品详情（编辑模式）
 */
const loadProductDetail = async (id: number) => {
  try {
    const res = await getProductDetail(id)
    const product = res.data

    // 填充表单
    productForm.name = product.name
    productForm.description = product.description
    productForm.mainImage = product.mainImage
    productForm.images = product.images
    productForm.categoryId = product.categoryId

    // 填充图片列表
    imageFileList.value = product.images.map((url: string, index: number) => ({
      name: `image-${index}`,
      url,
      uid: Date.now() + index,
      status: 'success' as const,
    }))

    // 从SKU反推规格
    const specMap = new Map<string, Set<string>>()
    product.skus.forEach((sku: ProductSku) => {
      Object.entries(sku.attributes).forEach(([key, value]: [string, string]) => {
        if (!specMap.has(key)) specMap.set(key, new Set())
        specMap.get(key)!.add(value)
      })
    })

    specList.value = Array.from(specMap.entries()).map(([name, values]) => ({
      name,
      values: Array.from(values),
      showInput: false,
      inputValue: '',
    }))

    // 填充SKU列表（分转元）
    skuList.value = product.skus.map((sku: ProductSku) => ({
      attributes: sku.attributes,
      priceYuan: sku.price / 100,
      originalPriceYuan: sku.originalPrice / 100,
      stock: sku.stock,
      image: sku.image,
    }))
  } catch (error) {
    ElMessage.error('加载商品信息失败')
    goBack()
  }
}

/**
 * 加载分类列表
 */
const loadCategories = async () => {
  try {
    const res = await getCategoryTree()
    categoryList.value = res.data
  } catch {
    // 分类加载失败不阻塞页面
    console.error('加载分类失败')
  }
}

/**
 * 离开页面确认
 * 如果有未保存的修改，弹出确认弹窗
 */
const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (hasUnsavedChanges.value) {
    e.preventDefault()
  }
}

// 页面加载时初始化
onMounted(async () => {
  await loadCategories()
  if (isEdit.value) {
    await loadProductDetail(Number(route.params.id))
  }
  // 监听浏览器关闭/刷新事件
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

/**
 * 路由离开守卫
 * 有未保存修改时提示用户
 */
import { onBeforeRouteLeave } from 'vue-router'
onBeforeRouteLeave(async () => {
  if (!hasUnsavedChanges.value) return true

  try {
    await ElMessageBox.confirm(
      '您有未保存的修改，确定要离开吗？离开后修改将丢失。',
      '提示',
      { confirmButtonText: '离开', cancelButtonText: '留下', type: 'warning' },
    )
    return true
  } catch {
    return false
  }
})
</script>

<style scoped>
.product-edit {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--color-text);
}

/* 分区标题：带底部分隔线 */
.section-title {
  margin: 20px 0 16px;
  padding-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.upload-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.spec-item {
  margin-bottom: 10px;
}

.spec-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.spec-name-input {
  width: 140px;
}

.spec-values {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
}

/* 规格值标签：蓝色背景 */
.spec-value-tag {
  margin-right: 4px;
  background: #ecf5ff;
  color: var(--color-primary);
  border-color: #d9ecff;
}

.spec-value-input {
  width: 120px;
}

.add-spec-btn {
  margin-bottom: 16px;
}

/* 批量设置区域：浅灰背景 */
.batch-setting {
  margin: 16px 0;
  padding: 16px;
  background: #FAFAFA;
  border-radius: var(--radius-card);
}

.batch-setting h4 {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.batch-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-row .el-input {
  width: 140px;
}

.sku-table {
  margin: 16px 0;
}

/* 提交按钮行：右对齐 */
.submit-row {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
