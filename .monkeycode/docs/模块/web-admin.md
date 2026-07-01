# web-admin 管理后台

面向平台管理员的综合管理后台，提供用户/商家/商品管理、RBAC 权限控制、营销活动管理、日志审计和安全事件监控等功能。

## 技术栈

- Vue 3.5 + Composition API + TypeScript
- Vue Router 4
- Pinia
- Element Plus 2.9
- ECharts (数据可视化)
- Vite 6
- @shop/shared

## 结构

```
packages/web-admin/src/
├── main.ts                   # 应用入口
├── App.vue                   # 根组件
├── router/
│   └── index.ts              # 路由配置（23 个路由 + 动态路由 + 权限守卫）
├── stores/
│   └── admin.ts              # 管理员状态（Token、权限列表、动态路由）
├── views/                    # 页面组件
│   ├── login/
│   │   └── index.vue         # 登录页
│   ├── dashboard/
│   │   └── index.vue         # 仪表盘（含 ECharts 图表）
│   ├── notification/
│   │   └── IndexView.vue     # 消息通知
│   ├── coupon/
│   │   └── IndexView.vue     # 优惠券管理
│   ├── promotion/
│   │   └── IndexView.vue     # 满减活动管理
│   ├── seckill/
│   │   └── IndexView.vue     # 秒杀活动管理
│   ├── comment/
│   │   └── IndexView.vue     # 评价管理
│   ├── business/             # 业务管理
│   │   ├── UserList.vue      # 用户管理
│   │   ├── MerchantList.vue  # 商家管理
│   │   ├── ProductList.vue   # 商品管理
│   │   ├── CategoryList.vue  # 分类管理
│   │   ├── BrandList.vue     # 品牌管理
│   │   ├── OrderList.vue     # 订单管理
│   │   ├── RefundList.vue   # 退款管理
│   │   └── WithdrawList.vue # 提现审核
│   ├── content/              # 内容管理
│   │   ├── BannerList.vue    # Banner 管理
│   │   └── NoticeList.vue    # 公告管理
│   ├── system/               # 系统管理
│   │   ├── AdminUserList.vue # 管理员管理
│   │   ├── RoleList.vue      # 角色管理
│   │   ├── PermissionList.vue # 权限管理
│   │   └── DeptList.vue      # 部门管理
│   ├── log/                  # 日志审计
│   │   ├── OperationLogList.vue # 操作日志
│   │   └── LoginLogList.vue  # 登录日志
│   └── security/
│       └── SecurityEventList.vue # 安全事件
├── components/               # 可复用组件
├── layouts/                  # 布局组件
│   ├── AdminLayout.vue       # 经典后台布局
│   ├── Sidebar.vue           # 侧边栏菜单
│   ├── HeaderBar.vue         # 顶部导航栏
│   └── TagsView.vue          # 多标签页导航
├── directives/
│   └── permission.ts         # v-permission 权限指令
├── composables/
│   └── useTheme.ts           # 暗黑模式主题支持
├── styles/                   # 全局样式
└── assets/                   # 静态资源
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `router/index.ts` | 路由定义 + 登录守卫 + 动态路由加载 |
| `stores/admin.ts` | 管理员状态（Token、权限列表、菜单生成） |
| `directives/permission.ts` | `v-permission` 指令，按钮级别权限控制 |
| `layouts/AdminLayout.vue` | 经典后台布局：侧边栏 + 顶栏 + 标签页 + 内容区 |
| `layouts/Sidebar.vue` | 左侧可折叠菜单，8 个一级菜单组 |
| `layouts/HeaderBar.vue` | 顶部栏：折叠按钮、全局搜索、全屏切换、用户下拉 |
| `composables/useTheme.ts` | Element Plus 暗黑模式切换 |

## 依赖

**本模块依赖**:
- `@shop/shared` — 共享 API、类型、组合式函数、工具
- `vue-router` — 路由
- `pinia` — 状态管理
- `element-plus` — UI 组件库
- `echarts` — 数据可视化（仪表盘图表）

**依赖本模块的**:
- 无（终端应用，不被其他前端模块依赖）

## 布局说明

`AdminLayout.vue` 使用经典后台管理布局：

```
┌─────────────────────────────────────┐
│  HeaderBar (顶栏)                      │
│  折叠 | 全局搜索 | 全屏 | 用户头像       │
├──────┬──────────────────────────────┤
│      │  TagsView (标签页导航)           │
│ 侧边栏 │──────────────────────────────┤
│ 菜单  │                               │
│      │      内容区 (router-view)        │
│ 8 个  │                               │
│ 菜单组 │                               │
│      │                               │
├──────┴──────────────────────────────┤
```

**侧边栏菜单结构** (8 个一级菜单组):
1. 仪表盘
2. 消息通知
3. 营销活动（优惠券/满减/秒杀）
4. 业务管理（用户/商家/商品/分类/品牌/订单/退款/提现）
5. 评价管理
6. 内容管理（Banner/公告）
7. 系统管理（管理员/角色/权限/部门）
8. 日志审计（操作日志/登录日志/安全事件）

## 权限系统

### 按钮级权限控制

通过 `v-permission` 指令实现：

```vue
<template>
  <!-- 有指定权限才渲染按钮 -->
  <el-button v-permission="['user:create']">新增用户</el-button>
  <el-button v-permission="['user:create', 'user:edit']">编辑用户</el-button>
</template>
```

### 权限管理流程

1. 管理员登录后从后端获取权限列表
2. `stores/admin.ts` 存储权限列表
3. `directives/permission.ts` 在元素挂载时校验权限
4. 无权限的元素从 DOM 中移除

## 主题支持

`composables/useTheme.ts` 提供 Element Plus 暗黑模式切换功能。通过 `html` 元素的 `dark` 类名控制全局主题。

## 规范

### 页面组织
- 按功能域分视图目录（`business/`, `system/`, `log/`, `content/`, `security/`）
- 每个列表页以 `List.vue` 后缀命名

### 状态管理
- 管理员信息和权限通过 `stores/admin.ts` 管理
- 业务列表数据在页面组件内管理

### 错误处理
- 网络/认证错误由 `@shop/shared/api/request.ts` 统一拦截
- 业务错误在组件内通过 `ElMessage.error()` 提示
