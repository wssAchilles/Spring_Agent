package tech.qiantong.qknow.ai.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Phase 62: 智能体技能编排不可变存证凭单 (Record)
 * <p>
 * 具备密码学不可篡改特性，内置 SHA-256 签名与自校验能力。
 */
public record SkillOrchestrationReceipt(
        String receiptId,
        long orchestrationEpoch,
        String taskIntent,
        List<String> selectedSkillIds,
        String dagExecutionPlan,
        double paretoFitnessScore,
        boolean hotSwapOccurred,
        boolean executionSuccess,
        String decisionSummary,
        long timestamp,
        String sha256Signature
) {

    /**
     * 工厂方法：构建并自动计算 SHA-256 签名的存证凭据
     */
    public static SkillOrchestrationReceipt create(
            String receiptId,
            long orchestrationEpoch,
            String taskIntent,
            List<String> selectedSkillIds,
            String dagExecutionPlan,
            double paretoFitnessScore,
            boolean hotSwapOccurred,
            boolean executionSuccess,
            String decisionSummary,
            long timestamp
    ) {
        String signature = computeHash(
                receiptId, orchestrationEpoch, taskIntent, selectedSkillIds,
                dagExecutionPlan, paretoFitnessScore, hotSwapOccurred,
                executionSuccess, decisionSummary, timestamp
        );
        return new SkillOrchestrationReceipt(
                receiptId, orchestrationEpoch, taskIntent,
                selectedSkillIds != null ? List.copyOf(selectedSkillIds) : List.of(),
                dagExecutionPlan, paretoFitnessScore, hotSwapOccurred,
                executionSuccess, decisionSummary, timestamp, signature
        );
    }

    /**
     * 自校验签名防篡改
     */
    public boolean verifySignature() {
        String expectedHash = computeHash(
                receiptId, orchestrationEpoch, taskIntent, selectedSkillIds,
                dagExecutionPlan, paretoFitnessScore, hotSwapOccurred,
                executionSuccess, decisionSummary, timestamp
        );
        return Objects.equals(this.sha256Signature, expectedHash);
    }

    private static String computeHash(
            String receiptId,
            long orchestrationEpoch,
            String taskIntent,
            List<String> selectedSkillIds,
            String dagExecutionPlan,
            double paretoFitnessScore,
            boolean hotSwapOccurred,
            boolean executionSuccess,
            String decisionSummary,
            long timestamp
    ) {
        String skillsStr = selectedSkillIds != null ? String.join(",", selectedSkillIds) : "";
        String raw = String.format("%s|%d|%s|%s|%s|%.6f|%b|%b|%s|%d",
                receiptId, orchestrationEpoch, taskIntent, skillsStr,
                dagExecutionPlan, paretoFitnessScore, hotSwapOccurred,
                executionSuccess, decisionSummary, timestamp);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺失 SHA-256 算法实现", e);
        }
    }
}
