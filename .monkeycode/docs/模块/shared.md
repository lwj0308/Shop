# @shop/shared 共享库

前端 monorepo 的共享代码库，为 web-user、web-merchant、web-admin 三个应用提供统一的类型定义、API 请求封装、组合式函数、工具函数和业务常量。

## 结构

```
packages/shared/src/
├── index.ts                 # 统一导出入口
├── api/                     # API 请求层
│   ├── index.ts             # 统一导出所有 API 模块
│   ├── request.ts           # Axios 封装（核心 HTTP 层）
│   └── modules/             # 按业务域拆分的 API 模块
│       ├── admin.ts         # 管理后台 API
│       ├── user.ts          # 用户 API
│       ├── merchant.ts      # 商家 API
│       ├── product.ts       # 商品 API
│       ├── order.ts         # 订单 API
│       ├── cart.ts          # 购物车 API
│       ├── payment.ts       # 支付 API
│       ├── coupon.ts        # 优惠券 API
│       ├── promotion.ts     # 满减活动 API
│       ├── seckill.ts       # 秒杀 API
│       ├── stats.ts         # 统计 API
│       └── notification.ts  # 通知 API
├── types/                   # TypeScript 类型定义
│   ├── index.ts             # 导出所有类型
│   ├── api.ts               # 通用 API 响应类型 ApiResponse<T>
│   ├── user.ts              # 用户类型
│   ├── merchant.ts          # 商家类型
│   ├── product.ts           # 商品类型
│   ├── cart.ts              # 购物车类型
│   ├── order.ts             # 订单类型
│   ├── payment.ts           # 支付类型
│   ├── coupon.ts            # 优惠券类型
│   ├── promotion.ts         # 满减活动类型
│   ├── seckill.ts           # 秒杀类型
│   ├── stats.ts             # 统计类型
│   └── notification.ts      # 通知类型
├── composables/             # Vue 组合式函数
│   ├── index.ts             # 导出
│   ├── useAuth.ts           # 认证状态管理
│   ├── useCart.ts           # 购物车状态管理
│   ├── useLoading.ts        # 加载状态管理
│   └── usePagination.ts     # 分页状态管理
├── utils/                   # 工具函数
│   ├── index.ts             # 导出
│   ├── auth.ts              # Token 存储/读取/清除
│   ├── format.ts            # 价格/日期/手机号格式化
│   ├── validate.ts          # 手机号/密码/邮箱校验
│   └── storage.ts           # localStorage 封装
└── constants/               # 业务常量
    ├── index.ts             # 导出
    ├── order.ts             # 订单状态枚举
    ├── product.ts           # 商品状态枚举
    ├── payment.ts           # 支付状态枚举
    ├── refund.ts            # 退款状态枚举
    ├── merchant.ts          # 商家状态枚举
    └── errorCode.ts         # 错误码映射
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `api/request.ts` | 核心 HTTP 客户端，封装认证、错误处理、重试、XSS 防护 |
| `api/index.ts` | 统一导出所有 API 模块，应用只需 `import { userApi } from '@shop/shared/api'` |
| `types/index.ts` | 统一导出所有类型定义 |
| `composables/useAuth.ts` | 多应用复用的认证逻辑 |
| `utils/auth.ts` | Token 的持久化存取和 Base64 编解码 |

## 依赖

**本模块依赖**:
- `axios` — HTTP 客户端
- Vue 3 (组合式 API)

**依赖本模块的**:
- `web-user` — 使用所有 API 模块、类型、组合式函数和工具
- `web-merchant` — 使用商家相关 API、类型和工具
- `web-admin` — 使用管理后台相关 API、类型和工具

## 规范

### 文件命名
- API 模块: `[domain].ts`（如 `user.ts`, `order.ts`）
- 类型定义: `[domain].ts`（如 `user.ts`, `order.ts`）
- 组合式函数: `use[Name].ts`
- 工具: `[name].ts`
- 常量: `[domain].ts`

### 代码模式

**API 模块模式**:
```typescript
// modules/example.ts
import request from '../request'
import type { ApiResponse, Example } from '../../types'

export function getList(params: any) {
  return request.get<ApiResponse<Example[]>>('/api/examples', { params })
}

export function getById(id: string) {
  return request.get<ApiResponse<Example>>(`/api/examples/${id}`)
}

export function create(data: any) {
  return request.post<ApiResponse<Example>>('/api/examples', data)
}
```

**组合式函数模式**:
```typescript
// composables/useExample.ts
import { ref } from 'vue'
import type { Ref } from 'vue'

export function useExample(): {
  data: Ref<Example | null>
  loading: Ref<boolean>
  fetchData(): Promise<void>
} {
  const data = ref<Example | null>(null)
  const loading = ref(false)

  async function fetchData() {
    loading.value = true
    try {
      data.value = await exampleApi.getData()
    } finally {
      loading.value = false
    }
  }

  return { data, loading, fetchData }
}
```

### request.ts 核心机制

1. **Token 自动注入**: 从 localStorage 读取 Token，注入 Authorization 头
2. **401 自动刷新**: Token 过期时自动重试刷新，竞态安全（多个同时请求只刷新一次）
3. **GET 重试**: GET 请求网络错误自动重试 1 次
4. **XSS 防护**: POST 请求体 HTML 标签自动转义
5. **请求取消**: 路由切换时自动取消未完成请求
6. **统一错误处理**: 401/403/500 自动处理并提示

## 添加新文件

### 添加新 API 模块

1. 在 `modules/` 下创建 `[domain].ts`
2. 定义 API 函数，使用 `request` 实例
3. 在 `api/index.ts` 中导出
4. 如需新类型，在 `types/` 下创建对应类型文件

### 添加新组合式函数

1. 在 `composables/` 下创建 `use[Name].ts`
2. 函数名以 `use` 开头
3. 从 `composables/index.ts` 导出

### 添加新工具函数

1. 在 `utils/` 下创建 `[name].ts`
2. 从 `utils/index.ts` 导出
