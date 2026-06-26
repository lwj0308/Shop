<template>
  <!-- 权限管理页面：树形表格展示权限菜单 -->
  <div class="permission-list">
    <div class="action-bar">
      <el-button v-permission="['admin:permission:add']" type="primary" @click="handleAdd(null)">新增顶级权限</el-button>
      <el-button @click="toggleExpandAll">{{ isExpandAll ? '折叠全部' : '展开全部' }}</el-button>
    </div>

    <el-card>
      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="permissionTree"
        row-key="id"
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children' }"
        stripe
      >
        <el-table-column label="权限名称" prop="name" min-width="200" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? '' : row.type === 2 ? 'success' : 'warning'" size="small">
              {{ typeMap[row.type] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限标识" prop="permissionKey" width="180" />
        <el-table-column label="路由路径" prop="path" width="150" />
        <el-table-column label="图标" prop="icon" width="80" />
        <el-table-column label="排序" prop="sort" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:permission:add']" type="primary" text size="small" @click="handleAdd(row)">新增子权限</el-button>
            <el-button v-permission="['admin:permission:edit']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:permission:remove']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="上级权限">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item label="权限类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item v-if="form.type !== 1" label="权限标识" prop="permissionKey">
          <el-input v-model="form.permissionKey" placeholder="如 admin:user:list" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="如 /system/admin-user" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" label="图标">
          <el-input v-model="form.icon" placeholder="图标名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 权限管理页面
 * 树形表格展示权限菜单，支持目录/菜单/按钮三种类型
 */

import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getPermissionTree, createPermission, updatePermission, deletePermission } from '@shop/shared/api/modules/admin'

const typeMap: Record<number, string> = { 1: '目录', 2: '菜单', 3: '按钮' }

const loading = ref(false)
const permissionTree = ref<any[]>([])
const isExpandAll = ref(true)
const refreshTable = ref(true)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const parentName = ref('顶级权限')

const form = reactive({
  id: null as number | null,
  parentId: 0,
  type: 2,
  name: '',
  permissionKey: '',
  path: '',
  icon: '',
  sort: 0,
  status: 1,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择权限类型', trigger: 'change' }],
}

/** 切换展开/折叠 */
async function toggleExpandAll() {
  isExpandAll.value = !isExpandAll.value
  refreshTable.value = false
  await nextTick()
  refreshTable.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await getPermissionTree()
    permissionTree.value = res.data.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleAdd(row: any | null) {
  dialogTitle.value = '新增权限'
  parentName.value = row?.name || '顶级权限'
  Object.assign(form, { id: null, parentId: row?.id || 0, type: row ? 2 : 1, name: '', permissionKey: '', path: '', icon: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑权限'
  parentName.value = row.parentName || '顶级权限'
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await updatePermission(form.id, form)
    } else {
      await createPermission(form)
    }
    ElMessage.success(form.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除权限「${row.name}」吗？`, '警告', { type: 'warning' })
  try {
    await deletePermission(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.permission-list { display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; gap: 8px; }
</style>
