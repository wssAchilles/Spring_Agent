package tech.qiantong.qknow.ai.consensus.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.consensus.model.ConsensusResult;
import tech.qiantong.qknow.ai.consensus.model.InspectedProposal;
import tech.qiantong.qknow.ai.consensus.model.WorkerProposal;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工业级拜占庭容错共识裁决引擎 (Consensus Arbiter)
 * 严格执行学术与工业双重规范：
 * 1. 拜占庭容错定理判定 (n >= 3f + 1, Quorum Q >= 2f + 1)；
 * 2. 离散/结构化决策加权多数投票；
 * 3. 自由文本阿里千问 1536 维超球面加权 Medoid 中心选取；
 * 4. Fast Quorum 提前截断与 Fail-Open 优雅降级。
 */
@Slf4j
@Component
public class ConsensusArbiter {

    private final ByzantineWorkerFilter workerFilter;

    @Autowired
    public ConsensusArbiter(ByzantineWorkerFilter workerFilter) {
        this.workerFilter = workerFilter != null ? workerFilter : new ByzantineWorkerFilter();
    }

    public ConsensusArbiter() {
        this(new ByzantineWorkerFilter());
    }

    public ByzantineWorkerFilter getWorkerFilter() {
        return workerFilter;
    }

    // ==========================================
    // 1. 拜占庭容错理论边界检测 (Theorem 1.1)
    // ==========================================

    /**
     * 判断是否满足经典拜占庭容错硬界限：n >= 3f + 1
     */
    public static boolean isBftTolerant(int totalNodes, int byzantineFaults) {
        if (byzantineFaults < 0 || totalNodes <= 0) {
            return false;
        }
        return totalNodes >= (3 * byzantineFaults + 1);
    }

    /**
     * 计算容纳 f 个拜占庭故障所需的最小法定人数 Quorum Q >= 2f + 1
     */
    public static int getRequiredQuorum(int byzantineFaults) {
        return 2 * byzantineFaults + 1;
    }

    /**
     * 计算 n 个节点集群最多可容忍的拜占庭恶意/故障节点数 f = floor((n - 1) / 3)
     */
    public static int getMaxFaults(int totalNodes) {
        if (totalNodes <= 0) {
            return 0;
        }
        return (totalNodes - 1) / 3;
    }

    // ==========================================
    // 2. 离散结构化加权多数投票 (Discrete Weighted Voting)
    // ==========================================

    /**
     * 离散决策加权投票
     */
    public ConsensusResult arbitrateDiscrete(String taskId, List<InspectedProposal> inspectedProposals) {
        if (inspectedProposals == null || inspectedProposals.isEmpty()) {
            return ConsensusResult.failOpen(taskId, "EMPTY_INPUT", 0);
        }

        int total = inspectedProposals.size();
        List<InspectedProposal> honest = inspectedProposals.stream()
                .filter(InspectedProposal::isHonest)
                .collect(Collectors.toList());

        if (honest.isEmpty()) {
            log.warn("[ConsensusArbiter] Task [{}] 无可用诚实节点，触发 Fail-Open 降级！", taskId);
            return ConsensusResult.failOpen(taskId, "NO_HONEST_WORKERS", total);
        }

        // 统计各离散提案内容的支持权重
        Map<String, Double> voteWeights = new LinkedHashMap<>();
        Map<String, String> bestWorkerForChoice = new HashMap<>();
        Map<String, Double> highestWorkerWeightForChoice = new HashMap<>();
        double totalHonestWeight = 0.0;

        for (InspectedProposal p : honest) {
            String contentKey = p.proposal().rawContent().trim();
            double w = Math.max(0.01, p.reputationWeight());
            voteWeights.merge(contentKey, w, Double::sum);
            totalHonestWeight += w;

            double prevMax = highestWorkerWeightForChoice.getOrDefault(contentKey, -1.0);
            if (w > prevMax) {
                highestWorkerWeightForChoice.put(contentKey, w);
                bestWorkerForChoice.put(contentKey, p.proposal().workerId());
            }
        }

        // 寻找加权得分最高选项
        String winnerChoice = null;
        double maxWeight = -1.0;
        for (Map.Entry<String, Double> entry : voteWeights.entrySet()) {
            if (entry.getValue() > maxWeight) {
                maxWeight = entry.getValue();
                winnerChoice = entry.getKey();
            }
        }

        double confidence = totalHonestWeight > 0 ? (maxWeight / totalHonestWeight) : 0.0;
        boolean quorumAchieved = confidence >= 0.50; // 加权绝对多数派

        return new ConsensusResult(
                taskId,
                winnerChoice,
                quorumAchieved,
                confidence,
                bestWorkerForChoice.get(winnerChoice),
                total,
                honest.size(),
                1,
                quorumAchieved ? "FAST_QUORUM_DISCRETE" : "INSUFFICIENT_QUORUM"
        );
    }

    // ==========================================
    // 3. 阿里千问 1536 维超球面加权 Medoid 中心选取
    // ==========================================

