package tech.qiantong.qknow.hermes.agent.planner.mcts;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * MCTS 搜索规划不可变存证凭单 Record (MctsSearchReceipt)
 * 基于 SHA-256 自签名与 MessageDigest.isEqual 常量时间抗时序侧信道比对。
 */
public record MctsSearchReceipt(
        String receiptId,
        String traceSessionId,
        int totalNodesExpanded,
        int prunedBranchCount,
        int maxDepthReached,
        double bestTrajectoryValue,
        List<String> optimalActionPath,
        String finalAnswerText,
        String sha256Signature,
        long executionLatencyMs,
        long timestampMs
) implements Serializable {

    public MctsSearchReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不可为空");
        Objects.requireNonNull(traceSessionId, "traceSessionId 不可为空");
        optimalActionPath = optimalActionPath != null ? List.copyOf(optimalActionPath) : List.of();
        finalAnswerText = finalAnswerText != null ? finalAnswerText : "";
    }

    public static MctsSearchReceipt createSigned(
            String receiptId, String traceSessionId, int totalExpanded,
            int prunedCount, int maxDepth, double bestValue,
            List<String> optimalPath, String finalAnswer, long latencyMs) {
        long now = System.currentTimeMillis();
        String payload = computeRawPayload(receiptId, traceSessionId, totalExpanded, prunedCount,
                maxDepth, bestValue, optimalPath, latencyMs, now);
        String signature = computeSha256(payload);
        return new MctsSearchReceipt(
                receiptId, traceSessionId, totalExpanded, prunedCount,
                maxDepth, bestValue, optimalPath, finalAnswer,
                signature, latencyMs, now
        );
    }

    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String payload = computeRawPayload(receiptId, traceSessionId, totalNodesExpanded, prunedBranchCount,
                maxDepthReached, bestTrajectoryValue, optimalActionPath,
                executionLatencyMs, timestampMs);
        String expected = computeSha256(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sha256Signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeRawPayload(
            String receiptId, String traceSessionId, int totalExpanded,
            int prunedCount, int maxDepth, double bestValue,
            List<String> optimalPath, long latencyMs, long timestampMs) {
        return String.format("%s|%s|%d|%d|%d|%.4f|%s|%d|%d",
                receiptId, traceSessionId, totalExpanded, prunedCount,
                maxDepth, bestValue, String.join(",", optimalPath != null ? optimalPath : List.of()),
                latencyMs, timestampMs);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
