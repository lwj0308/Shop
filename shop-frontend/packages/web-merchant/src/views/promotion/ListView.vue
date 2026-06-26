<template>
  <!-- 商家满减活动管理页 -->
  <div class="promotion-list">
    <!-- 搜索区：状态筛选 + 搜索/重置按钮 + 创建按钮 -->
    <el-card class="search-card">
      <div class="search-toolbar">
        <el-form :inline="true">
          <el-form-item label="状态">
            <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 140px">
              <el-option
                v-for="opt in promotionStatusOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          <span>创建满减活动</span>
        </el-button>
      </div>
    </el-card>

    <!-- 满减活动列表表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="活动名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="满减规则" min-width="160">
          <template #default="{ row }">
            <!-- 满 X 元 减 Y 元 -->
            满 {{ row.threshold }} 元 减 {{ row.discountAmount }} 元
          </template>
        </el-table-column>
        <el-table-column label="参与范围" width="100">
          <template #default="{ row }">
            <el-tag :type="promotionScopeTypeTagMap[row.scopeType]" size="small">
              {{ row.scopeTypeDesc || promotionScopeTypeTextMap[row.scopeType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="promotionStatusTagMap[row.status]" size="small">
              {{ row.statusDesc || promotionStatusTextMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="280">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} 至 {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <!-- 仅待生效状态可编辑 -->
            <el-button
              v-if="row.status === PromotionStatus.PENDING"
              type="primary"
              text
              size="small"
              @click="openEditDialog(row)"
            >编辑</el-button>
            <!-- 仅进行中状态可下架 -->
            <el-button
              v-if="row.status === PromotionStatus.ACTIVE"
              type="danger"
              text
              size="small"
              @click="handleOffline(row)"
            >下架</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 创建/编辑满减活动对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建满减活动' : '编辑满减活动'"
      width="640px"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
      >
        <el-form-item label="活动名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入活动名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="满减门槛金额" prop="threshold">
          <el-input-number
            v-model="form.threshold"
            :min="0.01"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">单位元，必须大于0（如满100元填100）</div>
        </el-form-item>
        <el-form-item label="优惠金额" prop="discountAmount">
          <el-input-number
            v-model="form.discountAmount"
            :min="0.01"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">单位元，必须大于0且小于门槛金额</div>
        </el-form-item>
        <el-form-item label="参与范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType">
            <el-radio
              v-for="opt in promotionScopeTypeOptions"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 仅当选择"指定商品"时才展示商品ID输入区 -->
        <el-form-item
          v-if="form.scopeType === PromotionScopeType.SPECIFIED"
          label="参与商品"
          prop="productIds"
        >
          <div class="product-input-area">
            <!-- 输入商品ID + 添加按钮 -->
            <div class="product-input-row">
              <el-input-number
                v-model="productIdInput"
                :min="1"
                :step="1"
                :controls="false"
                placeholder="输入商品ID"
                style="width: 200px"
              />
              <el-button type="primary" plain @click="addProductId">添加</el-button>
            </div>
            <!-- 已添加的商品ID以标签形式展示，可删除 -->
            <div v-if="form.productIds.length" class="product-tags">
              <el-tag
                v-for="(id, idx) in form.productIds"
                :key="idx"
                closable
                style="margin-right: 8px; margin-top: 8px"
                @close="removeProductId(idx)"
              >商品ID: {{ id }}</el-tag>
            </div>
            <div class="form-tip">请输入参与满减的商品ID，可添加多个</div>
          </div>
        </el-form-item>
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            start-placeholder="活动开始时间"
            end-placeholder="活动结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="活动描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入活动描述（选填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 商家满减活动管理页
 * 功能：分页查询满减活动、按状态筛选、创建满减活动、编辑待生效活动、下架进行中活动
 * 满减规则：订单金额满 threshold 元，减 discountAmount 元
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getMerchantPromotionList,
  createMerchantPromotion,
  updateMerchantPromotion,
  offlineMerchantPromotion,
  getMerchantPromotionDetail,
} from '@shop/shared'
import {
  PromotionStatus,
  PromotionScopeType,
  promotionStatusOptions,
  promotionStatusTextMap,
  promotionStatusTagMap,
  promotionScopeTypeOptions,
  promotionScopeTypeTextMap,
  promotionScopeTypeTagMap,
  type PromotionInfo,
  type PromotionCreateParams,
} from '@shop/shared'

/** 加载状态（表格 loading） */
const loading = ref(false)
/** 满减活动列表数据 */
const tableData = ref<PromotionInfo[]>([])
/** 总条数（分页用） */
const total = ref(0)

/** 查询参数：状态、分页 */
const queryForm = reactive({
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 表单实例引用（用于校验和重置） */
const formRef = ref<FormInstance>()
/** 对话框可见性 */
const dialogVisible = ref(false)
/** 对话框模式：create 新建 / edit 编辑 */
const dialogMode = ref<'create' | 'edit'>('create')
/** 编辑中的满减活动ID（创建时为 null） */
const editingId = ref<number | null>(null)
/** 提交中状态（防止重复点击） */
const submitLoading = ref(false)

/** 时间范围类型：el-date-picker datetimerange 绑定的 [开始, 结束] */
type TimeRange = [Date, Date] | null

/** 满减活动表单数据结构 */
interface PromotionForm {
  /** 活动名称 */
  name: string
  /** 满减门槛金额（满多少元） */
  threshold: number | undefined
  /** 优惠金额（减多少元） */
  discountAmount: number | undefined
  /** 参与范围：1全店 2指定商品 */
  scopeType: PromotionScopeType
  /** 参与商品ID列表（仅指定商品时使用） */
  productIds: number[]
  /** 活动时间范围 */
  timeRange: TimeRange
  /** 活动描述 */
  description: string
}

/** 生成默认空表单（打开新建对话框、关闭对话框时使用） */
function defaultForm(): PromotionForm {
  return {
    name: '',
    threshold: undefined,
    discountAmount: undefined,
    scopeType: PromotionScopeType.ALL,
    productIds: [],
    timeRange: null,
    description: '',
  }
}

/** 满减活动表单数据（响应式） */
const form = reactive<PromotionForm>(defaultForm())

/** 商品ID输入框的临时值（点"添加"后追加到 productIds） */
const productIdInput = ref<number | undefined>(undefined)

/** 表单校验规则 */
const formRules: FormRules = {
  name: [
    { required: true, message: '请输入活动名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度为2-50个字符', trigger: 'blur' },
  ],
  threshold: [
    {
      validator: (_rule, value, callback) => {
        // 门槛金额必须大于0
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入门槛金额'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  discountAmount: [
    {
      validator: (_rule, value, callback) => {
        // 优惠金额必须大于0
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入优惠金额'))
          return
        }
        // 优惠金额必须小于门槛金额（否则商家亏本）
        if (
          form.threshold !== undefined &&
          Number(form.threshold) > 0 &&
          Number(value) >= Number(form.threshold)
        ) {
          callback(new Error('优惠金额必须小于门槛金额'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  scopeType: [{ required: true, message: '请选择参与范围', trigger: 'change' }],
  productIds: [
    {
      validator: (_rule, _value, callback) => {
        // 仅指定商品范围时才校验：至少要选一个商品
        if (form.scopeType === PromotionScopeType.SPECIFIED && form.productIds.length === 0) {
          callback(new Error('请至少添加一个参与商品ID'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  timeRange: [
    {
      validator: (_rule, value, callback) => {
        if (!value || value.length !== 2) {
          callback(new Error('请选择活动时间范围'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

/** 点击搜索：回到第一页重新查询 */
function handleSearch() {
  queryForm.pageNum = 1
  loadData()
}

/** 重置搜索条件后重新查询 */
function handleReset() {
  queryForm.status = undefined
  queryForm.pageNum = 1
  loadData()
}

/** 加载满减活动列表（调用后端分页接口） */
async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantPromotionList({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      status: queryForm.status,
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 格式化时间：去掉 T 分隔符，截取到分钟（如 2026-06-25 10:00） */
function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

/** 打开创建对话框：重置表单为默认值 */
function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, defaultForm())
  productIdInput.value = undefined
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/**
 * 打开编辑对话框
 * 先调用详情接口获取完整数据（含参与商品ID列表），再填充表单
 * 仅待生效状态可编辑
 */
async function openEditDialog(row: PromotionInfo) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, defaultForm())
  productIdInput.value = undefined
  dialogVisible.value = true

  // 拉取详情，获取 productIds 等完整字段
  try {
    const res = await getMerchantPromotionDetail(row.id)
    const detail = res.data
    if (detail) {
      form.name = detail.name
      form.threshold = detail.threshold
      form.discountAmount = detail.discountAmount
      form.scopeType = detail.scopeType as PromotionScopeType
      form.productIds = detail.productIds ? [...detail.productIds] : []
      form.description = detail.description || ''
      // 把后端返回的时间字串转成 Date，供 date-picker 回显
      form.timeRange =
        detail.startTime && detail.endTime
          ? [new Date(detail.startTime), new Date(detail.endTime)]
          : null
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载详情失败')
    dialogVisible.value = false
    return
  }
  formRef.value?.clearValidate()
}

/** 对话框关闭后重置表单（清空残留数据） */
function handleDialogClosed() {
  Object.assign(form, defaultForm())
  productIdInput.value = undefined
  formRef.value?.clearValidate()
}

/**
 * 添加商品ID到参与商品列表
 * 校验：必须输入、不能重复添加
 */
function addProductId() {
  if (productIdInput.value === undefined || productIdInput.value === null) {
    ElMessage.warning('请输入商品ID')
    return
  }
  const id = Number(productIdInput.value)
  if (id <= 0) {
    ElMessage.warning('商品ID必须大于0')
    return
  }
  // 防止重复添加同一个商品ID
  if (form.productIds.includes(id)) {
    ElMessage.warning('该商品ID已添加')
    return
  }
  form.productIds.push(id)
  productIdInput.value = undefined
  // 触发 productIds 校验（添加后可能从错误态恢复）
  formRef.value?.validateField('productIds')
}

/** 删除参与商品（按索引删） */
function removeProductId(idx: number) {
  form.productIds.splice(idx, 1)
  // 触发 productIds 校验（删空后可能要提示）
  formRef.value?.validateField('productIds')
}

/** 把 Date 转成后端需要的 ISO 字串（本地时间，格式 YYYY-MM-DDTHH:mm:ss） */
function toIsoString(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 提交表单（创建或编辑，根据 dialogMode 判断） */
async function handleSubmit() {
  // 先做表单校验，校验不过直接返回
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (submitLoading.value) return

  // 时间范围拆成开始/结束两个时间字串
  const range = form.timeRange as [Date, Date]

  // 组装提交参数
  const params: PromotionCreateParams = {
    name: form.name,
    threshold: Number(form.threshold),
    discountAmount: Number(form.discountAmount),
    scopeType: form.scopeType,
    startTime: toIsoString(range[0]),
    endTime: toIsoString(range[1]),
    description: form.description,
    // 仅指定商品范围才传 productIds
    productIds:
      form.scopeType === PromotionScopeType.SPECIFIED ? form.productIds : undefined,
  }

  submitLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      await createMerchantPromotion(params)
      ElMessage.success('创建成功')
    } else {
      await updateMerchantPromotion(editingId.value as number, params)
      ElMessage.success('修改成功')
    }
    // 成功后关闭对话框并刷新列表
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

/** 下架满减活动（先弹确认框，确认后调用下架接口） */
async function handleOffline(row: PromotionInfo) {
  // 先弹确认框，用户取消则直接返回
  try {
    await ElMessageBox.confirm(
      `确定要下架满减活动"${row.name}"吗？下架后用户将无法继续享受该优惠。`,
      '下架确认',
      {
        confirmButtonText: '确定下架',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    // 用户点击了取消，什么都不做
    return
  }
  // 确认后调用下架接口
  try {
    await offlineMerchantPromotion(row.id)
    ElMessage.success('已下架')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '下架失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.promotion-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}
/* 搜索工具条：表单左对齐 + 创建按钮右对齐 */
.search-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
/* 表单项下方的提示文字 */
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
/* 参与商品输入区 */
.product-input-area {
  width: 100%;
}
.product-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.product-tags {
  margin-top: 4px;
}
</style>
