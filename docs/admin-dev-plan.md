# ShopMall 管理后台开发计划

## 一、功能模块分析

### 1.1 核心功能模块

| 模块 | 子功能 | 优先级 |
|------|--------|--------|
| **仪表盘** | 数据概览(今日订单/销售额/新增用户/在线商家)、销售趋势图、待办事项 | P0 |
| **用户管理** | 用户列表/搜索/禁用/解禁、用户详情查看 | P0 |
| **商家管理** | 商家列表、入驻审核(通过/拒绝)、商家禁用/启用 | P0 |
| **商品管理** | 商品列表/搜索/筛选、商品上架/下架、商品审核 | P0 |
| **分类管理** | 三级分类树、新增/编辑/删除分类 | P0 |
| **品牌管理** | 品牌列表、新增/编辑/删除品牌 | P1 |
| **订单管理** | 订单列表(7种状态Tab)、订单详情、发货操作 | P0 |
| **退款管理** | 退款列表、退款审核(同意/拒绝) | P0 |
| **内容管理** | Banner管理、推荐位管理、公告管理 | P1 |
| **系统管理** | 管理员列表、角色管理、权限管理、操作日志 | P0 |
| **安全审计** | 登录日志、操作审计、敏感操作追踪、异常检测 | P0 |

### 1.2 安全审计控制设计

#### RBAC 权限模型

```
管理员(admin_user)
    ├── 角色(admin_role) — 多对多关联
    │       └── 权限(admin_permission) — 多对多关联
    │               ├── 菜单权限(前端菜单显示控制)
    │               ├── 按钮权限(操作按钮显示控制)
    │               └── 数据权限(数据范围控制)
    └── 数据权限
            ├── 全部数据
            ├── 本部门数据
            ├── 本部门及下级
            └── 仅本人数据
```

#### 预设角色

| 角色 | 权限范围 |
|------|---------|
| 超级管理员 | 全部权限，不可删除 |
| 运营管理员 | 用户/商家/商品/订单/退款/内容管理 |
| 审核管理员 | 商家审核/商品审核/退款审核 |
| 客服管理员 | 订单查看/退款处理/用户查看 |
| 数据分析师 | 仪表盘/数据报表(只读) |

#### 安全控制措施

| 措施 | 说明 |
|------|------|
| 登录安全 | 登录失败5次锁定30分钟、验证码校验、IP白名单(可选) |
| 会话管理 | Token 2小时过期、单设备登录(踢掉旧会话)、操作自动续期 |
| 操作审计 | 所有增删改操作记录审计日志(操作人/模块/内容/IP/时间) |
| 敏感操作 | 禁用用户/审核商家/退款等需二次确认，记录操作原因 |
| 数据权限 | 不同角色看到不同范围的数据(如客服只看本部门订单) |
| 接口安全 | 网关层校验Token + 角色 + 权限，防止越权访问 |
| 密码策略 | 初始密码强制修改、密码复杂度校验、定期更换提醒 |

---

## 二、数据库设计

### shop_admin 数据库（新增）

```sql
-- 管理员表
admin_user
    id, username, password, nickname, avatar, email, phone,
    dept_id, status(正常/禁用), last_login_ip, last_login_time,
    password_change_time, create_time, update_time, deleted

-- 角色表
admin_role
    id, role_name, role_key(如admin/operator/auditor),
    data_scope(1全部/2本部门/3本部门及下级/4仅本人),
    status, remark, create_time, update_time, deleted

-- 权限/菜单表
admin_permission
    id, parent_id, name, type(1目录/2菜单/3按钮),
    permission_key(如user:list/user:edit/user:delete),
    path, icon, sort, status, create_time, update_time

-- 管理员-角色关联表
admin_user_role
    id, user_id, role_id

-- 角色-权限关联表
admin_role_permission
    id, role_id, permission_id

-- 部门表
admin_dept
    id, parent_id, name, sort, leader, status, create_time, update_time

-- 操作日志表
admin_operation_log
    id, user_id, username, module, operation_type(新增/修改/删除/查询/导出),
    description, request_url, request_method, request_params, response_result,
    ip, location, duration, status(成功/失败), error_msg, create_time

-- 登录日志表
admin_login_log
    id, username, ip, location, browser, os, status(成功/失败),
    fail_reason, login_time

-- 异常检测表
admin_security_event
    id, event_type(频繁登录失败/异常IP/权限越权/敏感操作),
    user_id, username, detail, ip, status(未处理/已处理/已忽略),
    handle_note, handle_time, create_time
```

---

## 三、技术架构

### 3.1 后端：shop-admin 微服务

