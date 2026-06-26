/**
 * 路由配置 - 管理后台
 *
 * 当前阶段：使用静态路由注册所有页面，方便开发调试
 * 后续阶段：切换为后端动态路由，根据权限动态加载
 *
 * 路由结构：
 * - /login → 登录页（独立布局）
 * - / → AdminLayout（侧边栏+顶栏+内容区）
 *   - /dashboard → 仪表盘
 *   - /business/* → 业务管理
 *   - /content/* → 内容管理
 *   - /system/* → 系统管理
 *   - /log/* → 日志管理
 *   - /security/* → 安全审计
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/** AdminLayout 布局组件（懒加载） */
const AdminLayout = () => import('@/layouts/AdminLayout.vue')

/**
 * 静态路由配置
 * 所有已登录用户都能访问的路由（权限控制由 v-permission 指令和后端 @RequirePermission 实现）
 */
const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      // ========== 仪表盘 ==========
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' },
      },

      // ========== 消息通知 ==========
      {
        path: 'notification',
        name: 'AdminNotification',
        component: () => import('@/views/notification/IndexView.vue'),
        meta: { title: '消息通知', icon: 'Bell' },
      },

      // ========== 优惠券管理 ==========
      {
        path: 'coupon',
        name: 'AdminCoupon',
        component: () => import('@/views/coupon/IndexView.vue'),
        meta: { title: '优惠券管理', icon: 'Ticket' },
      },

      // ========== 满减活动管理 ==========
      {
        path: 'promotion',
        name: 'AdminPromotion',
        component: () => import('@/views/promotion/IndexView.vue'),
        meta: { title: '满减活动', icon: 'Discount' },
      },

      // ========== 秒杀活动管理 ==========
      {
        path: 'seckill',
        name: 'AdminSeckill',
        component: () => import('@/views/seckill/IndexView.vue'),
        meta: { title: '秒杀活动', icon: 'AlarmClock' },
      },

      // ========== 评价管理 ==========
      {
        path: 'comment',
        name: 'AdminComment',
        component: () => import('@/views/comment/IndexView.vue'),
        meta: { title: '评价管理', icon: 'ChatDotRound' },
      },

      // ========== 业务管理 ==========
      {
        path: 'business/user',
        name: 'BusinessUser',
        component: () => import('@/views/business/UserList.vue'),
        meta: { title: '用户管理', icon: 'User' },
      },
      {
        path: 'business/merchant',
        name: 'BusinessMerchant',
        component: () => import('@/views/business/MerchantList.vue'),
        meta: { title: '商家管理', icon: 'OfficeBuilding' },
      },
      {
        path: 'business/product',
        name: 'BusinessProduct',
        component: () => import('@/views/business/ProductList.vue'),
        meta: { title: '商品管理', icon: 'Goods' },
      },
      {
        path: 'business/category',
        name: 'BusinessCategory',
        component: () => import('@/views/business/CategoryList.vue'),
        meta: { title: '分类管理', icon: 'Goods' },
      },
      {
        path: 'business/brand',
        name: 'BusinessBrand',
        component: () => import('@/views/business/BrandList.vue'),
        meta: { title: '品牌管理', icon: 'Goods' },
      },
      {
        path: 'business/order',
        name: 'BusinessOrder',
        component: () => import('@/views/business/OrderList.vue'),
        meta: { title: '订单管理', icon: 'ShoppingCart' },
      },
      {
        path: 'business/refund',
        name: 'BusinessRefund',
        component: () => import('@/views/business/RefundList.vue'),
        meta: { title: '退款管理', icon: 'Tickets' },
      },
      {
        path: 'business/withdraw',
        name: 'BusinessWithdraw',
        component: () => import('@/views/business/WithdrawList.vue'),
        meta: { title: '提现审核', icon: 'Money' },
      },

      // ========== 内容管理 ==========
      {
        path: 'content/banner',
        name: 'ContentBanner',
        component: () => import('@/views/content/BannerList.vue'),
        meta: { title: 'Banner管理', icon: 'PictureFilled' },
      },
      {
        path: 'content/notice',
        name: 'ContentNotice',
        component: () => import('@/views/content/NoticeList.vue'),
        meta: { title: '公告管理', icon: 'Document' },
      },

      // ========== 系统管理 ==========
      {
        path: 'system/admin-user',
        name: 'SystemAdminUser',
        component: () => import('@/views/system/AdminUserList.vue'),
        meta: { title: '管理员管理', icon: 'User' },
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: '角色管理', icon: 'Lock' },
      },
      {
        path: 'system/permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/PermissionList.vue'),
        meta: { title: '权限管理', icon: 'Lock' },
      },
      {
        path: 'system/dept',
        name: 'SystemDept',
        component: () => import('@/views/system/DeptList.vue'),
        meta: { title: '部门管理', icon: 'OfficeBuilding' },
      },

      // ========== 日志管理 ==========
      {
        path: 'log/operation',
        name: 'LogOperation',
        component: () => import('@/views/log/OperationLogList.vue'),
        meta: { title: '操作日志', icon: 'Document' },
      },
      {
        path: 'log/login',
        name: 'LogLogin',
        component: () => import('@/views/log/LoginLogList.vue'),
        meta: { title: '登录日志', icon: 'Document' },
      },

      // ========== 安全审计 ==========
      {
        path: 'security/event',
        name: 'SecurityEvent',
        component: () => import('@/views/security/SecurityEventList.vue'),
        meta: { title: '安全事件', icon: 'Lock' },
      },
    ],
  },
  // 兜底路由：未匹配的路径跳转404
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
})

/**
 * 路由守卫 - 登录校验 + 动态路由加载
 *
 * 每次路由跳转前：
 * 1. 检查是否已登录（有无Token）
 * 2. 未登录 → 跳转登录页
 * 3. 已登录但未加载动态路由 → 从后端获取权限菜单并注册路由
 * 4. 已登录且已加载路由 → 放行
 */
router.beforeEach(async (to, _from, next) => {
  // 动态导入store，避免循环依赖
  const { useAdminStore } = await import('@/stores/admin')
  const adminStore = useAdminStore()

  const hasToken = adminStore.token

  if (hasToken) {
    if (to.path === '/login') {
      // 已登录还访问登录页，直接跳转首页
      next({ path: '/' })
    } else {
      // 检查是否已加载动态路由
      if (adminStore.dynamicRoutesLoaded) {
        next()
      } else {
        try {
          // 从后端获取权限菜单，动态注册路由
          await adminStore.loadDynamicRoutes()
          // 重新导航到目标页面（因为动态路由刚注册，需要重新匹配）
          next({ ...to, replace: true })
        } catch (error) {
          // 加载动态路由失败（如Token过期），清除登录状态，跳转登录页
          adminStore.logout()
          next(`/login?redirect=${to.path}`)
        }
      }
    }
  } else {
    // 未登录
    if (to.path === '/login') {
      next()
    } else {
      // 保存目标路径，登录后自动跳回
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
