package tech.qiantong.qknow.ai.superagent;

import java.util.*;

/**
 * Phase 60: 端到端超级智能体生态统筹总调度中枢
 * <p>
 * 统筹认知自省、事件总线流控、主权安全治理与四维帕累托进化评估，生成不可变存证账本。
 */
public class SuperAgentEcosystemCoordinator {

    private final MetacognitiveIntrospector introspector;
    private final AutonomicEvolutionBus evolutionBus;
    private final SovereignGovernanceController governanceController;
    private final EvolutionaryFitnessEvaluator fitnessEvaluator;

    public SuperAgentEcosystemCoordinator(
            MetacognitiveIntrospector introspector,
            AutonomicEvolutionBus evolutionBus,
            SovereignGovernanceController governanceController,
            EvolutionaryFitnessEvaluator fitnessEvaluator
    ) {
        this.introspector = Objects.requireNonNull(introspector, "introspector 不能为空");
        this.evolutionBus = Objects.requireNonNull(evolutionBus, "evolutionBus 不能为空");
        this.governanceController = Objects.requireNonNull(governanceController, "governanceController 不能为空");
        this.fitnessEvaluator = Objects.requireNonNull(fitnessEvaluator, "fitnessEvaluator 不能为空");
    }

    /**
     * 协调生态治理周期并签发存证账本
     */
    public SuperAgentAuditLedger coordinateCycle(
            long governanceEpoch,
            List<String> activeAgentIds,
            double taskSuccessRate,
            double tokenEfficiency,
            double latencyMargin,
            double safetyCompliance
    ) {
        if (governanceEpoch <= 0) {
            throw new IllegalArgumentException("治理代际号必须大于 0");
        }
        if (activeAgentIds == null) {
            throw new IllegalArgumentException("活跃智能体列表不能为空");
        }

        // 1. 注册存活智能体
        for (String agentId : activeAgentIds) {
            if (governanceController.getAgentState(agentId) != SovereignGovernanceController.AgentState.JAIL_ISOLATED) {
                governanceController.registerAgent(agentId);
            }
        }

        // 2. 检查主权控制器是否已物理熔断
        boolean emergencyHalted = governanceController.isEmergencyHalted();

        // 3. 计算集群认知熵与自激振荡检测
        double entropy = introspector.calculateClusterCognitiveEntropy();
        boolean oscillation = introspector.detectOscillationLoop();
        boolean entropyCritical = introspector.isEntropyCritical();

        // 如果发现振荡或极端认知熵，进行警告或主动隔离
        if (oscillation && !activeAgentIds.isEmpty()) {
            String suspect = activeAgentIds.get(0);
            governanceController.reportViolation(suspect, "触发跨智能体环形振荡调用");
        }

        // 4. 评估四维帕累托适应度
        var fitnessResult = fitnessEvaluator.evaluate(
                taskSuccessRate, tokenEfficiency, latencyMargin, safetyCompliance
        );

        boolean promotionApproved = fitnessResult.promotionApproved() && !emergencyHalted && !entropyCritical;

        // 5. 综合决策摘要
        String decision;
        if (emergencyHalted) {
            decision = "生态处于紧急物理硬断路熔断状态，禁止一切策略晋级与非安全调用";
        } else if (entropyCritical) {
            decision = String.format("集群认知熵 %.4f 超过临界警戒线，冻结策略晋级并启动自省防御", entropy);
        } else if (promotionApproved) {
            decision = String.format("生态治理周期正常，策略获批晋级。帕累托适应度: %.4f", fitnessResult.overallFitnessScore());
        } else {
            decision = "治理评估完成，未满足晋级门槛: " + fitnessResult.decisionSummary();
        }

        // 6. 签发不可变 SHA-256 自签名账本
        String ledgerId = "LEDGER-EP-" + governanceEpoch + "-" + UUID.randomUUID().toString().substring(0, 8);
        return SuperAgentAuditLedger.create(
                ledgerId,
                governanceEpoch,
                entropy,
                governanceController.getActiveAgentCount(),
                governanceController.getIsolatedAgentCount(),
                fitnessResult.overallFitnessScore(),
                promotionApproved,
                emergencyHalted,
                decision,
                System.currentTimeMillis()
        );
    }

    public MetacognitiveIntrospector getIntrospector() {
        return introspector;
    }

    public AutonomicEvolutionBus getEvolutionBus() {
        return evolutionBus;
    }

    public SovereignGovernanceController getGovernanceController() {
        return governanceController;
    }

    public EvolutionaryFitnessEvaluator getFitnessEvaluator() {
        return fitnessEvaluator;
    }
}
