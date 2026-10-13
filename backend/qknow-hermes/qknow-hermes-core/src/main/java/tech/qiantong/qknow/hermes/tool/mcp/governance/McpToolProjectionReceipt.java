package tech.qiantong.qknow.hermes.tool.mcp.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 工具两阶段超球面动态投影与安全剪枝不可变存证凭单
 * 严格遵循纯 Java 21 Record 不可变性规范与常量时间防侧信道标准
 */
public record McpToolProjectionReceipt(
        String receiptId,
        String queryText,
        List<String> projectedToolCodes,
        int totalPoolSize,
        int projectedCount,
        double tokenCompressionRatio,
        Map<String, String> circuitStates,
        long timestampEpochMs,
        String sha256Signature
) {
    public McpToolProjectionReceipt {
        projectedToolCodes = List.copyOf(projectedToolCodes != null ? projectedToolCodes : List.of());
        circuitStates = Map.copyOf(circuitStates != null ? circuitStates : Map.of());
    }

    public static McpToolProjectionReceipt create(
            String queryText,
            List<String> projectedToolCodes,
            int totalPoolSize,
            double tokenCompressionRatio,
            Map<String, String> circuitStates
    ) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        List<String> safeTools = projectedToolCodes != null ? List.copyOf(projectedToolCodes) : List.of();
        Map<String, String> safeCircuits = circuitStates != null ? Map.copyOf(circuitStates) : Map.of();

        String sig = calculateSignature(id, queryText, safeTools, totalPoolSize, safeTools.size(), tokenCompressionRatio, now);

        return new McpToolProjectionReceipt(
                id,
                queryText != null ? queryText : "",
                safeTools,
                totalPoolSize,
                safeTools.size(),
                tokenCompressionRatio,
                safeCircuits,
                now,
                sig
        );
    }

    private static String calculateSignature(
            String id, String query, List<String> tools,
            int poolSize, int count, double ratio, long ts
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = id + "|" + (query != null ? query : "") + "|" + String.join(",", tools) + "|" +
                    poolSize + "|" + count + "|" + String.format("%.4f", ratio) + "|" + ts;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 散列计算失败", e);
        }
    }

    /**
     * 基于 MessageDigest.isEqual 的常量时间自验真方法，杜绝时序侧信道分析攻击
     */
    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String expected = calculateSignature(
                receiptId, queryText, projectedToolCodes,
                totalPoolSize, projectedCount, tokenCompressionRatio, timestampEpochMs
        );
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * 向后兼容历史构造函数（兼容存量测试与外部引用）
     */
    public McpToolProjectionReceipt(
            String queryHash,
            List<String> projectedTools,
            int totalRegisteredTools,
            int projectedCount,
            double compressionRatio,
            Map<String, String> circuitBreakerStates,
            java.time.Instant timestamp,
            String signature
    ) {
        this(
                queryHash != null ? queryHash : UUID.randomUUID().toString(),
                queryHash != null ? queryHash : "",
                projectedTools,
                totalRegisteredTools,
                projectedCount,
                compressionRatio,
                circuitBreakerStates,
                timestamp != null ? timestamp.toEpochMilli() : System.currentTimeMillis(),
                signature
        );
    }

    public String signature() {
        return sha256Signature;
    }

    public int totalRegisteredTools() {
        return totalPoolSize;
    }

    public double compressionRatio() {
        return tokenCompressionRatio;
    }

    public Map<String, String> circuitBreakerStates() {
        return circuitStates;
    }

    public String queryHash() {
        return receiptId;
    }

    public java.time.Instant timestamp() {
        return java.time.Instant.ofEpochMilli(timestampEpochMs);
    }
}
