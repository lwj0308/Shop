package com.shop.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.gateway.config.WhitelistConfig;
import com.shop.gateway.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 全局鉴权过滤器
 * <p>
 * 拦截所有经过网关的请求，校验Sa-Token是否有效。
 * 白名单路径直接放行，其他路径必须携带有效Token。
 * 校验通过后，将用户ID和角色信息写入Header传递给下游服务。
 * </p>
 * <p>
 * 小白理解：这个过滤器就像商场的保安，每个人进来都要检查"通行证"（Token）。
 * 有些区域（白名单）不需要通行证就能进，比如大厅、洗手间；
 * 其他区域必须有通行证才能进，而且保安还会在你的通行证上盖章（写Header），
 * 告诉里面的人你是谁、有什么权限。
 * </p>
 * <p>
 * 重要：Gateway是WebFlux响应式，使用ServerWebExchange，不是HttpServletRequest！
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 白名单配置，从Nacos读取 */
    private final WhitelistConfig whitelistConfig;

    /** Ant风格路径匹配器，支持通配符，比如 /api/product/** 可以匹配 /api/product/list */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 自定义Header名称：用户ID，下游服务通过这个Header知道当前登录用户是谁 */
    private static final String HEADER_USER_ID = "X-User-Id";

    /** 自定义Header名称：用户角色，下游服务通过这个Header判断用户权限 */
    private static final String HEADER_USER_ROLE = "X-User-Role";

    /** 需要清除的伪造Header列表，防止外部恶意传入 */
    private static final List<String> SENSITIVE_HEADERS = List.of(
            HEADER_USER_ID,
            HEADER_USER_ROLE,
            "X-User-Name",
            "X-User-Permissions"
    );

    /** Token续期阈值：剩余有效期小于总时长的1/3时自动续期 */
    private static final double RENEW_THRESHOLD = 1.0 / 3.0;

    /**
     * 过滤器的核心方法，每个请求都会经过这里
     * <p>
     * 执行流程：
     * 1. 判断请求路径是否在完全公开白名单内 → 是：直接放行
     * 2. 从Header获取Token → 没有Token：检查是否在登录用户白名单内，不在则返回401
     * 3. 校验Token格式（JWT结构检查）
     * 4. 用Sa-Token校验Token → 无效：返回401
     * 5. 清除外部可能伪造的Header，写入真实的用户信息
     * 6. Token续期检查：快过期时自动续期
     * 7. 放行
     * </p>
     *
     * @param exchange WebFlux的上下文对象，包含请求和响应信息
     * @param chain    过滤器链，调用chain.filter()表示放行到下一个过滤器
     * @return Mono<Void> 响应式返回值
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 第一步：检查是否在完全公开白名单内，这些路径不需要登录
        if (isWhitelisted(path)) {
            log.debug("完全公开白名单路径，直接放行：{}", path);
            // 安全修复：白名单路径也要剥离敏感Header，防止伪造X-User-Id等身份信息
            // 即使是公开接口，也不能让外部请求携带伪造的用户身份Header到达下游服务
            ServerHttpRequest whitelistedRequest = exchange.getRequest().mutate()
                    .headers(httpHeaders -> SENSITIVE_HEADERS.forEach(httpHeaders::remove))
                    .build();
            return chain.filter(exchange.mutate().request(whitelistedRequest).build());
        }

        // 第二步：从请求头获取Token
        String token = getTokenFromRequest(exchange);

        // 没有Token的情况
        if (token == null || token.isEmpty()) {
            // 检查是否在"仅限登录用户"白名单内（不在则拒绝）
            if (isAuthWhitelisted(path)) {
                log.debug("登录用户白名单路径，但未携带Token，拒绝访问：{}", path);
                return ResponseUtil.writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "请先登录");
            }
            log.warn("请求未携带Token，路径：{}", path);
            return ResponseUtil.writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录");
        }

        // 第三步：校验Token格式（基本的JWT结构检查）
        if (!isValidTokenFormat(token)) {
            log.warn("Token格式不合法，路径：{}", path);
            return ResponseUtil.writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "登录凭证格式错误");
        }

        // 第四步：使用Sa-Token校验Token是否有效
        Object loginId;
        try {
            // getLoginIdByToken：根据Token值获取对应的用户ID
            // 如果Token无效或过期，返回null
            loginId = StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            log.warn("Token校验异常，路径：{}，原因：{}", path, e.getMessage());
            return ResponseUtil.writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已过期");
        }

        // Token无效，返回401
        if (loginId == null) {
            log.warn("Token无效，路径：{}", path);
            return ResponseUtil.writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已过期");
        }

        // 第五步：校验通过，清除外部可能伪造的Header，写入真实的用户信息
        // 这一步非常重要！防止恶意用户自己传 X-User-Id 来冒充别人

        // 用 getRoleList(loginId) 而不是 getRoleList()
        // 因为网关的 GatewayFilter 中 Sa-Token 上下文不可用（WebFlux 响应式线程模型），
        // getRoleList() 需要从上下文获取当前登录ID，会报 "SaTokenContext 上下文尚未初始化"
        // getRoleList(loginId) 直接通过 loginId 查询角色，不需要上下文
        List<String> roleList = StpUtil.getRoleList(loginId);
        String roles = roleList != null && !roleList.isEmpty()
                ? String.join(",", roleList) : "";

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(httpHeaders -> {
                    // 先清除所有敏感Header（防止伪造）
                    SENSITIVE_HEADERS.forEach(httpHeaders::remove);
                    // 写入认证后的真实用户信息
                    httpHeaders.add(HEADER_USER_ID, loginId.toString());
                    httpHeaders.add(HEADER_USER_ROLE, roles);
                    // 替换 Authorization 头为纯 token 值（去掉 Bearer 前缀）
                    // 网关收到的 Authorization 是 "Bearer xxx"，但下游 Sa-Token 期望纯 token "xxx"
                    // 如果不替换，下游会拿 "Bearer xxx" 当 token 查询，导致 "token 无效"
                    httpHeaders.remove("Authorization");
                    httpHeaders.add("Authorization", token);
                })
                .build();

        // 第六步：Token续期检查
        // 如果Token剩余有效期小于总时长的1/3，自动续期，避免用户使用中突然掉线
        tryRenewToken(token, loginId.toString());

        // 用修改后的请求创建新的exchange，继续往下走
        ServerWebExchange newExchange = exchange.mutate().request(request).build();
        log.debug("鉴权通过，用户ID：{}，路径：{}", loginId, path);
        return chain.filter(newExchange);
    }

    /**
     * 判断请求路径是否在完全公开白名单内
     * <p>
     * 使用AntPathMatcher进行路径匹配，支持通配符：
     * - ? 匹配一个字符
     * - * 匹配一层路径内的任意字符
     * - ** 匹配任意层路径
     * </p>
     *
     * @param path 请求路径，比如 /api/user/login
     * @return true=在白名单内（不需要登录），false=不在白名单（需要登录）
     */
    private boolean isWhitelisted(String path) {
        List<String> whitelist = whitelistConfig.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        // 遍历白名单，只要有一个匹配就放行
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 判断请求路径是否在"仅限登录用户"白名单内
     * <p>
     * 这些接口需要登录（有有效Token），但不需要特定角色。
     * 比如商品浏览、搜索等接口，任何登录用户都能访问。
     * </p>
     *
     * @param path 请求路径
     * @return true=在登录用户白名单内，false=不在
     */
    private boolean isAuthWhitelisted(String path) {
        List<String> authWhitelist = whitelistConfig.getAuthWhitelist();
        if (authWhitelist == null || authWhitelist.isEmpty()) {
            return false;
        }
        return authWhitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 从请求头中获取Token
     * <p>
     * 支持两种方式传Token：
     * 1. Authorization: Bearer xxx（标准方式，前端常用）
     * 2. Authorization: xxx（直接传Token值）
     * </p>
     *
     * @param exchange WebFlux上下文
     * @return Token字符串，没有则返回null
     */
    private String getTokenFromRequest(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isEmpty()) {
            return null;
        }
        // 如果是 "Bearer xxx" 格式，去掉 "Bearer " 前缀
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    /**
     * 校验Token格式是否合法
     * <p>
     * Sa-Token默认使用uuid风格Token，这里做基本格式校验：
     * - Token不能太短（至少16个字符）
     * - Token不能包含特殊字符（只允许字母、数字、横线）
     * </p>
     * <p>
     * 如果项目后续切换到JWT风格Token，可以改为校验JWT三段式结构：
     * header.payload.signature
     * </p>
     *
     * @param token Token字符串
     * @return true=格式合法，false=格式不合法
     */
    private boolean isValidTokenFormat(String token) {
        if (token.length() < 16) {
            return false;
        }
        // 只允许字母、数字、横线，防止注入攻击
        return token.matches("^[a-zA-Z0-9\\-]+$");
    }

    /**
     * 尝试为快过期的Token续期
     * <p>
     * 当Token剩余有效期小于总时长的1/3时，自动续期。
     * 这样用户在持续使用系统时不会突然掉线，提升用户体验。
     * </p>
     * <p>
     * 小白理解：就像图书馆借书，如果书快到期了你还在看，
     * 系统会自动帮你续借，不用你特意跑去续借。
     * </p>
     *
     * @param token   Token字符串
     * @param loginId 用户ID
     */
    private void tryRenewToken(String token, String loginId) {
        try {
            // 获取Token的剩余有效期（秒）
            // -1表示永不过期，-2表示已过期
            long timeout = StpUtil.stpLogic.getTokenTimeout(token);

            // 永不过期或已过期的Token不需要续期
            if (timeout == -1 || timeout <= 0) {
                return;
            }

            // 获取配置的Token总有效期（秒）
            long totalTimeout = StpUtil.stpLogic.getConfigOrGlobal().getTimeout();
            if (totalTimeout <= 0) {
                return;
            }

            // 剩余有效期小于总时长的1/3时续期
            if (timeout < totalTimeout * RENEW_THRESHOLD) {
                StpUtil.renewTimeout(totalTimeout);
                log.debug("Token自动续期，用户ID：{}，剩余有效期：{}s，续期至：{}s",
                        loginId, timeout, totalTimeout);
            }
        } catch (Exception e) {
            // 续期失败不影响正常请求，只记录警告日志
            log.warn("Token续期失败，用户ID：{}，原因：{}", loginId, e.getMessage());
        }
    }

    /**
     * 过滤器的执行顺序，数字越小越先执行
     * <p>
     * 鉴权过滤器的优先级要高于日志过滤器，先检查权限再记录日志。
     * 这里设为-100，确保在其他过滤器之前执行。
     * </p>
     * <p>
     * Filter执行顺序：RequestIdFilter(-200) → RequestLogFilter(-90) → AuthGlobalFilter(-100)
     * 注意：Spring Cloud Gateway的GlobalFilter按order值从小到大执行
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
