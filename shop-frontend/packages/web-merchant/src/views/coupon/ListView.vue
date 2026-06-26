<template>
  <!-- 商家优惠券管理页 -->
  <div class="coupon-list">
    <!-- 搜索区：状态/类型筛选 + 创建按钮 -->
    <el-card class="search-card">
      <div class="search-toolbar">
        <el-form :inline="true">
          <el-form-item label="状态">
            <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 140px">
              <el-option
                v-for="opt in couponStatusOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="queryForm.type" placeholder="全部类型" clearable style="width: 140px">
              <el-option
                v-for="opt in couponTypeOptions"
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
          <span>创建优惠券</span>
        </el-button>
      </div>
    </el-card>

    <!-- 优惠券列表表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="优惠券名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="couponTypeTagMap[row.type]" size="small">
              {{ row.typeDesc || couponTypeTextMap[row.type] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面额/门槛" min-width="180">
          <template #default="{ row }">
            {{ formatAmountThreshold(row) }}
          </template>
        </el-table-column>
        <el-table-column label="发放总量" width="100">
          <template #default="{ row }">
            {{ row.totalCount === 0 ? '不限量' : row.totalCount }}
          </template>
        </el-table-column>
        <el-table-column label="已领取" prop="receivedCount" width="90" />
        <el-table-column label="已使用" prop="usedCount" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="couponStatusTagMap[row.status]" size="small">
              {{ row.statusDesc || couponStatusTextMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领取时间" min-width="280">
          <template #default="{ row }">
            {{ formatTime(row.receiveStartTime) }} 至 {{ formatTime(row.receiveEndTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <!-- 仅待生效状态可编辑 -->
            <el-button
              v-if="row.status === CouponStatus.PENDING"
              type="primary"
              text
              size="small"
              @click="openEditDialog(row)"
            >编辑</el-button>
            <!-- 仅进行中状态可下架 -->
            <el-button
              v-if="row.status === CouponStatus.ACTIVE"
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

    <!-- 创建/编辑优惠券对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建优惠券' : '编辑优惠券'"
      width="640px"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
      >
        <el-form-item label="优惠券名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入优惠券名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio
              v-for="opt in couponTypeOptions"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="amountLabel" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :max="form.type === CouponType.DISCOUNT ? 0.99 : 9999"
            :precision="2"
            :step="form.type === CouponType.DISCOUNT ? 0.01 : 1"
            :placeholder="amountPlaceholder"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">{{ amountTip }}</div>
        </el-form-item>
        <el-form-item label="使用门槛" prop="threshold">
          <el-input-number
            v-model="form.threshold"
            :min="0"
            :precision="2"
            :step="1"
            :disabled="form.type !== CouponType.FULL_REDUCTION"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">
            {{ form.type === CouponType.FULL_REDUCTION ? '满减类型必填，单位元' : '立减和折扣无门槛，固定为0' }}
          </div>
        </el-form-item>
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number
            v-model="form.totalCount"
            :min="0"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">0 表示不限量</div>
        </el-form-item>
        <el-form-item label="每人限领" prop="perLimit">
          <el-input-number
            v-model="form.perLimit"
            :min="1"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="领取时间" prop="receiveTimeRange">
          <el-date-picker
            v-model="form.receiveTimeRange"
            type="datetimerange"
            start-placeholder="领取开始时间"
            end-placeholder="领取结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="使用时间" prop="validTimeRange">
          <el-date-picker
            v-model="form.validTimeRange"
            type="datetimerange"
            start-placeholder="使用开始时间"
            end-placeholder="使用结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入优惠券描述（选填）"
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
 * 商家优惠券管理页
 * 功能：分页查询优惠券、按状态/类型筛选、创建优惠券、编辑待生效优惠券、下架进行中优惠券
 */

import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getMerchantCouponList,
  createMerchantCoupon,
  updateMerchantCoupon,
  offlineMerchantCoupon,
} from '@shop/shared'
import {
  CouponType,
  CouponStatus,
  couponTypeOptions,
  couponStatusOptions,
  couponTypeTextMap,
  couponStatusTextMap,
  couponTypeTagMap,
  couponStatusTagMap,
  type CouponInfo,
  type CouponCreateParams,
} from '@shop/shared'

/** 加载状态（表格 loading） */
const loading = ref(false)
/** 优惠券列表数据 */
const tableData = ref<CouponInfo[]>([])
/** 总条数（分页用） */
const total = ref(0)

/** 查询参数：状态、类型、分页 */
const queryForm = reactive({
  status: undefined as number | undefined,
  type: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 表单实例引用（用于校验和重置） */
const formRef = ref<FormInstance>()
/** 对话框可见性 */
const dialogVisible = ref(false)
/** 对话框模式：create 新建 / edit 编辑 */
const dialogMode = ref<'create' | 'edit'>('create')
/** 编辑中的优惠券ID（创建时为 null） */
const editingId = ref<number | null>(null)
/** 提交中状态（防止重复点击） */
const submitLoading = ref(false)

/** 时间范围类型：el-date-picker datetimerange 绑定的 [开始, 结束] */
type TimeRange = [Date, Date] | null

/** 优惠券表单数据结构 */
interface CouponForm {
  /** 优惠券名称 */
  name: string
  /** 类型：1满减 2折扣 3立减 */
  type: CouponType
  /** 面额（满减/立减为元，折扣为折扣率如0.85） */
  amount: number | undefined
  /** 使用门槛（仅满减有效） */
  threshold: number | undefined
  /** 发放总量（0表示不限量） */
  totalCount: number
  /** 每人限领数量 */
  perLimit: number
  /** 领取时间范围 */
  receiveTimeRange: TimeRange
  /** 使用时间范围 */
  validTimeRange: TimeRange
  /** 描述说明 */
  description: string
}

/** 生成默认空表单（打开新建对话框、关闭对话框时使用） */
function defaultForm(): CouponForm {
  return {
    name: '',
    type: CouponType.FULL_REDUCTION,
    amount: undefined,
    threshold: undefined,
    totalCount: 0,
    perLimit: 1,
    receiveTimeRange: null,
    validTimeRange: null,
    description: '',
  }
}

/** 优惠券表单数据（响应式） */
const form = reactive<CouponForm>(defaultForm())

/** 面额字段标签（折扣类型显示"折扣率"，其余显示"面额（元）"） */
const amountLabel = computed(() =>
  form.type === CouponType.DISCOUNT ? '折扣率' : '面额（元）',
)

/** 面额输入框 placeholder（折扣和满减/立减提示不同） */
const amountPlaceholder = computed(() =>
  form.type === CouponType.DISCOUNT ? '0.85 表示85折' : '请输入金额',
)

/** 面额下方提示文字 */
const amountTip = computed(() => {
  if (form.type === CouponType.DISCOUNT) return '折扣率，如 0.85 表示85折'
  if (form.type === CouponType.FULL_REDUCTION) return '满减金额，单位元'
  return '立减金额，单位元'
})

/**
 * 监听类型变化
 * 切换到非满减类型时，使用门槛自动置0（只有满减才有门槛）
 */
watch(
  () => form.type,
  (newType) => {
    if (newType !== CouponType.FULL_REDUCTION) {
      form.threshold = 0
    }
  },
)

/** 表单校验规则 */
const formRules: FormRules = {
  name: [
    { required: true, message: '请输入优惠券名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度为2-50个字符', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [
    {
      validator: (_rule, value, callback) => {
        // 面额必须大于0
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入面额'))
          return
        }
        // 折扣率必须小于1（0.85 表示85折）
        if (form.type === CouponType.DISCOUNT && Number(value) >= 1) {
          callback(new Error('折扣率需小于1，如0.85表示85折'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  threshold: [
    {
      validator: (_rule, value, callback) => {
        // 只有满减类型才校验门槛
        if (form.type === CouponType.FULL_REDUCTION) {
          if (value === undefined || value === null || Number(value) <= 0) {
            callback(new Error('请输入使用门槛金额'))
            return
          }
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  perLimit: [{ required: true, message: '请输入每人限领数量', trigger: 'blur' }],
  receiveTimeRange: [
    {
      validator: (_rule, value, callback) => {
        if (!value || value.length !== 2) {
          callback(new Error('请选择领取时间范围'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  validTimeRange: [
    {
      validator: (_rule, value, callback) => {
        if (!value || value.length !== 2) {
          callback(new Error('请选择使用时间范围'))
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
  queryForm.type = undefined
  queryForm.pageNum = 1
  loadData()
}

/** 加载优惠券列表（调用后端分页接口） */
async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantCouponList({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      status: queryForm.status,
      type: queryForm.type,
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

/** 格式化"面额/门槛"列的展示文本（根据类型不同展示不同） */
function formatAmountThreshold(row: CouponInfo): string {
  if (row.type === CouponType.FULL_REDUCTION) {
    return `满 ${row.threshold} 元 减 ${row.amount} 元`
  }
  if (row.type === CouponType.DISCOUNT) {
    return `折扣率 ${row.amount}（约${Math.round(row.amount * 100)}折）`
  }
  return `立减 ${row.amount} 元`
}

/** 打开创建对话框：重置表单为默认值 */
function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 打开编辑对话框：用列表行数据填充表单（仅待生效状态可编辑） */
function openEditDialog(row: CouponInfo) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, defaultForm())
  form.name = row.name
  form.type = row.type as CouponType
  form.amount = row.amount
  form.threshold = row.threshold
  form.totalCount = row.totalCount
  form.perLimit = row.perLimit
  form.description = row.description || ''
  // 把后端返回的时间字串转成 Date，供 date-picker 回显
  form.receiveTimeRange =
    row.receiveStartTime && row.receiveEndTime
      ? [new Date(row.receiveStartTime), new Date(row.receiveEndTime)]
      : null
  form.validTimeRange =
    row.validStartTime && row.validEndTime
      ? [new Date(row.validStartTime), new Date(row.validEndTime)]
      : null
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

/** 对话框关闭后重置表单（清空残留数据） */
function handleDialogClosed() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
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
  const receiveRange = form.receiveTimeRange as [Date, Date]
  const validRange = form.validTimeRange as [Date, Date]

  // 组装提交参数（非满减类型的门槛强制为0）
  const params: CouponCreateParams = {
    name: form.name,
    type: form.type,
    amount: Number(form.amount),
    threshold: form.type === CouponType.FULL_REDUCTION ? Number(form.threshold) : 0,
    totalCount: Number(form.totalCount),
    perLimit: Number(form.perLimit),
    receiveStartTime: toIsoString(receiveRange[0]),
    receiveEndTime: toIsoString(receiveRange[1]),
    validStartTime: toIsoString(validRange[0]),
    validEndTime: toIsoString(validRange[1]),
    description: form.description,
  }

  submitLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      await createMerchantCoupon(params)
      ElMessage.success('创建成功')
    } else {
      await updateMerchantCoupon(editingId.value as number, params)
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

/** 下架优惠券（先弹确认框，确认后调用下架接口） */
async function handleOffline(row: CouponInfo) {
  // 先弹确认框，用户取消则直接返回
  try {
    await ElMessageBox.confirm(
      `确定要下架优惠券"${row.name}"吗？下架后用户将无法继续领取。`,
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
    await offlineMerchantCoupon(row.id)
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
.coupon-list {
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
</style>
