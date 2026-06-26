<template>
  <div class="order-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="订单号"><el-input v-model="queryForm.orderNo" placeholder="订单号" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待付款" :value="0" /><el-option label="待发货" :value="1" /><el-option label="运输中" :value="2" /><el-option label="已收货" :value="3" /><el-option label="已完成" :value="4" /><el-option label="已取消" :value="5" />
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
        <el-table-column label="订单号" prop="orderNo" width="180" />
        <el-table-column label="用户" prop="username" width="100" />
        <el-table-column label="商品" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productName || '-' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }"><span style="color:#F56C6C;font-weight:600;">¥{{ row.totalAmount }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusType[row.status]" size="small">{{ orderStatusText[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" v-permission="['admin:manage:order']" type="primary" text size="small" @click="handleDeliver(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 发货弹窗：输入快递公司和快递单号 -->
    <el-dialog v-model="deliverDialogVisible" title="订单发货" width="450px" destroy-on-close>
      <el-form ref="deliverFormRef" :model="deliverForm" :rules="deliverFormRules" label-width="90px">
        <el-form-item label="快递公司" prop="logisticsCompany">
          <el-select v-model="deliverForm.logisticsCompany" placeholder="请选择快递公司" style="width: 100%">
            <el-option v-for="item in logisticsCompanies" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="快递单号" prop="logisticsNo">
          <el-input v-model="deliverForm.logisticsNo" placeholder="请输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="deliverLoading" @click="handleDeliverSubmit">确定发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单管理页面
 * 查看订单列表、发货操作
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getManageOrderList, deliverManageOrder } from '@shop/shared/api/modules/admin'

/** 订单状态对应的标签颜色 */
const orderStatusType: Record<number, string> = { 0: 'info', 1: 'warning', 2: '', 3: 'success', 4: 'success', 5: 'danger' }
/** 订单状态对应的中文文字 */
const orderStatusText: Record<number, string> = { 0: '待付款', 1: '待发货', 2: '运输中', 3: '已收货', 4: '已完成', 5: '已取消' }

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryForm = reactive({ orderNo: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

/** 发货弹窗相关状态 */
const deliverDialogVisible = ref(false)
const deliverLoading = ref(false)
const deliverFormRef = ref<FormInstance>()
/** 当前要发货的订单ID */
const currentOrderId = ref<number>(0)

/** 常用快递公司列表 */
const logisticsCompanies = ['顺丰速运', '中通快递', '圆通速递', '申通快递', '韵达快递', '京东物流', '邮政EMS']

/** 发货表单数据 */
const deliverForm = reactive({
  logisticsCompany: '',
  logisticsNo: '',
})

/** 发货表单校验规则 */
const deliverFormRules: FormRules = {
  logisticsCompany: [{ required: true, message: '请选择快递公司', trigger: 'change' }],
  logisticsNo: [{ required: true, message: '请输入快递单号', trigger: 'blur' }],
}

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() { queryForm.pageNum = 1; loadData() }

/** 重置搜索条件并重新查询 */
function handleReset() { queryForm.orderNo = ''; queryForm.status = undefined; queryForm.pageNum = 1; loadData() }

/** 加载订单列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getManageOrderList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开发货弹窗 */
function handleDeliver(row: any) {
  currentOrderId.value = row.id
  deliverForm.logisticsCompany = ''
  deliverForm.logisticsNo = ''
  deliverDialogVisible.value = true
}

/** 提交发货：校验表单后调用发货接口 */
async function handleDeliverSubmit() {
  const valid = await deliverFormRef.value?.validate().catch(() => false)
  if (!valid) return
  deliverLoading.value = true
  try {
    await deliverManageOrder(currentOrderId.value, deliverForm.logisticsNo, deliverForm.logisticsCompany)
    ElMessage.success('发货成功')
    deliverDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '发货失败')
  } finally {
    deliverLoading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.order-list { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
