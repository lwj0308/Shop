<template>
  <!-- 收银台页面：选择支付方式并完成支付 -->
  <div class="pay-page">
    <div class="pay-card">
      <!-- 加载中 -->
      <div v-if="loading" class="pay-loading">
        <el-skeleton :rows="4" animated />
      </div>

      <template v-else>
        <!-- 订单信息 -->
        <div class="order-info">
          <h1 class="pay-title">收银台</h1>
          <div class="order-no">订单编号：{{ orderNo }}</div>
          <div class="pay-amount">
            <span class="amount-label">应付金额</span>
            <span class="amount-value">¥{{ displayAmount }}</span>
          </div>
        </div>

        <!-- 支付方式选择 -->
        <div class="pay-methods">
          <h2 class="section-title">选择支付方式</h2>
          <div class="method-list">
            <!-- 支付宝 -->
            <div
              class="method-item"
              :class="{ active: selectedPayType === 3 }"
              @click="selectedPayType = 3"
            >
              <div class="method-icon alipay-icon">
                <span class="icon-text">支</span>
              </div>
              <div class="method-info">
                <div class="method-name">支付宝</div>
                <div class="method-desc">推荐使用</div>
              </div>
              <div class="method-check">
                <span v-if="selectedPayType === 3" class="check-icon">✓</span>
              </div>
            </div>

            <!-- 微信支付 -->
            <div
              class="method-item"
              :class="{ active: selectedPayType === 2 }"
              @click="selectedPayType = 2"
            >
              <div class="method-icon wechat-icon">
                <span class="icon-text">微</span>
              </div>
              <div class="method-info">
                <div class="method-name">微信支付</div>
                <div class="method-desc">安全便捷</div>
              </div>
              <div class="method-check">
                <span v-if="selectedPayType === 2" class="check-icon">✓</span>
              </div>
            </div>

            <!-- 模拟支付（测试用） -->
            <div
              class="method-item"
              :class="{ active: selectedPayType === 1 }"
              @click="selectedPayType = 1"
            >
              <div class="method-icon mock-icon">
                <span class="icon-text">测</span>
              </div>
              <div class="method-info">
                <div class="method-name">模拟支付</div>
                <div class="method-desc">测试环境专用</div>
              </div>
              <div class="method-check">
                <span v-if="selectedPayType === 1" class="check-icon">✓</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 模拟支付弹窗（选择微信/支付宝时展示） -->
        <div v-if="showSimulateDialog" class="simulate-dialog-mask" @click="closeSimulateDialog">
          <div class="simulate-dialog" @click.stop>
            <div class="simulate-header">
              <div class="simulate-platform" :class="simulatePlatformClass">
                {{ selectedPayType === 3 ? '支付宝' : '微信支付' }}
              </div>
              <button class="simulate-close" @click="closeSimulateDialog">×</button>
            </div>
            <div class="simulate-body">
              <!-- 模拟二维码 -->
              <div class="simulate-qr">
                <div class="qr-placeholder">
                  <div class="qr-pattern"></div>
                  <div class="qr-text">模拟二维码</div>
                </div>
              </div>
              <div class="simulate-amount">¥{{ displayAmount }}</div>
              <div class="simulate-tip">请使用{{ selectedPayType === 3 ? '支付宝' : '微信' }}扫码支付</div>
              <div class="simulate-hint">（测试环境模拟支付，点击下方按钮完成支付）</div>
            </div>
            <div class="simulate-footer">
              <button class="btn-cancel" @click="closeSimulateDialog">取消</button>
              <button class="btn-confirm" :disabled="paying" @click="confirmSimulatePay">
                {{ paying ? '支付中...' : '我已支付' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 确认支付按钮 -->
        <div class="pay-action">
          <button class="btn-pay" :disabled="paying" @click="handlePay">
            {{ paying ? '处理中...' : '确认支付' }}
          </button>
        </div>

        <!-- 安全提示 -->
        <div class="pay-notice">
          <span class="notice-icon">🔒</span>
          <span>支付环境安全加密，请放心支付</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 收银台页面（支付方式选择）
 *
 * 功能说明（小白版）：
 * 1. 从路由参数获取订单号（orderNo）
 * 2. 调用后端接口获取订单详情，拿到支付金额
 * 3. 展示三种支付方式：支付宝、微信支付、模拟支付
 * 4. 用户选择支付方式后点击"确认支付"
 * 5. 调用 createPayment 创建支付记录，再调用 mockPay 模拟支付成功
 * 6. 支付成功后跳转到支付结果页
 *
 * 注意：当前是 MVP 阶段，支付宝和微信支付都是模拟的，
 *       点击后会弹出一个模拟二维码弹窗，用户点击"我已支付"即完成支付。
 *       后续接入真实支付时，只需替换 mockPay 为真实的支付回调即可。
 */

import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderDetail, createPayment, mockPay } from '@shop/shared'
import { PAY_METHOD } from '@shop/shared'
import { formatPriceWithSymbol } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 是否正在加载订单信息 */
const loading = ref(true)
/** 是否正在支付 */
const paying = ref(false)
/** 订单金额（分） */
const orderAmount = ref(0)
/** 选中的支付方式：1-模拟 2-微信 3-支付宝 */
const selectedPayType = ref<number>(PAY_METHOD.ALIPAY)
/** 是否显示模拟支付弹窗 */
const showSimulateDialog = ref(false)

/** 从路由参数获取订单号 */
const orderNo = computed(() => (route.query.orderNo as string) || '')

/** 展示用金额（分转元，保留2位小数） */
const displayAmount = computed(() => (orderAmount.value / 100).toFixed(2))

/** 模拟弹窗的平台样式class */
const simulatePlatformClass = computed(() => ({
  'platform-alipay': selectedPayType.value === PAY_METHOD.ALIPAY,
  'platform-wechat': selectedPayType.value === PAY_METHOD.WECHAT,
}))

/**
 * 页面加载时获取订单详情
 */
onMounted(async () => {
  if (!orderNo.value) {
    ElMessage.error('未获取到订单编号')
    router.back()
    return
  }

  try {
    const res = await getOrderDetail(orderNo.value)
    orderAmount.value = res.data.payAmount || 0
  } catch (error) {
    const msg = error instanceof Error ? error.message : '获取订单信息失败'
    ElMessage.error(msg)
    router.back()
  } finally {
    loading.value = false
  }
})

/**
 * 点击"确认支付"按钮
 * 根据选择的支付方式处理：
 * - 模拟支付：直接创建支付记录并完成支付
 * - 支付宝/微信：弹出模拟二维码弹窗
 */
const handlePay = () => {
  if (selectedPayType.value === PAY_METHOD.MOCK) {
    // 模拟支付：直接走支付流程
    doPay()
  } else {
    // 支付宝/微信：弹出模拟二维码弹窗
    showSimulateDialog.value = true
  }
}

/**
 * 模拟弹窗中点击"我已支付"
 */
const confirmSimulatePay = () => {
  doPay()
}

/**
 * 关闭模拟支付弹窗
 */
const closeSimulateDialog = () => {
  if (!paying.value) {
    showSimulateDialog.value = false
  }
}

/**
 * 执行支付流程
 * 1. 调用 createPayment 创建支付记录（传入订单号、金额、支付方式）
 * 2. 调用 mockPay 模拟支付成功
 * 3. 跳转到支付结果页
 */
const doPay = async () => {
  paying.value = true
  try {
    // 第一步：创建支付记录
    // 注意：订单 payAmount 单位是"分"，支付 amount 单位是"元"，需要除以100
    const createRes = await createPayment({
      orderNo: orderNo.value,
      amount: orderAmount.value / 100,
      payType: selectedPayType.value as 1 | 2 | 3,
    })

    // 第二步：模拟支付成功（直接把状态改为已支付）
    const paymentId = createRes.data.id
    await mockPay(paymentId)

    // 第三步：跳转到支付结果页
    ElMessage.success('支付成功')
    router.replace({ name: 'PaymentResult', query: { orderNo: orderNo.value } })
  } catch (error) {
    const msg = error instanceof Error ? error.message : '支付失败，请重试'
    ElMessage.error(msg)
    showSimulateDialog.value = false
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.pay-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 60px 20px;
}

/* 支付卡片 */
.pay-card {
  background: #fff;
  border: 1px solid var(--color-border);
  padding: 48px 40px;
}

/* 加载中 */
.pay-loading {
  padding: 40px 0;
}

/* ==================== 订单信息 ==================== */
.order-info {
  text-align: center;
  margin-bottom: 40px;
  padding-bottom: 32px;
  border-bottom: 1px solid var(--color-border);
}

.pay-title {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 16px;
  letter-spacing: -0.01em;
}

.order-no {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 20px;
  letter-spacing: 0.02em;
}

.pay-amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.amount-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.amount-value {
  font-family: var(--font-heading);
  font-size: 40px;
  font-weight: 600;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

/* ==================== 支付方式选择 ==================== */
.pay-methods {
  margin-bottom: 40px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin: 0 0 20px;
}

.method-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.method-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border: 2px solid var(--color-border);
  cursor: pointer;
  transition: all var(--transition-base);
}

.method-item:hover {
  border-color: var(--color-text-secondary);
}

.method-item.active {
  border-color: var(--color-primary);
}

/* 支付方式图标 */
.method-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-text {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}

.alipay-icon {
  background: #1677ff;
}

.wechat-icon {
  background: #07c160;
}

.mock-icon {
  background: #909399;
}

/* 支付方式信息 */
.method-info {
  flex: 1;
}

.method-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text);
  margin-bottom: 2px;
}

