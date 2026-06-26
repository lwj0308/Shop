# 代码审查常见问题模式 - 好代码 vs 坏代码

> 本文档基于 ShopMall 管理后台（shop-admin）代码审查整理，总结常见的安全、架构、质量、性能和可维护性问题模式。
> 每个模式都包含：问题描述、坏代码示例、好代码示例、危害说明和修复要点。

---

## 一、安全问题

### 1.1 系统错误信息直接返回前端

**场景**：后端抛出异常时，把 Java 堆栈、SQL 错误、远程服务报错等内部信息直接返回给前端。

**坏代码**：
```java
// 全局异常处理器 - 兜底方法
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    // 把异常信息直接返回前端，可能暴露数据库结构、内部路径等
    return Result.fail(500, e.getMessage());
}

// Feign调用失败时
@ExceptionHandler(FeignException.class)
public Result<Void> handleFeignException(FeignException e) {
    // 远程服务的错误详情直接返回，可能包含其他服务的内部信息
    return Result.fail(500, e.getMessage());
}
```

**好代码**：
```java
// 全局异常处理器 - 兜底方法
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    // 日志记录完整信息（方便开发排查），但前端只返回通用提示
    log.error("系统异常: ", e);
    return Result.fail(ErrorCode.INTERNAL_ERROR);  // 返回"服务器内部错误"
}

// Feign调用失败时
@ExceptionHandler(FeignException.class)
public Result<Void> handleFeignException(FeignException e) {
    // 日志记录远程服务错误详情，前端只看到"服务器内部错误"
    log.error("Feign调用异常: {}", e.getMessage(), e);
    return Result.fail(ErrorCode.INTERNAL_ERROR);
}
```

**危害**：
- 暴露数据库表名、字段名、SQL语句 → 攻击者可构造 SQL 注入
- 暴露内部服务地址和端口 → 攻击者可直连内部服务绕过网关
- 暴露技术栈版本信息 → 攻击者可利用已知漏洞

**修复要点**：
- 所有非业务异常统一返回"服务器内部错误"
- 详细信息只记录在日志中，不返回前端
- 业务异常（BusinessException）可以返回具体提示，如"用户名或密码错误"

---

### 1.2 请求头直接信任导致身份伪造

**场景**：从 HTTP 请求头中获取用户身份信息，没有验证来源是否可信。

**坏代码**：
```java
// 拦截器：从请求头获取管理员ID
@Override
public boolean preHandle(HttpServletRequest request, ...) {
    // 直接信任 X-Admin-Id 请求头
    String adminIdHeader = request.getHeader("X-Admin-Id");
    if (adminIdHeader != null) {
        UserContext.setUserId(Long.parseLong(adminIdHeader));
    }
    return true;
}
```

**好代码**：
```java
// 拦截器：只从安全来源获取管理员ID
@Override
public boolean preHandle(HttpServletRequest request, ...) {
    // 只从 Sa-Token Session 获取（Session 是服务端存储的，无法伪造）
    if (StpUtil.isLogin()) {
        Object adminUserId = StpUtil.getSession().get("adminUserId");
        if (adminUserId != null) {
            UserContext.setUserId((Long) adminUserId);
        }
    }
    return true;
}
```

**危害**：
- 攻击者只需在请求中加 `X-Admin-Id: 1` 就能冒充超级管理员
- 任何被排除登录校验的接口都会被利用

**修复要点**：
- 用户身份只能从服务端 Session/Token 中获取，不能信任请求头
- 微服务间调用通过 Feign 拦截器透传 Token，而非自定义请求头

---

### 1.3 核心接口缺少权限控制

**场景**：管理后台的增删改查接口没有加权限注解，任何已登录用户都能操作。

**坏代码**：
```java
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @GetMapping("/list")
    public Result<PageResult<AdminUserVO>> getAdminUserList(...) {
        // 没有权限注解！任何已登录用户都能查看管理员列表
        return Result.success(adminUserService.getAdminUserList(...));
    }

    @PostMapping
    public Result<Void> createAdminUser(@RequestBody @Validated AdminUserCreateDTO dto) {
        // 没有权限注解！任何已登录用户都能创建管理员
        return Result.success(adminUserService.createAdminUser(dto));
    }
}
```

