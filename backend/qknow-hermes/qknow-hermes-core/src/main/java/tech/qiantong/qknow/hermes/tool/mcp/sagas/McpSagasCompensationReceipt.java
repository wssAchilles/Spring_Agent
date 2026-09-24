package tech.qiantong.qknow.hermes.tool.mcp.sagas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 级联 MCP Sagas 逆拓扑事务补偿存证凭单
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间验真
 */
public record McpSagasCompensationReceipt(
        String receiptId,
        String runtimeId,
        String flowId,
        int totalStepsToCompensate,
        int successfullyCompensatedSteps,
        boolean fullyCompensated,
        List<String> executedReverseOrder,
        long latencyMs,
        long timestamp,
        String signature
) {
    public McpSagasCompensationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(executedReverseOrder, "executedReverseOrder 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    public static McpSagasCompensationReceipt create(
            String runtimeId,
            String flowId,
            int totalStepsToCompensate,
            int successfullyCompensatedSteps,
            boolean fullyCompensated,
            List<String> executedReverseOrder,
            long latencyMs
    ) {
        String receiptId = "SAGA-REC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = System.currentTimeMillis();
        String payload = buildCanonicalPayload(receiptId, runtimeId, flowId, totalStepsToCompensate,
                successfullyCompensatedSteps, fullyCompensated, executedReverseOrder, latencyMs, timestamp);
        String signature = computeSha256(payload);

        return new McpSagasCompensationReceipt(
                receiptId,
                runtimeId,
                flowId != null ? flowId : "UNKNOWN_FLOW",
                totalStepsToCompensate,
                successfullyCompensatedSteps,
                fullyCompensated,
                List.copyOf(executedReverseOrder),
                latencyMs,
                timestamp,
                signature
        );
    }

    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(receiptId, runtimeId, flowId, totalStepsToCompensate,
                successfullyCompensatedSteps, fullyCompensated, executedReverseOrder, latencyMs, timestamp);
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String runtimeId,
            String flowId,
            int total,
            int success,
            boolean full,
            List<String> order,
            long latencyMs,
            long timestamp
    ) {
        return receiptId + "|" + runtimeId + "|" + flowId + "|" + total + "|" + success + "|"
                + full + "|" + String.join(",", order) + "|" + latencyMs + "|" + timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
