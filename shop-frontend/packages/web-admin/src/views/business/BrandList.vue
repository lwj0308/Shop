<template>
  <div class="brand-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词"><el-input v-model="queryForm.keyword" placeholder="品牌名称" clearable /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <div class="action-bar">
      <el-button v-permission="['admin:manage:brand']" type="primary" @click="handleAdd">新增品牌</el-button>
    </div>
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="品牌Logo" width="80">
          <template #default="{ row }"><el-image :src="row.logo" style="width:40px;height:40px;" fit="contain" /></template>
        </el-table-column>
        <el-table-column label="品牌名称" prop="name" min-width="150" />
        <el-table-column label="首字母" prop="firstLetter" width="80" />
        <el-table-column label="排序" prop="sort" width="80" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:manage:brand']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:manage:brand']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="450px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="品牌名称" prop="name"><el-input v-model="form.name" placeholder="请输入品牌名称" /></el-form-item>
        <el-form-item label="品牌Logo"><el-input v-model="form.logo" placeholder="Logo URL" /></el-form-item>
        <el-form-item label="首字母" prop="firstLetter"><el-input v-model="form.firstLetter" placeholder="如 N" maxlength="1" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 品牌管理页面
 * 品牌的增删改查
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getManageBrandList, addManageBrand, updateManageBrand, deleteManageBrand } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const queryForm = reactive({ keyword: '', pageNum: 1, pageSize: 10 })
const form = reactive({ id: null as number | null, name: '', logo: '', firstLetter: '', sort: 0 })
const formRules: FormRules = { name: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }] }

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.keyword = ''; queryForm.pageNum = 1; loadData() }

/** 加载品牌列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageBrandList()
    tableData.value = res.data.data || []
    total.value = tableData.value.length
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开新增品牌弹窗 */
function handleAdd() { dialogTitle.value = '新增品牌'; Object.assign(form, { id: null, name: '', logo: '', firstLetter: '', sort: 0 }); dialogVisible.value = true }

/** 打开编辑品牌弹窗 */
function handleEdit(row: any) { dialogTitle.value = '编辑品牌'; Object.assign(form, row); dialogVisible.value = true }

/** 提交表单（新增或编辑） */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (form.id) {
      await updateManageBrand(form.id, form)
    } else {
      await addManageBrand(form)
    }
    ElMessage.success(form.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 删除品牌 */
async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除品牌「${row.name}」吗？`, '警告', { type: 'warning' })
  try {
    await deleteManageBrand(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.brand-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.action-bar { display: flex; justify-content: flex-end; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
