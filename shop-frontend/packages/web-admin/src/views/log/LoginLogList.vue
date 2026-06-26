<template>
  <div class="login-log">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="用户名"><el-input v-model="queryForm.username" placeholder="用户名" clearable /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="用户名" prop="username" width="120" />
        <el-table-column label="IP" prop="ip" width="130" />
        <el-table-column label="浏览器" prop="browser" width="120" show-overflow-tooltip />
        <el-table-column label="操作系统" prop="os" width="120" show-overflow-tooltip />
        <el-table-column label="登录状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" prop="failReason" min-width="150" show-overflow-tooltip />
        <el-table-column label="登录时间" prop="loginTime" width="170" />
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录日志页面
 * 查看用户登录记录
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getLoginLogList } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({ username: '', pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.username = ''; queryForm.pageNum = 1; loadData() }

/** 加载登录日志列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getLoginLogList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.login-log { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
