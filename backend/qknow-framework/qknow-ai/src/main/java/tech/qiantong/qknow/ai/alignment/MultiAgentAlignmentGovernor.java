package tech.qiantong.qknow.ai.alignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 多智能体自主目标对齐宪政总控中枢 (协调门禁网关)
 */
@Component
public class MultiAgentAlignmentGovernor {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentAlignmentGovernor.class);

    private final PotentialBasedRewardShaper rewardShaper;
    private final GoalDriftDetector driftDetector;
    private final EthicalSafetyBarrier safetyBarrier;
    private final AlignmentAuditLedger auditLedger;

    public record AlignmentVerdict(
            boolean approved,
            String verdictCode,
            double shapedReward,
            double safetyMargin,
            Map<String, Object> sanitizedParams,
            String auditEventId,
            long latencyMs
    ) {}

    public MultiAgentAlignmentGovernor(
            PotentialBasedRewardShaper rewardShaper,
            GoalDriftDetector driftDetector,
            EthicalSafetyBarrier safetyBarrier,
            AlignmentAuditLedger auditLedger
    ) {
        this.rewardShaper = rewardShaper;
        this.driftDetector = driftDetector;
        this.safetyBarrier = safetyBarrier;
        this.auditLedger = auditLedger;
    }

    /**
     * 核心对齐仲裁入口 (端到端延迟 <= 5ms)
     */
    public AlignmentVerdict evaluateAction(
            String agentId,
            String actionName,
            Map<String, Object> parameters,
            GoalDriftDetector.IntentVector rootIntent,
            GoalDriftDetector.IntentVector actionIntent,
            PotentialBasedRewardShaper.PotentialState curState,
            PotentialBasedRewardShaper.PotentialState nextState
    ) {
        long start = System.nanoTime();

        // 1. 目标漂移检测 (超球面夹角测地线)
        GoalDriftDetector.DriftEvaluation drift = driftDetector.evaluateDrift(rootIntent, actionIntent);

        // 2. 动态伦理安全屏障判定与投影修补
        EthicalSafetyBarrier.BarrierDecision barrier = safetyBarrier.inspectAndFilter(actionName, parameters, drift);

        // 3. 势能奖励塑形 (定理 1.1: 策略不变性)
        double baseReward = barrier.allowed() ? 1.0 : -1.0;
        double curPot = rewardShaper.calculatePotential(curState);
        double nextPot = rewardShaper.calculatePotential(nextState);
        double shapedReward = rewardShaper.computeShapedReward(baseReward, curPot, nextPot, 0.95);

        // 4. 不可变对齐审计账本存证
        AlignmentAuditLedger.AlignmentAuditEntry audit = auditLedger.recordEntry(
                agentId, actionName, barrier.allowed(), barrier.verdictCode(), shapedReward
        );

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        return new AlignmentVerdict(
                barrier.allowed(),
                barrier.verdictCode(),
                shapedReward,
                barrier.safetyMargin(),
                barrier.sanitizedParams(),
                audit.eventId(),
                elapsedMs
        );
    }
}