**好代码**：
```java
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @RequirePermission("admin:user:list")
    @OperationLog(module = "管理员管理", type = OperationType.QUERY, description = "查询管理员列表")
    @GetMapping("/list")
    public Result<PageResult<AdminUserVO>> getAdminUserList(...) {
        return Result.success(adminUserService.getAdminUserList(...));
    }

    @RequirePermission("admin:user:add")
    @OperationLog(module = "管理员管理", type = OperationType.CREATE, description = "新增管理员")
    @PostMapping
    public Result<Void> createAdminUser(@RequestBody @Validated AdminUserCreateDTO dto) {
        return Result.success(adminUserService.createAdminUser(dto));
    }
}
```

**危害**：
- 普通客服可以删除超级管理员
- 任何登录用户可以给自己分配所有权限
- 没有审计日志，出了问题无法追溯

**修复要点**：
- 所有管理接口必须加 `@RequirePermission` 注解
- 所有增删改操作必须加 `@OperationLog` 注解
- 权限标识格式统一：`模块:资源:操作`（如 `admin:user:list`）

---

### 1.4 敏感数据未脱敏

**场景**：API 返回的手机号、邮箱、IP 地址等敏感信息没有做脱敏处理。

**坏代码**：
```java
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    // 手机号直接返回，13812345678
    private String phone;
    // 邮箱直接返回，alice@example.com
    private String email;
}
```

**好代码**：
```java
@Data
public class AdminUserVO {
    private Long id;
    private String username;

    /** 手机号（脱敏显示，如138****5678） */
    @JsonSerialize(using = PhoneDesensitizeSerializer.class)
    private String phone;

    /** 邮箱（脱敏显示，如a***@example.com） */
    @JsonSerialize(using = EmailDesensitizeSerializer.class)
    private String email;
}

// 脱敏序列化器
public class PhoneDesensitizeSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.length() < 7) {
            gen.writeString(value);
            return;
        }
        gen.writeString(value.substring(0, 3) + "****" + value.substring(value.length() - 4));
    }
}
```

**危害**：
- 用户隐私泄露，违反《个人信息保护法》
- 内部员工可以看到完整手机号，存在数据泄露风险

**修复要点**：
- 手机号中间4位替换为星号
- 邮箱@前只保留首字母
- IP地址最后一段替换为星号
- 使用 Jackson 的 `@JsonSerialize` 注解，自动脱敏

---

### 1.5 操作日志记录敏感字段

**场景**：操作日志把请求参数完整记录到数据库，可能包含密码明文。

**坏代码**：
```java
// 脱敏正则只匹配字符串值
for (String field : SENSITIVE_FIELDS) {
    json = json.replaceAll("(\"(" + field + ")\"\\s*:\\s*)\"[^\"]*\"", "$1\"******\"");
}
// 问题：如果 password 是数字类型 "password":123，正则匹配不到，密码被明文记录
```

**好代码**：
```java
// 脱敏正则同时匹配字符串值和非字符串值
for (String field : SENSITIVE_FIELDS) {
    // 匹配字符串值："password":"xxx"
    json = json.replaceAll("(\"(" + field + ")\"\\s*:\\s*)\"[^\"]*\"", "$1\"******\"");
    // 匹配非字符串值："password":123 或 "password":null
    json = json.replaceAll("(\"(" + field + ")\"\\s*:\\s*)(?!\"******\")\\S+", "$1\"******\"");
}

// 敏感字段列表要全面
private static final List<String> SENSITIVE_FIELDS = Arrays.asList(
    "password", "oldPassword", "newPassword", "confirmPassword", "token", "accessToken", "refreshToken"
);
```

**危害**：
- 密码明文被持久化到数据库
- Token 被记录后，任何有数据库访问权限的人都能冒充用户

**修复要点**：
- 脱敏正则要覆盖所有值类型（字符串、数字、布尔、null）
- 敏感字段列表要全面（password、token 等）
- 响应结果也要考虑脱敏

---

## 二、架构问题

### 2.1 Feign 调用无鉴权

**场景**：微服务之间通过 Feign 调用时，不携带登录凭证，下游服务无法识别调用者。

**坏代码**：
```java
// FeignClient 没有配置鉴权
@FeignClient(name = "shop-user", path = "/user", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {
    @GetMapping("/admin/list")
    Result<PageResult<UserVO>> listUsers(...);
    // 调用时不会携带Token，下游服务不知道是谁在调用
}
```

