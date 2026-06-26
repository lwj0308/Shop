<template>
  <div class="category-list">
    <div class="action-bar">
      <el-button v-permission="['admin:manage:category']" type="primary" @click="handleAdd(null)">新增分类</el-button>
    </div>
    <el-card>
      <el-table v-loading="loading" :data="categoryTree" row-key="id" :tree-props="{ children: 'children' }" stripe default-expand-all>
        <el-table-column label="分类名称" prop="name" min-width="200" />
        <el-table-column label="图标" prop="icon" width="80" />
        <el-table-column label="排序" prop="sort" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:manage:category']" type="primary" text size="small" @click="handleAdd(row)">新增子分类</el-button>
            <el-button v-permission="['admin:manage:category']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:manage:category']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="450px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="上级分类"><el-input :value="parentName" disabled /></el-form-item>
        <el-form-item label="分类名称" prop="name"><el-input v-model="form.name" placeholder="请输入分类名称" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="图标名称" /></el-form-item>
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
 * 分类管理页面
 * 支持树形结构的分类增删改查
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getManageCategoryTree, addManageCategory, updateManageCategory, deleteManageCategory } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const categoryTree = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const parentName = ref('顶级分类')

const form = reactive({ id: null as number | null, parentId: 0, name: '', icon: '', sort: 0 })
const formRules: FormRules = { name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }] }

/** 加载分类树形数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageCategoryTree()
    categoryTree.value = res.data.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开新增分类弹窗，row为父分类，null表示顶级分类 */
function handleAdd(row: any | null) {
  dialogTitle.value = '新增分类'
  parentName.value = row?.name || '顶级分类'
  Object.assign(form, { id: null, parentId: row?.id || 0, name: '', icon: '', sort: 0 })
  dialogVisible.value = true
}

/** 打开编辑分类弹窗 */
function handleEdit(row: any) {
  dialogTitle.value = '编辑分类'
  parentName.value = row.parentName || '顶级分类'
  Object.assign(form, row)
  dialogVisible.value = true
}

/** 提交表单（新增或编辑） */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (form.id) {
      await updateManageCategory(form.id, form)
    } else {
      await addManageCategory(form)
    }
    ElMessage.success(form.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 删除分类 */
async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除分类「${row.name}」吗？`, '警告', { type: 'warning' })
  try {
    await deleteManageCategory(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.category-list { display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; justify-content: flex-end; }
</style>