```
shop-admin (端口 8851)
├── controller/
│   ├── AdminAuthController      # 管理员登录/登出/验证码
│   ├── AdminUserController      # 管理员CRUD
│   ├── AdminRoleController      # 角色CRUD + 权限分配
│   ├── AdminPermissionController # 权限菜单树
│   ├── AdminDeptController      # 部门CRUD
│   ├── DashboardController      # 仪表盘数据
│   ├── UserManageController     # C端用户管理(Feign调用shop-user)
│   ├── MerchantManageController # 商家审核管理(Feign调用shop-merchant)
│   ├── ProductManageController  # 商品审核管理(Feign调用shop-product)
│   ├── OrderManageController    # 订单管理(Feign调用shop-order)
│   ├── RefundManageController   # 退款管理(Feign调用shop-order)
│   ├── CategoryManageController # 分类管理(Feign调用shop-product)
│   ├── BrandManageController    # 品牌管理(Feign调用shop-product)
│   ├── ContentController        # 内容管理(Banner/推荐位/公告)
│   ├── OperationLogController   # 操作日志查询
│   ├── LoginLogController       # 登录日志查询
│   └── SecurityEventController  # 安全事件管理
├── service/
├── mapper/
├── interceptor/                 # 管理员身份拦截器
├── aspect/                      # 操作日志AOP切面
├── security/                    # 权限校验、数据权限过滤
└── feign/                       # Feign客户端调用其他服务
```

### 3.2 前端：web-admin

```
shop-frontend/packages/web-admin (端口 3002)
├── src/
│   ├── layouts/
│   │   └── AdminLayout.vue      # 管理后台布局(侧边栏+顶栏+内容)
│   ├── views/
│   │   ├── login/               # 登录页
│   │   ├── dashboard/           # 仪表盘
│   │   ├── user/                # 用户管理
│   │   ├── merchant/            # 商家管理
│   │   ├── product/             # 商品管理
│   │   ├── category/            # 分类管理
│   │   ├── brand/               # 品牌管理
│   │   ├── order/               # 订单管理
│   │   ├── refund/              # 退款管理
│   │   ├── content/             # 内容管理
│   │   ├── system/              # 系统管理(管理员/角色/权限/日志)
│   │   └── security/            # 安全审计
│   ├── router/
│   ├── store/
│   ├── composables/
│   └── api/
```

### 3.3 关键技术决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 权限校验 | 自定义注解 + AOP | 比Sa-Token注解更灵活，支持数据权限 |
| 操作日志 | AOP切面 + 注解 | 声明式记录，不侵入业务代码 |
| 菜单渲染 | 后端返回权限树，前端动态路由 | 安全性高，菜单和按钮都受控 |
| 数据权限 | MyBatis拦截器 | 自动拼接数据范围SQL，业务无感知 |
| 验证码 | 后端生成 + Redis存储 | 防暴力破解 |
| 导出 | EasyExcel | 大数据量导出性能好 |

---

## 四、开发任务分解

### Phase 0：基础设施（前置）

- [x] Task 0.1: 创建 shop-admin Maven模块 + 启动类
- [x] Task 0.2: 创建 shop_admin 数据库 + 建表SQL + 测试数据
- [x] Task 0.3: 创建 Nacos 配置 shop-admin.yml
- [x] Task 0.4: 网关添加 shop-admin 路由规则
- [x] Task 0.5: shop-model 添加 admin 包(DTO/Entity/VO/Enum)

### Phase 1：认证与权限核心

- [ ] Task 1.1: 管理员登录/登出/验证码接口
- [ ] Task 1.2: RBAC权限模型(管理员/角色/权限 CRUD)
- [ ] Task 1.3: 权限校验注解 + AOP切面(@RequirePermission)
- [ ] Task 1.4: 操作日志AOP切面(@OperationLog)
- [ ] Task 1.5: 数据权限MyBatis拦截器
- [ ] Task 1.6: 管理员身份拦截器 + 网关鉴权适配

### Phase 2：业务管理接口

- [ ] Task 2.1: 仪表盘数据聚合接口(Feign调用各服务)
- [ ] Task 2.2: C端用户管理接口(Feign调用shop-user)
- [ ] Task 2.3: 商家审核管理接口(Feign调用shop-merchant)
- [ ] Task 2.4: 商品审核管理接口(Feign调用shop-product)
- [ ] Task 2.5: 分类/品牌管理接口(Feign调用shop-product)
- [ ] Task 2.6: 订单/退款管理接口(Feign调用shop-order)
- [ ] Task 2.7: 内容管理接口(Banner/推荐位/公告)

### Phase 3：安全审计

- [ ] Task 3.1: 登录日志记录(登录成功/失败)
- [ ] Task 3.2: 异常检测服务(频繁登录/异常IP/越权)
- [ ] Task 3.3: 安全事件管理接口

