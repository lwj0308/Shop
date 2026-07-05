package com.shop.seckill.config;

import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 系统自适应限流配置（RL-15 引入）
 * <p>
 * 系统自适应限流是 Sentinel 的"兜底保护"机制，和普通限流不同：
 * - 普通限流（FlowRule）：针对单个接口，比如"秒杀抢购每秒最多1000次"
 * - 系统限流（SystemRule）：针对整个应用，根据 CPU 使用率、系统负载、响应时间等指标
 *   动态调整，当系统压力过大时自动收紧所有限流规则
 * </p>
 * <p>
 * 小白理解：普通限流像每个路口设红绿灯（每个接口独立限流），
 * 系统限流像交警大队的总指挥，看到整条路都堵了（CPU飙高），直接拉警报
 * 让所有路口都延长红灯时间，防止整个系统崩溃。
 * </p>
 * <p>
 * 秒杀服务尤其需要这个保护：抢购瞬间流量可能是平时的几十倍，
 * 一旦系统扛不住，靠 SystemRule 自动收紧限流，避免雪崩。
 * </p>
 * <p>
 * 触发条件（满足任一即触发）：
 * - CPU 使用率 > 75%：CPU 忙不过来了
 * - 系统负载 > 4.0：系统扛不住了（4核机器满载是4.0）
 * - 平均响应时间 > 10ms：请求处理变慢了
 * - 入口 QPS > 2000：请求量太大了
 * - 入口线程数 > 100：并发线程太多了
 * </p>
 */
@Slf4j
@Configuration
public class SentinelSystemRuleConfig {

    /**
     * 服务启动时加载硬编码的系统规则
     * <p>
     * 这些规则作为兜底配置，即使 Nacos 不可用也能生效。
     * 后续可通过 Nacos 配置中心动态修改（参见 application.yml 中的 system 数据源配置）。
     * </p>
     */
    @PostConstruct
    public void initSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        // 规则1：CPU 使用率超过 75% 触发（防止 CPU 满载导致服务无响应）
        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(0.75);
        rules.add(cpuRule);

        // 规则2：系统负载超过 4.0 触发（4核机器满载负载为4.0）
        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(4.0);
        rules.add(loadRule);

        // 规则3：平均响应时间超过 10ms 触发（正常应该在几毫秒内）
        SystemRule rtRule = new SystemRule();
        rtRule.setAvgRt(10);
        rules.add(rtRule);

        // 规则4：入口总 QPS 超过 2000 触发（单机防护上限）
        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(2000);
        rules.add(qpsRule);

        // 规则5：入口总线程数超过 100 触发（防止线程池耗尽）
        SystemRule threadRule = new SystemRule();
        threadRule.setMaxThread(100);
        rules.add(threadRule);

        SystemRuleManager.loadRules(rules);
        log.info("RL-15：Sentinel 系统自适应限流规则已加载，共 {} 条规则", rules.size());
    }
}
