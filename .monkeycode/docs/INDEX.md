# Shop 商城前端文档

本文档涵盖 Shop 商城系统前端模块的架构设计、接口规范、开发指南和模块说明。面向开发者和系统维护人员。

**快速链接**: [架构](./ARCHITECTURE.md) | [接口](./INTERFACES.md) | [开发者指南](./DEVELOPER_GUIDE.md)

---

## 核心文档

### [架构](./ARCHITECTURE.md)
系统设计、技术栈、项目结构和数据流程。包含前端 monorepo 的组织方式、三个应用的职责划分、与后端微服务的交互方式，以及架构图和时序图。从这里开始了解系统全貌。

### [接口](./INTERFACES.md)
前端模块的对外编程接口，包括：路由系统（三个应用的路由表和守卫）、共享库 API（请求层、类型定义、组合式函数、工具函数）、状态管理（Pinia Store）、组件 Props 定义、权限指令用法和 Vite 代理配置。

### [开发者指南](./DEVELOPER_GUIDE.md)
环境搭建、开发工作流、编码规范和常见开发任务（添加页面、添加 API 模块、添加组合式函数等）。贡献者必读。

---

## 模块

| 模块 | 描述 | README |
|------|------|--------|
| `packages/shared` | 共享代码库：统一 API 请求层、TypeScript 类型、组合式函数、工具和常量 | [shared](./模块/shared.md) |
| `packages/web-user` | 用户端商城：商品浏览、购物车、下单、支付等消费者功能 | [web-user](./模块/web-user.md) |
| `packages/web-merchant` | 商家后台：商品管理、订单处理、营销活动、数据中心 | [web-merchant](./模块/web-merchant.md) |
| `packages/web-admin` | 管理后台：用户/商家/商品管理、RBAC 权限、日志审计 | [web-admin](./模块/web-admin.md) |

---

## 核心概念

理解这些领域概念有助于快速理解前端代码的组织逻辑：

| 概念 | 描述 |
|------|------|
| [Token 认证](./专有概念/Token认证.md) | 前端统一的用户身份认证机制，覆盖三个应用的登录/Token 刷新/路由守卫 |
| [API 请求层](./专有概念/API请求层.md) | 基于 Axios 的统一 HTTP 客户端，集成 Token 注入、错误处理、重试、XSS 防护 |
| [权限系统](./专有概念/权限系统.md) | 管理后台 RBAC 权限控制，菜单级和按钮级双重管控 |

---

## 入门指南

### 项目新人？

按此路径学习：
1. **[架构](./ARCHITECTURE.md)** — 了解前端 monorepo 的整体布局和各应用职责
2. **[核心概念](#核心概念)** — 理解 Token 认证、请求层和权限系统的工作方式
3. **[开发者指南](./DEVELOPER_GUIDE.md)** — 搭建本地开发环境并运行应用
4. **[模块文档](#模块)** — 深入阅读各前端模块的详细说明

### 需要集成？

1. **[接口](./INTERFACES.md)** — 路由定义、API 模块和状态管理接口
2. **[架构](./ARCHITECTURE.md)** — 系统边界和前后端交互方式

### 首次贡献？

1. **[开发者指南](./DEVELOPER_GUIDE.md)** — 环境搭建和开发规范
2. **[开发者指南 > 常见任务](./DEVELOPER_GUIDE.md#常见任务)** — 分步操作指南

---

## 快速参考

### 命令

```bash
# 进入前端目录
cd shop-frontend

# 安装依赖
pnpm install

# 启动各应用
pnpm run dev:user       # 用户端 (port 3000)
pnpm run dev:merchant   # 商家后台 (port 3001)
pnpm run dev:admin      # 管理后台 (port 3002)
```

### 重要文件

| 文件 | 目的 |
|------|------|
| `shop-frontend/pnpm-workspace.yaml` | pnpm 工作空间配置 |
| `shop-frontend/tsconfig.base.json` | 共享 TypeScript 基础配置 |
| `shop-frontend/packages/shared/src/api/request.ts` | 核心 HTTP 请求层 |
| `shop-frontend/packages/web-user/src/router/index.ts` | 用户端路由 |
| `shop-frontend/packages/web-merchant/src/router/index.ts` | 商家后台路由 |
| `shop-frontend/packages/web-admin/src/router/index.ts` | 管理后台路由 |

### API 代理

| 应用 | 端口 | API 代理目标 |
|------|------|-------------|
| web-user | 3000 | `http://localhost:8844` |
| web-merchant | 3001 | `http://localhost:8844` |
| web-admin | 3002 | `http://localhost:8844` |
