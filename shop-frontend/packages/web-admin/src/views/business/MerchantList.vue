<template>
  <div class="merchant-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词">
          <el-input v-model="queryForm.keyword" placeholder="商家名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
            <el-option label="已禁用" :value="3" />
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
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="商家名称" prop="shopName" min-width="150" />
        <el-table-column label="联系人" prop="contactName" width="100" />
        <el-table-column label="手机号" prop="contactPhone" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">{{ statusTextMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="['admin:manage:merchant']" type="success" text size="small" @click="handleAudit(row, true)">通过</el-button>
            <el-button v-if="row.status === 0" v-permission="['admin:manage:merchant']" type="danger" text size="small" @click="handleAudit(row, false)">拒绝</el-button>
            <el-button v-if="row.status === 1" v-permission="['admin:manage:merchant']" type="warning" text size="small" @click="handleToggle(row)">禁用</el-button>
            <el-button v-if="row.status === 3" v-permission="['admin:manage:merchant']" type="success" text size="small" @click="handleToggle(row)">启用</el-button>
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
 * 商家管理页面
 * 审核商家入驻申请、启用/禁用商家
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getManageMerchantList, auditMerchant, disableManageMerchant, enableManageMerchant } from '@shop/shared/api/modules/admin'

/** 商家状态对应的标签颜色 */
const statusTypeMap: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
/** 商家状态对应的中文文字 */
const statusTextMap: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已禁用' }

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({ keyword: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.keyword = ''; queryForm.status = undefined; queryForm.pageNum = 1; loadData() }

/** 加载商家列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageMerchantList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 审核商家入驻申请（通过/拒绝） */
async function handleAudit(row: any, approved: boolean) {
  const action = approved ? '通过' : '拒绝'
  await ElMessageBox.confirm(`确定要${action}商家「${row.shopName}」的入驻申请吗？`, '提示', { type: approved ? 'success' : 'warning' })
  try {
    await auditMerchant({ merchantId: row.id, approved, remark: '' })
    ElMessage.success(`已${action}`)
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 启用/禁用商家 */
async function handleToggle(row: any) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定要${action}商家「${row.shopName}」吗？`, '提示', { type: 'warning' })
  try {
    if (row.status === 1) {
      await disableManageMerchant(row.id)
    } else {
      await enableManageMerchant(row.id)
    }
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.merchant-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
