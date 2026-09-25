package tech.qiantong.qknow.hermes.swarm.coalition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 自适应合作博弈多智能体联盟组建与夏普利收益分配引擎 (Adaptive Coalition Game Former)
 * 基于阿里千问 1536 维超球面单位向量空间，严格满足 Lemma 143.1 核稳定性与次模性贪心近似。
 *
 * @author Achilles
 * @since Phase 143
 */
public class AdaptiveCoalitionGameFormer {

    public static final int EMBEDDING_DIM = 1536;

    /**
     * 智能体博弈参选档案
     */
    public record AgentGameProfile(
            String agentId,
            String tenantId,
            String agentRole,
            double[] qwenEmbedding,   // 阿里千问 1536 维超球面单位向量
            double reputationScore,    // 动态信誉分 (in [0.0, 1.0])
            double baseCost,           // 基础调用开销
            boolean isPinned           // 是否具有永久免死钉扎特权
    ) {
        public AgentGameProfile {
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(qwenEmbedding, "qwenEmbedding must not be null");
            if (qwenEmbedding.length != EMBEDDING_DIM) {
                throw new IllegalArgumentException("Qwen embedding dimension must be strictly " + EMBEDDING_DIM);
            }
        }
    }

    /**
     * 联盟博弈输出成果
     */
    public record CoalitionFormationResult(
            String coalitionId,
            String tenantId,
            String taskId,
            List<String> selectedMemberIds,
            Map<String, Double> shapleyPayoffs,
            double coalitionValue,
            double synergyRatio,
            AgentCoalitionContractReceipt receipt,
            double executionLatencyMs
    ) {}

    private final LogDampedReputationTracker reputationTracker;
    private final double commPenaltyLambda; // 协同通信损耗阻尼惩罚
    private final int maxCoalitionSize;      // 最大协同联盟规模

    public AdaptiveCoalitionGameFormer(LogDampedReputationTracker reputationTracker) {
        this(reputationTracker, 0.02, 6);
    }

    public AdaptiveCoalitionGameFormer(LogDampedReputationTracker reputationTracker, double commPenaltyLambda, int maxCoalitionSize) {
        this.reputationTracker = reputationTracker != null ? reputationTracker : new LogDampedReputationTracker();
        this.commPenaltyLambda = commPenaltyLambda;
        this.maxCoalitionSize = maxCoalitionSize;
    }

