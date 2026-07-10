package com.shop.common.annotation;

import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * 幂等切面
 * <p>
 * 拦截带有 {@link Idempotent} 注解的方法，自动执行幂等校验。
 * 核心流程：解析幂等键 → Redis SETNX原子操作 → 放行/拦截 → 异常回滚。
 * </p>
 *
 * <h3>幂等键解析优先级：</h3>
 * <ol>
 *   <li>Header：从请求 Header（默认 X-Idempotent-Key）获取</li>
 *   <li>SpEL：从注解 key 属性的 SpEL 表达式解析</li>
 *   <li>自动生成：className.methodName:argsHash</li>
 * </ol>
 *
 * <h3>降级策略：</h3>
 * <p>Redis 不可用时降级放行（记录 WARN 日志），幂等是防护而非阻断。</p>
 *
 * <h3>异常回滚：</h3>
 * <p>业务方法抛出异常时，删除已设置的 Redis Key，允许客户端重试。</p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;

    /** SETNX + EXPIRE 原子 Lua 脚本，保证设置值和过期时间的原子性 */
    private static final String SETNX_LUA = """
            if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then
                redis.call('expire', KEYS[1], ARGV[2])
                return 1
            else
                return 0
            end
            """;

    /** 删除 Key Lua 脚本（异常回滚时使用） */
    private static final String DEL_LUA = """
            redis.call('del', KEYS[1])
            return 1
            """;

    /** SpEL 表达式解析器（线程安全，可全局复用） */
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /** 参数名发现器（用于将 SpEL 中的 #参数名 映射到实际参数值） */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 环绕通知：拦截所有标注了 {@link Idempotent} 注解的方法
     *
     * @param joinPoint  AOP 连接点
     * @param idempotent 幂等注解实例
     * @return 业务方法执行结果
     */
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // 仅处理 SETNX 策略，TOKEN/STATE 策略预留扩展
        if (idempotent.strategy() != IdempotentStrategy.SETNX) {
            return proceedSafely(joinPoint);
        }

        // 1. 解析幂等键
        String idempotentKey = resolveKey(joinPoint, idempotent);
        String redisKey = idempotent.prefix() + idempotentKey;

        // 2. SETNX 原子操作（含降级策略）
        boolean acquired = setnxWithFallback(redisKey, idempotent.expire());
        if (!acquired) {
            log.warn("幂等拦截-重复请求: key={}", redisKey);
            throw new BusinessException(ErrorCode.REPEAT_REQUEST, idempotent.message());
        }

        // 3. 执行业务方法
        try {
            Object result = joinPoint.proceed();
            // 正常执行完毕，Key 自然过期即可
            return result;
        } catch (BusinessException e) {
            // 业务异常，回滚幂等 Key 允许重试
            deleteWithFallback(redisKey);
            log.info("幂等回滚-业务异常: key={}", redisKey);
            throw e;
        } catch (Throwable e) {
            // 其他异常（如运行时异常），同样回滚 Key
            deleteWithFallback(redisKey);
            log.info("幂等回滚-系统异常: key={}", redisKey);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 解析幂等键，按优先级依次尝试：Header → SpEL → 自动生成
     *
     * @param joinPoint  AOP 连接点
     * @param idempotent 幂等注解
     * @return 解析后的幂等键字符串
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // 优先级1：从 HTTP Header 获取
        String headerKey = resolveFromHeader(idempotent.header());
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }

        // 优先级2：从 SpEL 表达式解析
        String spelKey = resolveFromSpEL(joinPoint, idempotent.key());
        if (spelKey != null && !spelKey.isBlank()) {
            return spelKey;
        }

        // 优先级3：自动生成（类名.方法名:参数哈希）
        return generateAutoKey(joinPoint);
    }

    /**
     * 从 HTTP 请求 Header 中获取幂等键
     *
     * @param headerName Header 名称
     * @return Header 值，非 HTTP 请求场景返回 null
     */
    private String resolveFromHeader(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            return null;
        }
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(headerName);
        } catch (Exception e) {
            // 非 HTTP 请求场景（如内部 Feign 调用无 RequestContextHolder），跳过 Header 解析
            return null;
        }
    }

    /**
     * 从 SpEL 表达式解析幂等键
     * <p>支持 #参数名、#参数名.属性、字符串拼接等 SpEL 语法。</p>
     *
     * @param joinPoint     AOP 连接点
     * @param spelExpression SpEL 表达式
     * @return 解析结果字符串，表达式为空或解析失败返回 null
     */
    private String resolveFromSpEL(ProceedingJoinPoint joinPoint, String spelExpression) {
        if (spelExpression == null || spelExpression.isBlank()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
            Object[] args = joinPoint.getArgs();

            // 构建 SpEL 上下文，将方法参数绑定到 #参数名
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }

            Expression expression = SPEL_PARSER.parseExpression(spelExpression);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("幂等SpEL解析失败: expression={}", spelExpression, e);
            return null;
        }
    }

    /**
     * 自动生成幂等键：className.methodName:argsHash
     * <p>对参数做 MD5 哈希，避免 Key 过长和敏感信息泄露。</p>
     *
     * @param joinPoint AOP 连接点
     * @return 自动生成的幂等键
     */
    private String generateAutoKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 拼接当前用户ID，实现用户级幂等隔离
        Long userId = SecurityUtils.getCurrentUserId();
        String userPart = userId != null ? userId.toString() : "anonymous";

        // 对参数做 MD5 哈希
        Object[] args = joinPoint.getArgs();
        String argsHash = md5Digest(Objects.hash(args));

        return className + "." + methodName + ":" + userPart + ":" + argsHash;
    }

    /**
     * SETNX + EXPIRE 原子操作，Redis 不可用时降级放行
     *
     * @param key    Redis Key
     * @param expire 过期时间（秒）
     * @return true=首次请求放行，false=重复请求拦截
     */
    private boolean setnxWithFallback(String key, long expire) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SETNX_LUA, Long.class);
            Long result = stringRedisTemplate.execute(script, List.of(key), "1", String.valueOf(expire));
            return result != null && result == 1L;
        } catch (Exception e) {
            // Redis 不可用时降级放行，幂等是防护而非阻断
            log.warn("幂等SETNX异常，降级放行: key={}", key, e);
            return true;
        }
    }

    /**
     * 删除 Redis Key（异常回滚），删除失败仅记录日志不影响主流程
     *
     * @param key Redis Key
     */
    private void deleteWithFallback(String key) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEL_LUA, Long.class);
            stringRedisTemplate.execute(script, List.of(key));
        } catch (Exception e) {
            log.warn("幂等Key删除异常: key={}", key, e);
        }
    }

    /**
     * 直接执行业务方法（非 SETNX 策略时使用）
     *
     * @param joinPoint AOP 连接点
     * @return 业务方法执行结果
     */
    private Object proceedSafely(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * MD5 哈希摘要，用于参数哈希化
     *
     * @param value 待哈希的整数值
     * @return 8位十六进制哈希字符串
     */
    private static String md5Digest(int value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(4, digest.length); i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 不可用时回退到 hashCode
            return String.valueOf(value);
        }
    }
}