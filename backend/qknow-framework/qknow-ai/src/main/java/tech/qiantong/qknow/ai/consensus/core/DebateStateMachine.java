package tech.qiantong.qknow.ai.consensus.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.consensus.model.ConsensusResult;
import tech.qiantong.qknow.ai.consensus.model.InspectedProposal;
import tech.qiantong.qknow.ai.consensus.model.WorkerProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 工业级四态自适应辩论状态机 (Debate State Machine)
 * 状态机流转：PROPOSE -> REVIEW -> CHECK -> ARBITRATE
 * 严格执行学术与工业双重防死循环熔断机制：
 * 1. 余弦相似度 >= 0.90 提前短路收敛 (DEBATE_CONVERGED)；
 * 2. 连续两轮相似度变化 |S_r - S_{r-1}| <= 0.02 停滞强制截断 (DEBATE_STAGNATION_FORCED)；
 * 3. 辩论轮次硬上限 3 轮强制截断 (MAX_ROUNDS_REACHED)。
 */
@Slf4j
@Component
public class DebateStateMachine {

    public static final int MAX_DEBATE_ROUNDS = 3;
    public static final double CONVERGENCE_THRESHOLD = 0.90;
    public static final double STAGNATION_DELTA = 0.02;

    public enum DebateState {
        IDLE,
        PROPOSE,
        REVIEW,
        CHECK,
        ARBITRATE,
        TERMINATED
    }

    private final ByzantineWorkerFilter workerFilter;
    private final ConsensusArbiter arbiter;

    @Getter
    private DebateState currentState = DebateState.IDLE;

    @Autowired
    public DebateStateMachine(ByzantineWorkerFilter workerFilter, ConsensusArbiter arbiter) {
        this.workerFilter = workerFilter != null ? workerFilter : new ByzantineWorkerFilter();
        this.arbiter = arbiter != null ? arbiter : new ConsensusArbiter(this.workerFilter);
    }

    public DebateStateMachine() {
        this(new ByzantineWorkerFilter(), new ConsensusArbiter());
    }

    /**
     * 计算一组诚实提案的群体两两平均余弦相似度
     */
    public static double computeCohortMeanSimilarity(List<InspectedProposal> honestProposals) {
        if (honestProposals == null || honestProposals.size() < 2) {
            return 1.0;
        }

        List<InspectedProposal> valid = honestProposals.stream()
                .filter(p -> p.embedding1536() != null)
                .toList();
        int m = valid.size();
        if (m < 2) {
            return 1.0;
        }

        double totalSim = 0.0;
        int pairCount = 0;
        for (int i = 0; i < m; i++) {
            float[] vi = valid.get(i).embedding1536();
            for (int j = i + 1; j < m; j++) {
                float[] vj = valid.get(j).embedding1536();
                totalSim += ByzantineWorkerFilter.dotProduct(vi, vj);
                pairCount++;
            }
        }
        return pairCount > 0 ? (totalSim / pairCount) : 1.0;
    }

    /**
     * 驱动多轮自适应辩论演进
     */
    public ConsensusResult runAdaptiveDebate(
            String taskId,
            List<WorkerProposal> initialProposals,
            BiFunction<Integer, List<InspectedProposal>, List<WorkerProposal>> debateReviewer
    ) {
        if (initialProposals == null || initialProposals.isEmpty()) {
            this.currentState = DebateState.TERMINATED;
            return ConsensusResult.failOpen(taskId, "EMPTY_INITIAL_PROPOSALS", 0);
        }

        int currentRound = 1;
        List<WorkerProposal> currentProposals = initialProposals;
        List<Double> similarityHistory = new ArrayList<>();
        List<InspectedProposal> currentInspected = new ArrayList<>();
        String resolutionType = "MAX_ROUNDS_REACHED";

        while (currentRound <= MAX_DEBATE_ROUNDS) {
            log.info("[DebateStateMachine] 启动第 [{}/{}] 轮辩论流程...", currentRound, MAX_DEBATE_ROUNDS);

            // 1. PROPOSE / RECEIVE 状态
            this.currentState = DebateState.PROPOSE;
            currentInspected = workerFilter.filterProposals(currentProposals);

            List<InspectedProposal> honest = currentInspected.stream()
                    .filter(InspectedProposal::isHonest)
                    .collect(Collectors.toList());

            if (honest.isEmpty()) {
                log.warn("[DebateStateMachine] 第 [{}] 轮无可用诚实提案，终止并执行 Fail-Open 降级！", currentRound);
                this.currentState = DebateState.TERMINATED;
                return ConsensusResult.failOpen(taskId, "NO_HONEST_PROPOSALS_IN_DEBATE", initialProposals.size());
            }

            // 2. CHECK 状态：评估群体相似度与收敛指标
            this.currentState = DebateState.CHECK;
            double meanSim = computeCohortMeanSimilarity(honest);
            similarityHistory.add(meanSim);
            log.info("[DebateStateMachine] 轮次 [{}] 诚实节点数 [{}] 群体平均相似度: {}", currentRound, honest.size(), meanSim);

            // 条件 1：相似度 >= 0.90，提前短路收敛！
            if (meanSim >= CONVERGENCE_THRESHOLD) {
                log.info("[DebateStateMachine] 相似度达到 {} >= {}，触发提前短路收敛！", meanSim, CONVERGENCE_THRESHOLD);
                resolutionType = "DEBATE_CONVERGED";
                break;
            }

            // 条件 2：连续两轮相似度停滞漂移 <= 0.02，强制熔断防死循环！
            if (currentRound >= 2) {
                double prevSim = similarityHistory.get(currentRound - 2);
                double delta = Math.abs(meanSim - prevSim);
                if (delta <= STAGNATION_DELTA) {
                    log.warn("[DebateStateMachine] 连续两轮相似度变化 |{} - {}| = {} <= {}，判定辩论陷入死锁停滞，强制熔断截断！",
                            meanSim, prevSim, delta, STAGNATION_DELTA);
                    resolutionType = "DEBATE_STAGNATION_FORCED";
                    break;
                }
            }

            // 条件 3：达到硬上限截断
            if (currentRound >= MAX_DEBATE_ROUNDS) {
                log.warn("[DebateStateMachine] 达到硬上限轮次 [{}]，强制截断辩论！", MAX_DEBATE_ROUNDS);
                resolutionType = "MAX_ROUNDS_REACHED";
                break;
            }

            // 3. REVIEW 状态：触发下一轮交叉审查与反思
            this.currentState = DebateState.REVIEW;
            if (debateReviewer != null) {
                currentProposals = debateReviewer.apply(currentRound + 1, currentInspected);
            }
            currentRound++;
        }

        // 4. ARBITRATE 状态：执行最终加权 Medoid 裁决
        this.currentState = DebateState.ARBITRATE;
        ConsensusResult rawResult = arbiter.arbitrateContinuousMedoid(taskId, currentInspected);

        this.currentState = DebateState.TERMINATED;

        // 组装最终决议结果
        return new ConsensusResult(
                rawResult.taskId(),
                rawResult.consensusContent(),
                true, // 经过辩论已产出决议
                rawResult.consensusConfidence(),
                rawResult.selectedMedoidWorkerId(),
                rawResult.totalWorkers(),
                rawResult.honestWorkers(),
                currentRound,
                resolutionType
        );
    }
}
