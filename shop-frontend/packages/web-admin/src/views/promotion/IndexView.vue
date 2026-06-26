<template>
  <!-- 管理员满减活动管理页 -->
  <div class="promotion-list">
    <!-- 搜索区：按状态筛选满减活动 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
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
        <!-- 右上角创建平台满减活动按钮，使用 float 让它靠右 -->
        <el-form-item class="action-item">
          <el-button type="primary" @click="openCreateDialog">创建平台满减活动</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 满减活动表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="活动名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="满减规则" min-width="140">
          <template #default="{ row }">
            <!-- 满门槛减优惠金额，如：满200减20 -->
            满{{ row.threshold }}减{{ row.discountAmount }}
          </template>
        </el-table-column>
        <el-table-column label="参与范围" width="100">
          <template #default="{ row }">
            <el-tag :type="promotionScopeTypeTagMap[row.scopeType]" size="small">
              {{ row.scopeTypeDesc || promotionScopeTypeTextMap[row.scopeType] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <!-- merchantId===0 是平台活动，否则是某个商家的活动 -->
            <el-tag v-if="row.merchantId === 0" type="primary" size="small">平台活动</el-tag>
            <el-tag v-else type="info" size="small">商家ID:{{ row.merchantId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="promotionStatusTagMap[row.status]" size="small">
              {{ row.statusDesc || promotionStatusTextMap[row.status] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" width="320">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <!-- 管理员只能下架进行中的活动，不能编辑 -->
            <el-button
              v-if="row.status === PromotionStatus.ACTIVE"
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

    <!-- 创建平台满减活动对话框 -->
    <el-dialog v-model="dialogVisible" title="创建平台满减活动" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" placeholder="如：夏季满200减20" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="门槛金额" prop="threshold">
          <!-- 满多少元，如 200 表示满200元才享受优惠 -->
          <el-input-number v-model="form.threshold" :min="0" :precision="2" :step="1" style="width: 220px" />
          <span class="form-tip">元（满 threshold 元可用）</span>
        </el-form-item>
        <el-form-item label="优惠金额" prop="discountAmount">
          <!-- 减多少元，如 20 表示减20元 -->
          <el-input-number v-model="form.discountAmount" :min="0" :precision="2" :step="1" style="width: 220px" />
          <span class="form-tip">元（满 threshold 减 discountAmount）</span>
        </el-form-item>
        <el-form-item label="参与范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType">
            <el-radio :value="PromotionScopeType.ALL">全店</el-radio>
            <el-radio :value="PromotionScopeType.SPECIFIED">指定商品</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === PromotionScopeType.SPECIFIED" label="商品ID列表" prop="productIdsText">
          <!-- 指定商品时填写商品ID，多个用英文逗号分隔，如：1001,1002,1003 -->
          <el-input
            v-model="form.productIdsText"
            type="textarea"
            :rows="2"
            placeholder="多个商品ID用英文逗号分隔，如：1001,1002,1003"
          />
          <span class="form-tip">仅指定商品范围时需要填写</span>
        </el-form-item>
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="活动开始时间"
            end-placeholder="活动结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="活动说明（可选）" />
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
 * 管理员满减活动管理页
 *
 * 功能说明（小白版）：
 * 1. 查看全平台的满减活动（既包括平台自己创建的活动，也包括各个商家创建的活动）
 * 2. 支持按"状态"筛选
 * 3. 管理员可以创建"平台活动"（merchantId=0，全平台通用）
 * 4. 管理员只能"下架"进行中的活动，不能编辑任何活动（哪怕是商家的活动也不能改）
 *
 * 数据来源：通过 shop-admin 服务的 /admin/manage/promotion/* 接口，
 *          内部再通过 Feign 转发到 shop-merchant 服务的满减活动表查询。
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAdminPromotionList,
  createAdminPromotion,
  offlineAdminPromotion,
} from '@shop/shared'
import {
  PromotionStatus,
  PromotionScopeType,
  promotionStatusOptions,
  promotionStatusTextMap,
  promotionStatusTagMap,
  promotionScopeTypeTextMap,
  promotionScopeTypeTagMap,
  type PromotionInfo,
  type PromotionCreateParams,
} from '@shop/shared'

/** 加载状态（表格 loading 动画） */
const loading = ref(false)
/** 满减活动列表数据 */
const tableData = ref<PromotionInfo[]>([])
/** 总条数（分页用） */
const total = ref(0)

/** 查询参数（搜索区表单） */
const queryForm = reactive({
  status: undefined as number | undefined,
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
 * 创建平台满减活动表单
 * timeRange 是 el-date-picker 的 datetimerange 值（[start, end] 数组）
 * 提交时再拆成 startTime/endTime 字段
 * productIdsText 是商品ID的文本输入（逗号分隔），提交时再解析成数字数组
 */
const form = reactive({
  name: '',
  threshold: 0,
  discountAmount: 0,
  scopeType: PromotionScopeType.ALL,
  /** 活动时间范围 [start, end] */
  timeRange: [] as string[],
  /** 商品ID列表文本（逗号分隔），仅指定商品范围时填写 */
  productIdsText: '',
  description: '',
})

/** 表单校验规则 */
const formRules: FormRules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  threshold: [
    {
      validator: (_rule, value: number, callback) => {
        // 门槛金额必须大于0
        if (value === null || value === undefined || value <= 0) {
          callback(new Error('门槛金额必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  discountAmount: [
    {
      validator: (_rule, value: number, callback) => {
        // 优惠金额必须大于0
        if (value === null || value === undefined || value <= 0) {
          callback(new Error('优惠金额必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  scopeType: [{ required: true, message: '请选择参与范围', trigger: 'change' }],
  productIdsText: [
    {
      validator: (_rule, value: string, callback) => {
        // 指定商品范围时，必须填写至少一个商品ID
        if (form.scopeType === PromotionScopeType.SPECIFIED) {
          if (!value || !value.trim()) {
            callback(new Error('指定商品范围必须填写商品ID'))
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
  timeRange: [
    {
      validator: (_rule, value: string[], callback) => {
        // 活动时间范围必填，且必须是 [start, end] 两个值
        if (!value || value.length !== 2) {
          callback(new Error('请选择活动时间范围'))
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
  queryForm.pageNum = 1
  loadData()
}

/** 加载满减活动列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminPromotionList({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      status: queryForm.status,
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开创建平台满减活动对话框，重置表单为默认值 */
function openCreateDialog() {
  Object.assign(form, {
    name: '',
    threshold: 0,
    discountAmount: 0,
    scopeType: PromotionScopeType.ALL,
    timeRange: [],
    productIdsText: '',
    description: '',
  })
  dialogVisible.value = true
}

/** 提交创建平台满减活动 */
async function handleCreate() {
  // 先做表单校验，校验失败则不提交
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 组装提交参数
  const params: PromotionCreateParams = {
    name: form.name,
    threshold: form.threshold,
    discountAmount: form.discountAmount,
    scopeType: form.scopeType,
    startTime: formatLocalTime(form.timeRange[0]),
    endTime: formatLocalTime(form.timeRange[1]),
    description: form.description || undefined,
  }

  // 指定商品范围时，把逗号分隔的文本解析成数字数组
  if (form.scopeType === PromotionScopeType.SPECIFIED && form.productIdsText.trim()) {
    params.productIds = parseProductIds(form.productIdsText)
  }

  submitting.value = true
  try {
    await createAdminPromotion(params)
    ElMessage.success('平台满减活动创建成功')
    dialogVisible.value = false
    // 创建成功后刷新列表
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '创建失败')
  } finally {
    submitting.value = false
  }
}

/** 下架满减活动（仅进行中状态可下架） */
async function handleOffline(row: PromotionInfo) {
  // 弹确认框，避免误操作
  try {
    await ElMessageBox.confirm(
      `确定要下架满减活动「${row.name}」吗？下架后用户将无法继续享受该活动优惠。`,
      '下架确认',
      { type: 'warning' },
    )
  } catch {
    // 用户点了取消，啥也不做
    return
  }

  try {
    await offlineAdminPromotion(row.id)
    ElMessage.success('下架成功')
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '下架失败')
  }
}

/**
 * 把逗号分隔的商品ID文本解析成数字数组
 * 输入 "1001,1002,1003" 输出 [1001, 1002, 1003]
 * 会自动过滤掉空值和非数字内容
 */
function parseProductIds(text: string): number[] {
  return text
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .map((s) => Number(s))
    .filter((n) => !Number.isNaN(n) && n > 0)
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

/** 页面加载时获取满减活动列表 */
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
/* 让"创建平台满减活动"按钮靠右显示 */
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
