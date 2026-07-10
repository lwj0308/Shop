package com.shop.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性注解
 * <p>
 * 声明式幂等防护注解，标记在 Controller 方法上，通过 AOP 切面自动执行幂等校验。
 * 幂等键解析优先级：Header > SpEL表达式 > 自动生成（类名+方法名+参数哈希）。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * // 示例1：对外接口，从前端 Header 获取幂等键
 * {@code @Idempotent(prefix = "idempotent:order:cancel:")}
 * public Result<Void> cancelOrder(@PathVariable Long id) { ... }
 *
 * // 示例2：内部 Feign 接口，从业务参数 SpEL 提取幂等键
 * {@code @Idempotent(key = "#dto.orderNo", prefix = "idempotent:product:deduct:")}
 * public Result<Void> deductStock(StockDeductDTO dto) { ... }
 *
 * // 示例3：短过期时间场景（秒杀）
 * {@code @Idempotent(expire = 5, prefix = "idempotent:seckill:")}
 * public Result<Void> seckill() { ... }
 *
 * // 示例4：组合键（用户ID + 业务ID）
 * {@code @Idempotent(key = "#userId + '_' + #couponId", prefix = "idempotent:user:coupon:")}
 * public Result<Void> receiveCoupon(Long userId, Long couponId) { ... }
 * </pre>
 *
 * @see IdempotentStrategy
 * @see IdempotentAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Redis Key 前缀
     * <p>建议格式：{@code idempotent:{服务名}:{业务标识}:}，例如 {@code idempotent:order:cancel:}</p>
     */
    String prefix() default "idempotent:";

    /**
     * SpEL 表达式，从方法参数提取幂等键
     * <p>支持 {@code #参数名}、{@code #参数名.属性}、字符串拼接等 SpEL 语法。</p>
     * <p>为空时按优先级回退到 Header 或自动生成。</p>
     */
    String key() default "";

    /**
     * 从请求 Header 提取幂等键的 Header 名称
     * <p>默认值 {@code X-Idempotent-Key}，与前端拦截器约定一致。</p>
     * <p>设为空字符串则不从 Header 提取。</p>
     */
    String header() default "X-Idempotent-Key";

    /**
     * 过期时间（秒）
     * <p>在此时间窗口内，相同幂等键的重复请求将被拦截。</p>
     * <p>默认 300 秒（5分钟），秒杀等短时场景可设为 5 秒。</p>
     */
    long expire() default 300;

    /**
     * 重复请求提示语
     * <p>当拦截到重复请求时，返回给前端的错误提示信息。</p>
     */
    String message() default "请勿重复操作";

    /**
     * 幂等策略
     * <p>默认使用 SETNX 策略（Redis 原子操作），TOKEN 和 STATE 策略预留扩展。</p>
     *
     * @see IdempotentStrategy#SETNX
     */
    IdempotentStrategy strategy() default IdempotentStrategy.SETNX;
}