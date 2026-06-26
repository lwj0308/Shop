<template>
  <!-- 支付结果页 - 展示支付成功/失败状态 -->
  <div class="payment-result">
    <div class="result-card">
      <!-- 加载中 -->
      <div v-if="loading" class="result-loading">
        <el-skeleton :rows="4" animated />
      </div>

      <!-- 支付结果 -->
      <template v-else>
        <!-- 支付成功 -->
        <div v-if="paymentSuccess" class="result-content success">
          <div class="result-icon success-icon">✓</div>
          <h2 class="result-title">支付成功</h2>
          <p class="result-desc">您的订单已支付成功，商家将尽快为您发货</p>
          <div v-if="orderNo" class="order-no">
            订单编号：<span class="order-no-value">{{ orderNo }}</span>
          </div>
        </div>

        <!-- 支付失败/处理中 -->
        <div v-else class="result-content fail">
          <div class="result-icon fail-icon">!</div>
          <h2 class="result-title">{{ resultTitle }}</h2>
          <p class="result-desc">{{ resultMessage }}</p>
          <div v-if="orderNo" class="order-no">
            订单编号：<span class="order-no-value">{{ orderNo }}</span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="result-actions">
          <button class="btn-primary" @click="goOrderList">查看订单</button>
          <button class="btn-secondary" @click="goHome">继续购物</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 支付结果页
 * 展示支付成功/失败状态，提供查看订单或继续购物入口
 *
 * 功能说明（小白版）：
 * 1. 从路由参数中获取订单编号（orderNo）
 * 2. 调用后端接口查询这个订单的支付结果
 * 3. 根据支付状态展示成功或失败的页面
 * 4. 提供"查看订单"和"继续购物"两个按钮，让用户继续操作
 */

import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPaymentByOrderNo } from '@shop/shared'
import { PAY_STATUS } from '@shop/shared'

const route = useRoute()
const router = useRouter()

/** 是否正在加载支付结果 */
const loading = ref(true)
/** 支付是否成功 */
const paymentSuccess = ref(false)
/** 结果标题（失败或处理中时显示） */
const resultTitle = ref('支付处理中')
/** 结果描述信息 */
const resultMessage = ref('正在确认支付结果，请稍候...')

/** 从路由参数获取订单编号 */
const orderNo = computed(() => (route.query.orderNo as string) || '')

/**
 * 页面加载时查询支付结果
 * 调用后端接口获取这个订单的支付状态
 */
onMounted(async () => {
  if (!orderNo.value) {
    resultTitle.value = '参数错误'
    resultMessage.value = '未获取到订单编号，请返回重新操作'
    loading.value = false
    return
  }

  try {
    // 调用后端接口查询支付信息（返回 PaymentInfo，包含 payStatus）
    const res = await getPaymentByOrderNo(orderNo.value)
    const payStatus = res.data.payStatus

    // payStatus=2 表示已支付成功
    paymentSuccess.value = payStatus === PAY_STATUS.PAID

    if (paymentSuccess.value) {
      // 支付成功
      resultTitle.value = '支付成功'
      resultMessage.value = '您的订单已支付成功，商家将尽快为您发货'
    } else if (payStatus === PAY_STATUS.WAIT || payStatus === PAY_STATUS.PAYING) {
      // 待支付或支付中
      resultTitle.value = '支付处理中'
      resultMessage.value = '支付尚未完成，请稍后查看订单状态'
    } else {
      // 已关闭、已失败等
      resultTitle.value = '支付未完成'
      resultMessage.value = '支付尚未完成，请稍后查看订单状态'
    }
  } catch (error) {
    // 接口调用失败，可能是订单还没支付完成
    resultTitle.value = '支付查询失败'
    resultMessage.value = error instanceof Error ? error.message : '查询支付结果失败，请稍后查看订单状态'
  } finally {
    loading.value = false
  }
})

/**
 * 跳转到订单列表页
 */
const goOrderList = () => {
  router.push({ name: 'OrderList' })
}

/**
 * 跳转到首页继续购物
 */
const goHome = () => {
  router.push({ name: 'Home' })
}
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.payment-result {
  max-width: 1280px;
  margin: 0 auto;
  padding: 80px 0;
}

/* 结果卡片 */
.result-card {
  max-width: 560px;
  margin: 0 auto;
  padding: 64px 48px;
  background: #fff;
  border: 1px solid var(--color-border);
  text-align: center;
}

/* 加载中 */
.result-loading {
  padding: 40px 0;
}

/* 结果内容 */
.result-content {
  margin-bottom: 40px;
}

/* 结果图标 */
.result-icon {
  width: 80px;
  height: 80px;
  line-height: 80px;
  border-radius: 50%;
  margin: 0 auto 24px;
  font-size: 40px;
  font-weight: 300;
}

/* 成功图标：绿色圆圈+对勾 */
.success-icon {
  background: var(--color-accent);
  color: #fff;
}

/* 失败图标：灰色圆圈+感叹号 */
.fail-icon {
  background: var(--color-text-muted);
  color: #fff;
}

/* 结果标题 */
.result-title {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-text);
  margin: 0 0 16px;
  letter-spacing: -0.01em;
}

/* 结果描述 */
.result-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0 0 24px;
  line-height: 1.6;
}

/* 订单编号 */
.order-no {
  font-size: 13px;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}

.order-no-value {
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

/* 操作按钮 */
.result-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* 主按钮：黑底白字 */
.btn-primary {
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  padding: 14px 40px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.btn-primary:hover {
  background: transparent;
  color: var(--color-primary);
}

/* 次按钮：透明边框 */
.btn-secondary {
  background: transparent;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  padding: 14px 40px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.btn-secondary:hover {
  border-color: var(--color-text);
  color: var(--color-text);
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .payment-result {
    padding: 40px 0;
  }

  .result-card {
    padding: 48px 24px;
  }

  .result-title {
    font-size: 22px;
  }

  .result-actions {
    flex-direction: column;
    gap: 12px;
  }

  .btn-primary,
  .btn-secondary {
    width: 100%;
  }
}
</style>
