package tech.qiantong.qknow.ai.twin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 形式化反事实推演沙盘 (定理 1.1: 物理状态强隔离不变量 dS_phys/da* = 0)
 */
@Component
public class CounterfactualSimulationSandbox {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualSimulationSandbox.class);

    private final CausalInterventionOperator interventionOperator;

    public CounterfactualSimulationSandbox(CausalInterventionOperator interventionOperator) {
        this.interventionOperator = interventionOperator;
    }

    public enum SafetyVerdict {
        SAFE,               // 安全合规，放行
        CAUTION,            // 存在一定风险，预警放行
        HIGH_RISK,          // 高风险，需人工确认
        CRITICAL_BLOCKED    // 严重高危，绝对阻断
    }

    public record SimulationResult(
            String simulationId,
            SafetyVerdict verdict,
            double riskScore,
            double utilityScore,
            long executionTimeMs,
            String isolationProofHash,
            List<String> diagnosticLogs
    ) {}

    /**
     * 执行反事实推演仿真
     */
    public SimulationResult simulate(
            DigitalTwinSnapshot physicalSnapshot,
            CausalInterventionOperator.IntervenedAction action,
            int horizonSteps
    ) {
        long startTime = System.nanoTime();
        List<String> logs = new ArrayList<>();

        if (physicalSnapshot == null || action == null) {
            return new SimulationResult(
                    "SIM_ERR",
                    SafetyVerdict.CRITICAL_BLOCKED,
                    1.0,
                    0.0,
                    0,
                    "NULL_SNAPSHOT",
                    List.of("INVALID_INPUT_ARGUMENTS")
            );
        }

        String originHashBefore = physicalSnapshot.snapshotHash();

        // 1. 溯因 (Abduction)
        CausalInterventionOperator.CausalEvidence mockEvidence = new CausalInterventionOperator.CausalEvidence(
                "EVT_PRE_SIM",
                action.agentId(),
                "NORMAL_TELEMETRY",
                12.5,
                Map.of("clusterLoad", 0.45)
        );
        Map<String, Object> exogenousContext = interventionOperator.abductExogenousContext(mockEvidence);
        logs.add("Abduction completed, noiseLevel=" + exogenousContext.get("noiseLevel"));

        // 2. 干预 (Action) 产生 COW 虚拟快照
        DigitalTwinSnapshot virtualSnapshot = interventionOperator.applyIntervention(
                physicalSnapshot,
                action,
                exogenousContext
        );
        logs.add("Intervention applied, virtualVersion=" + virtualSnapshot.snapshotVersion());

        // 3. 预测 (Prediction) 前向展开
        CausalInterventionOperator.CounterfactualPrediction prediction = interventionOperator.predictOutcome(
                virtualSnapshot,
                action,
                horizonSteps
        );
        logs.add("Prediction finished, projectedRisk=" + prediction.projectedRisk() + ", approved=" + prediction.approved());

        // 4. 判定安全性
        SafetyVerdict verdict;
        if (prediction.projectedRisk() >= 0.80) {
            verdict = SafetyVerdict.CRITICAL_BLOCKED;
        } else if (prediction.projectedRisk() >= 0.50) {
            verdict = SafetyVerdict.HIGH_RISK;
        } else if (prediction.projectedRisk() >= 0.25) {
            verdict = SafetyVerdict.CAUTION;
        } else {
            verdict = SafetyVerdict.SAFE;
        }

        // 5. 校验物理状态强隔离不变量 (Theorem 1.1: 物理快照哈希必须恒等)
        String originHashAfter = physicalSnapshot.snapshotHash();
        if (!originHashBefore.equals(originHashAfter)) {
            throw new IllegalStateException("CRITICAL_VIOLATION: Physical state snapshot was mutated during counterfactual simulation!");
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        return new SimulationResult(
                prediction.scenarioId(),
                verdict,
                prediction.projectedRisk(),
                prediction.projectedUtility(),
                elapsedMs,
                virtualSnapshot.snapshotHash(),
                Collections.unmodifiableList(logs)
        );
    }
}
