package tech.qiantong.qknow.hermes.swarm.saga;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 纯 Java 21 Record 格式不可变多智能体分布式工具 Saga 补偿存证凭单 (Tool Saga Compensation Receipt)
 * 严格对齐 Phase 144 密码学零时序泄漏工程铁律与 W3C TraceContext 全链路因果追踪。
 *
 * @author Achilles
 * @since Phase 144
 */
public record ToolSagaCompensationReceipt(
        String receiptId,
        String tenantId,
        String taskId,
        String traceId,
        String transactionId,
        int totalSteps,
        int compensatedSteps,
        int failedSteps,
        boolean isFullyCompensated,
        boolean isLeaseExpired,
        String intentDisentangleDigest,
        List<String> stepOrder,
        double latencyMs,
        long timestamp,
        String sha256Signature
) {

    public ToolSagaCompensationReceipt {
        // 保证内部列表不可变防篡改
        stepOrder = stepOrder != null ? List.copyOf(stepOrder) : List.of();
    }

    /**
     * 工厂方法：自动规范化计算全字段防篡改 SHA-256 数字签名并签发不可变凭单
     */
    public static ToolSagaCompensationReceipt create(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            String transactionId,
            int totalSteps,
            int compensatedSteps,
            int failedSteps,
            boolean isFullyCompensated,
            boolean isLeaseExpired,
            String intentDisentangleDigest,
            List<String> stepOrder,
            double latencyMs,
            long timestamp
    ) {
        String payload = buildNormalizedPayload(
                receiptId, tenantId, taskId, traceId, transactionId,
                totalSteps, compensatedSteps, failedSteps, isFullyCompensated,
                isLeaseExpired, intentDisentangleDigest, stepOrder, latencyMs, timestamp
        );
        String signature = computeSha256Hex(payload);
        return new ToolSagaCompensationReceipt(
                receiptId, tenantId, taskId, traceId, transactionId,
                totalSteps, compensatedSteps, failedSteps, isFullyCompensated,
                isLeaseExpired, intentDisentangleDigest, stepOrder, latencyMs,
                timestamp, signature
        );
    }

    /**
     * 校验数字签名完整性 (严格使用 MessageDigest.isEqual 常量时间比对防时序侧信道攻击)
     */
    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String expectedPayload = buildNormalizedPayload(
                receiptId, tenantId, taskId, traceId, transactionId,
                totalSteps, compensatedSteps, failedSteps, isFullyCompensated,
                isLeaseExpired, intentDisentangleDigest, stepOrder, latencyMs, timestamp
        );
        String expectedSignature = computeSha256Hex(expectedPayload);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * 构建规范化全字段签名负载字符串
     */
    private static String buildNormalizedPayload(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            String transactionId,
            int totalSteps,
            int compensatedSteps,
            int failedSteps,
            boolean isFullyCompensated,
            boolean isLeaseExpired,
            String intentDisentangleDigest,
            List<String> stepOrder,
            double latencyMs,
            long timestamp
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiptId).append("|")
                .append(tenantId).append("|")
                .append(taskId).append("|")
                .append(traceId).append("|")
                .append(transactionId).append("|")
                .append(totalSteps).append("|")
                .append(compensatedSteps).append("|")
                .append(failedSteps).append("|")
                .append(isFullyCompensated).append("|")
                .append(isLeaseExpired).append("|")
                .append(intentDisentangleDigest != null ? intentDisentangleDigest : "").append("|")
                .append(stepOrder != null ? String.join(",", stepOrder) : "").append("|")
                .append(String.format("%.4f", latencyMs)).append("|")
                .append(timestamp);
        return sb.toString();
    }

    private static String computeSha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM SHA-256 algorithm unavailable", e);
        }
    }
}
