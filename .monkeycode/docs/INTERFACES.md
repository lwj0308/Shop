# 接口文档

本文档描述前端模块的对外编程接口，包括路由系统、共享库 API、状态管理和请求层。

---

## 1. 共享库 (@shop/shared) 接口

### 1.1 API 请求层

**入口**: `packages/shared/src/api/index.ts`

#### request.ts — HTTP 客户端

```typescript
// 创建带认证拦截器的 Axios 实例
const request: AxiosInstance

// 特性
// - 自动携带 Bearer Token (Authorization header)
// - 401 响应自动刷新 Token（竞态安全）
// - GET 请求网络错误自动重试 1 次
// - POST 请求体 HTML 标签自动转义（XSS 防护）
// - 页面切换时自动取消未完成请求
// - 统一错误处理及错误码中文映射
```

#### API 模块列表

| 模块 | 文件 | 功能 |
|------|------|------|
| `userApi` | `modules/user.ts` | 用户认证、注册、信息管理 |
| `adminApi` | `modules/admin.ts` | 管理后台接口 |
| `merchantApi` | `modules/merchant.ts` | 商家接口 |
| `productApi` | `modules/product.ts` | 商品查询、分类、品牌 |
| `orderApi` | `modules/order.ts` | 订单创建、查询、状态管理 |
| `cartApi` | `modules/cart.ts` | 购物车操作 |
| `paymentApi` | `modules/payment.ts` | 支付、退款 |
| `couponApi` | `modules/coupon.ts` | 优惠券管理 |
| `promotionApi` | `modules/promotion.ts` | 满减活动 |
| `seckillApi` | `modules/seckill.ts` | 秒杀活动 |
| `statsApi` | `modules/stats.ts` | 统计数据 |
| `notificationApi` | `modules/notification.ts` | 消息通知 |

### 1.2 类型定义

**入口**: `packages/shared/src/types/index.ts`

#### 通用类型

```typescript
// 标准 API 响应格式
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
```

#### 业务类型

| 类型文件 | 主要接口 |
|---------|---------|
| `user.ts` | `UserInfo`, `LoginRequest`, `RegisterRequest` |
| `merchant.ts` | `MerchantInfo`, `ShopInfo` |
| `product.ts` | `Product`, `ProductCategory`, `Brand` |
| `cart.ts` | `CartItem`, `CartInfo` |
| `order.ts` | `Order`, `OrderItem`, `OrderStatus` |
| `payment.ts` | `PaymentInfo`, `RefundInfo` |
| `coupon.ts` | `Coupon`, `UserCoupon` |
| `promotion.ts` | `Promotion` |
| `seckill.ts` | `SeckillActivity`, `SeckillProduct` |
| `stats.ts` | `DashboardStats`, `SalesStats` |
| `notification.ts` | `Notification` |

### 1.3 组合式函数 (Composables)

**入口**: `packages/shared/src/composables/index.ts`

| 函数 | 用途 |
|------|------|
| `useAuth()` | 登录状态检测、Token 管理、登出 |
| `useCart()` | 购物车增删改查、数量计算 |
| `useLoading()` | 全局/局部加载状态管理 |
| `usePagination()` | 分页状态、页码切换、重置 |

### 1.4 工具函数

**入口**: `packages/shared/src/utils/index.ts`

| 模块 | 主要函数 |
|------|---------|
| `auth.ts` | `getToken()`, `setToken()`, `removeToken()`, `getUserInfo()` |
| `format.ts` | `formatPrice()`, `formatDate()`, `formatPhone()` |
| `validate.ts` | `isPhone()`, `isPassword()`, `isEmail()` |
| `storage.ts` | `getItem()`, `setItem()`, `removeItem()` |

### 1.5 业务常量

**入口**: `packages/shared/src/constants/index.ts`

| 模块 | 内容 |
|------|------|
| `order.ts` | 订单状态枚举 (`PENDING_PAYMENT`, `PENDING_DELIVERY`, `DELIVERED`, `COMPLETED`, `CANCELLED` 等) |
| `product.ts` | 商品状态枚举 (`ON_SALE`, `OFF_SALE`) |
| `payment.ts` | 支付状态枚举 |
| `refund.ts` | 退款状态枚举 |
| `merchant.ts` | 商家状态枚举 |
| `errorCode.ts` | HTTP 错误码到中文描述映射 |

---

## 2. 路由系统

### 2.1 用户端路由 (web-user)

**文件**: `packages/web-user/src/router/index.ts`

