package tech.qiantong.qknow.hermes.benchmark.evaluation;

import java.util.List;
import java.util.Map;

/**
 * 正交消融学术基准聚合评测报告与显著性检验结果（Java 21 Record）。
 */
public record BenchmarkEvaluationReport(
        String profileId,
        long randomSeed,
        int totalTasks,
        double passAt1Rate,
        double validExecutionRate,
        double prefixCachePreservationRate,
        double meanVariationalUtility,
        double meanReasoningConsistency,
        double meanLatencyMs,
        double meanUncachedTokens,
        double causalDecouplingBlockRate,
        double paretoHypervolume4D,
        List<String> jsonlTrajectoryLines
) {

    /**
     * 跨档位统计检验与 4 维 Pareto 前沿对比摘要。
     */
    public record MatrixComparisonSummary(
            List<BenchmarkEvaluationReport> reports,
            Map<String, Double> mcnemarPValuesVsBaseline,
            Map<String, Double> wilcoxonUtilityPValuesVsBaseline,
            List<String> paretoOptimalProfiles
    ) {
    }
}
