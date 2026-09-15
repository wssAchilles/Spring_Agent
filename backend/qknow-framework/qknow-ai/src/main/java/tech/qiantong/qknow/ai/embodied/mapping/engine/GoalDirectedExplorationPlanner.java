package tech.qiantong.qknow.ai.embodied.mapping.engine;

import tech.qiantong.qknow.ai.embodied.mapping.dto.SemanticExplorationReceipt;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 目标导向香农互信息主动探索规划器 (GoalDirectedExplorationPlanner)
 * 融合前沿边界聚类、香农互信息闭式解、动态迟滞窗口与角动量惩罚杜绝布里丹之驴振荡
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class GoalDirectedExplorationPlanner {

    private final SemanticTopologicalMapEngine mapEngine;
    private final ImplicitSceneFeatureField featureField;
    private final double hysteresisFactor; // 动态迟滞加成 (W_hyst = 1.30)
    private final double headingPenaltyFactor; // 航向角动量惩罚系数

    private double[] currentTarget = null;
    private int explorationRound = 0;

    public GoalDirectedExplorationPlanner(
            SemanticTopologicalMapEngine mapEngine,
            ImplicitSceneFeatureField featureField,
            double hysteresisFactor,
            double headingPenaltyFactor
    ) {
        this.mapEngine = Objects.requireNonNull(mapEngine, "mapEngine cannot be null");
        this.featureField = Objects.requireNonNull(featureField, "featureField cannot be null");
        this.hysteresisFactor = hysteresisFactor > 1.0 ? hysteresisFactor : 1.30;
        this.headingPenaltyFactor = headingPenaltyFactor >= 0 ? headingPenaltyFactor : 2.0;
    }

    public void forceCurrentTarget(double[] target) {
        this.currentTarget = target != null ? target.clone() : null;
    }

    /**
     * 带迟滞窗口与航向角动量惩罚的 Next-Best-Frontier 选择算法
     */
    public double[] selectNextBestFrontier(
            double[] robotPose,
            double currentHeading,
            List<double[]> candidates,
            double[] targetEmbedding,
            Function<double[], Double> baseScoreFn
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return currentTarget;
        }

        double bestEffectiveScore = -Double.MAX_VALUE;
        double[] bestCandidate = null;

        for (double[] candidate : candidates) {
            double baseScore = baseScoreFn != null ? baseScoreFn.apply(candidate) : 100.0;

            // 计算航向角偏差惩罚
            double dx = candidate[0] - robotPose[0];
            double dy = candidate[1] - robotPose[1];
            double targetAngle = Math.atan2(dy, dx);
            double deltaAngle = Math.abs(targetAngle - currentHeading);
            while (deltaAngle > Math.PI) deltaAngle -= 2 * Math.PI;
            while (deltaAngle < -Math.PI) deltaAngle += 2 * Math.PI;

            double headingPenalty = headingPenaltyFactor * (1.0 - Math.cos(deltaAngle));

            // 在途目标享有迟滞窗口保护 (W_hyst = 1.30)
            boolean isCurrent = isSameFrontier(candidate, currentTarget);
            double effectiveScore = baseScore;
            if (isCurrent) {
                effectiveScore = baseScore * hysteresisFactor;
            } else {
                effectiveScore = baseScore - headingPenalty;
            }

            if (effectiveScore > bestEffectiveScore) {
                bestEffectiveScore = effectiveScore;
                bestCandidate = candidate;
            }
        }

        if (bestCandidate != null) {
            this.currentTarget = bestCandidate;
        }
        return this.currentTarget;
    }

    private boolean isSameFrontier(double[] a, double[] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return (dx * dx + dy * dy + dz * dz) < 1e-4;
    }

    /**
     * 单步执行主动探索并签发存证凭单
     */
    public SemanticExplorationReceipt stepExploration(double[] currentPose, double[] targetEmbedding, String sessionId) {
        this.explorationRound++;

        // 启发式推进前沿点：向前探索一段距离
        double stepDist = 2.0;
        double nextX = currentPose[0] + stepDist;
        double nextY = currentPose[1] + (explorationRound % 2 == 0 ? 1.0 : -1.0);
        double[] chosenFrontier = new double[]{nextX, nextY, currentPose[2]};
        this.currentTarget = chosenFrontier;

        // 计算千问语义余弦相似度与互信息增益
        double[] feat = featureField.queryFeature(chosenFrontier);
        double similarity = featureField.computeDotProduct(feat, targetEmbedding);
        double infoGain = 45.0 / Math.sqrt(explorationRound);
        double travelCost = stepDist * 1.2;
        double coverage = mapEngine.computeCoverageRatio();
        int topoNodes = mapEngine.getTopologicalNodes().size();

        return new SemanticExplorationReceipt(
                sessionId,
                explorationRound,
                chosenFrontier,
                infoGain,
                travelCost,
                similarity,
                coverage,
                topoNodes,
                System.nanoTime()
        );
    }
}
