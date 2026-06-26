<template>
  <!-- 管理员秒杀活动管理页 -->
  <div class="seckill-list">
    <!-- 搜索区：按状态筛选秒杀活动 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option
              v-for="opt in seckillStatusOptions"
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
        <!-- 右上角创建平台秒杀活动按钮，使用 float 让它靠右 -->
        <el-form-item class="action-item">
          <el-button type="primary" @click="openCreateDialog">创建平台秒杀活动</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 秒杀活动表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="商品/SKU" min-width="160">
          <template #default="{ row }">
            <!-- 展示商品ID和SKU ID，方便管理员定位到具体的规格商品 -->
            <div>商品ID：{{ row.productId }}</div>
            <div style="color: var(--el-text-color-secondary); font-size: 12px">SKU ID：{{ row.skuId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="秒杀价" width="100">
          <template #default="{ row }">
            <!-- 秒杀价用红色突出显示，吸引用户注意力 -->
            <span class="seckill-price">¥{{ formatPrice(row.seckillPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原价" width="100">
          <template #default="{ row }">
            <!-- 原价用划线表示，对比秒杀价 -->
            <span class="original-price">¥{{ formatPrice(row.originalPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存（剩余/总数）" min-width="180">
          <template #default="{ row }">
            <!-- 展示剩余库存/总库存，并附带进度条直观显示已售比例 -->
            <div class="stock-info">
              <span>{{ row.availableCount }}/{{ row.totalCount }}</span>
              <el-progress
                :percentage="calcSoldPercentage(row)"
                :stroke-width="8"
                :status="calcProgressStatus(row)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="限购" width="80" align="center">
          <template #default="{ row }">
            <!-- 每人限购数量 -->
            {{ row.limitCount }}
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
            <el-tag :type="seckillStatusTagMap[row.status]" size="small">
              {{ row.statusDesc || seckillStatusTextMap[row.status] || '未知' }}
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
            <!-- 管理员只能下架进行中的秒杀活动，不能编辑 -->
            <el-button
              v-if="row.status === SeckillStatus.ACTIVE"
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

    <!-- 创建平台秒杀活动对话框 -->
    <el-dialog v-model="dialogVisible" title="创建平台秒杀活动" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="商品ID" prop="productId">
          <!-- 商品ID（SPU），必须为正整数 -->
          <el-input-number v-model="form.productId" :min="1" :step="1" :precision="0" style="width: 220px" />
          <span class="form-tip">商品（SPU）ID</span>
        </el-form-item>
        <el-form-item label="SKU ID" prop="skuId">
          <!-- SKU ID（具体规格），必须为正整数 -->
          <el-input-number v-model="form.skuId" :min="1" :step="1" :precision="0" style="width: 220px" />
          <span class="form-tip">秒杀到规格级别</span>
        </el-form-item>
        <el-form-item label="原价" prop="originalPrice">
          <!-- 原价，秒杀价必须低于原价 -->
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="1" style="width: 220px" />
          <span class="form-tip">元</span>
        </el-form-item>
        <el-form-item label="秒杀价" prop="seckillPrice">
          <!-- 秒杀价，必须 > 0 且 < 原价 -->
          <el-input-number v-model="form.seckillPrice" :min="0" :precision="2" :step="1" style="width: 220px" />
          <span class="form-tip">元（必须小于原价）</span>
        </el-form-item>
        <el-form-item label="秒杀库存" prop="totalCount">
          <!-- 秒杀库存总数，必须 > 0 -->
          <el-input-number v-model="form.totalCount" :min="1" :step="1" :precision="0" style="width: 220px" />
          <span class="form-tip">件</span>
        </el-form-item>
        <el-form-item label="限购数量" prop="limitCount">
          <!-- 每人限购数量，默认1 -->
          <el-input-number v-model="form.limitCount" :min="1" :step="1" :precision="0" style="width: 220px" />
          <span class="form-tip">件/人</span>
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
 * 管理员秒杀活动管理页
 *
 * 功能说明（小白版）：
 * 1. 查看全平台的秒杀活动（既包括平台自己创建的活动，也包括各个商家创建的活动）
 * 2. 支持按"状态"筛选
 * 3. 管理员可以创建"平台活动"（merchantId=0，全平台通用）
 * 4. 管理员只能"下架"进行中的秒杀活动，不能编辑任何活动（哪怕是商家的活动也不能改）
 *
 * 数据来源：通过 shop-admin 服务的 /admin/manage/seckill/* 接口，
 *          内部再通过 Feign 转发到 shop-merchant 服务的秒杀活动表查询。
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAdminSeckillList,
  createAdminSeckill,
  offlineAdminSeckill,
} from '@shop/shared'
import {
  SeckillStatus,
  seckillStatusOptions,
  seckillStatusTextMap,
  seckillStatusTagMap,
  type SeckillInfo,
  type SeckillCreateParams,
} from '@shop/shared'

/** 加载状态（表格 loading 动画） */
const loading = ref(false)
/** 秒杀活动列表数据 */
const tableData = ref<SeckillInfo[]>([])
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
 * 创建平台秒杀活动表单
 * timeRange 是 el-date-picker 的 datetimerange 值（[start, end] 数组）
 * 提交时再拆成 startTime/endTime 字段
 */
const form = reactive({
  productId: 0,
  skuId: 0,
  originalPrice: 0,
  seckillPrice: 0,
  totalCount: 1,
  limitCount: 1,
  /** 活动时间范围 [start, end] */
  timeRange: [] as string[],
  description: '',
})

/** 表单校验规则 */
const formRules: FormRules = {
  productId: [
    {
      validator: (_rule, value: number, callback) => {
        // 商品ID必须是正整数
        if (!value || value <= 0) {
          callback(new Error('请输入有效的商品ID'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  skuId: [
    {
      validator: (_rule, value: number, callback) => {
        // SKU ID必须是正整数
        if (!value || value <= 0) {
          callback(new Error('请输入有效的SKU ID'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  originalPrice: [
    {
      validator: (_rule, value: number, callback) => {
        // 原价必须大于0
        if (value === null || value === undefined || value <= 0) {
          callback(new Error('原价必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  seckillPrice: [
    {
      validator: (_rule, value: number, callback) => {
        // 秒杀价必须 > 0 且 < 原价
        if (value === null || value === undefined || value <= 0) {
          callback(new Error('秒杀价必须大于0'))
        } else if (value >= form.originalPrice) {
          callback(new Error('秒杀价必须小于原价'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  totalCount: [
    {
      validator: (_rule, value: number, callback) => {
        // 秒杀库存总数必须 > 0
        if (!value || value <= 0) {
          callback(new Error('秒杀库存必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  limitCount: [
    {
      validator: (_rule, value: number, callback) => {
        // 限购数量必须 > 0
        if (!value || value <= 0) {
          callback(new Error('限购数量必须大于0'))
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

/** 加载秒杀活动列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminSeckillList({
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

/** 打开创建平台秒杀活动对话框，重置表单为默认值 */
function openCreateDialog() {
  Object.assign(form, {
    productId: 0,
    skuId: 0,
    originalPrice: 0,
    seckillPrice: 0,
    totalCount: 1,
    limitCount: 1,
    timeRange: [],
    description: '',
  })
  dialogVisible.value = true
}

/** 提交创建平台秒杀活动 */
async function handleCreate() {
  // 先做表单校验，校验失败则不提交
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 组装提交参数
  const params: SeckillCreateParams = {
    productId: form.productId,
    skuId: form.skuId,
    originalPrice: form.originalPrice,
    seckillPrice: form.seckillPrice,
    totalCount: form.totalCount,
    limitCount: form.limitCount,
    startTime: formatLocalTime(form.timeRange[0]),
    endTime: formatLocalTime(form.timeRange[1]),
    description: form.description || undefined,
  }

  submitting.value = true
  try {
    await createAdminSeckill(params)
    ElMessage.success('平台秒杀活动创建成功')
    dialogVisible.value = false
    // 创建成功后刷新列表
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '创建失败')
  } finally {
    submitting.value = false
  }
}

/** 下架秒杀活动（仅进行中状态可下架） */
async function handleOffline(row: SeckillInfo) {
  // 弹确认框，避免误操作
  try {
    await ElMessageBox.confirm(
      `确定要下架秒杀活动（商品ID：${row.productId} / SKU ID：${row.skuId}）吗？下架后用户将无法继续参与该秒杀活动。`,
      '下架确认',
      { type: 'warning' },
    )
  } catch {
    // 用户点了取消，啥也不做
    return
  }

  try {
    await offlineAdminSeckill(row.id)
    ElMessage.success('下架成功')
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '下架失败')
  }
}

/**
 * 计算秒杀已售百分比
 * 已售 = 总库存 - 剩余库存；返回百分比（0-100）
 * 如果总数为0则返回0，避免除以0
 */
function calcSoldPercentage(row: SeckillInfo): number {
  if (!row.totalCount || row.totalCount === 0) return 0
  const sold = row.totalCount - row.availableCount
  const percentage = Math.round((sold / row.totalCount) * 100)
  // 限制在 0-100 范围内，防止数据异常导致进度条显示错误
  return Math.max(0, Math.min(100, percentage))
}

/** 根据秒杀进度返回 el-progress 的 status 颜色提示 */
function calcProgressStatus(row: SeckillInfo): '' | 'success' | 'warning' | 'exception' {
  const percentage = calcSoldPercentage(row)
  // 已售 >=80% 显示 exception（红色，库存告急）；>=50% 显示 warning；其余默认
  if (percentage >= 80) return 'exception'
  if (percentage >= 50) return 'warning'
  return ''
}

/** 格式化价格：保留两位小数 */
function formatPrice(price: number): string {
  if (price === null || price === undefined) return '0.00'
  return price.toFixed(2)
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

/** 页面加载时获取秒杀活动列表 */
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.seckill-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}
/* 让"创建平台秒杀活动"按钮靠右显示 */
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
/* 秒杀价用红色突出显示 */
.seckill-price {
  color: var(--el-color-danger);
  font-weight: 600;
}
/* 原价用划线和灰色显示，与秒杀价形成对比 */
.original-price {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}
/* 库存进度区域排版 */
.stock-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}
</style>
