# web-user 用户端商城

面向消费者的商城前台应用，提供商品浏览、搜索、购物车、下单、支付等核心购物体验。

## 技术栈

- Vue 3.5 + Composition API + TypeScript
- Vue Router 4
- Pinia
- Element Plus 2.8
- Vite 5.4
- @shop/shared

## 结构

```
packages/web-user/src/
├── main.ts                   # 应用入口
├── App.vue                   # 根组件
├── router/
│   └── index.ts              # 路由配置（22 个路由）
├── stores/
│   └── user.ts               # 用户状态（Token、用户信息、登录状态）
├── views/                    # 页面组件
│   ├── home/
│   │   └── IndexView.vue     # 首页
│   ├── category/
│   │   └── IndexView.vue     # 商品分类页
│   ├── product/
│   │   └── DetailView.vue    # 商品详情页
│   ├── search/
│   │   └── IndexView.vue     # 搜索页
│   ├── cart/
│   │   └── IndexView.vue     # 购物车
│   ├── order/
│   │   ├── ConfirmView.vue   # 确认订单
│   │   ├── ListView.vue      # 我的订单
│   │   └── DetailView.vue    # 订单详情
│   ├── payment/
│   │   ├── PayView.vue       # 收银台
│   │   └── ResultView.vue    # 支付结果
│   ├── user/
│   │   ├── LoginView.vue     # 登录/注册页
│   │   └── CenterView.vue    # 个人中心
│   ├── coupon/
│   │   ├── MyCouponView.vue   # 我的优惠券
│   │   └── ReceiveView.vue   # 领券中心
│   ├── favorite/
│   │   └── ListView.vue      # 我的收藏
│   ├── footprint/
│   │   └── ListView.vue      # 浏览足迹
│   ├── review/
│   │   ├── CreateView.vue    # 发表评价
│   │   └── AppendView.vue    # 追加评价
│   ├── notification/
│   │   └── IndexView.vue     # 消息通知
│   └── seckill/
│       ├── ListView.vue      # 秒杀列表
│       └── DetailView.vue    # 秒杀详情
├── components/
│   ├── AuthModal.vue         # 登录/注册弹窗（三合一）
│   └── ProductCard.vue       # 商品卡片组件
├── layouts/
│   └── DefaultLayout.vue     # 默认布局（顶部导航 + 底部 + 内容区）
├── composables/              # 应用特定组合式函数
├── styles/                   # 全局样式
└── assets/                   # 静态资源
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `router/index.ts` | 路由定义 + 登录守卫（未登录弹出 AuthModal） |
| `stores/user.ts` | 用户状态管理，信息持久化到 localStorage |
| `layouts/DefaultLayout.vue` | 753 行，顶部透明导航栏、搜索下拉、底部导航 |
| `components/AuthModal.vue` | 626 行，三合一登录/注册弹窗 |
| `views/product/DetailView.vue` | 商品详情展示，SKU 选择，加入购物车 |

## 依赖

**本模块依赖**:
- `@shop/shared` — 共享 API、类型、组合式函数、工具
- `vue-router` — 路由
- `pinia` — 状态管理
- `element-plus` — UI 组件库

**依赖本模块的**:
- 无（终端应用，不被其他前端模块依赖）

## 路由守卫

- 未登录访问需登录页面时，**不跳转登录页**，而是弹出 `AuthModal` 弹窗
- 登录成功后自动关闭弹窗并继续导航
- `/register` 重定向到 `/login?tab=register`

## 布局说明

`DefaultLayout.vue` 实现极简独立站风格：

- **固定顶部导航栏**: 透明背景，滚动后变白
- **Logo + 导航菜单**: 首页、商品分类、秒杀等
- **右侧图标组**: 搜索（带热门搜索下拉）、账户、通知（未读铃铛徽章）、购物车（数量徽章）
- **底部**: 四列服务承诺 + 五列链接 + 版权信息
- **首页 Hero**: 导航栏透明，文字白色，非首页正常配色

## 规范

### 文件命名
- 视图目录: kebab-case（如 `views/order/`）
- 视图组件: PascalCase（如 `IndexView.vue`, `DetailView.vue`）
- 工具组件: PascalCase（如 `ProductCard.vue`）

### 状态管理
- 用户信息通过 `stores/user.ts` 管理，持久化到 localStorage
- 购物车状态通过 `@shop/shared/composables/useCart.ts` 管理
- 页面级状态直接在组件内使用 `ref`/`reactive`

### 错误处理
- 网络/认证错误由 `@shop/shared/api/request.ts` 统一拦截
- 业务错误在组件内通过 `ElMessage.error()` 提示
