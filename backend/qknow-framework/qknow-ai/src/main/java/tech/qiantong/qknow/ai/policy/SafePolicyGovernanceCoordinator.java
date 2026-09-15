package tech.qiantong.qknow.ai.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 端到端安全策略治理调度统筹中枢
 */
@Component
public class SafePolicyGovernanceCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SafePolicyGovernanceCoordinator.class);

    private final AdaptiveExplorationScheduler explorationScheduler;
    private final DoublyRobustOpeEvaluator opeEvaluator;
    private final ConservativePolicyGovernor cqlGovernor;
    private final ConstrainedPolicyOptimizer policyOptimizer;

    public SafePolicyGovernanceCoordinator(
            AdaptiveExplorationScheduler explorationScheduler,
            DoublyRobustOpeEvaluator opeEvaluator,
            ConservativePolicyGovernor cqlGovernor,
            ConstrainedPolicyOptimizer policyOptimizer
    ) {
        this.explorationScheduler = explorationScheduler;
        this.opeEvaluator = opeEvaluator;
        this.cqlGovernor = cqlGovernor;
        this.policyOptimizer = policyOptimizer;
    }

    /**
     * 端到端策略评估、保守校准、安全约束对偶更新与存证签发
     */
    public OfflineTrajectoryReceipt evaluateAndGovernPolicy(
            String evaluationBatchId,
            String targetPolicyId,
            String baselinePolicyId,
            List<DoublyRobustOpeEvaluator.TrajectoryStep> trajectories,
            DoublyRobustOpeEvaluator.Policy targetPolicy,
            DoublyRobustOpeEvaluator.BaselineValueModel baselineModel,
            Map<String, Double> estimatedCosts
    ) {
        // 1. 参数完备性与防御门禁
        if (trajectories == null || trajectories.isEmpty()) {
            throw new IllegalArgumentException("评估轨迹数据集不能为空");
        }
        if (targetPolicy == null || baselineModel == null) {
            throw new IllegalArgumentException("待治理目标策略与基准模型均不可为空");
        }

        long startTime = System.currentTimeMillis();
        String receiptId = "POL-REC-" + UUID.randomUUID().toString().substring(0, 8);

        // 2. 离线轨迹登记至保守性统计库
        cqlGovernor.recordTrajectories(trajectories);

        // 3. 执行双重稳健 (DR-OPE) 评估 (定理 1.1)
        DoublyRobustOpeEvaluator.OpeEvaluationResult opeResult =
                opeEvaluator.evaluatePolicy(trajectories, targetPolicy, baselineModel);

        // 4. 计算 CQL 悲观价值惩罚 (定理 1.2)
        String sampleState = trajectories.get(0).state();
        double cqlPenalty = cqlGovernor.computeCqlPenalty(sampleState, targetPolicy);
        double conservativeValue = cqlGovernor.calibrateConservativeValue(opeResult.doublyRobustValue(), cqlPenalty);

        // 5. 安全约束与拉格朗日乘子对偶更新 (定理 1.3)
        policyOptimizer.updateMultipliers(estimatedCosts);
        boolean constraintViolated = !policyOptimizer.isPolicySafe(estimatedCosts);

        // 6. 决策合入门禁：未违背安全约束 且 保守评估价值不低于基准价值
        double baselineExpectedValue = baselineModel.predictV(sampleState);
        boolean policyAccepted = (!constraintViolated) && (conservativeValue >= baselineExpectedValue);

        // 7. 密码学不可变存证凭单构建与签名
        OfflineTrajectoryReceipt receipt = OfflineTrajectoryReceipt.create(
                receiptId,
                evaluationBatchId,
                targetPolicyId,
                baselinePolicyId,
                opeResult.doublyRobustValue(),
                opeResult.directMethodValue(),
                opeResult.importanceSamplingValue(),
                cqlPenalty,
                conservativeValue,
                policyOptimizer.getMultipliers(),
                constraintViolated,
                policyAccepted,
                System.currentTimeMillis()
        );

        long costMs = System.currentTimeMillis() - startTime;
        log.info("安全策略治理协调完成: receiptId={}, accepted={}, conservativeVal={}, costMs={}",
                receiptId, policyAccepted, String.format("%.4f", conservativeValue), costMs);

        return receipt;
    }

    public AdaptiveExplorationScheduler getExplorationScheduler() {
        return explorationScheduler;
    }

    public DoublyRobustOpeEvaluator getOpeEvaluator() {
        return opeEvaluator;
    }

    public ConservativePolicyGovernor getCqlGovernor() {
        return cqlGovernor;
    }

    public ConstrainedPolicyOptimizer getPolicyOptimizer() {
        return policyOptimizer;
    }
}
