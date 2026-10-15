package tech.qiantong.qknow.hermes.agent.planner.prm;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 步骤级过程奖励评估不可变存证凭单 (StepProcessRewardReceipt)
 * 遵循 Java 21 Record 规范、阿里千问 1536 维超球面单位球几何约束与零时序泄漏工程铁律：
 * 1. 记录单步推演内容、过程质量得分 r_step in [0.0, 1.0] 与评估状态；
 * 2. 挂载 1536 维超球面状态向量并进行防御性拷贝；
 * 3. 采用 SHA-256 与 MessageDigest.isEqual 进行密码学自验真，防御时序侧信道攻击。
 */
public record StepProcessRewardReceipt(
        String receiptId,
        String traceSessionId,
        int stepIndex,
        String stepContent,
        double confidenceScore,
        StepEvaluationStatus status,
        float[] hypersphericalVector,
        String signatureSha256,
        long executionLatencyMs,
        long timestampEpochMs
) implements Serializable {

    public enum StepEvaluationStatus {
        PASSED,
        TRIGGER_BACKTRACK,
        REVISED
    }

    public StepProcessRewardReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(traceSessionId, "traceSessionId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        stepContent = stepContent != null ? stepContent : "";
        if (hypersphericalVector != null && hypersphericalVector.length != 1536) {
            throw new IllegalArgumentException("Vector dimension must be strictly 1536, got: " + hypersphericalVector.length);
        }
        hypersphericalVector = hypersphericalVector != null ? hypersphericalVector.clone() : null;
        signatureSha256 = signatureSha256 != null ? signatureSha256 : "";
    }

    @Override
    public float[] hypersphericalVector() {
        return hypersphericalVector != null ? hypersphericalVector.clone() : null;
    }

    public static StepProcessRewardReceipt createSigned(
            String receiptId,
            String traceSessionId,
            int stepIndex,
            String stepContent,
            double confidenceScore,
            StepEvaluationStatus status,
            float[] vector,
            long latencyMs,
            String secretKey
    ) {
        long now = System.currentTimeMillis();
        String payload = buildRawPayload(receiptId, traceSessionId, stepIndex, stepContent, confidenceScore, status, latencyMs, now, secretKey);
        String signature = computeSha256(payload);
        return new StepProcessRewardReceipt(
                receiptId, traceSessionId, stepIndex, stepContent,
                confidenceScore, status, vector, signature, latencyMs, now
        );
    }

    public boolean verifySignature(String secretKey) {
        if (signatureSha256 == null || signatureSha256.isBlank()) {
            return false;
        }
        String payload = buildRawPayload(receiptId, traceSessionId, stepIndex, stepContent, confidenceScore, status, executionLatencyMs, timestampEpochMs, secretKey);
        String expected = computeSha256(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureSha256.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildRawPayload(
            String receiptId,
            String traceSessionId,
            int stepIndex,
            String stepContent,
            double confidenceScore,
            StepEvaluationStatus status,
            long latencyMs,
            long timestampMs,
            String secretKey
    ) {
        return String.format(Locale.ROOT, "%s|%s|%d|%d|%.4f|%s|%d|%d|%s",
                receiptId, traceSessionId, stepIndex, stepContent.hashCode(),
                confidenceScore, status.name(), latencyMs, timestampMs,
                secretKey == null ? "HERMES_PRM_DEFAULT_SECRET" : secretKey);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
