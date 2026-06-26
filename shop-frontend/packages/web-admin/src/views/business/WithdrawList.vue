<template>
  <!-- 提现审核页面：管理员审核商家的提现申请 -->
  <div class="withdraw-list">
    <!-- 搜索区：按状态筛选 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
            <el-option label="已打款" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 提现申请列表表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="商家名称" prop="merchantName" min-width="140" show-overflow-tooltip />
        <el-table-column label="提现金额（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="amount-highlight">¥ {{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">{{ statusTextMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="银行名称" prop="bankName" min-width="130" show-overflow-tooltip />
        <el-table-column label="银行账号" prop="bankAccount" min-width="160" />
        <el-table-column label="账户名" prop="accountName" min-width="100" show-overflow-tooltip />
        <el-table-column label="审核备注" prop="auditRemark" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditRemark || '-' }}</template>
        </el-table-column>
        <el-table-column label="审核时间" prop="auditTime" width="170">
          <template #default="{ row }">{{ row.auditTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="success" text size="small" @click="handleAudit(row, 1)">通过</el-button>
            <el-button v-if="row.status === 0" type="danger" text size="small" @click="handleAudit(row, 2)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 审核弹窗：通过/拒绝提现申请 -->
    <el-dialog v-model="auditDialogVisible" :title="auditTitle" width="460px" destroy-on-close>
      <div class="audit-preview" v-if="currentRow">
        <div class="preview-row">
          <span class="preview-label">商家：</span>
          <span>{{ currentRow.merchantName || '-' }}</span>
        </div>
        <div class="preview-row">
          <span class="preview-label">提现金额：</span>
          <span class="amount-highlight">¥ {{ formatMoney(currentRow.amount) }}</span>
        </div>
        <div class="preview-row">
          <span class="preview-label">收款银行：</span>
          <span>{{ currentRow.bankName }}（{{ currentRow.bankAccount }}）</span>
        </div>
        <div class="preview-row">
          <span class="preview-label">账户名：</span>
          <span>{{ currentRow.accountName }}</span>
        </div>
      </div>
      <el-form v-if="auditForm.status === 2" label-width="80px">
        <el-form-item label="拒绝原因">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入拒绝原因（选填）"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-else
        type="warning"
        :closable="false"
        title="审核通过后，冻结金额将被扣减，表示钱已打款给商家。"
      />
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditLoading" @click="handleAuditSubmit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 提现审核页面
 * 管理员查看全平台商家的提现申请，并通过或拒绝。
 *
 * 审核流程：
 * - 通过：冻结金额扣减（钱已打款给商家），状态改为已通过
 * - 拒绝：冻结金额转回可用余额（解冻给商家），状态改为已拒绝
 */

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getManageWithdrawList, auditManageWithdraw } from '@shop/shared/api/modules/admin'

/** 提现状态对应的标签颜色 */
const statusTypeMap: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'primary' }
/** 提现状态对应的中文文字 */
const statusTextMap: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已打款' }

/** 搜索表单 */
const queryForm = reactive({
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 表格数据 */
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

/** 审核弹窗相关状态 */
const auditDialogVisible = ref(false)
const auditLoading = ref(false)
/** 当前要审核的提现申请 */
const currentRow = ref<any | null>(null)

/** 审核表单数据 */
const auditForm = reactive({
  id: 0,
  status: 1 as number,
  auditRemark: '',
})

/** 审核弹窗标题 */
const auditTitle = computed(() => (auditForm.status === 1 ? '审核通过' : '审核拒绝'))

/** 点击搜索按钮，重置到第1页并查询 */
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

/** 加载提现申请列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageWithdrawList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 打开审核弹窗
 * @param row 当前提现申请
 * @param status 审核结果：1通过 2拒绝
 */
function handleAudit(row: any, status: number) {
  currentRow.value = row
  auditForm.id = row.id
  auditForm.status = status
  auditForm.auditRemark = ''
  auditDialogVisible.value = true
}

/** 提交审核：调用审核接口 */
async function handleAuditSubmit() {
  auditLoading.value = true
  try {
    await auditManageWithdraw({
      id: auditForm.id,
      status: auditForm.status,
      auditRemark: auditForm.auditRemark || undefined,
    })
    ElMessage.success(auditForm.status === 1 ? '审核通过' : '已拒绝提现')
    auditDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '审核失败')
  } finally {
    auditLoading.value = false
  }
}

/**
 * 金额格式化：保留2位小数
 */
function formatMoney(value: number | undefined | null): string {
  if (value === undefined || value === null) return '0.00'
  return Number(value).toFixed(2)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.withdraw-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 金额高亮 */
.amount-highlight {
  color: var(--color-primary);
  font-weight: 600;
}

/* 审核弹窗中的预览区 */
.audit-preview {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 16px;
}

.preview-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.5;
}

.preview-row:last-child {
  margin-bottom: 0;
}

.preview-label {
  color: #909399;
  min-width: 80px;
  flex-shrink: 0;
}
</style>
