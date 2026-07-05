package com.shop.order.config;

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
 * 小白理解：限流规则就像餐厅的限流牌"每秒最多接 100 桌"，
 * 超过就告诉新来的客人"请稍等"，避免厨房忙不过来。
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

        // ========== order:create 资源限流规则 ==========
        // 下单接口限制 100 QPS，超过说明有异常流量（如刷单脚本）
        // 下单涉及数据库事务、库存扣减、优惠券核销等重操作，不能让 QPS 太高
        rules.add(buildFlowRule("order:create", 100));

        // ========== seckill:grab 资源限流规则 ==========
        // 秒杀抢购接口限制 2000 QPS，秒杀场景本身就有高并发
        // 但超过 2000 QPS 可能是恶意脚本，需要限制
        // 注意：Gateway 层已有令牌桶限流（3 QPS/IP），这里是服务级兜底
        rules.add(buildFlowRule("seckill:grab", 2000));

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
