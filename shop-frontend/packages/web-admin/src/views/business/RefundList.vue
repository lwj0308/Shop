<template>
  <div class="refund-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待审核" :value="0" /><el-option label="已同意" :value="1" /><el-option label="已拒绝" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="退款单号" prop="refundNo" width="180" />
        <el-table-column label="订单号" prop="orderNo" width="180" />
        <el-table-column label="退款金额" width="100">
          <template #default="{ row }"><span style="color:#F56C6C;font-weight:600;">¥{{ row.refundAmount }}</span></template>
        </el-table-column>
        <el-table-column label="原因" prop="reason" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'warning' : row.status === 1 ? 'success' : 'danger'" size="small">
              {{ ['待审核', '已同意', '已拒绝'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="['admin:manage:refund']" type="success" text size="small" @click="handleAudit(row, true)">同意</el-button>
            <el-button v-if="row.status === 0" v-permission="['admin:manage:refund']" type="danger" text size="small" @click="handleAudit(row, false)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 退款管理页面
 * 审核退款申请（同意/拒绝）
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getManageRefundList, auditManageRefund } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({ status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.status = undefined; queryForm.pageNum = 1; loadData() }

/** 加载退款列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageRefundList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 审核退款申请（同意/拒绝） */
async function handleAudit(row: any, approved: boolean) {
  const action = approved ? '同意' : '拒绝'
  await ElMessageBox.confirm(`确定要${action}此退款申请吗？`, '提示', { type: approved ? 'success' : 'warning' })
  try {
    await auditManageRefund({ refundId: row.id, approved, remark: '' })
    ElMessage.success(`已${action}`)
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.refund-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
