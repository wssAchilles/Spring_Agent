package tech.qiantong.qknow.hermes.memory.reflexion;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 语言强化自反思长效情景记忆不可变存证凭单 (纯 Java 21 Record)
 * 严格绑定阿里千问 1536 维超球面单位向量与 DeepSeek 思考链语言反思洞察
 *
 * @author Achilles
 * @since 2026-10-13
 */
public record ReflexionEpisodicMemoryReceipt(
        String receiptId,
        String tenantId,
        String agentRole,
        String taskIdentifier,
        int trialSequence,                // 试次编号 (Trial 1, 2, ...)
        String failedTrajectorySummary,   // 失败执行轨迹摘要
        String evaluatorFeedback,         // 评估器指出的核心失败原因
        String verbalReflectionLesson,    // DeepSeek 思考链生成的自然语言自反思先验规则
        float[] qwenHypersphericalVector, // 阿里千问 1536 维超球面单位向量 (L2 norm = 1.0)
        double fidelityScore,             // 向量与反思语义的保真度
        long createdAtEpochMs,
        String cryptographicSeal
) implements Serializable {

    public static final int VECTOR_DIMENSION = 1536;

    public ReflexionEpisodicMemoryReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(verbalReflectionLesson, "verbalReflectionLesson 不能为空");
        Objects.requireNonNull(cryptographicSeal, "cryptographicSeal 不能为空");
        if (qwenHypersphericalVector != null) {
            if (qwenHypersphericalVector.length != VECTOR_DIMENSION) {
                throw new IllegalArgumentException("千问向量维度必须为 1536 维，当前: " + qwenHypersphericalVector.length);
            }
            // 验证超球面 L2 单位范数
            double sumSq = 0.0;
            for (float f : qwenHypersphericalVector) {
                sumSq += (double) f * f;
            }
            if (Math.abs(Math.sqrt(sumSq) - 1.0) > 1e-3) {
                throw new IllegalArgumentException("超球面向量必须严格满足单位 L2 范数 ||v||_2 = 1.0");
            }
        }
    }

    public static ReflexionEpisodicMemoryReceipt create(
            String receiptId,
            String tenantId,
            String agentRole,
            String taskId,
            int trialSeq,
            String failedSummary,
            String feedback,
            String reflectionLesson,
            float[] vector1536,
            double fidelity
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format("%s|%s|%s|%s|%d|%s|%.4f|%d",
                receiptId, tenantId, agentRole, taskId, trialSeq, reflectionLesson, fidelity, now);
        String seal = computeSha256(payload);
        return new ReflexionEpisodicMemoryReceipt(
                receiptId, tenantId, agentRole, taskId, trialSeq, failedSummary,
                feedback, reflectionLesson, vector1536, fidelity, now, seal
        );
    }

    public boolean verifyCryptographicSeal() {
        String payload = String.format("%s|%s|%s|%s|%d|%s|%.4f|%d",
                receiptId, tenantId, agentRole, taskIdentifier, trialSequence, verbalReflectionLesson, fidelityScore, createdAtEpochMs);
        byte[] expected = computeSha256(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = cryptographicSeal.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算异常", e);
        }
    }
}
