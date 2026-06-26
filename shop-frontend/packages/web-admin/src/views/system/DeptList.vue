<template>
  <!-- 部门管理页面：树形表格 -->
  <div class="dept-list">
    <div class="action-bar">
      <el-button v-permission="['admin:dept:add']" type="primary" @click="handleAdd(null)">新增顶级部门</el-button>
      <el-button @click="toggleExpandAll">{{ isExpandAll ? '折叠全部' : '展开全部' }}</el-button>
    </div>

    <el-card>
      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="deptTree"
        row-key="id"
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children' }"
        stripe
      >
        <el-table-column label="部门名称" prop="name" min-width="200" />
        <el-table-column label="负责人" prop="leader" width="120" />
        <el-table-column label="排序" prop="sort" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:dept:add']" type="primary" text size="small" @click="handleAdd(row)">新增</el-button>
            <el-button v-permission="['admin:dept:edit']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:dept:remove']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="上级部门">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="负责人" prop="leader">
          <el-input v-model="form.leader" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
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
 * 部门管理页面
 * 树形表格展示部门层级结构
 */

import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getDeptTree, createDept, updateDept, deleteDept } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const deptTree = ref<any[]>([])
const isExpandAll = ref(true)
const refreshTable = ref(true)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const parentName = ref('顶级部门')

const form = reactive({
  id: null as number | null,
  parentId: 0,
  name: '',
  leader: '',
  sort: 0,
  status: 1,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

async function toggleExpandAll() {
  isExpandAll.value = !isExpandAll.value
  refreshTable.value = false
  await nextTick()
  refreshTable.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await getDeptTree()
    deptTree.value = res.data.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleAdd(row: any | null) {
  dialogTitle.value = '新增部门'
  parentName.value = row?.name || '顶级部门'
  Object.assign(form, { id: null, parentId: row?.id || 0, name: '', leader: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑部门'
  parentName.value = row.parentName || '顶级部门'
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await updateDept(form.id, form)
    } else {
      await createDept(form)
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
  await ElMessageBox.confirm(`确定要删除部门「${row.name}」吗？`, '警告', { type: 'warning' })
  try {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.dept-list { display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; gap: 8px; }
</style>
