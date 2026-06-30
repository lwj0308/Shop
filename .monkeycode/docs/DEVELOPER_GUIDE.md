# 开发者指南

## 项目目的

Shop 商城前端是一个基于 Vue 3 的 pnpm monorepo，包含用户端、商家后台、管理后台三个独立应用，以及一个共享代码库。它通过统一的共享层和 API 请求封装，为三类用户提供完整的电商操作界面。

**核心职责**:
- 为消费者提供商品浏览、搜索、购物车、下单、支付等购物体验
- 为商家提供商品管理、订单处理、营销活动、数据统计等运营工具
- 为平台管理员提供用户管理、商家审核、权限分配、日志审计等平台治理能力

**相关系统**:
- 后端微服务集群 — 提供数据接口
- API 网关 (shop-gateway) — 统一请求入口

## 环境搭建

### 前置条件

- Node.js >= 18
- pnpm >= 8（使用 npm install -g pnpm 安装）

### 安装

```bash
# 进入前端目录
cd shop-frontend

# 安装依赖（workspace 内所有包）
pnpm install
```

### 运行

```bash
# 启动用户端 (port 3000)
pnpm run dev:user

# 启动商家后台 (port 3001)
pnpm run dev:merchant

# 启动管理后台 (port 3002)
pnpm run dev:admin
```

### 构建

```bash
# 构建所有应用
pnpm run build:user
pnpm run build:merchant
pnpm run build:admin
```

### API 代理说明

开发环境下，三个前端应用的 Vite 配置均将 `/api` 请求代理到 `http://localhost:8844`（API 网关）。启动前需确保后端网关服务已运行。

## 开发工作流

### 代码质量工具

| 工具 | 命令 | 目的 |
|------|------|------|
| TypeScript | `npx tsc --noEmit` | 类型检查 |
| ESLint | 见 `shop-frontend/.eslintrc.cjs` | 代码检查 |
| Prettier | `npx prettier --check .` | 代码格式化 |

### 分支策略

- `main` — 生产就绪代码
- `feature/*` — 新功能
- `fix/*` — Bug 修复

### 提交规范

使用约定式提交 (Conventional Commits)：
- `feat:` — 新功能
- `fix:` — Bug 修复
- `chore:` — 杂项
- `refactor:` — 重构

## 常见任务

### 添加新 API 模块

**需修改的文件**:
1. `packages/shared/src/api/modules/[name].ts` — 添加 API 方法
2. `packages/shared/src/api/index.ts` — 导出新模块

**步骤**:
1. 在 `modules/` 下创建新文件，定义 API 方法
2. 在 `api/index.ts` 中添加导出
3. 如需新类型，在 `types/` 下添加类型定义

**示例**:
```typescript
// modules/review.ts
import request from '../request'

export function getReviewList(productId: string) {
  return request.get('/api/reviews', { params: { productId } })
}

export function createReview(data: CreateReviewRequest) {
  return request.post('/api/reviews', data)
}
```

### 添加新页面

**需修改的文件**:
1. `packages/web-[app]/src/views/[feature]/[ViewName].vue` — 页面组件
2. `packages/web-[app]/src/router/index.ts` — 添加路由

**步骤**:
1. 在对应 `views/` 目录下创建页面组件
2. 在路由文件中添加路由配置
3. 如需权限控制，在路由 meta 中添加 `requiresAuth` 或 `permission`

### 添加新组合式函数 (Composable)

**需修改的文件**:
1. `packages/shared/src/composables/use[Name].ts` — 函数实现
2. `packages/shared/src/composables/index.ts` — 导出

**步骤**:
1. 在 `composables/` 下创建新文件
2. 函数名以 `use` 开头
3. 从 `index.ts` 导出

### 添加新共享工具函数

**需修改的文件**:
1. `packages/shared/src/utils/[name].ts` — 函数实现
2. `packages/shared/src/utils/index.ts` — 导出

### 添加新业务常量

**需修改的文件**:
1. `packages/shared/src/constants/[name].ts` — 常量定义
2. `packages/shared/src/constants/index.ts` — 导出

### 修改路由配置

**文件位置**:
- 用户端: `packages/web-user/src/router/index.ts`
- 商家后台: `packages/web-merchant/src/router/index.ts`
- 管理后台: `packages/web-admin/src/router/index.ts`

### 修改 API 代理目标

如果后端网关地址变更，需修改三个应用的 Vite 配置：

- `packages/web-user/vite.config.ts`
- `packages/web-merchant/vite.config.ts`
- `packages/web-admin/vite.config.ts`

将 `proxy['/api'].target` 改为新地址。

## 编码规范

### 文件组织

- 每个 `.vue` 文件一个页面或组件
- 页面放在 `views/` 目录，按功能模块分文件夹
- 可复用组件放在对应应用的 `components/` 目录
- 跨应用共享的逻辑放在 `packages/shared/src/` 中

### 命名

| 类型 | 约定 | 示例 |
|------|------|------|
| Vue 组件文件 | PascalCase | `ProductCard.vue` |
| 组合式函数 | camelCase, use 前缀 | `useAuth.ts` |
| 工具函数 | camelCase | `format.ts` |
| 类型文件 | kebab-case | `product.ts` |
| Vue 视图目录 | kebab-case | `views/order/` |

### TypeScript 配置

项目使用 `tsconfig.base.json` 作为共享基础配置，各应用继承基础配置并添加自身设置。路径别名 `@shop/shared` 映射到 `packages/shared/src`。

### 样式处理

- 全局样式放在各应用的 `src/styles/` 目录
- 组件内部使用 `<style scoped>` 避免样式污染
- 优先使用 Element Plus 提供的 CSS 变量进行主题定制
- 管理后台通过 `composables/useTheme.ts` 支持暗黑模式切换

### 错误处理

```typescript
// 推荐: 使用共享请求层统一处理
import { userApi } from '@shop/shared/api'
const res = await userApi.getUserInfo()

// 网络错误、401、403、500 由 request.ts 统一拦截处理
// 业务错误码在组件中自行处理:
if (res.code !== 200) {
  ElMessage.error(res.message)
}
```

### 测试

项目未配置单元测试框架。如需添加，推荐使用 Vitest + Vue Test Utils。

## 目录结构约定

```
packages/[app]/
├── src/
│   ├── main.ts              # 应用入口
│   ├── App.vue              # 根组件
│   ├── router/              # 路由配置
│   │   └── index.ts
│   ├── stores/              # Pinia Store
│   │   └── [name].ts
│   ├── views/               # 页面组件
│   │   └── [feature]/
│   │       └── [ViewName].vue
│   ├── components/          # 可复用组件
│   │   └── [Component].vue
│   ├── layouts/             # 布局组件
│   │   └── [Layout].vue
│   ├── composables/         # 组合式函数 (应用特定)
│   ├── directives/          # 自定义指令
│   ├── styles/              # 全局样式
│   ├── assets/              # 静态资源
│   └── types/               # 应用特定类型
├── index.html               # HTML 入口
├── package.json
├── vite.config.ts
└── tsconfig.json
```