**好代码**：
```java
// 创建 Feign 鉴权配置
@Configuration
public class FeignAuthConfig {
    @Bean
    public RequestInterceptor feignAuthInterceptor() {
        return template -> {
            try {
                if (StpUtil.isLogin()) {
                    String tokenValue = StpUtil.getTokenValue();
                    if (tokenValue != null && !tokenValue.isEmpty()) {
                        template.header("satoken", tokenValue);
                    }
                }
            } catch (Exception e) {
                log.warn("Feign调用获取Token失败: {}", e.getMessage());
            }
        };
    }
}
```

**危害**：
- 任何能访问内网的服务都可以伪造请求
- 下游服务无法做权限校验
- 无法审计是谁触发了操作

**修复要点**：
- 创建 Feign RequestInterceptor，自动透传当前 Token
- 无 Token 场景（如定时任务）不影响调用，只记录警告

---

### 2.2 @Async 使用默认线程池

**场景**：使用 `@Async` 注解但没有配置自定义线程池。

**坏代码**：
```java
@Service
public class AdminOperationLogServiceImpl implements AdminOperationLogService {

    @Async  // 使用默认的 SimpleAsyncTaskExecutor，每次创建新线程，无上限！
    public void recordOperationLog(AdminOperationLog operationLog) {
        adminOperationLogMapper.insert(operationLog);
    }
}
```

**好代码**：
```java
// 1. 配置自定义线程池
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("adminAsyncExecutor")
    public Executor adminAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("admin-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

// 2. 指定使用自定义线程池
@Service
public class AdminOperationLogServiceImpl implements AdminOperationLogService {

    @Async("adminAsyncExecutor")  // 指定线程池，有上限保护
    public void recordOperationLog(AdminOperationLog operationLog) {
        adminOperationLogMapper.insert(operationLog);
    }
}
```

**危害**：
- 默认线程池无上限，高并发下可能创建数千线程导致 OOM
- 队列满后直接拒绝任务，日志数据丢失

**修复要点**：
- 自定义线程池，设置核心线程数、最大线程数、队列容量
- 使用 CallerRunsPolicy 拒绝策略（由调用者线程执行，不丢弃任务）

---

## 三、质量问题

### 3.1 N+1 查询问题

**场景**：在循环中逐个查询关联数据，导致 SQL 查询次数爆炸。

**坏代码**：
```java
// 查询管理员列表 - N+1 问题
public PageResult<AdminUserVO> getAdminUserList(...) {
    // 第1次SQL：查询管理员列表
    Page<AdminUser> page = adminUserMapper.selectPage(...);
    
    List<AdminUserVO> voList = page.getRecords().stream()
        .map(user -> {
            // 每个用户查2次SQL（角色关联 + 角色详情）
            List<AdminRoleVO> roles = getUserRoles(user.getId());
            // 每个用户查1次SQL（部门详情）
            AdminDeptVO dept = getDept(user.getDeptId());
            // 10条数据 = 1 + 10*3 = 31次SQL！
            return convertToVO(user, roles, dept);
        })
        .collect(Collectors.toList());
    return new PageResult<>(voList, page.getTotal());
}
```

