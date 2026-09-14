package tech.qiantong.qknow.ai.credit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 多智能体因果信贷与角色演化统一总控协调中枢 (Multi-Agent Credit & Evolution Coordinator)
 * 整合反共谋审计、反事实因果信贷归因、复制子角色演化与 SHA-256 密码学存证收据签发
 */
@Component
public class MultiAgentCreditAndEvolutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentCreditAndEvolutionCoordinator.class);

    private final AntiCollusionAuditor antiCollusionAuditor;
    private final CounterfactualCreditAssigner counterfactualCreditAssigner;
    private final AdaptiveRoleEvolutionGovernor adaptiveRoleEvolutionGovernor;

    @Autowired
    public MultiAgentCreditAndEvolutionCoordinator(
            AntiCollusionAuditor antiCollusionAuditor,
            CounterfactualCreditAssigner counterfactualCreditAssigner,
            AdaptiveRoleEvolutionGovernor adaptiveRoleEvolutionGovernor
    ) {
        this.antiCollusionAuditor = antiCollusionAuditor;
        this.counterfactualCreditAssigner = counterfactualCreditAssigner;
        this.adaptiveRoleEvolutionGovernor = adaptiveRoleEvolutionGovernor;
    }

    /**
     * 协调请求上下文数据模型
     */
    public record CoordinationRequest(
            String auctionId,
            String taskId,
            Map<String, Double> bids,
            Map<String, Double> valuations,
            List<CounterfactualCreditAssigner.AgentContribution> contributions,
            double passRate,
            double qwenHypersphereFidelity,
            double tokenEfficiency,
            double latencyScore
    ) {}

    /**
     * 端到端执行全流程协调：审计 -> 质检 -> 反事实归因 -> 角色演化 -> 签发存证凭单
     */
    public AttributionAuditReceipt coordinate(CoordinationRequest request) {
        long startTime = System.currentTimeMillis();

        String auctionId = request.auctionId() != null ? request.auctionId() : "AUC-" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = request.taskId() != null ? request.taskId() : "TASK-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 前置反共谋审计
        AntiCollusionAuditor.AuditorResult auditResult = antiCollusionAuditor.auditBids(request.bids(), request.valuations());

        if (!auditResult.passes()) {
            // 共谋卡特尔同盟熔断拦截：信贷全盘冻结，不推进后续角色演化
            long duration = System.currentTimeMillis() - startTime;
            log.warn("竞标审计熔断拦截: 卡特尔同盟 = {}, 风险指数 = {:.4f}", auditResult.cartelMembers(), auditResult.collusionRiskScore());
            return AttributionAuditReceipt.createReceipt(
                    auctionId,
                    taskId,
                    Map.of(),
                    auditResult.collusionRiskScore(),
                    auditResult.cartelMembers(),
                    Map.of(),
                    adaptiveRoleEvolutionGovernor.getRoleQuotas(),
                    0.0,
                    duration
            );
        }

        // 2. 四维多目标综合质量评估
        double totalQuality = counterfactualCreditAssigner.evaluateQuality(
                request.passRate(),
                request.qwenHypersphereFidelity(),
                request.tokenEfficiency(),
                request.latencyScore()
        );

        // 3. 反事实边际优势因果信贷归因 (消除搭便车)
        Map<String, Double> agentCredits = counterfactualCreditAssigner.assignCredits(totalQuality, request.contributions());

        // 4. 将智能体因果信贷聚合为四大角色平均适应度向量
        Map<String, Double> roleFitness = computeRoleFitness(agentCredits, request.contributions());

        // 5. 离散化复制子动力学角色演化更新
        Map<String, Double> nextEpochQuotas = adaptiveRoleEvolutionGovernor.evolve(roleFitness);

        long duration = System.currentTimeMillis() - startTime;

        // 6. 签发不可变密码学存证收据
        return AttributionAuditReceipt.createReceipt(
                auctionId,
                taskId,
                agentCredits,
                auditResult.collusionRiskScore(),
                Set.of(),
                roleFitness,
                nextEpochQuotas,
                totalQuality,
                duration
        );
    }

    /**
     * 聚合计算各角色平均适应度
     */
    private Map<String, Double> computeRoleFitness(
            Map<String, Double> agentCredits,
            List<CounterfactualCreditAssigner.AgentContribution> contributions
    ) {
        if (contributions == null || contributions.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> roleCreditSum = new LinkedHashMap<>();
        Map<String, Integer> roleCount = new LinkedHashMap<>();

        for (CounterfactualCreditAssigner.AgentContribution c : contributions) {
            String role = c.role() != null ? c.role().toUpperCase() : "GENERAL";
            double credit = agentCredits.getOrDefault(c.agentId(), 0.0);

            roleCreditSum.put(role, roleCreditSum.getOrDefault(role, 0.0) + credit);
            roleCount.put(role, roleCount.getOrDefault(role, 0) + 1);
        }

        Map<String, Double> roleFitness = new LinkedHashMap<>();
        for (String role : AdaptiveRoleEvolutionGovernor.STANDARD_ROLES) {
            int count = roleCount.getOrDefault(role, 0);
            if (count > 0) {
                double avg = roleCreditSum.getOrDefault(role, 0.0) / count;
                roleFitness.put(role, Math.round(avg * 10000.0) / 10000.0);
            } else {
                roleFitness.put(role, 0.25); // 默认基线适应度
            }
        }

        return Collections.unmodifiableMap(roleFitness);
    }

    public AntiCollusionAuditor getAntiCollusionAuditor() {
        return antiCollusionAuditor;
    }

    public CounterfactualCreditAssigner getCounterfactualCreditAssigner() {
        return counterfactualCreditAssigner;
    }

    public AdaptiveRoleEvolutionGovernor getAdaptiveRoleEvolutionGovernor() {
        return adaptiveRoleEvolutionGovernor;
    }
}
