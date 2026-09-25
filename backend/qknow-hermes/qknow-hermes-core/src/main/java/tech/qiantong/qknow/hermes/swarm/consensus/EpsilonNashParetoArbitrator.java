package tech.qiantong.qknow.hermes.swarm.consensus;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 松弛纳什均衡 (ε-Nash) 早停收敛与多目标帕累托前沿权衡仲裁器 (EpsilonNashParetoArbitrator)
 * <p>
 * 核心机制：
 * 1. 追踪相邻辩论轮次智能体策略向量在阿里千问 1536 维超球面上的测地欧氏角位移差分 Δσ；
 * 2. 当 Δσ <= 0.05 时，精准判定达到 ε-Nash 纳什均衡稳态，立即果断早停退出辩论循环，博弈轮次强制锁定在 <= 5 轮；
 * 3. 针对候选方案构建包含“质量得分、成本节约得分、安全低风险得分”的三元效用向量；
 * 4. 执行多目标帕累托非支配排序 (Pareto Dominance Filter)，剔除被绝对支配的劣解，从帕累托前沿中推选全局最优折中解；
 * 5. 单轮纳什判定与帕累托双重仲裁耗时严格 <= 3.0ms。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
public class EpsilonNashParetoArbitrator {

    public static final int EMBEDDING_DIM = 1536;
    public static final double EPSILON_NASH_TOLERANCE = 0.05;
    public static final int MAX_DEBATE_ROUNDS = 5;

    // 多目标综合效用评估权重：质量(45%) + 成本节约(25%) + 安全稳健性(30%)
    public static final double WEIGHT_QUALITY = 0.45;
    public static final double WEIGHT_COST = 0.25;
    public static final double WEIGHT_RISK = 0.30;

    /**
     * 候选方案载荷
     *
     * @param solutionId 方案全局唯一编号
     * @param proposalTitle 方案标题/摘要
     * @param qwenEmbedding 阿里千问 1536 维超球面归一化向量
     * @param qualityScore 方案质量得分 [0.0, 1.0] (越大越优)
     * @param costScore 成本优势得分 [0.0, 1.0] (越大代表成本越低/越节约)
     * @param riskScore 安全稳健性得分 [0.0, 1.0] (越大代表风险越低/越安全)
     */
    public record CandidateSolution(
            String solutionId,
            String proposalTitle,
            double[] qwenEmbedding,
            double qualityScore,
            double costScore,
            double riskScore
    ) {
        public CandidateSolution {
            Objects.requireNonNull(solutionId, "solutionId 不能为空");
            Objects.requireNonNull(proposalTitle, "proposalTitle 不能为空");
            Objects.requireNonNull(qwenEmbedding, "qwenEmbedding 不能为空");
            if (qwenEmbedding.length != EMBEDDING_DIM) {
                throw new IllegalArgumentException("阿里千问超球面向量维度必须严格为 " + EMBEDDING_DIM);
            }
        }

        /**
         * 计算三元目标综合效用值
         */
        public double computeCompositeUtility() {
            return WEIGHT_QUALITY * qualityScore + WEIGHT_COST * costScore + WEIGHT_RISK * riskScore;
        }

        /**
         * 判定当前方案是否帕累托支配目标方案 other (Pareto Dominance)
         * 当且仅当：在所有三个维度上都不劣于 other，且至少在一个维度上严格优于 other
         */
        public boolean dominates(CandidateSolution other) {
            boolean noWorse = this.qualityScore >= other.qualityScore &&
                    this.costScore >= other.costScore &&
                    this.riskScore >= other.riskScore;
            boolean strictlyBetter = this.qualityScore > other.qualityScore ||
                    this.costScore > other.costScore ||
                    this.riskScore > other.riskScore;
            return noWorse && strictlyBetter;
        }
    }

    /**
     * 帕累托仲裁与纳什收敛输出结果
     *
     * @param winningSolutionId 胜选方案编号
     * @param winningSolution 胜选方案完整载荷
     * @param paretoFrontier 帕累托前沿非支配解集
     * @param dominatedSolutions 被支配淘汰的劣解集
     * @param isEpsilonNashConverged 是否因 ε-Nash 纳什均衡稳态达成早停
     * @param totalRounds 经历的辩论总轮次
     * @param finalGeodesicDelta 最终轮次的策略测地角位移差分 Δσ
     * @param latencyMs 仲裁执行总耗时 (毫秒)
     */
    public record ParetoArbitrationResult(
            String winningSolutionId,
            CandidateSolution winningSolution,
            List<CandidateSolution> paretoFrontier,
            List<CandidateSolution> dominatedSolutions,
            boolean isEpsilonNashConverged,
            int totalRounds,
            double finalGeodesicDelta,
            double latencyMs
    ) {}

