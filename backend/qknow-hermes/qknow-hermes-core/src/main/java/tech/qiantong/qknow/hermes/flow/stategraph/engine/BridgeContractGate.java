package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V-Model R2 桥接契约门控与有界投机分叉决策器（Phase 9.2 PA-TSG 引理 1.1）。
 * <p>
 * 针对 MuSiQue 3–4 跳“首跳桥接实体选错导致后续 8–10 步全盘空转”问题：
 * 1. 零额外 LLM Token 强制校验候选实体非空与原文跨度真实落位（杜绝幻觉实体传给下游）；
 * 2. 在千问 {@code S^1023} 单位超球面上校核桥接实体的槽位余弦相似下界；
 * 3. 当 Top-1 与 Top-2 候选余弦间距落入歧义区时，签发严格宽度 {@code W <= 2} 的有界投机分叉。
 * </p>
 */
public class BridgeContractGate {

    public static final double DEFAULT_MIN_BRIDGE_COSINE = 0.25;
    public static final double DEFAULT_FORK_MARGIN_THRESHOLD = 0.08;
    public static final int MAX_SPECULATIVE_FORK_WIDTH = 2;

    /**
     * 不可变 R2 桥接门控裁决凭单。
     */
    public record BridgeGateDecision(
            boolean passed,
            boolean speculativeForkTriggered,
            List<String> selectedBridgeEntities,
            double top1Cosine,
            double top2Margin,
            String failureReason
    ) {
        public BridgeGateDecision {
            selectedBridgeEntities = List.copyOf(selectedBridgeEntities != null ? selectedBridgeEntities : List.of());
            failureReason = failureReason != null ? failureReason : "";
        }
    }

    private final AtomicLong totalEvaluations = new AtomicLong(0L);
    private final AtomicLong totalRejections = new AtomicLong(0L);
    private final AtomicLong totalSpeculativeForks = new AtomicLong(0L);

    /**
     * 执行 R2 桥接契约校验与有界投机分叉裁决。
     */
    public BridgeGateDecision evaluateBridge(
            String slotDescription,
            List<String> candidateEntities,
            List<float[]> candidateUnit1024,
            float[] slotUnit1024,
            String sourceObservationText,
            double minBridgeCosine,
            double forkMarginThreshold
    ) {
        totalEvaluations.incrementAndGet();
        if (candidateEntities == null || candidateEntities.isEmpty()) {
            totalRejections.incrementAndGet();
            return new BridgeGateDecision(false, false, List.of(), 0.0, 0.0, "R2_BRIDGE_EMPTY_CANDIDATES");
        }
        String safeObs = sourceObservationText == null ? "" : sourceObservationText.toLowerCase(Locale.ROOT);
        double safeMinCos = minBridgeCosine > -1.0 ? minBridgeCosine : DEFAULT_MIN_BRIDGE_COSINE;
        double safeMargin = forkMarginThreshold > 0.0 ? forkMarginThreshold : DEFAULT_FORK_MARGIN_THRESHOLD;

        record ScoredCandidate(String entity, double cosine) {}
        List<ScoredCandidate> grounded = new ArrayList<>();

        for (int i = 0; i < candidateEntities.size(); i++) {
            String entity = candidateEntities.get(i);
            if (entity == null || entity.isBlank()) {
                continue;
            }
            String trimmed = entity.trim();
            // 确定性原文跨度落位校验：非空且若提供了源观测文本则必须真实存在于源文本中
            if (!safeObs.isEmpty() && !safeObs.contains(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            float[] vec = (candidateUnit1024 != null && i < candidateUnit1024.size()) ? candidateUnit1024.get(i) : null;
            double cos = (slotUnit1024 != null && vec != null) ? cosine(slotUnit1024, vec) : 1.0;
            if (cos >= safeMinCos) {
                grounded.add(new ScoredCandidate(trimmed, cos));
            }
        }

        if (grounded.isEmpty()) {
            totalRejections.incrementAndGet();
            return new BridgeGateDecision(
                    false, false, List.of(), 0.0, 0.0,
                    "R2_BRIDGE_GROUNDING_OR_COSINE_REJECTED"
            );
        }

        grounded.sort((a, b) -> Double.compare(b.cosine(), a.cosine()));
        double top1Cos = grounded.get(0).cosine();
        double top2Cos = grounded.size() >= 2 ? grounded.get(1).cosine() : -1.0;
        double margin = grounded.size() >= 2 ? Math.max(0.0, top1Cos - top2Cos) : 1.0;

        if (grounded.size() >= 2 && margin < safeMargin) {
            totalSpeculativeForks.incrementAndGet();
            List<String> forked = List.of(grounded.get(0).entity(), grounded.get(1).entity());
            return new BridgeGateDecision(true, true, forked, top1Cos, margin, "");
        }

        return new BridgeGateDecision(true, false, List.of(grounded.get(0).entity()), top1Cos, margin, "");
    }

    public java.util.Map<String, Object> getTelemetryStats() {
        return java.util.Map.of(
                "totalBridgeEvaluations", totalEvaluations.get(),
                "totalBridgeRejections", totalRejections.get(),
                "totalSpeculativeForks", totalSpeculativeForks.get()
        );
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        if (denom <= 1e-12) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, dot / denom));
    }
}
