/**
 * 路由配置
 * 定义所有页面的URL路径和对应的组件
 * 还包含路由守卫，控制哪些页面需要登录才能访问
 */

import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated, cancelAllRequests } from '@shop/shared'

/**
 * 不需要登录就能访问的页面白名单
 * 在这里配置路由名称，这些页面即使未登录也能访问
 * 方便后续新增页面时快速配置
 */
const WHITE_LIST: string[] = ['Home', 'Category', 'ProductDetail', 'Search', 'Login']

/** 路由规则定义 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/home/IndexView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/category/IndexView.vue'),
        meta: { title: '分类' },
      },
      {
        path: 'product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/product/DetailView.vue'),
        meta: { title: '商品详情' },
      },
      // 秒杀列表页：所有用户均可浏览进行中的秒杀活动
      {
        path: 'seckill',
        name: 'SeckillList',
        component: () => import('@/views/seckill/ListView.vue'),
        meta: { title: '限时秒杀' },
      },
      // 秒杀详情页：浏览不需要登录，点击抢购时才检查登录
      {
        path: 'seckill/:id',
        name: 'SeckillDetail',
        component: () => import('@/views/seckill/DetailView.vue'),
        meta: { title: '秒杀详情' },
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/search/IndexView.vue'),
        meta: { title: '搜索' },
      },
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/cart/IndexView.vue'),
        meta: { title: '购物车', requiresAuth: true },
      },
      {
        path: 'order',
        children: [
          {
            path: 'confirm',
            name: 'OrderConfirm',
            component: () => import('@/views/order/ConfirmView.vue'),
            meta: { title: '确认订单', requiresAuth: true },
          },
          {
            path: 'list',
            name: 'OrderList',
            component: () => import('@/views/order/ListView.vue'),
            meta: { title: '我的订单', requiresAuth: true },
          },
          {
            path: ':id',
            name: 'OrderDetail',
            component: () => import('@/views/order/DetailView.vue'),
            meta: { title: '订单详情', requiresAuth: true },
          },
        ],
      },
      {
        path: 'user',
        children: [
          {
            path: 'center',
            name: 'UserCenter',
            component: () => import('@/views/user/CenterView.vue'),
            meta: { title: '个人中心', requiresAuth: true },
          },
        ],
      },
      {
        path: 'payment/pay',
        name: 'PaymentPay',
        component: () => import('@/views/payment/PayView.vue'),
        meta: { title: '收银台', requiresAuth: true },
      },
      {
        path: 'payment/result',
        name: 'PaymentResult',
        component: () => import('@/views/payment/ResultView.vue'),
        meta: { title: '支付结果', requiresAuth: true },
      },
      {
        path: 'notification',
        name: 'Notification',
        component: () => import('@/views/notification/IndexView.vue'),
        meta: { title: '消息通知', requiresAuth: true },
      },
      {
        path: 'coupon',
        name: 'MyCoupon',
        component: () => import('@/views/coupon/MyCouponView.vue'),
        meta: { title: '我的优惠券', requiresAuth: true },
      },
      {
        path: 'coupon/receive',
        name: 'CouponReceive',
        component: () => import('@/views/coupon/ReceiveView.vue'),
        meta: { title: '领券中心', requiresAuth: true },
      },
      // 我的收藏页：展示用户收藏的商品列表
      {
        path: 'favorite',
        name: 'FavoriteList',
        component: () => import('@/views/favorite/ListView.vue'),
        meta: { title: '我的收藏', requiresAuth: true },
      },
      // 浏览足迹页：展示用户最近浏览过的商品
      {
        path: 'footprint',
        name: 'FootprintList',
        component: () => import('@/views/footprint/ListView.vue'),
        meta: { title: '浏览足迹', requiresAuth: true },
      },
      // 发表评价页：用户对已完成订单中的商品进行评价
      {
        path: 'review/create',
        name: 'ReviewCreate',
        component: () => import('@/views/review/CreateView.vue'),
        meta: { title: '发表评价', requiresAuth: true },
      },
      // 追加评价页：用户对已发表过的评价进行追加评论
      {
        path: 'review/append',
        name: 'ReviewAppend',
        component: () => import('@/views/review/AppendView.vue'),
        meta: { title: '追加评价', requiresAuth: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/user/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    redirect: { path: '/login', query: { tab: 'register' } },
  },
]

/** 创建路由实例 */
const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局路由守卫
 * 在每次跳转前检查：
 * 1. 需要登录的页面，如果没登录就弹出登录弹窗（登录成功后自动跳转）
 * 2. 已登录的用户访问登录页，自动跳到首页
 * 3. 页面切换时取消上一个页面未完成的请求
 */
router.beforeEach(async (to, _from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || ''} - SHOPMALL`

  // 页面切换时，取消上一个页面未完成的请求，避免数据错乱
  cancelAllRequests()

  const loggedIn = isAuthenticated()

  // 需要登录但未登录 → 弹出登录弹窗，取消当前导航
  if (to.meta.requiresAuth && !loggedIn) {
    // 动态导入store，避免循环依赖
    const { useAuthModalStore } = await import('@/stores/authModal')
    const authModalStore = useAuthModalStore()
    authModalStore.openAuthModal({
      description: '登录后访问该页面',
      execute: () => {
        router.push(to.fullPath)
      },
    })
    next(false)  // 取消当前导航，留在原页面
    return
  }

  // 已登录访问登录页 → 跳转首页
  if (loggedIn && to.name === 'Login') {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router
