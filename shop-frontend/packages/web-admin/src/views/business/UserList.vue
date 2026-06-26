<template>
  <!-- C端用户管理页面（Feign代理shop-user） -->
  <div class="user-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词">
          <el-input v-model="queryForm.keyword" placeholder="用户名/手机号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <el-table-column label="用户名" prop="username" width="120" />
        <el-table-column label="昵称" prop="nickname" width="120" />
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="注册时间" prop="createTime" width="170" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="['admin:manage:user']"
              :type="row.status === 1 ? 'danger' : 'success'"
              text size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
  </div>
</template>

<script setup lang="ts">
/**
 * C端用户管理页面
 * 通过Feign代理调用shop-user服务
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getManageUserList, disableManageUser, enableManageUser } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({ keyword: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.keyword = ''; queryForm.status = undefined; queryForm.pageNum = 1; loadData() }

/** 加载用户列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageUserList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 切换用户启用/禁用状态 */
async function handleToggleStatus(row: any) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定要${action}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  try {
    if (row.status === 1) {
      await disableManageUser(row.id)
    } else {
      await enableManageUser(row.id)
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
.user-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
