package tech.qiantong.qknow.mcp.client.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 不可变 MCP 执行存证凭单 (McpExecutionReceipt)
 * 基于纯 Java 21 Record 格式构建
 * 内置全字段 SHA-256 密码学自签名与反篡改自验真能力
 */
public record McpExecutionReceipt(
        String receiptId,
        String transportMode,
        String serverId,
        String toolName,
        String argumentHash,
        String riskLevel,
        String approvalStatus,
        String approverUserId,
        String executionStatus,
        long durationNanos,
        long timestampMillis,
        String signature
) {

    /**
     * 创建并自动签署存证凭单
     */
    public static McpExecutionReceipt createSigned(
            String receiptId,
            String transportMode,
            String serverId,
            String toolName,
            String argumentHash,
            String riskLevel,
            String approvalStatus,
            String approverUserId,
            String executionStatus,
            long durationNanos,
            long timestampMillis) {

        if (receiptId == null || transportMode == null || serverId == null || toolName == null) {
            throw new IllegalArgumentException("Essential receipt identifiers must not be null");
        }

        String signature = calculateSignature(
                receiptId, transportMode, serverId, toolName, argumentHash,
                riskLevel, approvalStatus, approverUserId, executionStatus,
                durationNanos, timestampMillis
        );

        return new McpExecutionReceipt(
                receiptId, transportMode, serverId, toolName, argumentHash,
                riskLevel, approvalStatus, approverUserId, executionStatus,
                durationNanos, timestampMillis, signature
        );
    }

    /**
     * 计算凭单全字段 SHA-256 签名
     */
    public static String calculateSignature(
            String receiptId, String transportMode, String serverId, String toolName,
            String argumentHash, String riskLevel, String approvalStatus,
            String approverUserId, String executionStatus, long durationNanos, long timestampMillis) {
        try {
            String payload = String.join("|",
                    String.valueOf(receiptId),
                    String.valueOf(transportMode),
                    String.valueOf(serverId),
                    String.valueOf(toolName),
                    String.valueOf(argumentHash),
                    String.valueOf(riskLevel),
                    String.valueOf(approvalStatus),
                    String.valueOf(approverUserId),
                    String.valueOf(executionStatus),
                    String.valueOf(durationNanos),
                    String.valueOf(timestampMillis)
            );
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("计算 MCP 存证凭单签名失败", e);
        }
    }

    /**
     * 自签名验真：验证凭单是否未经篡改
     */
    public boolean verifySignature() {
        if (this.signature == null) {
            return false;
        }
        String expected = calculateSignature(
                receiptId, transportMode, serverId, toolName, argumentHash,
                riskLevel, approvalStatus, approverUserId, executionStatus,
                durationNanos, timestampMillis
        );
        return expected.equalsIgnoreCase(this.signature);
    }
}
