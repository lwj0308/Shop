<template>
  <div class="notice-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="类型">
          <el-select v-model="queryForm.type" placeholder="全部" clearable>
            <el-option label="通知" :value="1" /><el-option label="公告" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <div class="action-bar">
      <el-button v-permission="['admin:content:notice']" type="primary" @click="handleAdd">发布公告</el-button>
    </div>
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }"><el-tag :type="row.type === 1 ? '' : 'warning'" size="small">{{ row.type === 1 ? '通知' : '公告' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '已发布' : '草稿' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="发布时间" prop="publishTime" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:content:notice']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:content:notice']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" placeholder="公告标题" /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type"><el-radio :value="1">通知</el-radio><el-radio :value="2">公告</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="6" placeholder="公告内容" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status"><el-radio :value="1">发布</el-radio><el-radio :value="0">草稿</el-radio></el-radio-group>
        </el-form-item>
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
 * 公告管理页面
 * 通知和公告的增删改查
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getNoticeList, createNotice, updateNotice, deleteNotice } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const queryForm = reactive({ type: undefined as number | undefined, pageNum: 1, pageSize: 10 })
const form = reactive({ id: null as number | null, title: '', type: 2, content: '', status: 1 })
const formRules: FormRules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }], content: [{ required: true, message: '请输入内容', trigger: 'blur' }] }

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.type = undefined; queryForm.pageNum = 1; loadData() }

/** 加载公告列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getNoticeList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开发布公告弹窗 */
function handleAdd() { dialogTitle.value = '发布公告'; Object.assign(form, { id: null, title: '', type: 2, content: '', status: 1 }); dialogVisible.value = true }

/** 打开编辑公告弹窗 */
function handleEdit(row: any) { dialogTitle.value = '编辑公告'; Object.assign(form, row); dialogVisible.value = true }

/** 提交表单（新增或编辑） */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (form.id) {
      await updateNotice(form.id, form)
    } else {
      await createNotice(form)
    }
    ElMessage.success(form.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 删除公告 */
async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除公告「${row.title}」吗？`, '警告', { type: 'warning' })
  try {
    await deleteNotice(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.notice-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.action-bar { display: flex; justify-content: flex-end; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
