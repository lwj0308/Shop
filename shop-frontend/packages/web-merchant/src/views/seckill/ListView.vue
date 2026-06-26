<template>
  <!-- 商家秒杀活动管理页 -->
  <div class="seckill-list">
    <!-- 搜索区：状态筛选 + 搜索/重置按钮 + 创建按钮 -->
    <el-card class="search-card">
      <div class="search-toolbar">
        <el-form :inline="true">
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
        </el-form>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          <span>创建秒杀活动</span>
        </el-button>
      </div>
    </el-card>

    <!-- 秒杀活动列表表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <!-- 商品ID / SKU ID：合并展示在一列 -->
        <el-table-column label="商品/SKU" min-width="140">
          <template #default="{ row }">
            <div class="product-cell">
              <div>商品ID: {{ row.productId }}</div>
              <div class="sku-text">SKU ID: {{ row.skuId }}</div>
            </div>
          </template>
        </el-table-column>
        <!-- 秒杀价：红色醒目 -->
        <el-table-column label="秒杀价" width="110">
          <template #default="{ row }">
            <span class="seckill-price">¥{{ row.seckillPrice }}</span>
          </template>
        </el-table-column>
        <!-- 原价：划线展示 -->
        <el-table-column label="原价" width="110">
          <template #default="{ row }">
            <span class="original-price">¥{{ row.originalPrice }}</span>
          </template>
        </el-table-column>
        <!-- 库存：剩余/总数 + 进度条 -->
        <el-table-column label="秒杀库存" min-width="180">
          <template #default="{ row }">
            <div class="stock-cell">
              <div class="stock-text">
                {{ row.availableCount }} / {{ row.totalCount }}
              </div>
              <el-progress
                :percentage="calcStockPercent(row.availableCount, row.totalCount)"
                :stroke-width="10"
                :status="row.availableCount === 0 ? 'exception' : ''"
              />
            </div>
          </template>
        </el-table-column>
        <!-- 限购数量 -->
        <el-table-column label="限购" width="80">
          <template #default="{ row }">{{ row.limitCount }}</template>
        </el-table-column>
        <!-- 状态标签 -->
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="seckillStatusTagMap[row.status]" size="small">
              {{ row.statusDesc || seckillStatusTextMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 活动时间范围 -->
        <el-table-column label="活动时间" min-width="280">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} 至 {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <!-- 操作：仅进行中状态可下架 -->
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === SeckillStatus.ACTIVE"
              type="danger"
              text
              size="small"
              @click="handleOffline(row)"
            >下架</el-button>
            <span v-else class="empty-action">-</span>
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

    <!-- 创建秒杀活动对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="创建秒杀活动"
      width="640px"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
      >
        <!-- 商品ID -->
        <el-form-item label="商品ID" prop="productId">
          <el-input-number
            v-model="form.productId"
            :min="1"
            :step="1"
            :controls="false"
            placeholder="请输入商品ID"
            style="width: 220px"
          />
          <div class="form-tip">必填，要参与秒杀的商品ID（数字）</div>
        </el-form-item>
        <!-- SKU ID -->
        <el-form-item label="SKU ID" prop="skuId">
          <el-input-number
            v-model="form.skuId"
            :min="1"
            :step="1"
            :controls="false"
            placeholder="请输入SKU ID"
            style="width: 220px"
          />
          <div class="form-tip">必填，要参与秒杀的SKU规格ID（数字）</div>
        </el-form-item>
        <!-- 原价 -->
        <el-form-item label="原价" prop="originalPrice">
          <el-input-number
            v-model="form.originalPrice"
            :min="0.01"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">单位元，必须大于0（如 99.90）</div>
        </el-form-item>
        <!-- 秒杀价：必须小于原价 -->
        <el-form-item label="秒杀价" prop="seckillPrice">
          <el-input-number
            v-model="form.seckillPrice"
            :min="0.01"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">单位元，必须大于0且小于原价</div>
        </el-form-item>
        <!-- 秒杀库存 -->
        <el-form-item label="秒杀库存" prop="totalCount">
          <el-input-number
            v-model="form.totalCount"
            :min="1"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">必填，秒杀可售卖的总数量（整数）</div>
        </el-form-item>
        <!-- 限购数量 -->
        <el-form-item label="限购数量" prop="limitCount">
          <el-input-number
            v-model="form.limitCount"
            :min="1"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <div class="form-tip">每个用户最多可抢购的数量，默认1</div>
        </el-form-item>
        <!-- 活动时间范围 -->
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
        <!-- 活动描述 -->
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
 * 商家秒杀活动管理页
 * 功能：分页查询秒杀活动、按状态筛选、创建秒杀活动、下架进行中活动
 * 秒杀规则：商品按秒杀价售卖，库存有限，每人限购 limitCount 件
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getMerchantSeckillList,
  createMerchantSeckill,
  offlineMerchantSeckill,
} from '@shop/shared'
import {
  SeckillStatus,
  seckillStatusOptions,
  seckillStatusTextMap,
  seckillStatusTagMap,
  type SeckillInfo,
  type SeckillCreateParams,
} from '@shop/shared'

/** 加载状态（表格 loading） */
const loading = ref(false)
/** 秒杀活动列表数据 */
const tableData = ref<SeckillInfo[]>([])
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
/** 提交中状态（防止重复点击） */
const submitLoading = ref(false)

/** 时间范围类型：el-date-picker datetimerange 绑定的 [开始, 结束] */
type TimeRange = [Date, Date] | null

/** 秒杀活动表单数据结构 */
interface SeckillForm {
  /** 商品ID */
  productId: number | undefined
  /** SKU ID */
  skuId: number | undefined
  /** 原价（元） */
  originalPrice: number | undefined
  /** 秒杀价（元，必须小于原价） */
  seckillPrice: number | undefined
  /** 秒杀库存总数 */
  totalCount: number | undefined
  /** 每人限购数量 */
  limitCount: number
  /** 活动时间范围 */
  timeRange: TimeRange
  /** 活动描述 */
  description: string
}

/** 生成默认空表单（打开新建对话框、关闭对话框时使用） */
function defaultForm(): SeckillForm {
  return {
    productId: undefined,
    skuId: undefined,
    originalPrice: undefined,
    seckillPrice: undefined,
    totalCount: undefined,
    limitCount: 1,
    timeRange: null,
    description: '',
  }
}

/** 秒杀活动表单数据（响应式） */
const form = reactive<SeckillForm>(defaultForm())

/** 表单校验规则 */
const formRules: FormRules = {
  // 商品ID：必填，必须大于0
  productId: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入商品ID'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  // SKU ID：必填，必须大于0
  skuId: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入SKU ID'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  // 原价：必填，必须大于0
  originalPrice: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入原价'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  // 秒杀价：必填，必须大于0且小于原价
  seckillPrice: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入秒杀价'))
          return
        }
        // 秒杀价必须小于原价（否则商家亏本，秒杀失去意义）
        if (
          form.originalPrice !== undefined &&
          Number(form.originalPrice) > 0 &&
          Number(value) >= Number(form.originalPrice)
        ) {
          callback(new Error('秒杀价必须小于原价'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  // 秒杀库存：必填，必须大于0
  totalCount: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入秒杀库存'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  // 限购数量：必填，必须大于0
  limitCount: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || Number(value) <= 0) {
          callback(new Error('请输入限购数量'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  // 活动时间范围：必填
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

/** 加载秒杀活动列表（调用后端分页接口） */
async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantSeckillList({
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

/**
 * 计算库存已售百分比（用于进度条展示）
 * 用总数减去剩余得到已售，再除以总数得到百分比
 * @param available 剩余库存
 * @param total 库存总数
 * @returns 0-100 的整数百分比
 */
function calcStockPercent(available: number, total: number): number {
  if (!total || total <= 0) return 0
  const sold = total - available
  const percent = Math.round((sold / total) * 100)
  // 防止边界值超出 0-100
  return Math.max(0, Math.min(100, percent))
}

/** 格式化时间：去掉 T 分隔符，截取到分钟（如 2026-06-25 10:00） */
function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

/** 打开创建对话框：重置表单为默认值 */
function openCreateDialog() {
  Object.assign(form, defaultForm())
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

/** 提交表单（创建秒杀活动） */
async function handleSubmit() {
  // 先做表单校验，校验不过直接返回
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (submitLoading.value) return

  // 时间范围拆成开始/结束两个时间字串
  const range = form.timeRange as [Date, Date]

  // 组装提交参数
  const params: SeckillCreateParams = {
    productId: Number(form.productId),
    skuId: Number(form.skuId),
    originalPrice: Number(form.originalPrice),
    seckillPrice: Number(form.seckillPrice),
    totalCount: Number(form.totalCount),
    limitCount: Number(form.limitCount),
    startTime: toIsoString(range[0]),
    endTime: toIsoString(range[1]),
    description: form.description,
  }

  submitLoading.value = true
  try {
    await createMerchantSeckill(params)
    ElMessage.success('创建成功')
    // 成功后关闭对话框并刷新列表
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

/** 下架秒杀活动（先弹确认框，确认后调用下架接口，仅进行中状态可下架） */
async function handleOffline(row: SeckillInfo) {
  // 先弹确认框，用户取消则直接返回
  try {
    await ElMessageBox.confirm(
      `确定要下架该秒杀活动吗？下架后用户将无法继续抢购。`,
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
    await offlineMerchantSeckill(row.id)
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
.seckill-list {
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
/* 商品/SKU 单元格 */
.product-cell {
  line-height: 1.6;
}
.sku-text {
  color: #909399;
  font-size: 12px;
}
/* 秒杀价：红色醒目 */
.seckill-price {
  color: #f56c6c;
  font-weight: bold;
  font-size: 15px;
}
/* 原价：划线展示 */
.original-price {
  color: #909399;
  text-decoration: line-through;
}
/* 库存单元格：文字 + 进度条 */
.stock-cell {
  width: 100%;
}
.stock-text {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
/* 操作列无操作时的占位 */
.empty-action {
  color: #c0c4cc;
}
</style>