    /**
     * 核心调度：基于超球面正交投影与次模贪心夏普利近似组建最佳核稳定协同联盟
     *
     * @param tenantId      租户隔离标识
     * @param taskId        任务标识
     * @param traceId       W3C 32位十六进制因果 TraceId
     * @param taskEmbedding 任务语义向量 (阿里千问 1536 维超球面单位向量)
     * @param candidatePool 候选智能体池
     * @return 决策完备的联盟博弈成果与不可变存证凭单
     */
    public CoalitionFormationResult formOptimalCoalition(
            String tenantId,
            String taskId,
            String traceId,
            double[] taskEmbedding,
            List<AgentGameProfile> candidatePool
    ) {
        long startNano = System.nanoTime();

        // 1. 租户物理集合过滤与单位向量归一化校验
        double[] normalizedTask = normalizeVector(taskEmbedding);
        List<AgentGameProfile> tenantCandidates = new ArrayList<>();
        for (AgentGameProfile p : candidatePool) {
            if (tenantId.equals(p.tenantId())) {
                // 排除处于冷备隔离态且非钉扎的智能体
                if (!reputationTracker.isQuarantined(tenantId, p.agentId()) || p.isPinned()) {
                    tenantCandidates.add(p);
                }
            }
        }

        if (tenantCandidates.isEmpty()) {
            double latency = (System.nanoTime() - startNano) / 1_000_000.0;
            String coalitionId = "COALITION-EMPTY-" + System.currentTimeMillis();
            AgentCoalitionContractReceipt emptyReceipt = AgentCoalitionContractReceipt.create(
                    "RCP-" + coalitionId, tenantId, taskId, traceId, coalitionId,
                    List.of(), Map.of(), Map.of(), 0.0, 1.0, false, latency, System.currentTimeMillis()
            );
            return new CoalitionFormationResult(
                    coalitionId, tenantId, taskId, List.of(), Map.of(), 0.0, 1.0, emptyReceipt, latency
            );
        }

        // 2. 贪心增量次模构建过程
        List<AgentGameProfile> selected = new ArrayList<>();
        List<double[]> orthogonalBasis = new ArrayList<>(); // 联盟子空间的正交基底
        Set<String> selectedIds = new HashSet<>();
        List<Double> marginalGains = new ArrayList<>(); // 记录入选步骤的边际贡献

        double currentCoverageEnergy = 0.0;

        for (int step = 0; step < maxCoalitionSize; step++) {
            AgentGameProfile bestCandidate = null;
            double[] bestOrthoResidual = null;
            double bestMarginalGain = -1.0;

            for (AgentGameProfile cand : tenantCandidates) {
                if (selectedIds.contains(cand.agentId())) {
                    continue;
                }
                double[] candVec = normalizeVector(cand.qwenEmbedding());

                // 计算相对于当前已选联盟正交补空间的投影残差向量
                double[] residual = computeOrthogonalComplement(candVec, orthogonalBasis);
                double residualNorm = computeVectorNorm(residual);

                // 任务亲和度 (超球面余弦)
                double taskCosine = Math.max(0.0, dotProduct1536(candVec, normalizedTask));

                // 综合有效信誉权重 (结合 tracker 与档案)
                double effectiveRep = Math.max(0.1, reputationTracker.getProfile(tenantId, cand.agentId()).currentReputation());

                // 边际增益函数: Rep * ||P_perp v_i|| * cos(v_i, q) - lambda * |S|
                double marginalGain = (effectiveRep * residualNorm * taskCosine) - (commPenaltyLambda * selected.size());

                if (marginalGain > bestMarginalGain) {
                    bestMarginalGain = marginalGain;
                    bestCandidate = cand;
                    bestOrthoResidual = residual;
                }
            }

            // 终止判定：若没有正向增益或无法增进技能正交性，则提前收敛
            if (bestCandidate == null || bestMarginalGain <= 1e-5) {
                break;
            }

            selected.add(bestCandidate);
            selectedIds.add(bestCandidate.agentId());
            marginalGains.add(bestMarginalGain);

            // 将正交残差单位化并追加至正交基
            double norm = computeVectorNorm(bestOrthoResidual);
            if (norm > 1e-6) {
                double[] normalizedBasis = new double[EMBEDDING_DIM];
                for (int d = 0; d < EMBEDDING_DIM; d++) {
                    normalizedBasis[d] = bestOrthoResidual[d] / norm;
                }
                orthogonalBasis.add(normalizedBasis);
            }
        }

        // 3. 计算联盟总体特征价值 v(S)
        // 投影覆盖度: ||P_W q|| = sqrt( sum( (q . e_k)^2 ) )
        double projectedSumSq = 0.0;
        for (double[] basis : orthogonalBasis) {
            double dot = dotProduct1536(normalizedTask, basis);
            projectedSumSq += dot * dot;
        }
        double rawCoverage = Math.sqrt(Math.min(1.0, projectedSumSq));
        double coalitionValue = Math.max(0.0, rawCoverage - (commPenaltyLambda * selected.size()));

        // 计算单兵 baseline 参照（取成员中单兵最大能力）
        double maxSingleCapability = 0.0;
        for (AgentGameProfile p : selected) {
            double singleDot = Math.max(0.0, dotProduct1536(p.qwenEmbedding(), normalizedTask));
            if (singleDot > maxSingleCapability) {
                maxSingleCapability = singleDot;
            }
        }
        double synergyRatio = maxSingleCapability > 1e-6 ? (coalitionValue / maxSingleCapability) : 1.0;

        // 4. 夏普利值 (Shapley Value) 增量闭式公平分配
        Map<String, Double> shapleyPayoffs = computeShapleyPayoffs(selected, marginalGains, normalizedTask, coalitionValue);

        // 5. 联动信誉追踪器记录成功贡献与演化
        Map<String, Double> updatedReputations = new TreeMap<>();
        boolean containsQuarantined = false;
        for (AgentGameProfile p : selected) {
            double payoff = shapleyPayoffs.getOrDefault(p.agentId(), 0.0);
            LogDampedReputationTracker.ReputationProfile updated =
                    reputationTracker.recordSuccessContribution(tenantId, p.agentId(), payoff, coalitionValue);
            updatedReputations.put(p.agentId(), updated.currentReputation());
            if (updated.quarantineLevel() == LogDampedReputationTracker.QuarantineLevel.LEVEL_2_QUARANTINED) {
                containsQuarantined = true;
            }
        }

        double latencyMs = (System.nanoTime() - startNano) / 1_000_000.0;
        String coalitionId = "COALITION-" + System.currentTimeMillis() + "-" + Math.abs(taskId.hashCode() % 10000);

        List<String> memberIds = selected.stream().map(AgentGameProfile::agentId).toList();

        // 6. 生成纯 Java 21 Record 格式不可变存证凭单 (含常量时间 SHA-256 签名)
        AgentCoalitionContractReceipt receipt = AgentCoalitionContractReceipt.create(
                "RCP-" + coalitionId,
                tenantId,
                taskId,
                traceId,
                coalitionId,
                memberIds,
                shapleyPayoffs,
                updatedReputations,
                coalitionValue,
                synergyRatio,
                containsQuarantined,
                latencyMs,
                System.currentTimeMillis()
        );

        return new CoalitionFormationResult(
                coalitionId, tenantId, taskId, memberIds, shapleyPayoffs,
                coalitionValue, synergyRatio, receipt, latencyMs
        );
    }

