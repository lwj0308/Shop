<template>
  <div class="product-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词"><el-input v-model="queryForm.keyword" placeholder="商品名称" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="已上架" :value="1" /><el-option label="已下架" :value="0" />
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
        <el-table-column label="商品图片" width="80">
          <template #default="{ row }"><el-image :src="row.mainImage" style="width:50px;height:50px;" fit="cover" /></template>
        </el-table-column>
        <el-table-column label="商品名称" prop="name" min-width="200" show-overflow-tooltip />
        <el-table-column label="价格" width="120">
          <template #default="{ row }"><span style="color:#F56C6C;font-weight:600;">¥{{ row.minPrice }}</span></template>
        </el-table-column>
        <el-table-column label="商家" prop="merchantName" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:manage:product']" :type="row.status === 1 ? 'warning' : 'success'" text size="small" @click="handleToggle(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
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
 * 商品管理页面
 * 管理平台商品的上架/下架状态
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getManageProductList, offShelfManageProduct, onShelfManageProduct } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({ keyword: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.keyword = ''; queryForm.status = undefined; queryForm.pageNum = 1; loadData() }

/** 加载商品列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageProductList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 切换商品上架/下架状态 */
async function handleToggle(row: any) {
  const action = row.status === 1 ? '下架' : '上架'
  await ElMessageBox.confirm(`确定要${action}商品「${row.name}」吗？`, '提示', { type: 'warning' })
  try {
    if (row.status === 1) {
      await offShelfManageProduct(row.id)
    } else {
      await onShelfManageProduct(row.id)
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
.product-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