### Phase 4：前端开发

- [ ] Task 4.1: 初始化 web-admin 前端项目(Vue3 + Element Plus)
- [ ] Task 4.2: 登录页 + 动态路由 + 权限指令
- [ ] Task 4.3: 管理后台布局(侧边栏/顶栏/面包屑/标签页)
- [ ] Task 4.4: 仪表盘页面
- [ ] Task 4.5: 用户/商家/商品管理页面
- [ ] Task 4.6: 分类/品牌/订单/退款管理页面
- [ ] Task 4.7: 内容管理页面
- [ ] Task 4.8: 系统管理页面(管理员/角色/权限/日志)
- [ ] Task 4.9: 安全审计页面

### Phase 5：联调与测试

- [ ] Task 5.1: 全链路联调
- [ ] Task 5.2: 权限边界测试(越权/无权限/数据范围)
- [ ] Task 5.3: 安全审计验证

### 任务依赖关系

```
Phase 0 → Phase 1 → Phase 2 (可并行) → Phase 4 → Phase 5
                   → Phase 3 (可并行) ↗
```

---

## 五、测试账号规划

| 角色 | 用户名 | 密码 | 权限 |
|------|--------|------|------|
| 超级管理员 | admin | admin123 | 全部权限 |
| 运营管理员 | operator | oper123 | 用户/商家/商品/订单/内容 |
| 审核管理员 | auditor | audit123 | 商家审核/商品审核/退款审核 |
| 客服管理员 | service | serv123 | 订单查看/退款处理 |

---

## 六、UI原型

原型文件：`docs/prototype-web-admin.html`

包含12个完整页面，参考 Ant Design Pro 风格设计，深色侧边栏 + 浅色内容区。

---

## 七、代码审查记录

### 7.1 审查时间
2026-06-18

### 7.2 已修复问题

| 优先级 | 问题 | 修复方案 |
|--------|------|----------|
| P0 | X-Admin-Id 请求头可伪造，导致权限绕过 | 移除对请求头的直接信任，仅通过 Sa-Token Session 获取管理员ID |
| P0 | 25个核心管理接口缺失 @RequirePermission | 给8个Controller的所有方法补上权限注解和操作日志注解 |
| P0 | Feign调用无鉴权，微服务间调用可被伪造 | 创建 FeignAuthConfig，自动透传 Sa-Token |
| P0 | 操作日志可能明文记录密码 | 增强脱敏正则，支持非字符串值，扩展敏感字段列表 |
| P0 | 系统错误信息直接返回前端 | 创建 AdminGlobalExceptionHandler，统一返回"服务器内部错误" |
| P1 | 数据权限过滤失败时静默跳过 | 改为抛出异常拒绝查询，防止越权访问 |
| P1 | 4个Feign代理接口缺少 @Validated | 补上 @Validated 注解，DTO校验生效 |
| P1 | 权限查询无缓存，每次鉴权4次DB | 使用 Sa-Token Session 缓存权限/角色列表 |
| P1 | N+1查询：管理员列表10条=31次SQL | 批量预查询角色和部门，降到4次SQL |
| P1 | 递归查询子部门N+1问题 | 一次查询所有部门，内存中递归构建 |
| P1 | 关联表循环单条INSERT | 使用 Db.saveBatch() 批量插入 |
| P1 | @Async使用默认线程池，可能OOM | 创建自定义线程池 AsyncConfig |
| P2 | 5个QueryDTO未继承PageRequest，分页无上限 | 继承 PageRequest，自动获得 @Max(100) 保护 |
| P2 | VO返回手机号/邮箱/IP未脱敏 | 创建脱敏序列化器，Jackson自动脱敏 |
| P2 | getClientIp方法重复3次 | 提取 IpUtils 工具类 |

### 7.3 待修复问题（P3）

| 问题 | 说明 |
|------|------|
| 密码强度校验缺失 | 创建和修改密码时未校验复杂度 |
| 枚举已定义但Entity全用魔法数字 | 6个枚举形同虚设，DataScopeInterceptor中1/2/3/4散布 |
| Feign接口路径重复 | BrandFeignClient/CategoryFeignClient 的 path + 方法路径重复 |
| Update DTO中status字段与专用接口冲突 | 状态变更可通过Update接口绕过业务校验 |
| 角色roleKey更新时无唯一性校验 | 创建有唯一检查，更新没有 |
| 权限parentId可设为自身ID导致循环引用 | 需添加循环引用校验 |
| 事务管理不一致 | 部分写方法缺少 @Transactional |
| Dashboard三个Feign调用串行 | 应使用 CompletableFuture 并行 |
| DataScopeInterceptor反射修改BoundSql | JDK 17+可能失败，应使用MyBatis-Plus官方方式 |
