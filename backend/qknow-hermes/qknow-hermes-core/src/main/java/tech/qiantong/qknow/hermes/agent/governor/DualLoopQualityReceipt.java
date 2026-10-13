package tech.qiantong.qknow.hermes.agent.governor;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 双环质量评估与自愈优化不可变凭单 Record (DualLoopQualityReceipt)
 * 记录内环确定性规则修复与外环 Evaluator-Optimizer 收敛全过程，
 * 采用 SHA-256 自签名与 MessageDigest.isEqual 常量时间抗时序侧信道比对。
 */
public record DualLoopQualityReceipt(
        String receiptId,
        String sessionId,
        String initialCandidateHash,
        String finalOptimizedHash,
        int innerLoopFixedCount,
        int outerLoopRounds,
        double evaluatorScore,
        boolean passedThreshold,
        String terminationReason,
        List<String> diagnosticFindings,
        String sha256Signature,
        long timestamp
) implements Serializable {

    public DualLoopQualityReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        diagnosticFindings = diagnosticFindings != null ? List.copyOf(diagnosticFindings) : List.of();
    }

    public static DualLoopQualityReceipt createSigned(
            String receiptId,
            String sessionId,
            String initialHash,
            String finalHash,
            int innerFixed,
            int outerRounds,
            double score,
            boolean passed,
            String reason,
            List<String> diagnostics,
            long timestamp) {

        String rawData = computeRawData(receiptId, sessionId, initialHash, finalHash,
                innerFixed, outerRounds, score, passed, reason, diagnostics, timestamp);
        String signature = computeSha256(rawData);

        return new DualLoopQualityReceipt(
                receiptId,
                sessionId,
                initialHash,
                finalHash,
                innerFixed,
                outerRounds,
                score,
                passed,
                reason,
                diagnostics,
                signature,
                timestamp
        );
    }

    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String expectedData = computeRawData(receiptId, sessionId, initialCandidateHash, finalOptimizedHash,
                innerLoopFixedCount, outerLoopRounds, evaluatorScore, passedThreshold, terminationReason, diagnosticFindings, timestamp);
        String expectedHash = computeSha256(expectedData);
        byte[] a = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] b = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String computeRawData(
            String receiptId,
            String sessionId,
            String initialHash,
            String finalHash,
            int innerFixed,
            int outerRounds,
            double score,
            boolean passed,
            String reason,
            List<String> diagnostics,
            long timestamp) {
        return receiptId + "|" + sessionId + "|" + initialHash + "|" + finalHash + "|"
                + innerFixed + "|" + outerRounds + "|" + score + "|" + passed + "|" + reason + "|"
                + String.join(";", diagnostics != null ? diagnostics : List.of()) + "|" + timestamp;
    }

    private static String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
