package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Position-Swap 双向匿名盲审与偏见消除评估器。
 *
 * <p>吸收 Google Research Harness² selection.py 核心算法，
 * 通过确定性高熵哈希置换将基线与候选匿名映射为 Option A 与 Option B，
 * 执行正向与反向对称双向盲审，彻底消除大模型固有位置偏见（Primacy/Recency Bias）。
 * 同时支持物理差分硬短路门禁，在物理指标退化时跳过调用以节约成本。</p>
 */
public final class PositionSwapBlindEvaluator {

    private final String cryptographicSalt;

    public enum VerdictOutcome {
        ROBUST_WIN_ADOPT,       // Quadrant 1: 双向一致判定候选胜出，予以采纳
        ROBUST_LOSS_REJECT,     // Quadrant 2: 双向一致判定基线胜出，拒绝候选
        BIAS_CONFLICT_REJECT,   // Quadrant 3/4: 双向选择冲突，判定位置偏见平局，安全回退基线
        SHORT_CIRCUITED_REJECT  // 物理差分门禁硬拦截（退化短路）
    }

    public record BlindEvaluationResult(
            String taskId,
            boolean initialSwapBit,
            String pass1Winner,
            String pass2Winner,
            boolean isConsistent,
            VerdictOutcome outcome,
            String arbitrationReason
    ) {}

    public PositionSwapBlindEvaluator(String cryptographicSalt) {
        this.cryptographicSalt = (cryptographicSalt == null || cryptographicSalt.isBlank())
                ? "STAGE4_HIGH_ENTROPY_SALT_20261030"
                : cryptographicSalt;
    }

    /**
     * 执行双向匿名盲审。
     *
     * @param diff 物理差分指标
     * @param baseSummary 基线输出摘要
     * @param candSummary 候选输出摘要
     * @param blindJudge 盲审裁判函数（入参为 (optionA, optionB)，返回胜出者标识 "A" 或 "B"）
     * @return 双向盲审结果凭单
     */
    public BlindEvaluationResult evaluate(
            ProbeExecutionDiff diff,
            String baseSummary,
            String candSummary,
            BiFunction<String, String, String> blindJudge) {

        Objects.requireNonNull(diff, "diff 不能为空");
        Objects.requireNonNull(blindJudge, "blindJudge 不能为空");

        // 1. 物理指标硬拦截（短路门禁）
        if (!diff.usageComparable()) {
            return new BlindEvaluationResult(
                    diff.taskId(), false, "NONE", "NONE", false,
                    VerdictOutcome.SHORT_CIRCUITED_REJECT,
                    "物理指标硬拦截: Baseline 或 Candidate 的真实 Token Usage 未知"
            );
        }
        if (diff.deltaTokens() > 0) {
            return new BlindEvaluationResult(
                    diff.taskId(), false, "NONE", "NONE", false,
                    VerdictOutcome.SHORT_CIRCUITED_REJECT,
                    "物理指标硬拦截: 候选 Token 发生正向膨胀 (+" + diff.deltaTokens() + ")"
            );
        }
        if (diff.deltaDurationMs() > 5) {
            return new BlindEvaluationResult(
                    diff.taskId(), false, "NONE", "NONE", false,
                    VerdictOutcome.SHORT_CIRCUITED_REJECT,
                    "物理指标硬拦截: 候选耗时超出容忍上限 (+" + diff.deltaDurationMs() + "ms)"
            );
        }
        if (!diff.toolSelectionMatched()) {
            return new BlindEvaluationResult(
                    diff.taskId(), false, "NONE", "NONE", false,
                    VerdictOutcome.SHORT_CIRCUITED_REJECT,
                    "物理指标硬拦截: 候选工具调用不匹配或失败"
            );
        }

        // 2. 计算确定性置换标志位
        boolean swapBit = computeInitialSwapBit(diff.taskId());

        // 3. Pass 1 正向映射评估
        String pass1OptA = swapBit ? candSummary : baseSummary;
        String pass1OptB = swapBit ? baseSummary : candSummary;
        String pass1Choice = blindJudge.apply(pass1OptA, pass1OptB);
        String pass1RealWinner = resolveRealWinner(pass1Choice, swapBit, false);

        // 4. Pass 2 对称对调评估（物理对调 Option A 与 Option B）
        String pass2OptA = pass1OptB;
        String pass2OptB = pass1OptA;
        String pass2Choice = blindJudge.apply(pass2OptA, pass2OptB);
        String pass2RealWinner = resolveRealWinner(pass2Choice, swapBit, true);

        // 5. 双向一致性检验与 4 象限状态机判定
        boolean isConsistent = Objects.equals(pass1RealWinner, pass2RealWinner);
        VerdictOutcome outcome;
        String reason;

        if (isConsistent) {
            if ("CANDIDATE".equalsIgnoreCase(pass1RealWinner)) {
                outcome = VerdictOutcome.ROBUST_WIN_ADOPT;
                reason = "Quadrant 1: 双向盲审一致判定候选者更优，予以采纳";
            } else {
                outcome = VerdictOutcome.ROBUST_LOSS_REJECT;
                reason = "Quadrant 2: 双向盲审一致判定基线更优，拒绝候选";
            }
        } else {
            outcome = VerdictOutcome.BIAS_CONFLICT_REJECT;
            reason = String.format("Quadrant 3/4: 触发位置偏见冲突 (Pass1=%s, Pass2=%s)，安全回退基线",
                    pass1RealWinner, pass2RealWinner);
        }

        return new BlindEvaluationResult(
                diff.taskId(), swapBit, pass1RealWinner, pass2RealWinner, isConsistent, outcome, reason
        );
    }

    private boolean computeInitialSwapBit(String taskId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(taskId.getBytes(StandardCharsets.UTF_8));
            md.update(cryptographicSalt.getBytes(StandardCharsets.UTF_8));
            md.update("STAGE4_POSITION_SWAP".getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            return (hash[0] & 0x01) == 1;
        } catch (NoSuchAlgorithmException e) {
            return (taskId.hashCode() & 0x01) == 1;
        }
    }

    private static String resolveRealWinner(String choice, boolean initialSwapBit, boolean isPass2) {
        boolean choseA = "A".equalsIgnoreCase(choice);
        if (!isPass2) {
            // Pass 1: OptA = (swapBit ? Cand : Base), OptB = (swapBit ? Base : Cand)
            if (choseA) {
                return initialSwapBit ? "CANDIDATE" : "BASELINE";
            } else {
                return initialSwapBit ? "BASELINE" : "CANDIDATE";
            }
        } else {
            // Pass 2: OptA = Pass1.OptB, OptB = Pass1.OptA
            if (choseA) {
                return initialSwapBit ? "BASELINE" : "CANDIDATE";
            } else {
                return initialSwapBit ? "CANDIDATE" : "BASELINE";
            }
        }
    }
}