.method-desc {
  font-size: 12px;
  color: var(--color-text-muted);
}

/* 选中标记 */
.method-check {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-icon {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  background: var(--color-primary);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
}

/* ==================== 确认支付按钮 ==================== */
.pay-action {
  margin-bottom: 24px;
}

.btn-pay {
  width: 100%;
  background: var(--color-primary);
  color: #fff;
  border: none;
  padding: 16px;
  font-size: 15px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.05em;
}

.btn-pay:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-pay:disabled {
  background: var(--color-text-muted);
  cursor: not-allowed;
}

/* 安全提示 */
.pay-notice {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* ==================== 模拟支付弹窗 ==================== */
.simulate-dialog-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.simulate-dialog {
  background: #fff;
  width: 360px;
  border-radius: 12px;
  overflow: hidden;
}

.simulate-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  color: #fff;
}

.platform-alipay {
  background: #1677ff;
}

.platform-wechat {
  background: #07c160;
}

.simulate-platform {
  font-size: 16px;
  font-weight: 500;
  padding: 4px 16px;
  border-radius: 4px;
}

.simulate-close {
  background: rgba(255, 255, 255, 0.3);
  border: none;
  color: #fff;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.simulate-body {
  padding: 32px 24px;
  text-align: center;
}

/* 模拟二维码 */
.simulate-qr {
  margin-bottom: 20px;
}

.qr-placeholder {
  width: 180px;
  height: 180px;
  margin: 0 auto;
  border: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.qr-pattern {
  width: 120px;
  height: 120px;
  background: repeating-conic-gradient(#333 0% 25%, #fff 0% 50%) 50% / 20px 20px;
}

.qr-text {
  font-size: 12px;
  color: var(--color-text-muted);
}

.simulate-amount {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
  font-variant-numeric: tabular-nums;
}

.simulate-tip {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.simulate-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}

.simulate-footer {
  display: flex;
  gap: 12px;
  padding: 0 24px 24px;
}

.btn-cancel {
  flex: 1;
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  padding: 12px;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
}

.btn-confirm {
  flex: 1;
  background: var(--color-primary);
  border: none;
  color: #fff;
  padding: 12px;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
}

.btn-confirm:disabled {
  background: var(--color-text-muted);
  cursor: not-allowed;
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .pay-page {
    padding: 30px 16px;
  }

  .pay-card {
    padding: 32px 24px;
  }

  .amount-value {
    font-size: 32px;
  }

  .simulate-dialog {
    width: 90%;
    max-width: 360px;
  }
}
</style>
