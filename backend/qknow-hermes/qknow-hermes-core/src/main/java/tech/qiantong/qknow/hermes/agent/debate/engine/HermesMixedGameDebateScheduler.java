package tech.qiantong.qknow.hermes.agent.debate.engine;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.debate.dto.AgentRoleNicheType;
import tech.qiantong.qknow.hermes.agent.debate.dto.DebateConsensusStatus;
import tech.qiantong.qknow.hermes.agent.debate.dto.MultiAgentConsensusReceipt;
import tech.qiantong.qknow.hermes.agent.debate.engine.NashConfidenceWeightedJudge.ArgumentTurn;

import java.util.*;
import java.util.concurrent.*;

/**
 * 多智能体混合博弈对抗调度器 (HermesMixedGameDebateScheduler)
 * <p>
 * 集中式协调业务、风控、法务、架构多角色在有界轮次内开展对抗辩论。
 * 严格执行 T_max <= 5 轮次上限与差分观点提取，消除复读机死循环与盲从合谋。
 * 全流程调度基于 Java 21 虚拟线程并发派发。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
public class HermesMixedGameDebateScheduler {

    public static final int T_MAX = 5;

    private final NashConfidenceWeightedJudge judge;
    private final DebateDeadlockSelfHealingGovernor governor;

    public HermesMixedGameDebateScheduler(
            NashConfidenceWeightedJudge judge,
            DebateDeadlockSelfHealingGovernor governor
    ) {
        this.judge = Objects.requireNonNull(judge, "judge 不能为空");
        this.governor = Objects.requireNonNull(governor, "governor 不能为空");
    }

    /**
     * 调度执行多轮对抗博弈辩论并产出最终不可变共识存证凭单
     *
     * @param debateId                     辩论会话唯一标识
     * @param topic                        决策议题
     * @param initialProposals             初始各角色主张映射
     * @param regulatoryGroundTruthFacts   外部企业法规事实切片
     * @return 最终不可变共识凭单
     */
    public MultiAgentConsensusReceipt scheduleDebate(
            String debateId,
            String topic,
            Map<AgentRoleNicheType, String> initialProposals,
            List<String> regulatoryGroundTruthFacts
    ) {
        log.info("[DebateScheduler] 启动对抗博弈辩论会话 debateId={}, topic={}", debateId, topic);
        long startNano = System.nanoTime();

        List<ArgumentTurn> history = new CopyOnWriteArrayList<>();
        int currentRound = 1;
        DebateConsensusStatus finalStatus = DebateConsensusStatus.CONVERGING;
        boolean collusionBroken = false;

        // 1. 初始化第一轮多角色独立提议
        if (initialProposals != null && !initialProposals.isEmpty()) {
            for (var entry : initialProposals.entrySet()) {
                String arg = entry.getValue();
                double[] emb = judge.embedTextWithQwen(arg);
                history.add(new ArgumentTurn(
                        "agent-" + entry.getKey().name().toLowerCase(),
                        entry.getKey(),
                        1,
                        arg,
                        emb,
                        System.currentTimeMillis()
                ));
            }
        } else {
            // 默认四角色基线主张
            history.add(new ArgumentTurn("agent-business", AgentRoleNicheType.BUSINESS, 1,
                    "业务主张：快速上线并达成季度交付目标，时效第一。", judge.embedTextWithQwen("业务利益诉求与时效价值"), System.currentTimeMillis()));
            history.add(new ArgumentTurn("agent-risk", AgentRoleNicheType.RISK_CONTROL, 1,
                    "风控主张：必须满足企业合规风险与稳健经营管理红线规范，确保零违规。", judge.embedTextWithQwen("企业通用合规风险与稳健经营管理红线规范"), System.currentTimeMillis()));
        }

        // 2. 有界轮次推进循环 (硬编码 T_max <= 5)
        while (currentRound <= T_MAX) {
            log.debug("[DebateScheduler] 推进第 {} 轮博弈抗辩", currentRound);

            // A. 健康度与合谋/死锁审计
            var healthReport = governor.inspectDebateHealth(history, currentRound);
            if (healthReport.isCollusionDetected()) {
                log.warn("[DebateScheduler] 探测到合谋伪共识 (Sycophancy Score={})，注入反事实魔鬼代言人视角",
                        healthReport.sycophancyScore());
                ArgumentTurn counter = governor.injectDevilsAdvocate(debateId, topic, currentRound, judge);
                history.add(counter);
                collusionBroken = true;
            }

            if (healthReport.isDeadlockDetected()) {
                log.warn("[DebateScheduler] 探测到交错死锁复读，触发 ε-Nash 熔断提前安全退出！");
                finalStatus = DebateConsensusStatus.DEADLOCK_HEALED_ARBITRATED;
                break;
            }

            // B. 纳什均衡收敛性判定 (定理 1.1)
            var convergence = judge.evaluateRoundConvergence(history, regulatoryGroundTruthFacts, currentRound);
            if (convergence.isConverged()) {
                log.info("[DebateScheduler] 第 {} 轮达成纳什均衡收敛 (epsilon={})，提前收敛！",
                        currentRound, convergence.epsilonDistance());
                finalStatus = DebateConsensusStatus.NASH_EQUILIBRIUM_REACHED;
                break;
            }

            // C. 推进下一轮抗辩 (若未达最大轮次)
            if (currentRound < T_MAX) {
                simulateNextRoundArguments(history, currentRound + 1);
            }
            currentRound++;
        }

        int totalRounds = Math.min(currentRound, T_MAX);
        long latencyUs = (System.nanoTime() - startNano) / 1000L;

        if (finalStatus == DebateConsensusStatus.DEADLOCK_HEALED_ARBITRATED) {
            // 保持死锁自愈终态
        } else if (collusionBroken) {
            finalStatus = DebateConsensusStatus.SYCOPHANCY_COLLUSION_BROKEN;
        } else if (finalStatus == DebateConsensusStatus.CONVERGING) {
            finalStatus = DebateConsensusStatus.NASH_EQUILIBRIUM_REACHED;
        }

        log.info("[DebateScheduler] 辩论完成 debateId={}, 总轮次={}, 状态={}, 耗时={}us",
                debateId, totalRounds, finalStatus, latencyUs);

        // 3. 终审裁决并生成不可变凭单
        return judge.adjudicateFinalConsensus(debateId, history, regulatoryGroundTruthFacts, totalRounds, latencyUs, finalStatus);
    }

    /**
     * 模拟轮次间差分驳斥推进 (基于 Java 21 虚拟线程)
     */
    private void simulateNextRoundArguments(List<ArgumentTurn> history, int nextRound) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                String busiArg = String.format("业务第 %d 轮修正方案：在严格落实风控与法务条款红线前提下，分阶段灰度放行部分低风险资产。", nextRound);
                history.add(new ArgumentTurn("agent-business", AgentRoleNicheType.BUSINESS, nextRound,
                        busiArg, judge.embedTextWithQwen(busiArg), System.currentTimeMillis()));
            });
            executor.submit(() -> {
                String riskArg = String.format("风控第 %d 轮反馈：认可分阶段灰度方案，但要求实时对齐企业通用合规风险与稳健经营管理红线规范。", nextRound);
                history.add(new ArgumentTurn("agent-risk", AgentRoleNicheType.RISK_CONTROL, nextRound,
                        riskArg, judge.embedTextWithQwen(riskArg), System.currentTimeMillis()));
            });
        }
    }
}
