package tech.qiantong.qknow.mcp.core.gateway.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 不可变密码学 MCP 执行存证凭单 (Java 21 原生 Record)
 * 具备基于 SHA-256 的自签名与验真机制，保障全链路操作不可篡改与合规可审计性
 */
public record McpExecutionReceipt(
        String receiptId,
        String sessionId,
        String serverId,
        String toolName,
        McpContractType contractType,
        String executionStatus,
        long latencyUs,
        String inputHash,
        String outputHash,
        String busStatus,
        String signature
) {
    /**
     * 生成并签发不可变凭单
     */
    public static McpExecutionReceipt create(
            String receiptId,
            String sessionId,
            String serverId,
            String toolName,
            McpContractType contractType,
            String executionStatus,
            long latencyUs,
            String inputHash,
            String outputHash,
            String busStatus
    ) {
        String rawData = buildPayloadString(receiptId, sessionId, serverId, toolName, contractType,
                executionStatus, latencyUs, inputHash, outputHash, busStatus);
        String sig = computeSha256(rawData);
        return new McpExecutionReceipt(
                receiptId, sessionId, serverId, toolName, contractType,
                executionStatus, latencyUs, inputHash, outputHash, busStatus, sig
        );
    }

    /**
     * 校验密码学自签名真伪
     *
     * @return 验真是否通过
     */
    public boolean verifySignature() {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String rawData = buildPayloadString(receiptId, sessionId, serverId, toolName, contractType,
                executionStatus, latencyUs, inputHash, outputHash, busStatus);
        String expectedSig = computeSha256(rawData);
        return expectedSig.equalsIgnoreCase(signature);
    }

    private static String buildPayloadString(
            String receiptId,
            String sessionId,
            String serverId,
            String toolName,
            McpContractType contractType,
            String executionStatus,
            long latencyUs,
            String inputHash,
            String outputHash,
            String busStatus
    ) {
        return (receiptId != null ? receiptId : "") + "|" +
                (sessionId != null ? sessionId : "") + "|" +
                (serverId != null ? serverId : "") + "|" +
                (toolName != null ? toolName : "") + "|" +
                (contractType != null ? contractType.name() : "") + "|" +
                (executionStatus != null ? executionStatus : "") + "|" +
                latencyUs + "|" +
                (inputHash != null ? inputHash : "") + "|" +
                (outputHash != null ? outputHash : "") + "|" +
                (busStatus != null ? busStatus : "");
    }

    public static String computeSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
