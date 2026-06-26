<template>
  <!-- 管理员优惠券管理页 -->
  <div class="coupon-list">
    <!-- 搜索区：按状态/类型筛选优惠券 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
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
        <!-- 右上角创建平台券按钮，使用 float 让它靠右 -->
        <el-form-item class="action-item">
          <el-button type="primary" @click="openCreateDialog">创建平台券</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 优惠券表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="优惠券名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="couponTypeTagMap[row.type]" size="small">
              {{ row.typeDesc || couponTypeTextMap[row.type] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <!-- merchantId===0 是平台券，否则是某个商家的券 -->
            <el-tag v-if="row.merchantId === 0" type="primary" size="small">平台券</el-tag>
            <el-tag v-else type="info" size="small">商家ID:{{ row.merchantId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面额/门槛" min-width="160">
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
              {{ row.statusDesc || couponStatusTextMap[row.status] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领取时间" width="320">
          <template #default="{ row }">
            {{ formatTime(row.receiveStartTime) }} ~ {{ formatTime(row.receiveEndTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <!-- 管理员只能下架进行中的券，不能编辑 -->
            <el-button
              v-if="row.status === CouponStatus.ACTIVE"
              type="danger"
              text
              size="small"
              @click="handleOffline(row)"
            >
              下架
            </el-button>
            <span v-else style="color: var(--el-text-color-placeholder)">—</span>
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

    <!-- 创建平台券对话框 -->
    <el-dialog v-model="dialogVisible" title="创建平台券" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="form.name" placeholder="如：618大促满减券" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="CouponType.FULL_REDUCTION">满减</el-radio>
            <el-radio :value="CouponType.DISCOUNT">折扣</el-radio>
            <el-radio :value="CouponType.DIRECT_DISCOUNT">立减</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面额" prop="amount">
          <!-- 折扣类型填折扣率（如0.85表示85折），满减/立减填金额（元） -->
          <el-input-number
            v-model="form.amount"
            :min="0"
            :precision="form.type === CouponType.DISCOUNT ? 2 : 2"
            :step="form.type === CouponType.DISCOUNT ? 0.01 : 1"
            :placeholder="form.type === CouponType.DISCOUNT ? '0.85 表示85折' : '元'"
            style="width: 220px"
          />
          <span class="form-tip">{{ amountTip }}</span>
        </el-form-item>
        <el-form-item label="使用门槛" prop="threshold">
          <!-- 满减类型必填门槛，立减和折扣为0 -->
          <el-input-number
            v-model="form.threshold"
            :min="0"
            :precision="2"
            :step="1"
            :disabled="form.type !== CouponType.FULL_REDUCTION"
            style="width: 220px"
          />
          <span class="form-tip">{{ form.type === CouponType.FULL_REDUCTION ? '元（满 threshold 元可用）' : '立减/折扣无门槛，固定为0' }}</span>
        </el-form-item>
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="0" :step="1" style="width: 220px" />
          <span class="form-tip">0 表示不限量</span>
        </el-form-item>
        <el-form-item label="每人限领" prop="perLimit">
          <el-input-number v-model="form.perLimit" :min="1" :step="1" style="width: 220px" />
          <span class="form-tip">默认1张</span>
        </el-form-item>
        <el-form-item label="领取时间" prop="receiveTimeRange">
          <el-date-picker
            v-model="form.receiveTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="领取开始时间"
            end-placeholder="领取结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="使用时间" prop="validTimeRange">
          <el-date-picker
            v-model="form.validTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="使用开始时间"
            end-placeholder="使用结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="优惠券说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理员优惠券管理页
 *
 * 功能说明（小白版）：
 * 1. 查看全平台的优惠券（既包括平台自己发的券，也包括各个商家发的券）
 * 2. 支持按"状态"和"类型"筛选
 * 3. 管理员可以创建"平台券"（merchantId=0，全平台通用）
 * 4. 管理员只能"下架"进行中的券，不能编辑任何券（哪怕是商家的券也不能改）
 *
 * 数据来源：通过 shop-admin 服务的 /admin/manage/coupon/* 接口，
 *          内部再通过 Feign 转发到 shop-merchant 服务的优惠券表查询。
 */

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAdminCouponList,
  createAdminCoupon,
  offlineAdminCoupon,
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

/** 加载状态（表格 loading 动画） */
const loading = ref(false)
/** 优惠券列表数据 */
const tableData = ref<CouponInfo[]>([])
/** 总条数（分页用） */
const total = ref(0)

/** 查询参数（搜索区表单） */
const queryForm = reactive({
  status: undefined as number | undefined,
  type: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 创建对话框显示状态 */
const dialogVisible = ref(false)
/** 提交中状态（防止重复点击） */
const submitting = ref(false)
/** 表单引用（用于校验和重置） */
const formRef = ref<FormInstance>()

/**
 * 创建平台券表单
 * receiveTimeRange / validTimeRange 是 el-date-picker 的 datetimerange 值（[start, end] 数组）
 * 提交时再拆成 receiveStartTime/receiveEndTime 等字段
 */
const form = reactive({
  name: '',
  type: CouponType.FULL_REDUCTION,
  amount: 0,
  threshold: 0,
  totalCount: 0,
  perLimit: 1,
  /** 领取时间范围 [start, end] */
  receiveTimeRange: [] as string[],
  /** 使用时间范围 [start, end] */
  validTimeRange: [] as string[],
  description: '',
})

/** 面额输入框的提示文字（根据类型动态变化） */
const amountTip = computed(() => {
  if (form.type === CouponType.DISCOUNT) {
    return '折扣率，0.85 表示85折'
  }
  return '单位：元'
})

/** 表单校验规则 */
const formRules: FormRules = {
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [
    {
      validator: (_rule, value: number, callback) => {
        // 面额必须大于0
        if (value === null || value === undefined || value <= 0) {
          callback(new Error('面额必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  threshold: [
    {
      validator: (_rule, value: number, callback) => {
        // 满减类型必须填门槛且大于0；立减/折扣固定为0
        if (form.type === CouponType.FULL_REDUCTION) {
          if (value === null || value === undefined || value <= 0) {
            callback(new Error('满减类型必须填写使用门槛'))
          } else {
            callback()
          }
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  totalCount: [{ required: true, message: '请填写发放总量', trigger: 'blur' }],
  perLimit: [{ required: true, message: '请填写每人限领数', trigger: 'blur' }],
  receiveTimeRange: [
    {
      validator: (_rule, value: string[], callback) => {
        // 领取时间范围必填，且必须是 [start, end] 两个值
        if (!value || value.length !== 2) {
          callback(new Error('请选择领取时间范围'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  validTimeRange: [
    {
      validator: (_rule, value: string[], callback) => {
        // 使用时间范围必填，且必须是 [start, end] 两个值
        if (!value || value.length !== 2) {
          callback(new Error('请选择使用时间范围'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

/** 点击搜索：重置到第1页并查询 */
function handleSearch() {
  queryForm.pageNum = 1
  loadData()
}

/** 重置搜索条件并重新查询 */
function handleReset() {
  queryForm.status = undefined
  queryForm.type = undefined
  queryForm.pageNum = 1
  loadData()
}

/** 加载优惠券列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminCouponList({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      status: queryForm.status,
      type: queryForm.type,
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开创建平台券对话框，重置表单为默认值 */
function openCreateDialog() {
  Object.assign(form, {
    name: '',
    type: CouponType.FULL_REDUCTION,
    amount: 0,
    threshold: 0,
    totalCount: 0,
    perLimit: 1,
    receiveTimeRange: [],
    validTimeRange: [],
    description: '',
  })
  dialogVisible.value = true
}

/** 提交创建平台券 */
async function handleCreate() {
  // 先做表单校验，校验失败则不提交
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 立减/折扣类型门槛固定为0
  const threshold = form.type === CouponType.FULL_REDUCTION ? form.threshold : 0

  // 拆分时间范围数组为开始/结束时间字段
  const params: CouponCreateParams = {
    name: form.name,
    type: form.type,
    amount: form.amount,
    threshold,
    totalCount: form.totalCount,
    perLimit: form.perLimit,
    receiveStartTime: formatLocalTime(form.receiveTimeRange[0]),
    receiveEndTime: formatLocalTime(form.receiveTimeRange[1]),
    validStartTime: formatLocalTime(form.validTimeRange[0]),
    validEndTime: formatLocalTime(form.validTimeRange[1]),
    description: form.description || undefined,
  }

  submitting.value = true
  try {
    await createAdminCoupon(params)
    ElMessage.success('平台券创建成功')
    dialogVisible.value = false
    // 创建成功后刷新列表
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '创建失败')
  } finally {
    submitting.value = false
  }
}

/** 下架优惠券（仅进行中状态可下架） */
async function handleOffline(row: CouponInfo) {
  // 弹确认框，避免误操作
  try {
    await ElMessageBox.confirm(
      `确定要下架优惠券「${row.name}」吗？下架后用户将无法继续领取。`,
      '下架确认',
      { type: 'warning' },
    )
  } catch {
    // 用户点了取消，啥也不做
    return
  }

  try {
    await offlineAdminCoupon(row.id)
    ElMessage.success('下架成功')
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '下架失败')
  }
}

/**
 * 格式化"面额/门槛"列的展示文字
 * - 满减：满 threshold 减 amount 元
 * - 折扣：amount*10 折（如 0.85 → 8.5折）
 * - 立减：立减 amount 元
 */
function formatAmountThreshold(row: CouponInfo): string {
  if (row.type === CouponType.FULL_REDUCTION) {
    return `满${row.threshold}减${row.amount}元`
  }
  if (row.type === CouponType.DISCOUNT) {
    // 0.85 → 8.5折；为避免浮点精度问题，乘10后保留1位小数再去掉多余的0
    const discount = Number((row.amount * 10).toFixed(1))
    return `${discount}折`
  }
  if (row.type === CouponType.DIRECT_DISCOUNT) {
    return `立减${row.amount}元`
  }
  return '-'
}

/** 格式化时间：去掉 T 符号，只保留到分钟 */
function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

/**
 * 把 el-date-picker 选中的时间字符串格式化为本地时间 ISO 字串
 * 因为 value-format 已设置为 'YYYY-MM-DDTHH:mm:ss'，picker 返回的就是本地时间字串
 * 这里只做兜底处理：如果是 Date 对象则手动拼接成本地字串（不能用 toISOString()，那是 UTC）
 */
function formatLocalTime(value: string | Date | undefined): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  // 兜底：如果是 Date 对象，手动拼本地时间字串（不能用 toISOString()，那是 UTC）
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}` +
    `T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
  )
}

/** 从 catch 捕获的 error 中提取后端返回的错误消息 */
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const msg = (error as { message: unknown }).message
    return typeof msg === 'string' ? msg : ''
  }
  return ''
}

/** 页面加载时获取优惠券列表 */
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
/* 让"创建平台券"按钮靠右显示 */
.action-item {
  float: right;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.form-tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
