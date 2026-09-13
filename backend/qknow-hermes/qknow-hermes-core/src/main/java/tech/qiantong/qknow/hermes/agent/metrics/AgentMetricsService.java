package tech.qiantong.qknow.hermes.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Agent 智能体运行时微观可观测性治理服务
 * 监控 ReAct 循环步数、单轮思考耗时、工具执行状态与防死循环熔断
 */
@Component
public class AgentMetricsService {

    private final MeterRegistry registry;
    private final DistributionSummary reactStepsSummary;
    private final Counter cycleBreakerTriggeredCounter;
    private final Counter callLimitExceededCounter;

    public AgentMetricsService(MeterRegistry registry) {
        this.registry = registry;

        if (registry != null) {
            // 记录 ReAct 循环步数分布（用于观测 Agent 是否陷入长推理或死循环倾向）
            this.reactStepsSummary = DistributionSummary.builder("agent.react.steps")
                    .description("Agent ReAct 循环执行步数分布")
                    .baseUnit("steps")
                    .percentilePrecision(2)
                    .register(registry);

            // 熔断与异常拦截计数器
            this.cycleBreakerTriggeredCounter = Counter.builder("agent.guard.breaker.total")
                    .tag("event", "cycle_detected")
                    .description("触发防死循环熔断的次数")
                    .register(registry);

            this.callLimitExceededCounter = Counter.builder("agent.guard.breaker.total")
                    .tag("event", "call_limit_exceeded")
                    .description("超过最大步数限制截断的次数")
                    .register(registry);
        } else {
            this.reactStepsSummary = null;
            this.cycleBreakerTriggeredCounter = null;
            this.callLimitExceededCounter = null;
        }
    }

    public void recordReactSteps(int steps) {
        if (reactStepsSummary != null) {
            reactStepsSummary.record(steps);
        }
    }

    public void recordTurnDuration(String agentName, int turnIndex, long durationNanos) {
        if (registry == null) {
            return;
        }
        // 严格控制 Tag 基数：turnIndex 离散归一化为 1, 2, 3, 4, 5, "5+"
        String turnTag = turnIndex <= 5 ? String.valueOf(turnIndex) : "5+";
        String effectiveAgent = (agentName != null && !agentName.isBlank()) ? agentName : "default";
        Timer.builder("agent.react.turn.duration")
                .tag("agent", effectiveAgent)
                .tag("turn", turnTag)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordToolExecution(String toolName, boolean success, long durationNanos) {
        if (registry == null) {
            return;
        }
        String effectiveTool = (toolName != null && !toolName.isBlank()) ? toolName : "unknown";
        Timer.builder("agent.tool.execution.duration")
                .tag("tool", effectiveTool)
                .tag("outcome", success ? "ok" : "error")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordCycleBreakerTriggered() {
        if (cycleBreakerTriggeredCounter != null) {
            cycleBreakerTriggeredCounter.increment();
        }
    }

    public void recordCallLimitExceeded() {
        if (callLimitExceededCounter != null) {
            callLimitExceededCounter.increment();
        }
    }
}
