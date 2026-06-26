<template>
  <!-- 左侧侧边栏 -->
  <div class="sidebar-wrapper">
    <!-- Logo区域 -->
    <div class="sidebar-logo" :class="{ collapse: collapse }">
      <h1 v-if="!collapse">ShopMall</h1>
      <h1 v-else>S</h1>
    </div>

    <!-- 导航菜单 -->
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapse"
        :collapse-transition="false"
        background-color="var(--color-sidebar-bg)"
        text-color="var(--color-sidebar-text)"
        active-text-color="var(--color-sidebar-active)"
        router
      >
        <!-- 遍历菜单列表渲染菜单项 -->
        <template v-for="item in menuList" :key="item.path">
          <!-- 有子菜单 -->
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单 -->
          <el-menu-item v-else :index="item.path">
            <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
/**
 * 侧边栏导航菜单
 *
 * 显示后台的功能菜单，支持折叠/展开。
 * 菜单数据目前是静态的，后续会改为从后端动态获取。
 */

import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Odometer,
  User,
  OfficeBuilding,
  Goods,
  ShoppingCart,
  Tickets,
  PictureFilled,
  Setting,
  Lock,
  Document,
  Money,
  Bell,
  Ticket,
  Discount,
  AlarmClock,
  ChatDotRound,
} from '@element-plus/icons-vue'

defineProps<{
  /** 是否折叠 */
  collapse: boolean
}>()

const route = useRoute()

/** 当前激活的菜单项（根据当前路由路径自动高亮） */
const activeMenu = computed(() => route.path)

/** 菜单列表（后续改为从后端动态获取） */
const menuList = [
  {
    title: '仪表盘',
    path: '/dashboard',
    icon: Odometer,
  },
  {
    title: '消息通知',
    path: '/notification',
    icon: Bell,
  },
  {
    title: '优惠券管理',
    path: '/coupon',
    icon: Ticket,
  },
  {
    title: '满减活动',
    path: '/promotion',
    icon: Discount,
  },
  {
    title: '秒杀活动',
    path: '/seckill',
    icon: AlarmClock,
  },
  {
    title: '评价管理',
    path: '/comment',
    icon: ChatDotRound,
  },
  {
    title: '业务管理',
    path: '/business',
    icon: Goods,
    children: [
      { title: '用户管理', path: '/business/user', icon: User },
      { title: '商家管理', path: '/business/merchant', icon: OfficeBuilding },
      { title: '商品管理', path: '/business/product', icon: Goods },
      { title: '分类管理', path: '/business/category', icon: Goods },
      { title: '品牌管理', path: '/business/brand', icon: Goods },
      { title: '订单管理', path: '/business/order', icon: ShoppingCart },
      { title: '退款管理', path: '/business/refund', icon: Tickets },
      { title: '提现审核', path: '/business/withdraw', icon: Money },
    ],
  },
  {
    title: '内容管理',
    path: '/content',
    icon: PictureFilled,
    children: [
      { title: 'Banner管理', path: '/content/banner', icon: PictureFilled },
      { title: '公告管理', path: '/content/notice', icon: Document },
    ],
  },
  {
    title: '系统管理',
    path: '/system',
    icon: Setting,
    children: [
      { title: '管理员管理', path: '/system/admin-user', icon: User },
      { title: '角色管理', path: '/system/role', icon: Lock },
      { title: '权限管理', path: '/system/permission', icon: Lock },
      { title: '部门管理', path: '/system/dept', icon: OfficeBuilding },
    ],
  },
  {
    title: '日志管理',
    path: '/log',
    icon: Document,
    children: [
      { title: '操作日志', path: '/log/operation', icon: Document },
      { title: '登录日志', path: '/log/login', icon: Document },
    ],
  },
  {
    title: '安全审计',
    path: '/security',
    icon: Lock,
    children: [
      { title: '安全事件', path: '/security/event', icon: Lock },
    ],
  },
]
</script>

<style scoped>
.sidebar-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-logo.collapse h1 {
  font-size: 24px;
}

/* 覆盖Element Plus菜单样式 */
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item.is-active) {
  background-color: var(--color-sidebar-active) !important;
  color: #fff !important;
}

:deep(.el-sub-menu .el-menu-item) {
  padding-left: 52px !important;
}
</style>
