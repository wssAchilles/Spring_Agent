package tech.qiantong.qknow.ai.scenario.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 业务场景端到端不可变存证凭单 (纯 Java 21 Record 格式)
 * 记录跨境采购决策全链路要素，内置 SHA-256 密码学自签名与运行时验真
 */
public record BusinessScenarioAuditReceipt(
        String receiptId,
        String scenarioId,
        String businessType,
        int thinkingSteps,
        List<String> mcpToolsInvoked,
        List<String> graphEntitiesMatched,
        String finalDecision,
        long totalLatencyMs,
        long timestamp,
        String sha256Signature
) {
    /**
     * 工厂方法：创建并自动完成 SHA-256 密码学自签名
     */
    public static BusinessScenarioAuditReceipt create(
            String scenarioId,
            String businessType,
            int thinkingSteps,
            List<String> mcpToolsInvoked,
            List<String> graphEntitiesMatched,
            String finalDecision,
            long totalLatencyMs,
            long timestamp
    ) {
        String receiptId = "RCPT_SCENARIO_" + scenarioId + "_" + timestamp;
        String signature = calculateSignature(
                receiptId, scenarioId, businessType, thinkingSteps,
                mcpToolsInvoked, graphEntitiesMatched, finalDecision,
                totalLatencyMs, timestamp
        );
        return new BusinessScenarioAuditReceipt(
                receiptId, scenarioId, businessType, thinkingSteps,
                List.copyOf(mcpToolsInvoked), List.copyOf(graphEntitiesMatched),
                finalDecision, totalLatencyMs, timestamp, signature
        );
    }

    /**
     * 运行时验真：校验凭单是否被篡改
     */
    public boolean verifySignature() {
        String expectedSignature = calculateSignature(
                receiptId, scenarioId, businessType, thinkingSteps,
                mcpToolsInvoked, graphEntitiesMatched, finalDecision,
                totalLatencyMs, timestamp
        );
        return expectedSignature.equals(this.sha256Signature);
    }

    private static String calculateSignature(
            String receiptId,
            String scenarioId,
            String businessType,
            int thinkingSteps,
            List<String> mcpToolsInvoked,
            List<String> graphEntitiesMatched,
            String finalDecision,
            long totalLatencyMs,
            long timestamp
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("|",
                    receiptId,
                    scenarioId,
                    businessType,
                    String.valueOf(thinkingSteps),
                    String.join(",", mcpToolsInvoked),
                    String.join(",", graphEntitiesMatched),
                    finalDecision,
                    String.valueOf(totalLatencyMs),
                    String.valueOf(timestamp)
            );
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