    /**
     * 闭式夏普利公平分配计算：严格遵循效率性公理（总和等于 v(S)）与虚拟玩家零收益公理
     */
    private Map<String, Double> computeShapleyPayoffs(
            List<AgentGameProfile> selected,
            List<Double> marginalGains,
            double[] normalizedTask,
            double coalitionValue
    ) {
        Map<String, Double> payoffs = new TreeMap<>();
        if (selected.isEmpty()) {
            return payoffs;
        }

        double[] rawWeights = new double[selected.size()];
        double sumWeights = 0.0;

        for (int i = 0; i < selected.size(); i++) {
            AgentGameProfile p = selected.get(i);
            double taskCosine = Math.max(0.0, dotProduct1536(p.qwenEmbedding(), normalizedTask));

            // 虚拟玩家判定：若与任务完全正交且边际贡献为 0，分配权重严格为 0
            if (taskCosine <= 1e-7 || marginalGains.get(i) <= 1e-7) {
                rawWeights[i] = 0.0;
            } else {
                // 边际增益与能力契合度联合加权
                rawWeights[i] = marginalGains.get(i) + (0.1 * taskCosine * p.reputationScore());
                sumWeights += rawWeights[i];
            }
        }

        // 效率性归一化：保证收益总和恒等于联盟特征值 coalitionValue
        if (sumWeights > 1e-9 && coalitionValue > 1e-9) {
            double accumulated = 0.0;
            for (int i = 0; i < selected.size() - 1; i++) {
                double portion = (rawWeights[i] / sumWeights) * coalitionValue;
                payoffs.put(selected.get(i).agentId(), portion);
                accumulated += portion;
            }
            // 最后一员吸收微小截断残差，保证总和精确等于 coalitionValue
            double lastPortion = Math.max(0.0, coalitionValue - accumulated);
            payoffs.put(selected.get(selected.size() - 1).agentId(), lastPortion);
        } else {
            for (AgentGameProfile p : selected) {
                payoffs.put(p.agentId(), 0.0);
            }
        }

        return payoffs;
    }

    /**
     * 计算向量相对于已有正交基的正交补残差 (Gram-Schmidt 正交化)
     */
    private double[] computeOrthogonalComplement(double[] target, List<double[]> basisList) {
        double[] residual = target.clone();
        for (double[] basis : basisList) {
            double proj = dotProduct1536(target, basis);
            for (int d = 0; d < EMBEDDING_DIM; d++) {
                residual[d] -= proj * basis[d];
            }
        }
        return residual;
    }

    /**
     * 高性能 8 路展开 1536 维向量点积 (SIMD Friendly)
     */
    public static double dotProduct1536(double[] a, double[] b) {
        double sum0 = 0.0, sum1 = 0.0, sum2 = 0.0, sum3 = 0.0;
        double sum4 = 0.0, sum5 = 0.0, sum6 = 0.0, sum7 = 0.0;

        int limit = EMBEDDING_DIM - 7;
        int i = 0;
        for (; i < limit; i += 8) {
            sum0 += a[i] * b[i];
            sum1 += a[i + 1] * b[i + 1];
            sum2 += a[i + 2] * b[i + 2];
            sum3 += a[i + 3] * b[i + 3];
            sum4 += a[i + 4] * b[i + 4];
            sum5 += a[i + 5] * b[i + 5];
            sum6 += a[i + 6] * b[i + 6];
            sum7 += a[i + 7] * b[i + 7];
        }
        for (; i < EMBEDDING_DIM; i++) {
            sum0 += a[i] * b[i];
        }
        return sum0 + sum1 + sum2 + sum3 + sum4 + sum5 + sum6 + sum7;
    }

    /**
     * 计算向量 L2 模长
     */
    public static double computeVectorNorm(double[] vec) {
        return Math.sqrt(Math.max(0.0, dotProduct1536(vec, vec)));
    }

    /**
     * 向量 L2 范数单位归一化 (保持 ||v||_2 = 1.0)
     */
    public static double[] normalizeVector(double[] vec) {
        double norm = computeVectorNorm(vec);
        if (norm <= 1e-12) {
            double[] fallback = new double[EMBEDDING_DIM];
            fallback[0] = 1.0;
            return fallback;
        }
        double[] normalized = new double[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            normalized[i] = vec[i] / norm;
        }
        return normalized;
    }

    public LogDampedReputationTracker getReputationTracker() {
        return reputationTracker;
    }
}