| 路径 | 组件 | 需登录 | 说明 |
|------|------|--------|------|
| `/` | `views/home/IndexView.vue` | 否 | 首页 |
| `/category` | `views/category/IndexView.vue` | 否 | 商品分类 |
| `/product/:id` | `views/product/DetailView.vue` | 否 | 商品详情 |
| `/seckill` | `views/seckill/ListView.vue` | 否 | 秒杀列表 |
| `/seckill/:id` | `views/seckill/DetailView.vue` | 否 | 秒杀详情 |
| `/search` | `views/search/IndexView.vue` | 否 | 搜索页 |
| `/cart` | `views/cart/IndexView.vue` | 是 | 购物车 |
| `/order/confirm` | `views/order/ConfirmView.vue` | 是 | 确认订单 |
| `/order/list` | `views/order/ListView.vue` | 是 | 我的订单 |
| `/order/:id` | `views/order/DetailView.vue` | 是 | 订单详情 |
| `/user/center` | `views/user/CenterView.vue` | 是 | 个人中心 |
| `/payment/pay` | `views/payment/PayView.vue` | 是 | 收银台 |
| `/payment/result` | `views/payment/ResultView.vue` | 是 | 支付结果 |
| `/notification` | `views/notification/IndexView.vue` | 是 | 消息通知 |
| `/coupon` | `views/coupon/MyCouponView.vue` | 是 | 我的优惠券 |
| `/coupon/receive` | `views/coupon/ReceiveView.vue` | 是 | 领券中心 |
| `/favorite` | `views/favorite/ListView.vue` | 是 | 我的收藏 |
| `/footprint` | `views/footprint/ListView.vue` | 是 | 浏览足迹 |
| `/review/create` | `views/review/CreateView.vue` | 是 | 发表评价 |
| `/review/append` | `views/review/AppendView.vue` | 是 | 追加评价 |
| `/login` | `views/user/LoginView.vue` | - | 登录页 |

**路由守卫**:
- 未登录访问需登录页面时，弹出 `AuthModal` 登录弹窗
- 登录成功后自动跳回原页面
- `/register` 重定向到 `/login?tab=register`

### 2.2 商家后台路由 (web-merchant)

**文件**: `packages/web-merchant/src/router/index.ts`

| 路径 | 组件 | 说明 |
|------|------|------|
| `/login` | `views/login/LoginView.vue` | 商家登录 |
| `/apply` | `views/apply/ApplyView.vue` | 商家入驻申请 |
| `/` → `dashboard` | `views/dashboard/IndexView.vue` | 工作台 |
| `/product/list` | `views/product/ListView.vue` | 商品列表 |
| `/product/edit/:id?` | `views/product/EditView.vue` | 编辑商品 |
| `/order/list` | `views/order/ListView.vue` | 订单列表 |
| `/order/:id` | `views/order/DetailView.vue` | 订单详情 |
| `/comment/list` | `views/comment/ListView.vue` | 评价管理 |
| `/data` | `views/data/DataView.vue` | 数据中心 |
| `/settlement` | `views/settlement/IndexView.vue` | 结算管理 |
| `/shop/settings` | `views/shop/SettingsView.vue` | 店铺设置 |
| `/notification` | `views/notification/IndexView.vue` | 消息通知 |
| `/coupon` | `views/coupon/ListView.vue` | 优惠券管理 |
| `/promotion` | `views/promotion/ListView.vue` | 满减活动 |
| `/seckill` | `views/seckill/ListView.vue` | 秒杀活动 |

**路由守卫**:
- 白名单: `/login`, `/apply`
- 未登录 → 跳转登录页 (带 redirect 参数)
- 已登录但未入驻 (商家状态 != 1) → 强制跳转入驻页
- 已入驻访问登录/入驻页 → 跳转工作台
- 获取商家信息失败 → 自动登出

### 2.3 管理后台路由 (web-admin)

**文件**: `packages/web-admin/src/router/index.ts`

| 路径 | 组件 | 说明 |
|------|------|------|
| `/login` | `views/login/index.vue` | 登录 |
| `/dashboard` | `views/dashboard/index.vue` | 仪表盘 |
| `/notification` | `views/notification/IndexView.vue` | 消息通知 |
| `/coupon` | `views/coupon/IndexView.vue` | 优惠券管理 |
| `/promotion` | `views/promotion/IndexView.vue` | 满减活动 |
| `/seckill` | `views/seckill/IndexView.vue` | 秒杀活动 |
| `/comment` | `views/comment/IndexView.vue` | 评价管理 |
| `/business/user` | `views/business/UserList.vue` | 用户管理 |
| `/business/merchant` | `views/business/MerchantList.vue` | 商家管理 |
| `/business/product` | `views/business/ProductList.vue` | 商品管理 |
| `/business/category` | `views/business/CategoryList.vue` | 分类管理 |
| `/business/brand` | `views/business/BrandList.vue` | 品牌管理 |
| `/business/order` | `views/business/OrderList.vue` | 订单管理 |
| `/business/refund` | `views/business/RefundList.vue` | 退款管理 |
| `/business/withdraw` | `views/business/WithdrawList.vue` | 提现审核 |
| `/content/banner` | `views/content/BannerList.vue` | Banner 管理 |
| `/content/notice` | `views/content/NoticeList.vue` | 公告管理 |
| `/system/admin-user` | `views/system/AdminUserList.vue` | 管理员管理 |
| `/system/role` | `views/system/RoleList.vue` | 角色管理 |
| `/system/permission` | `views/system/PermissionList.vue` | 权限管理 |
| `/system/dept` | `views/system/DeptList.vue` | 部门管理 |
| `/log/operation` | `views/log/OperationLogList.vue` | 操作日志 |
| `/log/login` | `views/log/LoginLogList.vue` | 登录日志 |
| `/security/event` | `views/security/SecurityEventList.vue` | 安全事件 |

