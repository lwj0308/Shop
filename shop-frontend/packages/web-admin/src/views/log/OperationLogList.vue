<template>
  <div class="operation-log">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="模块"><el-input v-model="queryForm.module" placeholder="操作模块" clearable /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="操作人" prop="operatorName" width="100" />
        <el-table-column label="模块" prop="module" width="120" />
        <el-table-column label="操作类型" prop="operationType" width="100" />
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="IP" prop="ip" width="120" />
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ row.duration }}ms</template>
        </el-table-column>
        <el-table-column label="操作时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="操作日志详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作人">{{ detail.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ detail.operationType }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.duration }}ms</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2"><pre class="json-pre">{{ formatJson(detail.requestParams) }}</pre></el-descriptions-item>
        <el-descriptions-item label="响应结果" :span="2"><pre class="json-pre">{{ formatJson(detail.responseResult) }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 操作日志页面
 * 查看系统操作日志及详情
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationLogList } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<any>({})
const queryForm = reactive({ module: '', pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.module = ''; queryForm.pageNum = 1; loadData() }

/** 加载操作日志列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getOperationLogList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开日志详情弹窗 */
function handleDetail(row: any) { detail.value = row; detailVisible.value = true }

/** 格式化JSON字符串，方便阅读 */
function formatJson(str: string) { try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str } }

onMounted(() => { loadData() })
</script>

<style scoped>
.operation-log { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.json-pre { max-height: 200px; overflow: auto; font-size: 12px; white-space: pre-wrap; word-break: break-all; background: var(--color-bg); padding: 8px; border-radius: 4px; }
</style>
