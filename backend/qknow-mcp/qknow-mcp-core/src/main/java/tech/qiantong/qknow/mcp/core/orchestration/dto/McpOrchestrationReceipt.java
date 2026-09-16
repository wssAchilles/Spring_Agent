package tech.qiantong.qknow.mcp.core.orchestration.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 不可变 MCP 编排存证凭单 Record (Java 21 原生 Record)
 * 具备基于 SHA-256 的密码学自签名与验真机制，保障全链路操作不可篡改
 */
public record McpOrchestrationReceipt(
        String receiptId,
        String sessionId,
        String userIntent,
        List<String> plannedToolchain,
        int cycleDetectedCount,
        int selfHealingAttempts,
        long routeLatencyMicros,
        long totalExecutionLatencyMicros,
        String busStatus,
        String signature
) {
    public static McpOrchestrationReceipt create(
            String receiptId,
            String sessionId,
            String userIntent,
            List<String> plannedToolchain,
            int cycleDetectedCount,
            int selfHealingAttempts,
            long routeLatencyMicros,
            long totalExecutionLatencyMicros,
            String busStatus
    ) {
        String raw = buildPayloadString(receiptId, sessionId, userIntent, cycleDetectedCount, selfHealingAttempts, busStatus);
        String sig = computeSha256(raw);
        return new McpOrchestrationReceipt(
                receiptId, sessionId, userIntent, plannedToolchain, cycleDetectedCount,
                selfHealingAttempts, routeLatencyMicros, totalExecutionLatencyMicros, busStatus, sig
        );
    }

    public boolean verifySignature() {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String raw = buildPayloadString(receiptId, sessionId, userIntent, cycleDetectedCount, selfHealingAttempts, busStatus);
        String expected = computeSha256(raw);
        return expected.equalsIgnoreCase(signature);
    }

    private static String buildPayloadString(
            String receiptId, String sessionId, String userIntent,
            int cycleDetectedCount, int selfHealingAttempts, String busStatus
    ) {
        return receiptId + ":" + sessionId + ":" + (userIntent != null ? userIntent : "") +
                ":" + cycleDetectedCount + ":" + selfHealingAttempts + ":" + busStatus;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
