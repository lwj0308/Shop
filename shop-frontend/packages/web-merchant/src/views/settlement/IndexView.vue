<template>
  <!-- 结算管理页面：商家查看结算账户、结算流水、申请提现 -->
  <div class="settlement-page">
    <!-- 顶部账户信息卡片：显示银行卡信息和余额 -->
    <el-card class="account-card" v-loading="accountLoading">
      <div class="account-header">
        <span class="account-title">结算账户</span>
        <el-button type="primary" size="small" @click="handleEditAccount">
          {{ accountInfo ? '修改账户' : '配置账户' }}
        </el-button>
      </div>

      <!-- 已配置结算账户：显示银行卡和余额信息 -->
      <div v-if="accountInfo" class="account-body">
        <div class="account-info">
          <div class="info-item">
            <span class="info-label">银行名称：</span>
            <span>{{ accountInfo.bankName || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">银行账号：</span>
            <span>{{ accountInfo.bankAccount || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">账户名：</span>
            <span>{{ accountInfo.accountName || '-' }}</span>
          </div>
        </div>
        <div class="balance-info">
          <div class="balance-item">
            <div class="balance-label">可用余额（元）</div>
            <div class="balance-value primary">¥ {{ formatMoney(accountInfo.balance) }}</div>
          </div>
          <div class="balance-divider"></div>
          <div class="balance-item">
            <div class="balance-label">冻结金额（元）</div>
            <div class="balance-value warning">¥ {{ formatMoney(accountInfo.frozenAmount) }}</div>
          </div>
          <div class="balance-divider"></div>
          <div class="balance-item">
            <el-button
              type="success"
              :disabled="!accountInfo.balance || accountInfo.balance <= 0"
              @click="handleOpenWithdraw"
            >
              申请提现
            </el-button>
          </div>
        </div>
      </div>

      <!-- 未配置结算账户：提示去配置 -->
      <el-empty v-else description="尚未配置结算账户，请先配置银行账户信息" :image-size="80" />
    </el-card>

    <!-- Tab 切换：结算流水 / 提现申请 -->
    <el-card>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 结算流水 Tab -->
        <el-tab-pane label="结算流水" name="records">
          <div class="tab-toolbar">
            <el-select v-model="recordQuery.status" placeholder="结算状态" clearable style="width: 140px">
              <el-option label="全部" :value="undefined" />
              <el-option label="待结算" :value="0" />
              <el-option label="已结算" :value="1" />
              <el-option label="已退款" :value="2" />
            </el-select>
            <el-button type="primary" @click="handleSearchRecords">搜索</el-button>
            <el-button @click="handleResetRecords">重置</el-button>
          </div>

          <el-table v-loading="recordLoading" :data="recordList" stripe>
            <el-table-column label="订单号" prop="orderNo" min-width="180" show-overflow-tooltip />
            <el-table-column label="订单金额（元）" width="130" align="right">
              <template #default="{ row }">¥ {{ formatMoney(row.orderAmount) }}</template>
            </el-table-column>
            <el-table-column label="抽成比例" width="100" align="right">
              <template #default="{ row }">{{ formatRate(row.commissionRate) }}</template>
            </el-table-column>
            <el-table-column label="平台抽成（元）" width="130" align="right">
              <template #default="{ row }">¥ {{ formatMoney(row.commissionAmount) }}</template>
            </el-table-column>
            <el-table-column label="应得金额（元）" width="130" align="right">
              <template #default="{ row }">
                <span class="amount-highlight">¥ {{ formatMoney(row.settlementAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getRecordStatusType(row.status)" size="small">
                  {{ row.statusDesc }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结算时间" prop="settleTime" width="170" />
            <el-table-column label="创建时间" prop="createTime" width="170" />
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="recordQuery.pageNum"
              v-model:page-size="recordQuery.pageSize"
              :total="recordTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadRecords"
              @current-change="loadRecords"
            />
          </div>
        </el-tab-pane>

        <!-- 提现申请 Tab -->
        <el-tab-pane label="提现申请" name="withdraws">
          <div class="tab-toolbar">
            <el-select v-model="withdrawQuery.status" placeholder="提现状态" clearable style="width: 140px">
              <el-option label="全部" :value="undefined" />
              <el-option label="待审核" :value="0" />
              <el-option label="已通过" :value="1" />
              <el-option label="已拒绝" :value="2" />
              <el-option label="已打款" :value="3" />
            </el-select>
            <el-button type="primary" @click="handleSearchWithdraws">搜索</el-button>
            <el-button @click="handleResetWithdraws">重置</el-button>
          </div>

          <el-table v-loading="withdrawLoading" :data="withdrawList" stripe>
            <el-table-column label="提现金额（元）" width="140" align="right">
              <template #default="{ row }">
                <span class="amount-highlight">¥ {{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getWithdrawStatusType(row.status)" size="small">
                  {{ row.statusDesc }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="银行名称" prop="bankName" min-width="140" show-overflow-tooltip />
            <el-table-column label="银行账号" prop="bankAccount" min-width="160" />
            <el-table-column label="账户名" prop="accountName" min-width="120" show-overflow-tooltip />
            <el-table-column label="审核备注" prop="auditRemark" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.auditRemark || '-' }}</template>
            </el-table-column>
            <el-table-column label="审核时间" prop="auditTime" width="170">
              <template #default="{ row }">{{ row.auditTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="申请时间" prop="createTime" width="170" />
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="withdrawQuery.pageNum"
              v-model:page-size="withdrawQuery.pageSize"
              :total="withdrawTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadWithdraws"
              @current-change="loadWithdraws"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 配置/修改结算账户弹窗 -->
    <el-dialog
      v-model="accountDialogVisible"
      :title="accountInfo ? '修改结算账户' : '配置结算账户'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountFormRules" label-width="90px">
        <el-form-item label="银行名称" prop="bankName">
          <el-input v-model="accountForm.bankName" placeholder="如：中国工商银行" maxlength="50" />
        </el-form-item>
        <el-form-item label="银行账号" prop="bankAccount">
          <el-input v-model="accountForm.bankAccount" placeholder="银行卡号" maxlength="30" />
        </el-form-item>
        <el-form-item label="账户名" prop="accountName">
          <el-input v-model="accountForm.accountName" placeholder="银行卡持卡人姓名" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="accountSubmitLoading" @click="handleAccountSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 申请提现弹窗 -->
    <el-dialog v-model="withdrawDialogVisible" title="申请提现" width="460px" destroy-on-close>
      <div class="withdraw-preview" v-if="accountInfo">
        <div class="preview-row">
          <span class="preview-label">可用余额：</span>
          <span class="amount-highlight">¥ {{ formatMoney(accountInfo.balance) }}</span>
        </div>
        <div class="preview-row">
          <span class="preview-label">收款银行：</span>
          <span>{{ accountInfo.bankName }}（{{ accountInfo.bankAccount }}）</span>
        </div>
        <div class="preview-row">
          <span class="preview-label">账户名：</span>
          <span>{{ accountInfo.accountName }}</span>
        </div>
      </div>
      <el-form ref="withdrawFormRef" :model="withdrawForm" :rules="withdrawFormRules" label-width="90px">
        <el-form-item label="提现金额" prop="amount">
          <el-input-number
            v-model="withdrawForm.amount"
            :min="1"
            :max="accountInfo?.balance || 0"
            :precision="2"
            :step="100"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="withdrawSubmitLoading" @click="handleWithdrawSubmit">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 结算管理页面
 * 商家管理自己的结算账户、查看结算流水、申请提现
 *
 * 页面结构：
 * 1. 顶部账户信息卡片：显示银行卡信息和可用余额、冻结金额
 * 2. Tab 切换：结算流水（每笔订单的结算记录）/ 提现申请（提现历史）
 * 3. 配置/修改结算账户弹窗
 * 4. 申请提现弹窗（金额从可用余额转入冻结金额，等待管理员审核）
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getSettlementAccount,
  addSettlementAccount,
  updateSettlementAccount,
  getSettlementRecords,
  applyWithdraw,
  getWithdrawList,
} from '@shop/shared'
import type {
  SettlementAccountInfo,
  SettlementAccountParams,
  SettlementRecordInfo,
  WithdrawOrderInfo,
  SettlementStatus,
  WithdrawStatus,
} from '@shop/shared'

// ==================== 结算账户 ====================

/** 结算账户信息（null表示尚未配置） */
const accountInfo = ref<SettlementAccountInfo | null>(null)
/** 账户信息加载中 */
const accountLoading = ref(false)

/** 配置/修改账户弹窗显示状态 */
const accountDialogVisible = ref(false)
/** 账户表单提交中 */
const accountSubmitLoading = ref(false)
/** 账户表单引用 */
const accountFormRef = ref<FormInstance>()

/** 账户表单数据 */
const accountForm = reactive<SettlementAccountParams>({
  bankName: '',
  bankAccount: '',
  accountName: '',
})

/** 账户表单校验规则 */
const accountFormRules: FormRules = {
  bankName: [{ required: true, message: '请输入银行名称', trigger: 'blur' }],
  bankAccount: [{ required: true, message: '请输入银行账号', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入账户名', trigger: 'blur' }],
}

// ==================== Tab 切换 ====================

/** 当前激活的 Tab：records-结算流水 withdraws-提现申请 */
const activeTab = ref<'records' | 'withdraws'>('records')

// ==================== 结算流水 ====================

/** 结算流水查询参数 */
const recordQuery = reactive({
  status: undefined as SettlementStatus | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 结算流水列表 */
const recordList = ref<SettlementRecordInfo[]>([])
/** 结算流水总数 */
const recordTotal = ref(0)
/** 结算流水加载中 */
const recordLoading = ref(false)

// ==================== 提现申请 ====================

/** 提现申请查询参数 */
const withdrawQuery = reactive({
  status: undefined as WithdrawStatus | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 提现申请列表 */
const withdrawList = ref<WithdrawOrderInfo[]>([])
/** 提现申请总数 */
const withdrawTotal = ref(0)
/** 提现申请加载中 */
const withdrawLoading = ref(false)

/** 提现弹窗显示状态 */
const withdrawDialogVisible = ref(false)
/** 提现提交中 */
const withdrawSubmitLoading = ref(false)
/** 提现表单引用 */
const withdrawFormRef = ref<FormInstance>()

/** 提现表单数据 */
const withdrawForm = reactive({
  amount: 1,
})

/** 提现表单校验规则 */
const withdrawFormRules: FormRules = {
  amount: [
    { required: true, message: '请输入提现金额', trigger: 'blur' },
    {
      validator: (_rule, value: number, callback) => {
        if (value < 1) {
          callback(new Error('提现金额必须大于1元'))
        } else if (accountInfo.value && value > accountInfo.value.balance) {
          callback(new Error('提现金额不能超过可用余额'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// ==================== 数据加载方法 ====================

/**
 * 加载结算账户信息
 * 页面初始化和提现成功后调用，刷新余额
 */
async function loadAccount() {
  accountLoading.value = true
  try {
    const res = await getSettlementAccount()
    accountInfo.value = res.data.data || null
  } catch (error: any) {
    ElMessage.error(error.message || '加载结算账户失败')
  } finally {
    accountLoading.value = false
  }
}

/**
 * 加载结算流水列表
 */
async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await getSettlementRecords(recordQuery)
    recordList.value = res.data.data.records || []
    recordTotal.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载结算流水失败')
  } finally {
    recordLoading.value = false
  }
}

/**
 * 加载提现申请列表
 */
async function loadWithdraws() {
  withdrawLoading.value = true
  try {
    const res = await getWithdrawList(withdrawQuery)
    withdrawList.value = res.data.data.records || []
    withdrawTotal.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载提现申请失败')
  } finally {
    withdrawLoading.value = false
  }
}

// ==================== 事件处理方法 ====================

/**
 * Tab 切换时加载数据
 * 切换到结算流水/提现申请时，如果列表为空则重新加载
 */
function handleTabChange(name: string | number) {
  if (name === 'records' && recordList.value.length === 0) {
    loadRecords()
  } else if (name === 'withdraws' && withdrawList.value.length === 0) {
    loadWithdraws()
  }
}

/**
 * 打开配置/修改结算账户弹窗
 * 修改时回填已有信息
 */
function handleEditAccount() {
  if (accountInfo.value) {
    // 修改：回填信息（银行账号是脱敏的，提示用户重新输入）
    accountForm.bankName = accountInfo.value.bankName
    accountForm.bankAccount = ''
    accountForm.accountName = accountInfo.value.accountName
    ElMessage.info('银行账号已脱敏显示，修改时请重新输入完整卡号')
  } else {
    // 新增：清空表单
    accountForm.bankName = ''
    accountForm.bankAccount = ''
    accountForm.accountName = ''
  }
  accountDialogVisible.value = true
}

/**
 * 提交结算账户表单
 * 已有账户走更新接口，没有账户走添加接口
 */
async function handleAccountSubmit() {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid) return

  accountSubmitLoading.value = true
  try {
    if (accountInfo.value) {
      await updateSettlementAccount(accountForm)
      ElMessage.success('结算账户已更新')
    } else {
      await addSettlementAccount(accountForm)
      ElMessage.success('结算账户已配置')
    }
    accountDialogVisible.value = false
    loadAccount()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    accountSubmitLoading.value = false
  }
}

/** 结算流水搜索：重置到第1页 */
function handleSearchRecords() {
  recordQuery.pageNum = 1
  loadRecords()
}

/** 结算流水重置：清空筛选条件 */
function handleResetRecords() {
  recordQuery.status = undefined
  recordQuery.pageNum = 1
  loadRecords()
}

/** 提现申请搜索：重置到第1页 */
function handleSearchWithdraws() {
  withdrawQuery.pageNum = 1
  loadWithdraws()
}

/** 提现申请重置：清空筛选条件 */
function handleResetWithdraws() {
  withdrawQuery.status = undefined
  withdrawQuery.pageNum = 1
  loadWithdraws()
}

/**
 * 打开申请提现弹窗
 * 重置提现金额为最小值1
 */
function handleOpenWithdraw() {
  withdrawForm.amount = 1
  withdrawDialogVisible.value = true
}

/**
 * 提交提现申请
 * 校验表单后调用接口，成功后刷新账户余额和提现列表
 */
async function handleWithdrawSubmit() {
  const valid = await withdrawFormRef.value?.validate().catch(() => false)
  if (!valid) return

  withdrawSubmitLoading.value = true
  try {
    await applyWithdraw({ amount: withdrawForm.amount })
    ElMessage.success('提现申请已提交，等待平台审核')
    withdrawDialogVisible.value = false
    // 刷新账户余额和提现列表
    loadAccount()
    if (activeTab.value === 'withdraws') {
      loadWithdraws()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '提现失败')
  } finally {
    withdrawSubmitLoading.value = false
  }
}

// ==================== 工具方法 ====================

/**
 * 金额格式化：保留2位小数
 * 后端返回的是 number 类型（元），直接 toFixed(2)
 */
function formatMoney(value: number | undefined | null): string {
  if (value === undefined || value === null) return '0.00'
  return Number(value).toFixed(2)
}

/**
 * 抽成比例格式化：0.05 → 5%
 */
function formatRate(value: number | undefined | null): string {
  if (value === undefined || value === null) return '-'
  return `${(Number(value) * 100).toFixed(2)}%`
}

/**
 * 结算状态对应的标签颜色
 * 0-待结算（warning）1-已结算（success）2-已退款（danger）
 */
function getRecordStatusType(status: number): 'warning' | 'success' | 'danger' | 'info' {
  switch (status) {
    case 0:
      return 'warning'
    case 1:
      return 'success'
    case 2:
      return 'danger'
    default:
      return 'info'
  }
}

/**
 * 提现状态对应的标签颜色
 * 0-待审核（warning）1-已通过（primary）2-已拒绝（danger）3-已打款（success）
 */
function getWithdrawStatusType(status: number): 'warning' | 'primary' | 'danger' | 'success' | 'info' {
  switch (status) {
    case 0:
      return 'warning'
    case 1:
      return 'primary'
    case 2:
      return 'danger'
    case 3:
      return 'success'
    default:
      return 'info'
  }
}

// ==================== 初始化 ====================

onMounted(() => {
  // 页面加载时同时获取账户信息和结算流水
  loadAccount()
  loadRecords()
})
</script>

<style scoped>
.settlement-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 账户信息卡片 */
.account-card :deep(.el-card__body) {
  padding: 20px;
}

.account-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.account-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

.account-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.account-info {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.info-item {
  font-size: 14px;
  line-height: 1.5;
}

.info-label {
  color: #909399;
  margin-right: 4px;
}

/* 余额信息区 */
.balance-info {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 24px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f4ff 100%);
  border-radius: 4px;
}

.balance-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.balance-label {
  font-size: 13px;
  color: #909399;
}

.balance-value {
  font-size: 24px;
  font-weight: 600;
}

.balance-value.primary {
  color: var(--color-primary);
}

.balance-value.warning {
  color: var(--el-color-warning);
}

.balance-divider {
  width: 1px;
  height: 40px;
  background: #dcdfe6;
}

/* Tab 工具栏 */
.tab-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 金额高亮 */
.amount-highlight {
  color: var(--color-primary);
  font-weight: 600;
}

/* 提现弹窗中的预览区 */
.withdraw-preview {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 16px;
}

.preview-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.5;
}

.preview-row:last-child {
  margin-bottom: 0;
}

.preview-label {
  color: #909399;
  min-width: 80px;
  flex-shrink: 0;
}
</style>
