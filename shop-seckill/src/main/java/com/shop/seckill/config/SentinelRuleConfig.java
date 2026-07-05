package com.shop.seckill.config;

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
 * </p>
 * <p>
 * 小白理解：限流规则就像活动报名处的限流牌"每秒最多接 50 个报名"，
 * 超过就让排队等候，避免工作人员忙不过来。
 * </p>
 */
@Slf4j
@Configuration
public class SentinelRuleConfig {

    /**
     * 服务启动时初始化限流规则
     * <p>
     * @PostConstruct 确保服务启动时就加载规则，不会存在空窗期。
     * </p>
     */
    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        // ========== seckill:activity:create 资源限流规则 ==========
        // 创建秒杀活动是低频操作（商家偶尔创建），QPS 上限设为 50
        // 超过 50 QPS 说明可能是恶意调用或脚本攻击
        rules.add(buildFlowRule("seckill:activity:create", 50));

        // ========== seckill:activity:offline 资源限流规则 ==========
        // 下架秒杀活动也是低频操作，QPS 上限设为 50
        rules.add(buildFlowRule("seckill:activity:offline", 50));

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
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setLimitApp("default");
        rule.setStrategy(RuleConstant.STRATEGY_DIRECT);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }
}
