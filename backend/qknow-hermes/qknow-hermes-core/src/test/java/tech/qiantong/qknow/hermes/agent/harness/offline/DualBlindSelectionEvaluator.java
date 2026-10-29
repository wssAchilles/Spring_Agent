package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 双向匿名盲审评估器 (Position-Swap Dual Pass Selection Evaluator)。
 *
 * <p>吸收 Harness² selection.py 的匿名映射思想，并创新性增加 Position-Swap Dual Pass 对称双向评估机制，
 * 彻底消除大语言模型原生的“首项位置偏好 (Position Bias)”，并严格贯彻数据零泄漏门禁。</p>
 */
public class DualBlindSelectionEvaluator {

    public static final String LABEL_BASELINE = "BASELINE";
    public static final String LABEL_CANDIDATE = "CANDIDATE";

    /**
     * 双向匿名盲审裁决结果凭单（不可变 Java 21 Record）。
     */
    public record SelectionDecisionRecord(
            String taskId,
            String chosenCandidateId,      // 胜出标识（LABEL_BASELINE 或 LABEL_CANDIDATE）
            boolean candidateAdopted,      // 是否采纳候选者
            String pass1ChosenAnonId,      // Pass 1 匿名代号（A 或 B）
            String pass2ChosenAnonId,      // Pass 2 匿名代号（A 或 B）
            boolean positionConsistency,   // 双向位置一致性验证是否通过
            String rationalSummary,        // 盲审裁判理由
            long evaluationDurationMs
    ) {
        public SelectionDecisionRecord {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(chosenCandidateId, "chosenCandidateId");
            Objects.requireNonNull(pass1ChosenAnonId, "pass1ChosenAnonId");
            Objects.requireNonNull(pass2ChosenAnonId, "pass2ChosenAnonId");
        }
    }

    /**
     * 盲审裁判模拟判定函数接口（用于单元测试或模型调度）。
     */
    @FunctionalInterface
    public interface BlindJudgeFunction {
        /**
         * 匿名评估两个选项。
         *
         * @param anonOptionA 选项 A 的交付物与执行指标摘要
         * @param anonOptionB 选项 B 的交付物与执行指标摘要
         * @return "A" 或 "B"
         */
        String judge(String anonOptionA, String anonOptionB);
    }

    /**
     * 基于 SHA-256 伪随机种子确定 Pass 1 中 BASELINE 与 CANDIDATE 对应 A/B 的映射。
     */
    public static Map<String, String> assignAnonymizedIds(String taskId, String salt) {
        String seedSource = (taskId == null ? "default" : taskId) + "|" + (salt == null ? "salt" : salt) + "|selection";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seedSource.getBytes(StandardCharsets.UTF_8));
            // 取首字节判断奇偶
            boolean baselineIsA = (hash[0] & 1) == 0;
            if (baselineIsA) {
                return Map.of("A", LABEL_BASELINE, "B", LABEL_CANDIDATE);
            } else {
                return Map.of("A", LABEL_CANDIDATE, "B", LABEL_BASELINE);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 缺少 SHA-256", e);
        }
    }

    /**
     * 执行双向交叉匿名盲审评估。
     */
    public SelectionDecisionRecord evaluate(
            OfflineBenchmarkTestCase testCase,
            ProbeExecutionDiff diff,
            String baseSummary,
            String candSummary,
            BlindJudgeFunction judgeFunction) {

        long startNs = System.nanoTime();

        // 1. 数据零泄漏前置雷区扫描
        String combinedContext = (baseSummary == null ? "" : baseSummary) + " " + (candSummary == null ? "" : candSummary);
        SelectionDataLeakageTripwire.assertNoLeak(combinedContext, List.of());

        // 2. 预算硬门禁初筛：若候选者未通过预算门禁，直接宣布 Baseline 胜出，无需调用评测
        if (!diff.satisfiesBudgetGate() || !diff.toolSelectionMatched() || !diff.nonDegradingQuality()) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            return new SelectionDecisionRecord(
                    testCase.taskId(),
                    LABEL_BASELINE,
                    false,
                    "N/A",
                    "N/A",
                    true,
                    "候选者未通过物理预算门禁或质量退化，安全回退 Baseline",
                    durationMs
            );
        }

        // 3. 确定 Pass 1 匿名映射
        Map<String, String> pass1Map = assignAnonymizedIds(testCase.taskId(), "round-1");
        String pass1SummaryA = pass1Map.get("A").equals(LABEL_BASELINE) ? baseSummary : candSummary;
        String pass1SummaryB = pass1Map.get("B").equals(LABEL_BASELINE) ? baseSummary : candSummary;

        // 执行 Pass 1 盲审
        String pass1Choice = judgeFunction.judge(pass1SummaryA, pass1SummaryB);
        String pass1Winner = pass1Map.getOrDefault(pass1Choice, LABEL_BASELINE);

        // 4. 执行 Pass 2 交换位置对称盲审 (Position-Swap)
        // 在 Pass 2 中，强制对调 A 和 B 的呈现内容
        String pass2SummaryA = pass1SummaryB;
        String pass2SummaryB = pass1SummaryA;
        // 此时 Pass 2 的 A 对应 Pass 1 的 B，Pass 2 的 B 对应 Pass 1 的 A
        Map<String, String> pass2Map = Map.of(
                "A", pass1Map.get("B"),
                "B", pass1Map.get("A")
        );

        String pass2Choice = judgeFunction.judge(pass2SummaryA, pass2SummaryB);
        String pass2Winner = pass2Map.getOrDefault(pass2Choice, LABEL_BASELINE);

        // 5. 双向一致性判定
        boolean positionConsistency = pass1Winner.equals(pass2Winner);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        if (positionConsistency && LABEL_CANDIDATE.equals(pass1Winner)) {
            // 双向盲审一致判定候选胜出
            return new SelectionDecisionRecord(
                    testCase.taskId(),
                    LABEL_CANDIDATE,
                    true,
                    pass1Choice,
                    pass2Choice,
                    true,
                    "双向交叉盲审均一致裁决候选者质量胜出且通过位置一致性检验",
                    durationMs
            );
        } else {
            // 结论冲突（存在位置偏见）或裁判判定 Baseline 胜出，安全中立回退 Baseline
            String reason = positionConsistency
                    ? "裁判一致判定 Baseline 胜出"
                    : "Pass 1 与 Pass 2 裁决冲突（触发大模型位置偏置防线），安全回退 Baseline";
            return new SelectionDecisionRecord(
                    testCase.taskId(),
                    LABEL_BASELINE,
                    false,
                    pass1Choice,
                    pass2Choice,
                    positionConsistency,
                    reason,
                    durationMs
            );
        }
    }
}
