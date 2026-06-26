/**
 * 商家后台路由配置
 * 定义所有页面的URL路径和对应的组件
 * 包含路由守卫：未登录跳转登录页，未入驻跳转入驻页，Token过期自动跳转
 */

import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@shop/shared'

/** 白名单路由：不需要登录就能访问的页面 */
const WHITE_LIST: string[] = ['MerchantLogin', 'MerchantApply']

/** 路由规则定义 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'MerchantLogin',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '商家登录' },
  },
  {
    path: '/apply',
    name: 'MerchantApply',
    component: () => import('@/views/apply/ApplyView.vue'),
    meta: { title: '商家入驻' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/IndexView.vue'),
        meta: { title: '工作台', requiresAuth: true },
      },
      {
        path: 'product',
        children: [
          {
            path: 'list',
            name: 'ProductList',
            component: () => import('@/views/product/ListView.vue'),
            meta: { title: '商品列表', requiresAuth: true, parentTitle: '商品管理' },
          },
          {
            path: 'edit/:id?',
            name: 'ProductEdit',
            component: () => import('@/views/product/EditView.vue'),
            meta: { title: '编辑商品', requiresAuth: true, parentTitle: '商品管理' },
          },
        ],
      },
      {
        path: 'order',
        children: [
          {
            path: 'list',
            name: 'MerchantOrderList',
            component: () => import('@/views/order/ListView.vue'),
            meta: { title: '订单列表', requiresAuth: true, parentTitle: '订单管理' },
          },
          {
            path: ':id',
            name: 'MerchantOrderDetail',
            component: () => import('@/views/order/DetailView.vue'),
            meta: { title: '订单详情', requiresAuth: true, parentTitle: '订单管理' },
          },
        ],
      },
      {
        path: 'comment',
        children: [
          {
            path: 'list',
            name: 'MerchantCommentList',
            component: () => import('@/views/comment/ListView.vue'),
            meta: { title: '评价管理', requiresAuth: true, parentTitle: '评价管理' },
          },
        ],
      },
      {
        path: 'data',
        name: 'DataCenter',
        component: () => import('@/views/data/DataView.vue'),
        meta: { title: '数据中心', requiresAuth: true },
      },
      {
        path: 'settlement',
        name: 'Settlement',
        component: () => import('@/views/settlement/IndexView.vue'),
        meta: { title: '结算管理', requiresAuth: true },
      },
      {
        path: 'shop/settings',
        name: 'ShopSettings',
        component: () => import('@/views/shop/SettingsView.vue'),
        meta: { title: '店铺设置', requiresAuth: true },
      },
      {
        path: 'notification',
        name: 'MerchantNotification',
        component: () => import('@/views/notification/IndexView.vue'),
        meta: { title: '消息通知', requiresAuth: true },
      },
      {
        path: 'coupon',
        name: 'CouponList',
        component: () => import('@/views/coupon/ListView.vue'),
        meta: { title: '优惠券管理', requiresAuth: true },
      },
      {
        path: 'promotion',
        name: 'MerchantPromotion',
        component: () => import('@/views/promotion/ListView.vue'),
        meta: { title: '满减活动', requiresAuth: true },
      },
      {
        path: 'seckill',
        name: 'MerchantSeckill',
        component: () => import('@/views/seckill/ListView.vue'),
        meta: { title: '秒杀活动', requiresAuth: true },
      },
    ],
  },
]

/** 创建路由实例 */
const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局路由守卫
 * 1. 白名单路由直接放行
 * 2. 未登录访问需要登录的页面 → 跳转登录页（带上原路径方便登录后跳回）
 * 3. 已登录访问登录页 → 跳转工作台
 * 4. 已登录但未入驻 → 跳转入驻页
 * 5. Token过期 → 自动跳转登录页
 */
router.beforeEach(async (to, _from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || ''} - ShopMall商家后台`

  // 白名单路由直接放行，不需要任何检查
  if (WHITE_LIST.includes(to.name as string)) {
    // 已登录访问登录页 → 跳转工作台
    if (isAuthenticated() && to.name === 'MerchantLogin') {
      next({ name: 'Dashboard' })
      return
    }
    next()
    return
  }

  // 非白名单路由，检查登录状态
  const loggedIn = isAuthenticated()

  // 未登录 → 跳转登录页，记录原路径方便登录后跳回
  if (!loggedIn) {
    next({ name: 'MerchantLogin', query: { redirect: to.fullPath } })
    return
  }

  // 已登录，检查商家入驻状态
  // 动态导入 store 避免循环依赖
  const { useMerchantStore } = await import('@/stores/merchant')
  const merchantStore = useMerchantStore()

  // 如果还没有商家信息，尝试从服务器获取
  if (!merchantStore.merchantInfo) {
    try {
      await merchantStore.fetchMerchantInfo()
    } catch (err) {
      // 获取失败说明Token可能已过期，清除并跳转登录页
      console.error('[路由守卫] fetchMerchantInfo 失败:', err)
      merchantStore.logout()
      next({ name: 'MerchantLogin', query: { redirect: to.fullPath } })
      return
    }
  }

  // 检查入驻状态：0-审核中 1-已通过 2-已拒绝
  const merchantStatus = merchantStore.merchantInfo?.status
  console.log('[路由守卫] to.path=', to.path, 'merchantStatus=', merchantStatus, 'merchantInfo=', merchantStore.merchantInfo)

  // 未入驻（审核中或已拒绝）且不在入驻页 → 跳转入驻页
  if (merchantStatus !== 1 && to.name !== 'MerchantApply') {
    console.log('[路由守卫] 未入驻，跳转到 /apply')
    next({ name: 'MerchantApply' })
    return
  }

  // 已入驻但访问入驻页 → 跳转工作台
  if (merchantStatus === 1 && to.name === 'MerchantApply') {
    next({ name: 'Dashboard' })
    return
  }

  console.log('[路由守卫] 放行')
  next()
})

export default router