    /**
     * 针对自由文本计算加权 Medoid
     */
    public ConsensusResult arbitrateContinuousMedoid(String taskId, List<InspectedProposal> inspectedProposals) {
        if (inspectedProposals == null || inspectedProposals.isEmpty()) {
            return ConsensusResult.failOpen(taskId, "EMPTY_INPUT", 0);
        }

        int total = inspectedProposals.size();
        List<InspectedProposal> honest = inspectedProposals.stream()
                .filter(p -> p.isHonest() && p.embedding1536() != null)
                .collect(Collectors.toList());

        if (honest.isEmpty()) {
            log.warn("[ConsensusArbiter] Task [{}] 诚实节点集为空，执行 Fail-Open 兜底！", taskId);
            return ConsensusResult.failOpen(taskId, "NO_HONEST_WORKERS", total);
        }

        // 单节点特判
        if (honest.size() == 1) {
            InspectedProposal single = honest.getFirst();
            return new ConsensusResult(
                    taskId,
                    single.proposal().rawContent(),
                    true,
                    single.proposal().selfConfidence(),
                    single.proposal().workerId(),
                    total,
                    1,
                    1,
                    "FAST_QUORUM_MEDOID"
            );
        }

        int m = honest.size();
        double[] weightedScores = new double[m];
        double[] totalOtherWeights = new double[m];

        // 依据加权余弦测地线相似度计算 Medoid 得分
        // Score(i) = sum_{j != i} (w_j * cos(v_i, v_j))
        for (int i = 0; i < m; i++) {
            float[] vi = honest.get(i).embedding1536();
            double scoreSum = 0.0;
            double otherWeightSum = 0.0;

            for (int j = 0; j < m; j++) {
                if (i != j) {
                    float[] vj = honest.get(j).embedding1536();
                    double wj = Math.max(0.01, honest.get(j).reputationWeight());
                    double sim = ByzantineWorkerFilter.dotProduct(vi, vj);
                    scoreSum += wj * sim;
                    otherWeightSum += wj;
                }
            }
            weightedScores[i] = scoreSum;
            totalOtherWeights[i] = otherWeightSum;
        }

        // 选取出加权中心度最高 (Medoid) 的诚实提案
        int bestIdx = 0;
        double bestScore = -1e9;
        for (int i = 0; i < m; i++) {
            if (weightedScores[i] > bestScore) {
                bestScore = weightedScores[i];
                bestIdx = i;
            }
        }

        InspectedProposal medoid = honest.get(bestIdx);
        double cohortConfidence = totalOtherWeights[bestIdx] > 0
                ? (bestScore / totalOtherWeights[bestIdx])
                : 0.80;
        cohortConfidence = Math.clamp(cohortConfidence, 0.0, 1.0);

        // Dempster-Shafer 证据融合：自评置信度与群测地线余弦相似度加权融合
        double selfConf = Math.max(0.50, medoid.proposal().selfConfidence());
        double finalConfidence = Math.clamp(0.35 * selfConf + 0.65 * cohortConfidence, 0.0, 1.0);

        // 检验 Fast Quorum：诚实节点达到多数派 (>= total/2) 且综合置信度 >= 0.35
        boolean quorumPassed = (honest.size() >= (total + 1) / 2) && (finalConfidence >= 0.35);

        return new ConsensusResult(
                taskId,
                medoid.proposal().rawContent(),
                quorumPassed,
                finalConfidence,
                medoid.proposal().workerId(),
                total,
                honest.size(),
                1,
                quorumPassed ? "FAST_QUORUM_MEDOID" : "INSUFFICIENT_QUORUM"
        );
    }

    // ==========================================
    // 4. Fast Quorum 提前截断与高可用降级 (Fail-Open)
    // ==========================================

    /**
     * 接收流式提案，当达到法定 Quorum 时提前截断裁决
     */
    public ConsensusResult arbitrateWithFastQuorumCutoff(
            String taskId,
            List<WorkerProposal> incomingProposals,
            int expectedTotalWorkers,
            double fastQuorumSimilarityThreshold
    ) {
        if (incomingProposals == null || incomingProposals.isEmpty()) {
            return ConsensusResult.failOpen(taskId, "STREAM_TIMEOUT", expectedTotalWorkers);
        }

        // 拜占庭多维过滤
        List<InspectedProposal> inspected = workerFilter.filterProposals(incomingProposals);
        List<InspectedProposal> honest = inspected.stream()
                .filter(InspectedProposal::isHonest)
                .toList();

        int maxFaults = getMaxFaults(expectedTotalWorkers);
        int requiredQuorum = getRequiredQuorum(maxFaults);

        // 诚实节点达到 Quorum 人数门限
        if (honest.size() >= requiredQuorum) {
            ConsensusResult result = arbitrateContinuousMedoid(taskId, inspected);
            if (result.isConsensusAchieved() && result.consensusConfidence() >= fastQuorumSimilarityThreshold) {
                log.info("[ConsensusArbiter] 提前触发 Fast Quorum 截断！HonestNodes={}/{}, Confidence={}",
                        honest.size(), expectedTotalWorkers, result.consensusConfidence());
                return result;
            }
        }

        // 若收齐全部仍未达到高置信度或节点不足，降级执行标准裁决
        return arbitrateContinuousMedoid(taskId, inspected);
    }
}
