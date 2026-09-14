package tech.qiantong.qknow.ai.twin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Pearl 因果推断 do-演算干预算子 (定理 1.1: 溯因-干预-前向推导三阶段)
 */
@Component
public class CausalInterventionOperator {

    private static final Logger log = LoggerFactory.getLogger(CausalInterventionOperator.class);

    /**
     * 事实观察证据 (Abduction 阶段输入)
     */
    public record CausalEvidence(
            String eventId,
            String agentId,
            String observedOutcome,
            double observedLatencyMs,
            Map<String, Object> exogenousFactors
    ) {}

    /**
     * 形式化干预动作 (Action do(A=a*) 阶段输入)
     */
    public record IntervenedAction(
            String actionId,
            String agentId,
            String targetAction,
            Map<String, Object> parameters,
            boolean isHighRisk
    ) {}

    /**
     * 反事实前向推演预判 (Prediction 阶段输出)
     */
    public record CounterfactualPrediction(
            String scenarioId,
            String targetAgentId,
            String intervenedAction,
            double projectedUtility,
            double projectedRisk,
            boolean approved,
            List<String> predictedSideEffects
    ) {}

    /**
     * 阶段 1: 溯因 (Abduction) - 根据已知事实推断外生情境变量
     */
    public Map<String, Object> abductExogenousContext(CausalEvidence evidence) {
        Map<String, Object> context = new HashMap<>();
        if (evidence == null) {
            return context;
        }
        context.put("baseEventId", evidence.eventId());
        context.put("agentId", evidence.agentId());
        context.put("priorLatency", evidence.observedLatencyMs());
        if (evidence.exogenousFactors() != null) {
            context.putAll(evidence.exogenousFactors());
        }
        // 计算环境噪声因子
        double noiseFactor = evidence.observedLatencyMs() > 2000.0 ? 0.8 : 0.2;
        context.put("noiseLevel", noiseFactor);
        return context;
    }

    /**
     * 阶段 2: 干预 (Action) - 在结构因果模型中置换方程 do(A = a*)
     */
    public DigitalTwinSnapshot applyIntervention(
            DigitalTwinSnapshot baseSnapshot,
            IntervenedAction action,
            Map<String, Object> abductedContext
    ) {
        if (baseSnapshot == null || action == null) {
            return baseSnapshot;
        }

        Map<String, Object> delta = new HashMap<>();
        delta.put("lastIntervenedAction", action.targetAction());
        delta.put("isIntervened", true);
        delta.put("abductedNoise", abductedContext != null ? abductedContext.getOrDefault("noiseLevel", 0.1) : 0.1);
        if (action.parameters() != null) {
            delta.putAll(action.parameters());
        }

        // 调用 COW 结构共享衍生虚拟孪生分支 (物理隔离不变量成立)
        return baseSnapshot.withIntervention(action.agentId(), "INTERVENED_" + action.targetAction(), delta);
    }

    /**
     * 阶段 3: 预测 (Prediction) - 在干预后的因果拓扑上执行前向轻量展开
     */
    public CounterfactualPrediction predictOutcome(
            DigitalTwinSnapshot virtualSnapshot,
            IntervenedAction action,
            int horizonSteps
    ) {
        String scenarioId = "CF_SCENARIO_" + UUID.randomUUID().toString().substring(0, 8);
        List<String> sideEffects = new ArrayList<>();

        // 识别高危特征
        boolean isHighRisk = action.isHighRisk() || isCriticalForbiddenAction(action.targetAction(), action.parameters());

        double projectedRisk;
        double projectedUtility;

        if (isHighRisk) {
            projectedRisk = 0.95;
            projectedUtility = 0.10;
            sideEffects.add("DETECTED_UNAUTHORIZED_CRITICAL_OPERATION");
            sideEffects.add("POTENTIAL_PERMANENT_DATA_CORRUPTION");
        } else {
            // 安全操作：计算基于步数的效用增益与风险
            double baseLoad = virtualSnapshot.getAgent(action.agentId())
                    .map(DigitalTwinSnapshot.AgentTwinState::loadFactor)
                    .orElse(0.3);
            projectedRisk = Math.min(0.20, baseLoad * 0.25);
            projectedUtility = Math.max(0.85, 1.0 - projectedRisk);
            sideEffects.add("RESOURCE_CONSUMPTION_WITHIN_BUDGET");
        }

        boolean approved = !isHighRisk && projectedRisk < 0.30;

        return new CounterfactualPrediction(
                scenarioId,
                action.agentId(),
                action.targetAction(),
                projectedUtility,
                projectedRisk,
                approved,
                Collections.unmodifiableList(sideEffects)
        );
    }

    /**
     * 检查是否属于高危禁止操作
     */
    private boolean isCriticalForbiddenAction(String actionName, Map<String, Object> params) {
        if (actionName == null) return false;
        String upper = actionName.toUpperCase();
        if (upper.contains("DROP_TABLE") || upper.contains("PURGE_ALL") || upper.contains("DELETE_ALL")
                || upper.contains("UNAUTHORIZED_TRANSFER") || upper.contains("OVERWRITE_SECURITY_POLICY")) {
            return true;
        }
        if (params != null) {
            for (Object v : params.values()) {
                if (v != null && v.toString().toUpperCase().contains("DROP TABLE")) {
                    return true;
                }
            }
        }
        return false;
    }
}
