<template>
  <!-- 管理员管理页面 -->
  <div class="admin-user-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词">
          <el-input v-model="queryForm.keyword" placeholder="用户名/昵称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button v-permission="['admin:user:add']" type="primary" @click="handleAdd">新增管理员</el-button>
    </div>

    <!-- 表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="用户名" prop="username" width="120" />
        <el-table-column label="昵称" prop="nickname" width="120" />
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="部门" width="120">
          <template #default="{ row }">{{ row.dept?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role.id" size="small" style="margin-right:4px;">
              {{ role.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:user:edit']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:user:edit']" type="warning" text size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button v-permission="['admin:user:edit']" :type="row.status === 1 ? 'danger' : 'success'" text size="small" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button v-permission="['admin:user:remove']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select v-model="form.deptId" :data="deptTree" :props="{ label: 'name', value: 'id' }" placeholder="请选择部门" check-strictly />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色">
            <el-option v-for="role in allRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
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
 * 管理员管理页面
 * 包含搜索、新增、编辑、删除、状态切换、密码重置功能
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getAdminUserList, createAdminUser, updateAdminUser, deleteAdminUser, updateAdminUserStatus, resetAdminUserPassword, getAllRoles, getDeptTree } from '@shop/shared/api/modules/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const allRoles = ref<any[]>([])
const deptTree = ref<any[]>([])

/** 查询参数 */
const queryForm = reactive({
  keyword: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 表单数据 */
const form = reactive({
  id: null as number | null,
  username: '',
  nickname: '',
  password: '',
  phone: '',
  email: '',
  deptId: null as number | null,
  roleIds: [] as number[],
})

/** 表单校验规则 */
const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 搜索 */
function handleSearch() {
  queryForm.pageNum = 1
  loadData()
}

/** 重置搜索条件 */
function handleReset() {
  queryForm.keyword = ''
  queryForm.status = undefined
  queryForm.pageNum = 1
  loadData()
}

/** 加载列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminUserList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 新增 */
function handleAdd() {
  dialogTitle.value = '新增管理员'
  Object.assign(form, { id: null, username: '', nickname: '', password: '', phone: '', email: '', deptId: null, roleIds: [] })
  dialogVisible.value = true
}

/** 编辑 */
function handleEdit(row: any) {
  dialogTitle.value = '编辑管理员'
  Object.assign(form, { ...row, password: '', roleIds: row.roles?.map((r: any) => r.id) || [] })
  dialogVisible.value = true
}

/** 提交表单 */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.id) {
      await updateAdminUser(form.id, form)
    } else {
      await createAdminUser(form)
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

/** 切换状态 */
async function handleToggleStatus(row: any) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定要${action}管理员「${row.username}」吗？`, '提示', { type: 'warning' })
  try {
    await updateAdminUserStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

/** 重置密码 */
async function handleResetPwd(row: any) {
  await ElMessageBox.confirm(`确定要重置管理员「${row.username}」的密码吗？重置后密码为 123456`, '提示', { type: 'warning' })
  try {
    await resetAdminUserPassword(row.id)
    ElMessage.success('密码已重置为：123456')
  } catch (error: any) {
    ElMessage.error(error.message || '重置失败')
  }
}

/** 删除 */
async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除管理员「${row.username}」吗？此操作不可恢复！`, '警告', { type: 'warning' })
  try {
    await deleteAdminUser(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => {
  loadData()
  loadRolesAndDepts()
})

/** 加载所有角色和部门树（用于新增/编辑表单选择） */
async function loadRolesAndDepts() {
  try {
    const [rolesRes, deptRes] = await Promise.all([
      getAllRoles(),
      getDeptTree(),
    ])
    allRoles.value = rolesRes.data.data || []
    deptTree.value = deptRes.data.data || []
  } catch {
    // 静默失败，不影响主流程
  }
}
</script>

<style scoped>
.admin-user-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