**好代码**：
```java
// 查询管理员列表 - 批量预查询
public PageResult<AdminUserVO> getAdminUserList(...) {
    // 第1次SQL：查询管理员列表
    Page<AdminUser> page = adminUserMapper.selectPage(...);
    List<AdminUser> records = page.getRecords();
    
    // 第2次SQL：批量查询所有用户的角色关联
    List<Long> userIds = records.stream().map(AdminUser::getId).collect(Collectors.toList());
    List<AdminUserRole> allUserRoles = adminUserRoleMapper.selectList(
        new LambdaQueryWrapper<AdminUserRole>().in(AdminUserRole::getUserId, userIds)
    );
    Map<Long, List<Long>> userRoleMap = allUserRoles.stream()
        .collect(Collectors.groupingBy(AdminUserRole::getUserId,
            Collectors.mapping(AdminUserRole::getRoleId, Collectors.toList())));
    
    // 第3次SQL：批量查询所有角色
    List<Long> allRoleIds = allUserRoles.stream().map(AdminUserRole::getRoleId).distinct().collect(Collectors.toList());
    Map<Long, AdminRole> roleMap = adminRoleMapper.selectList(
        new LambdaQueryWrapper<AdminRole>().in(AdminRole::getId, allRoleIds)
    ).stream().collect(Collectors.toMap(AdminRole::getId, r -> r));
    
    // 第4次SQL：批量查询所有部门
    List<Long> deptIds = records.stream().map(AdminUser::getDeptId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    Map<Long, AdminDept> deptMap = adminDeptMapper.selectList(
        new LambdaQueryWrapper<AdminDept>().in(AdminDept::getId, deptIds)
    ).stream().collect(Collectors.toMap(AdminDept::getId, d -> d));
    
    // 10条数据 = 4次SQL！从31次降到4次
    List<AdminUserVO> voList = records.stream()
        .map(user -> convertToVO(user, userRoleMap, roleMap, deptMap))
        .collect(Collectors.toList());
    return new PageResult<>(voList, page.getTotal());
}
```

**危害**：
- 100 条数据可能产生 300+ 次 SQL 查询
- 数据库连接池被占满，其他请求排队等待
- 接口响应时间随数据量线性增长

**修复要点**：
- 先收集所有需要查询的 ID
- 一次性批量查询，构建 Map
- 在内存中匹配关联数据

---

### 3.2 循环单条 INSERT

**场景**：批量插入关联数据时，循环调用单条 INSERT。

**坏代码**：
```java
// 循环单条插入 - 5个角色 = 5次SQL
private void saveUserRoles(Long userId, List<Long> roleIds) {
    for (Long roleId : roleIds) {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        adminUserRoleMapper.insert(userRole);  // 每次循环执行一条INSERT
    }
}
```

**好代码**：
```java
// 批量插入 - 5个角色 = 1次SQL
private void saveUserRoles(Long userId, List<Long> roleIds) {
    List<AdminUserRole> userRoles = roleIds.stream().map(roleId -> {
        AdminUserRole userRole = new AdminUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }).collect(Collectors.toList());
    
    // 使用MyBatis-Plus的Db工具类批量插入
    Db.saveBatch(userRoles);
}
```

**危害**：
- 每次循环一次网络往返，10条数据10次网络开销
- 大量INSERT语句无法合并，性能差

**修复要点**：
- 使用 `Db.saveBatch()` 或 `IService.saveBatch()` 批量插入
- MyBatis-Plus 会生成一条批量 INSERT 语句

---

### 3.3 @RequestBody 缺少 @Validated

**场景**：Controller 方法接收 DTO 参数时，没有加 `@Validated` 注解，导致 DTO 上的校验注解不生效。

**坏代码**：
```java
@PostMapping
public Result<Void> addBrand(@RequestBody BrandDTO dto) {
    // DTO上有@NotBlank注解，但Controller没加@Validated，校验不生效！
    // 前端可以传空名称的品牌
    return Result.success(brandFeignClient.addBrand(dto));
}
```

**好代码**：
```java
@PostMapping
public Result<Void> addBrand(@RequestBody @Validated BrandDTO dto) {
    // @Validated 让DTO上的校验注解生效
    // 如果name为空，会自动返回400错误："name: 不能为空"
    return Result.success(brandFeignClient.addBrand(dto));
}
```

**危害**：
- 任何非法数据都能写入数据库
- 可能导致空指针、数据不一致等问题

**修复要点**：
- 所有 `@RequestBody` 参数必须加 `@Validated`
- DTO 字段要加完整的校验注解（`@NotBlank`、`@Size`、`@Pattern` 等）

---

## 四、性能问题

### 4.1 权限查询无缓存

**场景**：每次权限校验都查数据库，高频接口下数据库压力大。

**坏代码**：
```java
@Override
public List<String> getPermissionList(Object loginId, String loginType) {
    // 每次都查4次数据库：userRole → role → rolePermission → permission
    return getAdminPermissions(Long.parseLong(loginId.toString()));
}
```

