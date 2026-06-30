<template>
  <!-- 角色管理页：玻璃搜索栏 + 玻璃表格 + 玻璃弹窗 -->
  <div class="role-list">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">角色管理</h2>
      <p class="page-subtitle">管理平台角色、权限分配</p>
    </div>

    <!-- 搜索栏：玻璃卡片 -->
    <el-card class="glass-card search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="关键词">
          <el-input v-model="queryForm.keyword" placeholder="角色名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏：右对齐新增按钮 -->
    <div class="action-bar">
      <el-button v-permission="['admin:role:add']" type="primary" @click="handleAdd">新增角色</el-button>
    </div>

    <!-- 数据表格：玻璃卡片 -->
    <el-card class="glass-card">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="角色名称" prop="name" width="150" />
        <el-table-column label="角色标识" prop="roleKey" width="120" />
        <el-table-column label="排序" prop="sort" width="80" />
        <el-table-column label="数据权限" width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ dataScopeMap[row.dataScope] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['admin:role:edit']" type="primary" text size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="['admin:role:edit']" type="warning" text size="small" @click="handleAssignPerm(row)">分配权限</el-button>
            <el-button v-permission="['admin:role:remove']" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 新增/编辑弹窗：玻璃化 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close class="glass-dialog">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如 admin、editor" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-select v-model="form.dataScope" placeholder="请选择">
            <el-option label="全部数据" :value="1" />
            <el-option label="本部门数据" :value="2" />
            <el-option label="本部门及下级" :value="3" />
            <el-option label="仅本人数据" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗：玻璃化 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="500px" destroy-on-close class="glass-dialog">
      <el-tree
        ref="permTreeRef"
        :data="permissionTree"
        :props="{ label: 'name', children: 'children' }"
        show-checkbox
        node-key="id"
        :default-checked-keys="assignedPermIds"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permLoading" @click="handleSavePerm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 角色管理页面
 * 包含角色CRUD和权限分配功能
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getAdminRoleList, createAdminRole, updateAdminRole, deleteAdminRole, getPermissionTree } from '@shop/shared/api/modules/admin'

/** 数据权限映射 */
const dataScopeMap: Record<number, string> = { 1: '全部数据', 2: '本部门', 3: '本部门及下级', 4: '仅本人' }

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const permDialogVisible = ref(false)
const permLoading = ref(false)
const permTreeRef = ref<any>()
const permissionTree = ref<any[]>([])
const assignedPermIds = ref<number[]>([])
const currentRoleId = ref<number>(0)

const queryForm = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const form = reactive({
  id: null as number | null,
  name: '', roleKey: '', sort: 0, dataScope: 4, status: 1, remark: '',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
}

function handleSearch() { queryForm.pageNum = 1; loadData() }
function handleReset() { queryForm.keyword = ''; queryForm.pageNum = 1; loadData() }

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminRoleList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '新增角色'
  Object.assign(form, { id: null, name: '', roleKey: '', sort: 0, dataScope: 4, status: 1, remark: '' })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑角色'
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await updateAdminRole(form.id, form)
    } else {
      await createAdminRole(form)
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

function handleAssignPerm(row: any) {
  currentRoleId.value = row.id
  assignedPermIds.value = row.permissions?.map((p: any) => p.id) || []
  loadPermissionTree()
  permDialogVisible.value = true
}

/** 加载权限树 */
async function loadPermissionTree() {
  try {
    const res = await getPermissionTree()
    permissionTree.value = res.data.data || []
  } catch {
    // 静默失败
  }
}

async function handleSavePerm() {
  permLoading.value = true
  try {
    const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
    // 调用已有的 updateAdminRole 接口保存权限分配
    // 后端 updateAdminRole 方法支持通过 permissionIds 字段更新权限关联
    await updateAdminRole(currentRoleId.value, { permissionIds: checkedKeys })
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '权限分配失败')
  } finally {
    permLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除角色「${row.name}」吗？`, '警告', { type: 'warning' })
  try {
    await deleteAdminRole(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
/* 角色列表根容器 */
.role-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 页面标题 */
.page-header {
  margin-bottom: 4px;
}

.page-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text);
  font-family: var(--font-display);
  letter-spacing: -0.02em;
}

.page-subtitle {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

/* 玻璃卡片：覆盖 el-card 默认样式 */
.glass-card {
  background: var(--color-glass) !important;
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--color-glass-border) !important;
  border-radius: var(--radius-card) !important;
  box-shadow: var(--shadow-card) !important;
}

.glass-card :deep(.el-card__header) {
  border-bottom: 1px solid var(--color-border) !important;
}

.glass-card :deep(.el-card__body) {
  padding: 20px;
}

/* 搜索卡片：表单底部不留白 */
.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}

/* 搜索表单项：圆角输入框 */
.search-card :deep(.el-input__wrapper),
.search-card :deep(.el-select__wrapper) {
  border-radius: var(--radius-button);
}

/* 操作栏：右对齐 */
.action-bar {
  display: flex;
  justify-content: flex-end;
}

/* 表格容器：圆角 + 透明背景 */
.glass-card :deep(.el-table) {
  border-radius: var(--radius-button);
  overflow: hidden;
  background: transparent;
}

/* 表头：浅色玻璃背景 + 次要色文字 */
.glass-card :deep(.el-table th.el-table__cell) {
  background: rgba(248, 250, 252, 0.6);
  color: var(--color-text-secondary);
  font-weight: 600;
}

/* 表格行：透明背景 */
.glass-card :deep(.el-table tr),
.glass-card :deep(.el-table td.el-table__cell) {
  background: transparent;
}

html.dark .glass-card :deep(.el-table th.el-table__cell) {
  background: rgba(30, 41, 59, 0.5);
}

/* text 类型按钮：hover 时翡翠软色背景 */
.glass-card :deep(.el-button.is-text:hover) {
  background: var(--gradient-brand-soft) !important;
  color: var(--color-primary) !important;
}

/* 分页器：右对齐 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 权限树节点 hover 态：el-tree 在弹窗内，通过 :deep 穿透 */
:deep(.el-tree-node__content:hover) {
  background: var(--gradient-brand-soft) !important;
}

:deep(.el-tree-node__content.is-checked) {
  color: var(--color-primary);
}
</style>

<!-- 非 scoped 全局样式：el-dialog 会 teleport 到 body，scoped 无法穿透，必须用全局样式 -->
<style>
/* 玻璃弹窗：圆角 + 头部渐变软背景 + body 玻璃白 */
.glass-dialog {
  border-radius: var(--radius-card) !important;
  overflow: hidden;
  border: 1px solid var(--color-glass-border) !important;
  box-shadow: var(--shadow-hover) !important;
}

.glass-dialog .el-dialog__header {
  background: var(--gradient-brand-soft);
  border-bottom: 1px solid var(--color-border);
  padding: 18px 24px;
  margin: 0;
}

.glass-dialog .el-dialog__title {
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-text);
}

.glass-dialog .el-dialog__body {
  padding: 24px;
  background: var(--color-card);
}

.glass-dialog .el-dialog__footer {
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
  background: rgba(248, 250, 252, 0.5);
}

/* 暗色模式：弹窗背景更深 */
html.dark .glass-dialog {
  background: rgba(30, 41, 59, 0.95) !important;
}

html.dark .glass-dialog .el-dialog__body {
  background: rgba(30, 41, 59, 0.95);
}

html.dark .glass-dialog .el-dialog__footer {
  background: rgba(15, 23, 42, 0.5);
}
</style>
