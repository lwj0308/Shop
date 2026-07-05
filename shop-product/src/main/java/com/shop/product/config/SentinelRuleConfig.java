package com.shop.product.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则配置（硬编码方式）
 * <p>
 * RL-02 阶段使用硬编码方式初始化限流规则，RL-05 阶段会迁移到 Nacos 持久化。
 * 硬编码的好处是简单直接，不依赖外部配置中心，适合开发阶段验证。
 * </p>
 * <p>
 * 限流规则说明：
 * - grade=FLOW_GRADE_QPS：按 QPS 限流（每秒请求数）
 * - limitApp="default"：对所有调用方生效
 * - strategy=STRATEGY_DIRECT：直接限流（不区分调用来源）
 * - controlBehavior=CONTROL_BEHAVIOR_DEFAULT：直接拒绝（超限的请求返回 BlockException）
 * </p>
 * <p>
 * 小白理解：限流规则就像超市门口的限流牌"每秒最多进100人"，
 * 超过这个人数就拒绝进入，等有人出来才能再进。
 * </p>
 */
@Slf4j
@Configuration
public class SentinelRuleConfig {

    /**
     * 服务启动时初始化限流规则
     * <p>
     * @PostConstruct 注解确保这个方法在 Bean 初始化完成后、服务开始处理请求前执行。
     * 这样服务一启动就有限流保护，不会存在"启动后几秒内没限流"的空窗期。
     * </p>
     */
    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        // ========== stock:deduct 资源限流规则 ==========
        // 库存扣减是高频操作（每个订单都要扣库存），QPS 上限设为 1000
        // 超过 1000 QPS 说明有异常流量（可能是刷单或爬虫），需要限流保护
        rules.add(buildFlowRule("stock:deduct", 1000));

        // 加载规则到 Sentinel 规则管理器
        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 限流规则初始化完成，共 {} 条规则", rules.size());
    }

    /**
     * 构建一条限流规则
     *
     * @param resource 资源名（与 @SentinelResource 的 value 对应）
     * @param qps      QPS 阈值（每秒允许的最大请求数）
     * @return FlowRule 限流规则对象
     */
    private FlowRule buildFlowRule(String resource, int qps) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);   // 按 QPS 限流
        rule.setCount(qps);                            // QPS 阈值
        rule.setLimitApp("default");                   // 对所有调用方生效
        rule.setStrategy(RuleConstant.STRATEGY_DIRECT); // 直接限流
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT); // 超限直接拒绝
        return rule;
    }
}
