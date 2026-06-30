<template>
  <!-- 左侧侧边栏：深色玻璃质感 + 渐变 Logo + 渐变激活指示条 -->
  <div class="sidebar-wrapper">
    <!-- Logo区域：渐变方块图标 + 渐变文字 -->
    <div class="sidebar-logo" :class="{ collapse: collapse }">
      <div class="logo-icon">S</div>
      <div v-if="!collapse" class="logo-text">
        <span class="gradient-text">ShopMall</span>
        <span class="sub">管理后台</span>
      </div>
    </div>

    <!-- 导航菜单：保留所有业务绑定（router / activeMenu / collapse / 菜单数据） -->
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapse"
        :collapse-transition="false"
        background-color="transparent"
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

/* Logo 区：上下居中，底部细分隔线 */
.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-logo.collapse {
  justify-content: center;
  padding: 0;
}

/* 渐变方块图标：白字 + 翡翠青绿渐变背景 + 发光阴影 */
.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 18px;
  flex-shrink: 0;
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.4);
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.logo-text .sub {
  font-size: 11px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
  letter-spacing: 0.02em;
}

/* ===== 覆盖 Element Plus el-menu 在深色玻璃侧边栏中的样式 ===== */

/* 去掉菜单右侧默认边框 */
:deep(.el-menu) {
  border-right: none;
  padding: 12px 8px;
  background: transparent;
}

/* 菜单项：圆角 + 半透明 hover 态 + 平滑过渡 */
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  border-radius: var(--radius-button);
  margin: 2px 0;
  transition: all 0.25s var(--ease-out);
}

/* hover：翡翠渐变软背景 + 翡翠亮文字 */
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--gradient-brand-soft) !important;
  color: var(--color-sidebar-active) !important;
}

/* 激活态：渐变软背景 + 翡翠亮文字 + 左侧渐变指示条 */
:deep(.el-menu-item.is-active) {
  background: var(--gradient-brand-soft) !important;
  color: var(--color-sidebar-active) !important;
  font-weight: 600;
  position: relative;
}

/* 激活项左侧的翡翠青绿渐变指示条 */
:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 3px;
  background: var(--gradient-brand);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.6);
}

/* 子菜单项缩进保持 */
:deep(.el-sub-menu .el-menu-item) {
  padding-left: 52px !important;
}

/* 展开子菜单标题箭头颜色 */
:deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.5);
}

/* 暗色模式下侧边栏更深更沉浸 */
html.dark .sidebar-logo {
  border-bottom-color: rgba(51, 65, 85, 0.4);
}
</style>