---

## 3. 状态管理 (Pinia Store)

### 3.1 用户端 Store

**文件**: `packages/web-user/src/stores/user.ts`

```typescript
interface UserStore {
  token: string | null
  userInfo: UserInfo | null
  isLoggedIn: boolean
  
  login(credentials: LoginRequest): Promise<void>
  logout(): void
  getUserInfo(): Promise<void>
}
// 用户信息持久化到 localStorage
```

### 3.2 商家后台 Store

**文件**: `packages/web-merchant/src/stores/merchant.ts`

```typescript
interface MerchantStore {
  token: string | null
  merchantInfo: MerchantInfo | null
  shopInfo: ShopInfo | null
  loading: boolean
  isLoggedIn: boolean
  isApplied: boolean
  
  login(credentials: LoginRequest): Promise<void>
  logout(): void
  getMerchantInfo(): Promise<void>
}
// 商家信息持久化到 localStorage
```

### 3.3 管理后台 Store

**文件**: `packages/web-admin/src/stores/admin.ts`

```typescript
interface AdminStore {
  token: string | null
  adminInfo: AdminInfo | null
  permissions: string[]
  menus: RouteRecordRaw[]
  
  login(credentials: LoginRequest): Promise<void>
  logout(): void
  getAdminInfo(): Promise<void>
  hasPermission(permission: string): boolean
  generateRoutes(): RouteRecordRaw[]
}
```

### 3.4 全局购物车 Store (共享层)

**文件**: `packages/shared/src/composables/useCart.ts`

```typescript
function useCart(): {
  cartItems: Ref<CartItem[]>
  cartCount: Ref<number>
  totalPrice: Ref<number>
  addToCart(item: CartItem): Promise<void>
  removeFromCart(id: string): Promise<void>
  updateQuantity(id: string, qty: number): Promise<void>
  clearCart(): void
  fetchCart(): Promise<void>
}
```

---

## 4. 组件接口

### 4.1 AuthModal 登录弹窗

**位置**: `packages/web-user/src/components/AuthModal.vue`

**Props**: 无（通过 Pinia Store 驱动）

**功能**: 三合一登录/注册弹窗，支持手机号+验证码登录、密码登录、注册

**事件**:
- `@close` — 关闭弹窗
- `@success` — 登录/注册成功

### 4.2 ProductCard 商品卡片

**位置**: `packages/web-user/src/components/ProductCard.vue`

**Props**:
```typescript
{
  product: Product       // 商品信息
  showPrice?: boolean    // 是否显示价格 (默认 true)
}
```

### 4.3 管理后台布局组件

**位置**: `packages/web-admin/src/layouts/`

| 组件 | 文件 | 用途 |
|------|------|------|
| `AdminLayout` | `AdminLayout.vue` | 经典后台布局（侧边栏 + 顶栏 + 标签页 + 内容区） |
| `Sidebar` | `Sidebar.vue` | 左侧可折叠菜单（8 个一级菜单组） |
| `HeaderBar` | `HeaderBar.vue` | 顶部导航（折叠按钮、全局搜索、全屏切换、用户下拉） |
| `TagsView` | `TagsView.vue` | 多标签页导航 |

### 4.4 权限指令

**位置**: `packages/web-admin/src/directives/permission.ts`

```typescript
// 按钮级别权限控制
v-permission="['permission:key']"
// 多个权限用数组
v-permission="['user:create', 'user:edit']"
```

---

## 5. Vite 代理配置

### web-user (端口 3000)

```typescript
// vite.config.ts
{
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8844',
        changeOrigin: true
      }
    }
  }
}
```

### web-merchant (端口 3001)

```typescript
{
  server: {
    port: 3001,
    proxy: {
      '/api': {
        target: 'http://localhost:8844',
        changeOrigin: true
      }
    }
  }
}
```

### web-admin (端口 3002)

```typescript
{
  server: {
    port: 3002,
    proxy: {
      '/api': {
        target: 'http://localhost:8844',
        changeOrigin: true
      }
    }
  }
}
```