**好代码**：
```java
@Override
public List<String> getPermissionList(Object loginId, String loginType) {
    // 先从Session缓存获取
    List<String> cached = (List<String>) StpUtil.getSessionByLoginId(loginId).get("permissionList");
    if (cached != null) {
        return cached;  // 缓存命中，0次SQL
    }
    
    // 缓存未命中，查数据库
    List<String> permissions = getAdminPermissions(Long.parseLong(loginId.toString()));
    // 存入Session缓存（Session随Token过期自动失效）
    StpUtil.getSessionByLoginId(loginId).set("permissionList", permissions);
    return permissions;
}
```

**危害**：
- 一个页面可能触发 10+ 次权限校验
- 每次校验 4 次 SQL，一个页面就是 40+ 次查询
- 高并发下数据库可能被压垮

**修复要点**：
- 使用 Sa-Token Session 缓存权限列表
- Session 随 Token 过期自动失效，无需手动管理 TTL
- 权限变更时需要清除对应用户的 Session 缓存

---

### 4.2 递归查询数据库（N+1 的变种）

**场景**：递归查询树形数据时，每层递归查一次数据库。

**坏代码**：
```java
// 递归查询子部门 - 每层查一次数据库
private void findChildDeptIds(Long parentId, List<Long> result) {
    List<AdminDept> children = adminDeptMapper.selectList(
        new LambdaQueryWrapper<AdminDept>().eq(AdminDept::getParentId, parentId)
    );
    for (AdminDept dept : children) {
        result.add(dept.getId());
        findChildDeptIds(dept.getId(), result);  // 递归，又查一次数据库
    }
}
// 5层部门 = 5次SQL
```

**好代码**：
```java
// 一次查询所有部门，内存中递归
private List<Long> getChildDeptIds(Long parentId) {
    // 1次SQL：查出所有部门
    List<AdminDept> allDepts = adminDeptMapper.selectList(null);
    
    List<Long> deptIds = new ArrayList<>();
    deptIds.add(parentId);
    findChildDeptIdsInMemory(parentId, allDepts, deptIds);
    return deptIds;
}

private void findChildDeptIdsInMemory(Long parentId, List<AdminDept> allDepts, List<Long> result) {
    for (AdminDept dept : allDepts) {
        if (parentId.equals(dept.getParentId())) {
            result.add(dept.getId());
            findChildDeptIdsInMemory(dept.getId(), allDepts, result);  // 内存递归，不查数据库
        }
    }
}
// 5层部门 = 1次SQL
```

**危害**：
- 部门层级越深，SQL 查询越多
- 高并发下大量递归查询可能拖垮数据库

**修复要点**：
- 一次查询所有数据，在内存中构建树
- 数据量不大（通常几十到几百条）时适用
- 数据量特别大时，考虑使用 `WITH RECURSIVE` CTE 查询

---

## 五、可维护性问题

### 5.1 代码重复（DRY 原则违反）

**场景**：相同的逻辑在多个文件中重复实现。

**坏代码**：
```java
// OperationLogAspect.java
private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        ip = request.getRemoteAddr();
    }
    if (ip != null && ip.contains(",")) {
        ip = ip.split(",")[0].trim();
    }
    return ip;
}

// RequirePermissionAspect.java - 完全相同的代码又写了一遍！
private String getClientIp() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) return "unknown";
    HttpServletRequest request = attributes.getRequest();
    String ip = request.getHeader("X-Forwarded-For");
    // ... 完全相同的逻辑 ...
}

// SaTokenConfig.java - 又写了一遍！
```

**好代码**：
```java
// IpUtils.java - 统一的工具类
public class IpUtils {
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    public static String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "unknown";
        return getClientIp(attributes.getRequest());
    }
}

// 其他地方统一调用
String ip = IpUtils.getClientIp(request);  // 有request时
String ip = IpUtils.getClientIp();          // 没有request时
```

**危害**：
- 修改时容易遗漏某个地方
- 3份代码可能逐渐产生差异，行为不一致

**修复要点**：
- 提取公共工具类，统一调用
- 修改一处就全部生效

---

### 5.2 魔法数字

**场景**：代码中直接使用数字常量，没有用枚举或常量说明含义。

**坏代码**：
```java
if (maxDataScope == 1) {  // 1是什么意思？要看数据库设计文档才知道
    return;
}
maxDataScope = 4;  // 4又是什么？
switch (dataScopeCode) {
    case 2:  // 2代表什么？
    case 3:  // 3代表什么？
    case 4:  // 4代表什么？
}
```

