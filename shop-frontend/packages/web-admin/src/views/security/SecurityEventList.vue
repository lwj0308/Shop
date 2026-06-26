<template>
  <div class="security-event">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="类型">
          <el-select v-model="queryForm.eventType" placeholder="全部" clearable>
            <el-option label="登录异常" :value="1" /><el-option label="越权访问" :value="2" /><el-option label="暴力破解" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待处理" :value="0" /><el-option label="已处理" :value="1" />
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
        <el-table-column label="事件类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.eventType === 1 ? 'warning' : row.eventType === 2 ? 'danger' : ''" size="small">
              {{ eventTypeMap[row.eventType] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="IP" prop="ip" width="130" />
        <el-table-column label="用户" prop="username" width="100" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">{{ row.status === 0 ? '待处理' : '已处理' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="['admin:security:handle']" type="primary" text size="small" @click="handleProcess(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="processVisible" title="处理安全事件" width="450px">
      <el-form :model="processForm" label-width="80px">
        <el-form-item label="处理备注">
          <el-input v-model="processForm.handleRemark" type="textarea" :rows="4" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 安全事件页面
 * 查看和处理安全告警事件（登录异常、越权访问、暴力破解等）
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSecurityEventList, handleSecurityEvent } from '@shop/shared/api/modules/admin'

/** 安全事件类型映射 */
const eventTypeMap: Record<number, string> = { 1: '登录异常', 2: '越权访问', 3: '暴力破解' }

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const processVisible = ref(false)
const processForm = reactive({ id: 0, handleRemark: '' })
const queryForm = reactive({ eventType: undefined as number | undefined, status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.eventType = undefined; queryForm.status = undefined; queryForm.pageNum = 1; loadData() }

/** 加载安全事件列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getSecurityEventList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开处理安全事件弹窗 */
function handleProcess(row: any) { processForm.id = row.id; processForm.handleRemark = ''; processVisible.value = true }

/** 提交安全事件处理结果 */
async function submitProcess() {
  try {
    await handleSecurityEvent(processForm.id, { handleRemark: processForm.handleRemark })
    ElMessage.success('处理成功')
    processVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '处理失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.security-event { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
