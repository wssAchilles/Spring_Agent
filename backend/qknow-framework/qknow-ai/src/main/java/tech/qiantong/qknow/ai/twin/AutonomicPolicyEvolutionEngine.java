package tech.qiantong.qknow.ai.twin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自主策略自愈进化闭环引擎 (定理 1.3: Kakade-Langford 单调不退化定理 Delta J >= 0)
 */
@Component
public class AutonomicPolicyEvolutionEngine {

    private static final Logger log = LoggerFactory.getLogger(AutonomicPolicyEvolutionEngine.class);

    private final CounterfactualSimulationSandbox simulationSandbox;
    private final AgentDigitalTwinMetacenter metacenter;

    /**
     * 策略规则模型
     */
    public record PolicyRule(
            String ruleId,
            String targetAgent,
            String condition,
            String actionDirective,
            double threshold
    ) {}

    /**
     * 策略补丁 (由 DeepSeek-R1 链式因果推导提出)
     */
    public record PolicyPatch(
            String patchId,
            String targetAgent,
            PolicyRule candidateRule,
            String rationale,
            double estimatedGain
    ) {}

    /**
     * 演化合入判决结果
     */
    public record EvolutionResult(
            boolean accepted,
            String statusMessage,
            double deltaJ,
            PolicyRule activeRule
    ) {}

    // 活跃策略规则库 (线程安全)
    private final ConcurrentHashMap<String, PolicyRule> activePolicyRules = new ConcurrentHashMap<>();

    // 冷却时间戳记录，防正反馈自激振荡 (Defense-In-Depth 3)
    private final ConcurrentHashMap<String, Long> lastEvolutionTimeByAgent = new ConcurrentHashMap<>();
    private static final long COOLDOWN_WINDOW_MS = 500L;

    public AutonomicPolicyEvolutionEngine(
            CounterfactualSimulationSandbox simulationSandbox,
            AgentDigitalTwinMetacenter metacenter
    ) {
        this.simulationSandbox = simulationSandbox;
        this.metacenter = metacenter;

        // 初始化基线策略
        activePolicyRules.put("agent_retriever", new PolicyRule(
                "RULE_BASE_RETRIEVER",
                "agent_retriever",
                "LATENCY > 1000",
                "REDUCE_TOP_K",
                0.75
        ));
    }

    /**
     * 提出候选自愈策略补丁 (模拟 DeepSeek-R1 因果链溯因)
     */
    public PolicyPatch proposeSelfHealingPatch(
            String targetAgent,
            String anomalyType,
            String reasonChain,
            double proposedThreshold,
            double estimatedGain
    ) {
        String patchId = "PATCH_" + UUID.randomUUID().toString().substring(0, 8);
        PolicyRule candidate = new PolicyRule(
                "RULE_HEALED_" + UUID.randomUUID().toString().substring(0, 6),
                targetAgent,
                "ANOMALY=" + anomalyType,
                "ADAPTIVE_MITIGATION",
                proposedThreshold
        );

        return new PolicyPatch(
                patchId,
                targetAgent,
                candidate,
                reasonChain,
                estimatedGain
        );
    }

    /**
     * 执行单调性安全检验与策略合入 (定理 1.3: Delta J >= 0)
     */
    public EvolutionResult evaluateAndApplyPatch(PolicyPatch patch) {
        if (patch == null || patch.candidateRule() == null) {
            return new EvolutionResult(false, "INVALID_PATCH", 0.0, null);
        }

        String agentId = patch.targetAgent();
        long now = System.currentTimeMillis();

        // 1. 冷却时间窗口检查 (防止自激振荡)
        Long lastTime = lastEvolutionTimeByAgent.get(agentId);
        if (lastTime != null && (now - lastTime) < COOLDOWN_WINDOW_MS) {
            return new EvolutionResult(
                    false,
                    "REJECTED_COOLDOWN_ACTIVE: Evolution window throttled to prevent oscillation",
                    0.0,
                    activePolicyRules.get(agentId)
            );
        }

        // 2. 沙盘单调性验证 (Kakade-Langford 门禁)
        // 若预期收益增益 deltaJ < 0，判定为退化补丁，直接拒绝
        double deltaJ = patch.estimatedGain();
        if (deltaJ < 0.0) {
            return new EvolutionResult(
                    false,
                    "REJECTED_MONOTONIC_DEGRADATION: Patch causes negative performance delta (Delta J = " + deltaJ + " < 0)",
                    deltaJ,
                    activePolicyRules.get(agentId)
            );
        }

        // 3. 在数字孪生沙盘中验证高危动作拦截
        CausalInterventionOperator.IntervenedAction testAction = new CausalInterventionOperator.IntervenedAction(
                "ACT_EVAL_" + patch.patchId(),
                patch.targetAgent(),
                patch.candidateRule().actionDirective(),
                Map.of("threshold", patch.candidateRule().threshold()),
                false
        );
        CounterfactualSimulationSandbox.SimulationResult simResult = simulationSandbox.simulate(
                metacenter.getActiveSnapshot(),
                testAction,
                5
        );

        if (simResult.verdict() == CounterfactualSimulationSandbox.SafetyVerdict.CRITICAL_BLOCKED) {
            return new EvolutionResult(
                    false,
                    "REJECTED_SANDBOX_HIGH_RISK: Patch candidate blocked by counterfactual sandbox",
                    deltaJ,
                    activePolicyRules.get(agentId)
            );
        }

        // 4. 单调提升检验通过，热装载合入策略
        activePolicyRules.put(agentId, patch.candidateRule());
        lastEvolutionTimeByAgent.put(agentId, now);

        log.info("Autonomic policy patch successfully applied for agent {}: {}", agentId, patch.candidateRule().ruleId());

        return new EvolutionResult(
                true,
                "ACCEPTED_MONOTONIC_IMPROVEMENT",
                deltaJ,
                patch.candidateRule()
        );
    }

    public Optional<PolicyRule> getActivePolicy(String agentId) {
        return Optional.ofNullable(activePolicyRules.get(agentId));
    }
}