**好代码**：
```java
if (maxDataScope == AdminDataScopeEnum.ALL.getCode()) {  // 一看就懂：全部数据权限
    return;
}
maxDataScope = AdminDataScopeEnum.SELF.getCode();  // 一看就懂：仅本人数据
switch (dataScopeCode) {
    case 2: // DEPT 本部门数据
    case 3: // DEPT_AND_CHILD 本部门及下级
    case 4: // SELF 仅本人数据
}
// 更好的做法：直接用枚举比较
if (maxDataScope == AdminDataScopeEnum.ALL.getCode()) { ... }
```

**危害**：
- 新人看不懂代码含义
- 数字写错不容易发现（`== 1` 写成 `== 2`）
- 修改枚举值时需要全文搜索替换

**修复要点**：
- 定义枚举类，用枚举常量替代魔法数字
- 至少在 switch-case 中添加注释说明

---

### 5.3 分页参数无上限保护

**场景**：分页查询接口的 pageSize 参数没有最大值限制。

**坏代码**：
```java
// QueryDTO 自己定义 pageSize，没有上限
@Data
public class AdminUserQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;  // 攻击者可以传 pageSize=999999
    private String keyword;
}
```

**好代码**：
```java
// 继承 PageRequest，自动获得 @Max(100) 保护
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends PageRequest {
    // pageNum 和 pageSize 由父类提供，自带校验
    // @Min(1) @Max(100) pageSize 不能超过100
    private String keyword;
}
```

**危害**：
- 攻击者传 `pageSize=999999` 导致一次查询百万条数据
- 可能导致 OOM、数据库超时
- 影响其他用户的请求性能

**修复要点**：
- 所有分页查询 DTO 继承 `PageRequest`
- `PageRequest` 中 `pageSize` 加 `@Max(100)` 限制
- Controller 方法参数加 `@Validated`

---

## 六、安全策略问题

### 6.1 安全过滤失败时静默跳过

**场景**：数据权限过滤等安全机制失败时，选择跳过而非拒绝。

**坏代码**：
```java
try {
    String modifiedSql = buildDataScopeSql(originalSql, ...);
    // 修改SQL...
} catch (Exception e) {
    // SQL解析失败就跳过过滤，用户可以看到不该看的数据
    log.warn("数据权限过滤异常，跳过过滤: {}", e.getMessage());
}
```

**好代码**：
```java
try {
    String modifiedSql = buildDataScopeSql(originalSql, ...);
    // 修改SQL...
} catch (Exception e) {
    // 安全过滤失败时拒绝查询，宁可让请求失败也不能泄露数据
    log.error("数据权限过滤异常，拒绝查询以防止越权访问", e);
    throw new SQLException("数据权限过滤异常，拒绝查询");
}
```

**危害**：
- 攻击者可以故意构造复杂SQL导致解析失败，绕过数据权限
- 用户可能看到其他部门的数据

**修复要点**：
- 安全相关逻辑失败时，应选择"拒绝"而非"放行"
- 遵循"最小权限原则"：宁可误拒，不可误放

---

## 七、速查清单

写代码前过一遍这个清单，可以避免 80% 的常见问题：

| 检查项 | 要点 |
|--------|------|
| 接口权限 | 所有管理接口是否加了 `@RequirePermission`？ |
| 操作审计 | 所有增删改操作是否加了 `@OperationLog`？ |
| 参数校验 | `@RequestBody` 是否加了 `@Validated`？DTO字段校验是否完整？ |
| 敏感数据 | 返回前端的手机号/邮箱/IP是否脱敏？日志是否排除密码？ |
| 错误处理 | 系统异常是否只返回"服务器内部错误"？是否记录了详细日志？ |
| N+1查询 | 列表查询是否批量预查询关联数据？ |
| 批量操作 | 循环INSERT是否改为 `saveBatch`？ |
| 分页保护 | pageSize 是否有 `@Max` 限制？ |
| 异步线程 | `@Async` 是否指定了自定义线程池？ |
| 代码重复 | 相同逻辑是否提取为工具类？ |
| 魔法数字 | 是否用枚举常量替代了硬编码数字？ |
| 安全失败 | 安全过滤失败时是"拒绝"还是"放行"？ |
