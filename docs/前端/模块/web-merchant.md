# web-merchant 商家后台

面向入驻商家的管理后台，提供商品管理、订单处理、营销活动配置、数据中心和店铺设置等功能。

## 技术栈

- Vue 3.5 + Composition API + TypeScript
- Vue Router 4
- Pinia
- Element Plus 2.9
- Vite 6
- @shop/shared

## 结构

```
packages/web-merchant/src/
├── main.ts                   # 应用入口
├── App.vue                   # 根组件
├── router/
│   └── index.ts              # 路由配置（14 个路由 + 入驻流程守卫）
├── stores/
│   └── merchant.ts           # 商家状态（Token、商家信息、店铺信息、loading）
├── views/                    # 页面组件
│   ├── login/
│   │   └── LoginView.vue     # 商家登录
│   ├── apply/
│   │   └── ApplyView.vue     # 商家入驻申请
│   ├── dashboard/
│   │   └── IndexView.vue     # 工作台
│   ├── product/
│   │   ├── ListView.vue      # 商品列表
│   │   └── EditView.vue      # 编辑商品
│   ├── order/
│   │   ├── ListView.vue      # 订单列表
│   │   └── DetailView.vue    # 订单详情
│   ├── comment/
│   │   └── ListView.vue      # 评价管理
│   ├── data/
│   │   └── DataView.vue      # 数据中心
│   ├── settlement/
│   │   └── IndexView.vue     # 结算管理
│   ├── shop/
│   │   └── SettingsView.vue  # 店铺设置
│   ├── notification/
│   │   └── IndexView.vue     # 消息通知
│   ├── coupon/
│   │   └── ListView.vue      # 优惠券管理
│   ├── promotion/
│   │   └── ListView.vue      # 满减活动
│   └── seckill/
│       └── ListView.vue      # 秒杀活动
├── components/               # 可复用组件
├── layouts/                  # 布局组件
├── composables/              # 应用特定组合式函数
├── styles/                   # 全局样式
└── assets/                   # 静态资源
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `router/index.ts` | 路由定义 + 三层路由守卫（登录检测、入驻检测、反向跳转） |
| `stores/merchant.ts` | 商家状态管理，信息持久化到 localStorage |
| `views/dashboard/IndexView.vue` | 工作台仪表盘 |
| `views/product/EditView.vue` | 商品新建/编辑页面 |
| `views/apply/ApplyView.vue` | 商家入驻申请表单 |

## 依赖

**本模块依赖**:
- `@shop/shared` — 共享 API、类型、组合式函数、工具
- `vue-router` — 路由
- `pinia` — 状态管理
- `element-plus` — UI 组件库

**依赖本模块的**:
- 无（终端应用，不被其他前端模块依赖）

## 路由守卫

商家后台有三层路由守卫逻辑：

1. **登录检测**: 白名单 `['/login', '/apply']` — 未登录访问其他页面跳转 `/login?redirect=xxx`
2. **入驻检测**: 已登录但商家状态 `!= 1`（未入驻/审核中/被驳回）→ 强制跳转 `/apply`
3. **反向跳转**: 已入驻访问 `/login` 或 `/apply` → 跳转 `/dashboard`
4. **异常处理**: 获取商家信息失败 → 清除状态并跳转 `/login`

## 状态管理

`stores/merchant.ts` 负责：
- Token 认证状态
- 商家信息（含入驻状态）
- 店铺信息
- 全局 loading 状态
- 所有状态持久化到 localStorage

## 规范

### 页面组织
- 按功能模块分视图目录（`product/`, `order/`, `coupon/` 等）
- 每个模块通常包含 `ListView.vue`（列表页）和 `DetailView.vue`/`EditView.vue`（详情/编辑页）

### 错误处理
- 网络/认证错误由 `@shop/shared/api/request.ts` 统一拦截
- 业务错误在组件内通过 `ElMessage.error()` 提示
- 路由守卫层面的认证失败自动清除状态并跳转登录页
