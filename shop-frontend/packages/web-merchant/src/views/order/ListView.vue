<template>
  <!-- 订单列表页：Tab切换 + 订单表格 + 发货/退款弹窗 -->
  <div class="order-list">
    <!-- Tab切换 -->
    <div class="order-tabs">
      <div
        v-for="tab in orderTabs"
        :key="tab.value"
        :class="['tab-item', { active: activeTab === tab.value }]"
        @click="activeTab = tab.value; loadOrders()"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="tab-count" :style="{ color: tab.color }">({{ tab.count }})</span>
      </div>
    </div>

    <!-- 订单表格 -->
    <el-table v-loading="loading" :data="orderList" stripe>
      <el-table-column label="订单号" width="180">
        <template #default="{ row }">
          <span class="order-no" @click="goToDetail(row.id)">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="商品信息" min-width="250">
        <template #default="{ row }">
          <div class="order-items">
            <div v-for="item in row.items" :key="item.id" class="order-item">
              <el-image :src="item.productImage" class="item-image" fit="cover" />
              <div class="item-text">
                <div class="item-name">{{ item.productName }}</div>
                <div class="item-sku">{{ item.skuName }} × {{ item.quantity }}</div>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">
          <span class="amount">{{ formatPriceWithSymbol(row.payAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="getOrderTagType(row.status)"
            size="small"
          >
            {{ ORDER_STATUS_MAP[row.status]?.label || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === ORDER_STATUS.PENDING_DELIVERY"
            type="primary" size="small"
            @click="openShipDialog(row)"
          >
            发货
          </el-button>
          <el-button
            v-if="row.status === ORDER_STATUS.REFUNDING"
            type="warning" size="small"
            @click="openRefundDialog(row)"
          >
            退款审核
          </el-button>
          <el-button type="primary" text size="small" @click="goToDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-row">
      <span class="pagination-total">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @size-change="loadOrders"
        @current-change="loadOrders"
      />
    </div>

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipDialogVisible" title="订单发货" width="480px" @close="resetShipForm">
      <div v-if="currentOrder" class="dialog-info">
        <div class="dialog-info-label">订单号：{{ currentOrder.orderNo }}</div>
        <div class="dialog-info-value">{{ currentOrder.items?.[0]?.productName }}</div>
      </div>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="100px">
        <el-form-item label="物流公司" prop="expressCompany">
          <el-select v-model="shipForm.expressCompany" placeholder="请选择物流公司" style="width: 100%">
            <el-option label="顺丰速运" value="顺丰速运" />
            <el-option label="中通快递" value="中通快递" />
            <el-option label="圆通速递" value="圆通速递" />
            <el-option label="韵达快递" value="韵达快递" />
            <el-option label="申通快递" value="申通快递" />
            <el-option label="京东物流" value="京东物流" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" prop="expressNo">
          <el-input v-model="shipForm.expressNo" placeholder="请输入物流单号" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipLoading" @click="handleShip">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 退款审核弹窗 -->
    <el-dialog v-model="refundDialogVisible" title="退款审核" width="480px" @close="resetRefundForm">
      <div v-if="currentOrder" class="dialog-info">
        <div class="dialog-info-label">订单号：{{ currentOrder.orderNo }}</div>
        <div class="dialog-info-value">{{ currentOrder.items?.[0]?.productName }} · 退款金额：{{ formatPriceWithSymbol(currentOrder.payAmount) }}</div>
        <div class="dialog-info-reason">退款原因：商品与描述不符</div>
      </div>
      <el-form ref="refundFormRef" :model="refundForm" :rules="refundRules" label-width="100px">
        <el-form-item label="审核结果" prop="action">
          <el-radio-group v-model="refundForm.action">
            <el-radio value="agree">同意退款</el-radio>
            <el-radio value="reject">拒绝退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="refundForm.action === 'reject'" label="拒绝原因" prop="reason">
          <el-input
            v-model="refundForm.reason"
            type="textarea"
            placeholder="请输入拒绝退款的原因"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="refundLoading" @click="handleRefund">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单列表页
 * 展示商家收到的所有订单，支持发货、退款处理等
 * 优化点：发货弹窗表单校验、退款审核弹窗（拒绝时原因必填）、订单金额格式化
 */

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getMerchantOrderList,
  shipOrder,
  agreeRefund,
  rejectRefund,
} from '@shop/shared'
import { formatPriceWithSymbol, formatDate } from '@shop/shared'
import { ORDER_STATUS, ORDER_STATUS_MAP } from '@shop/shared'
import type { OrderInfo, OrderStatus } from '@shop/shared'

const router = useRouter()

/** 订单列表数据 */
const orderList = ref<OrderInfo[]>([])

/** 加载状态 */
const loading = ref(false)

/** 分页参数 */
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

/** 状态筛选 */
const statusFilter = ref<OrderStatus | undefined>(undefined)

/** 当前激活的Tab */
const activeTab = ref('all')

/** 订单Tab列表 */
const orderTabs = [
  { label: '全部', value: 'all', count: 156 },
  { label: '待发货', value: 'pending', count: 12, color: '#E6A23C' },
  { label: '运输中', value: 'shipping', count: 28, color: '#409EFF' },
  { label: '已收货', value: 'received', count: 110, color: '#67C23A' },
  { label: '退款中', value: 'refunding', count: 6, color: '#F56C6C' },
]

/**
 * 根据订单状态获取Tag类型
 * 用于Element Plus的el-tag组件
 */
const getOrderTagType = (status: number): string => {
  const map: Record<number, string> = {
    [ORDER_STATUS.PENDING_DELIVERY]: 'warning',
    [ORDER_STATUS.SHIPPING]: '',
    [ORDER_STATUS.COMPLETED]: 'success',
    [ORDER_STATUS.REFUNDING]: 'danger',
  }
  return map[status] || 'info'
}

/** 当前操作的订单 */
const currentOrder = ref<OrderInfo | null>(null)

/** 发货弹窗可见性 */
const shipDialogVisible = ref(false)
/** 发货按钮loading */
const shipLoading = ref(false)
/** 发货表单引用 */
const shipFormRef = ref<FormInstance>()

/** 发货表单数据 */
const shipForm = reactive({
  expressNo: '',
  expressCompany: '',
})

/** 发货表单校验规则 */
const shipRules: FormRules = {
  expressNo: [
    { required: true, message: '请输入物流单号', trigger: 'blur' },
    { min: 6, max: 50, message: '物流单号长度为6-50个字符', trigger: 'blur' },
    { validator: (_rule, value, callback) => {
      // 物流单号只允许字母、数字和连字符
      if (value && !/^[A-Za-z0-9\-]+$/.test(value)) {
        callback(new Error('物流单号只允许字母、数字和连字符'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
}

/** 退款审核弹窗可见性 */
const refundDialogVisible = ref(false)
/** 退款按钮loading */
const refundLoading = ref(false)
/** 退款表单引用 */
const refundFormRef = ref<FormInstance>()

/** 退款表单数据 */
const refundForm = reactive({
  action: 'agree' as 'agree' | 'reject',
  reason: '',
})

/** 退款表单校验规则 */
const refundRules: FormRules = {
  action: [
    { required: true, message: '请选择审核结果', trigger: 'change' },
  ],
  reason: [
    { validator: (_rule, value, callback) => {
      // 拒绝退款时，原因必填
      if (refundForm.action === 'reject' && !value?.trim()) {
        callback(new Error('拒绝退款时必须填写原因'))
      } else {
        callback()
      }
    }, trigger: 'blur' },
  ],
}

/**
 * 加载订单列表
 */
const loadOrders = async () => {
  loading.value = true
  try {
    const res = await getMerchantOrderList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
    })
    orderList.value = res.data.records
    total.value = res.data.total
  } catch {
    ElMessage.error('加载订单列表失败')
  } finally {
    loading.value = false
  }
}

/** 跳转到订单详情页 */
const goToDetail = (id: number) => {
  router.push(`/order/${id}`)
}

/**
 * 打开发货弹窗
 */
const openShipDialog = (order: OrderInfo) => {
  currentOrder.value = order
  shipDialogVisible.value = true
}

/** 重置发货表单 */
const resetShipForm = () => {
  shipForm.expressNo = ''
  shipFormRef.value?.resetFields()
}

/**
 * 确认发货
 * 校验表单后调用发货API
 */
const handleShip = async () => {
  const valid = await shipFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (shipLoading.value) return
  shipLoading.value = true

  try {
    await shipOrder(currentOrder.value!.id, shipForm.expressNo, shipForm.expressCompany)
    ElMessage.success('发货成功')
    shipDialogVisible.value = false
    loadOrders()
  } catch (error) {
    const msg = error instanceof Error ? error.message : '发货失败'
    ElMessage.error(msg)
  } finally {
    shipLoading.value = false
  }
}

/**
 * 打开退款审核弹窗
 */
const openRefundDialog = (order: OrderInfo) => {
  currentOrder.value = order
  refundForm.action = 'agree'
  refundForm.reason = ''
  refundDialogVisible.value = true
}

/** 重置退款表单 */
const resetRefundForm = () => {
  refundForm.action = 'agree'
  refundForm.reason = ''
  refundFormRef.value?.resetFields()
}

/**
 * 提交退款审核
 * 同意退款直接调用同意API，拒绝退款需要填写原因
 */
const handleRefund = async () => {
  const valid = await refundFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (refundLoading.value) return
  refundLoading.value = true

  try {
    if (refundForm.action === 'agree') {
      await agreeRefund(currentOrder.value!.id)
      ElMessage.success('已同意退款')
    } else {
      await rejectRefund(currentOrder.value!.id, refundForm.reason)
      ElMessage.success('已拒绝退款')
    }
    refundDialogVisible.value = false
    loadOrders()
  } catch (error) {
    const msg = error instanceof Error ? error.message : '操作失败'
    ElMessage.error(msg)
  } finally {
    refundLoading.value = false
  }
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.order-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Tab切换栏 */
.order-tabs {
  display: flex;
  border-bottom: 2px solid #e4e7ed;
  margin-bottom: 4px;
  background: var(--color-card);
  border-radius: var(--radius-card) var(--radius-card) 0 0;
  padding: 0 20px;
}

.tab-item {
  padding: 12px 20px;
  cursor: pointer;
  color: var(--color-text-muted);
  font-size: 14px;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.tab-item:hover {
  color: var(--color-primary);
}

.tab-item.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 500;
}

.tab-count {
  font-size: 12px;
  margin-left: 4px;
}

/* 金额：红色加粗 */
.amount {
  color: var(--color-danger);
  font-weight: bold;
}

/* 订单商品信息 */
.order-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-image {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-button);
  flex-shrink: 0;
}

.item-text {
  min-width: 0;
}

.item-name {
  font-size: 13px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-sku {
  font-size: 12px;
  color: var(--color-text-muted);
}

/* 订单号：蓝色可点击 */
.order-no {
  color: var(--color-primary);
  cursor: pointer;
}

.order-no:hover {
  text-decoration: underline;
}

/* 分页行 */
.pagination-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.pagination-total {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 弹窗内信息区域 */
.dialog-info {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}

.dialog-info-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.dialog-info-value {
  font-size: 14px;
  color: var(--color-text);
  margin-top: 4px;
}

.dialog-info-reason {
  font-size: 13px;
  color: var(--color-warning);
  margin-top: 4px;
}
</style>