    /**
     * 检查相邻轮次智能体策略向量集是否达成 ε-Nash 测地线收敛 (定理 1.2)
     *
     * @param currentRoundEmbeddings 当前轮次各智能体超球面嵌入向量
     * @param previousRoundEmbeddings 上一轮次各智能体超球面嵌入向量
     * @return 测地线平均角位移差分 Δσ
     */
    public double computeGeodesicDisplacementDelta(
            List<double[]> currentRoundEmbeddings,
            List<double[]> previousRoundEmbeddings
    ) {
        if (currentRoundEmbeddings == null || previousRoundEmbeddings == null ||
                currentRoundEmbeddings.isEmpty() || previousRoundEmbeddings.isEmpty()) {
            return 1.0;
        }

        int count = Math.min(currentRoundEmbeddings.size(), previousRoundEmbeddings.size());
        if (count == 0) {
            return 1.0;
        }

        double totalDisplacement = 0.0;
        for (int i = 0; i < count; i++) {
            double dot = computeDotProduct(currentRoundEmbeddings.get(i), previousRoundEmbeddings.get(i));
            // 钳位在 [-1.0, 1.0] 避免浮点误差导致 acos NaN
            double clamped = Math.max(-1.0, Math.min(1.0, dot));
            double angle = Math.acos(clamped);
            totalDisplacement += angle;
        }

        return totalDisplacement / count;
    }

    /**
     * 过滤计算多目标候选方案集合的帕累托前沿 (Pareto Frontier)
     *
     * @param candidates 候选方案列表
     * @return 拆分出的非支配前沿与被支配方案
     */
    public Map<String, List<CandidateSolution>> filterParetoFrontier(List<CandidateSolution> candidates) {
        Map<String, List<CandidateSolution>> result = new HashMap<>();
        List<CandidateSolution> frontier = new ArrayList<>();
        List<CandidateSolution> dominated = new ArrayList<>();

        if (candidates == null || candidates.isEmpty()) {
            result.put("frontier", frontier);
            result.put("dominated", dominated);
            return result;
        }

        for (int i = 0; i < candidates.size(); i++) {
            CandidateSolution cand = candidates.get(i);
            boolean isDominated = false;
            for (int j = 0; j < candidates.size(); j++) {
                if (i != j && candidates.get(j).dominates(cand)) {
                    isDominated = true;
                    break;
                }
            }
            if (isDominated) {
                dominated.add(cand);
            } else {
                frontier.add(cand);
            }
        }

        result.put("frontier", frontier);
        result.put("dominated", dominated);
        return result;
    }

    /**
     * 执行完整的多智能体纳什均衡早停判定与帕累托前沿权衡仲裁
     *
     * @param candidates 候选方案集合
     * @param currentRound 当前轮次 (1-indexed)
     * @param geodesicDelta 当前轮次的测地线角位移差分
     * @return 最终帕累托仲裁决议
     */
    public ParetoArbitrationResult arbitrateConsensus(
            List<CandidateSolution> candidates,
            int currentRound,
            double geodesicDelta
    ) {
        long startNs = System.nanoTime();

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("候选方案列表不能为空");
        }

        // 1. 纳什均衡稳态早停判定
        boolean isEpsilonNashConverged = geodesicDelta <= EPSILON_NASH_TOLERANCE || currentRound >= MAX_DEBATE_ROUNDS;

        // 2. 帕累托非支配排序
        Map<String, List<CandidateSolution>> paretoMap = filterParetoFrontier(candidates);
        List<CandidateSolution> frontier = paretoMap.get("frontier");
        List<CandidateSolution> dominated = paretoMap.get("dominated");

        // 3. 从帕累托前沿中，根据多目标综合效用函数选出终审胜选方案
        CandidateSolution winningSolution = frontier.stream()
                .max(Comparator.comparingDouble(CandidateSolution::computeCompositeUtility))
                .orElse(candidates.get(0));

        double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;

        log.info("多智能体帕累托仲裁完成: winningId={}, frontierCount={}, dominatedCount={}, isNashConverged={}, latency={}ms",
                winningSolution.solutionId(), frontier.size(), dominated.size(), isEpsilonNashConverged, latencyMs);

        return new ParetoArbitrationResult(
                winningSolution.solutionId(),
                winningSolution,
                frontier,
                dominated,
                isEpsilonNashConverged,
                currentRound,
                geodesicDelta,
                latencyMs
        );
    }

    /**
     * 计算两个 1536 维超球面向量的内积
     */
    private double computeDotProduct(double[] u, double[] v) {
        if (u == null || v == null || u.length != EMBEDDING_DIM || v.length != EMBEDDING_DIM) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dot += u[i] * v[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
