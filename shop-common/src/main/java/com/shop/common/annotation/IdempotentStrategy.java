package com.shop.common.annotation;

/**
 * 幂等策略枚举
 * <p>
 * 定义接口幂等性的实现策略，配合 {@link Idempotent} 注解使用。
 * 不同业务场景可选择最适合的幂等策略。
 * </p>
 *
 * <ul>
 *   <li>SETNX - 基于 Redis SETNX 原子操作，适用于大多数写接口</li>
 *   <li>TOKEN - 基于 Token 令牌机制，需前端先获取 Token 再提交（预留扩展）</li>
 *   <li>STATE - 基于业务状态判断，依赖数据库状态字段防重（预留扩展）</li>
 * </ul>
 */
public enum IdempotentStrategy {

    /**
     * SETNX策略：使用 Redis SETNX + EXPIRE 原子操作保证幂等
     * <p>首次请求 SETNX 成功放行，重复请求 SETNX 失败拦截。</p>
     */
    SETNX,

    /**
     * TOKEN策略：基于 Token 令牌的幂等校验
     * <p>前端先获取 Token，提交时携带 Token，服务端验证并删除 Token。</p>
     * <p>当前版本预留，后续按需实现。</p>
     */
    TOKEN,

    /**
     * STATE策略：基于业务状态字段判断幂等
     * <p>依赖数据库中的状态字段（如订单状态、支付状态）判断是否重复操作。</p>
     * <p>当前版本预留，后续按需实现。</p>
     */
    STATE
}