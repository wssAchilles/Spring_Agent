package tech.qiantong.qknow.ai.rag.hierarchical.model;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Phase 123 核心资产：高保真层次化 GraphRAG 融合不可变存证凭单 (HierarchicalGraphRagReceipt)
 * 纯 Java 21 Record 格式，固化超长文档四级金字塔路径、Steiner 因果树拓扑哈希、PPR 分布熵与反幻觉接地评分
 * 内置基于 HMAC-SHA256 的密码学防篡改自签名与常量时间抗侧信道自验真方法
 */
public record HierarchicalGraphRagReceipt(
        String receiptId,
        String sessionId,
        String query,
        List<String> hierarchicalPath,
        String steinerTopologyHash,
        int steinerNodeCount,
        double pprEntropy,
        double groundingScore,
        String executionStatus,
        long latencyMicros,
        long timestampEpochMs,
        String hmacSha256Signature
) {

    // 状态常量定义
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_REJECTED_UNGROUNDED = "STATUS_REJECTED_UNGROUNDED";
    public static final String STATUS_FAILED_EXECUTION = "STATUS_FAILED_EXECUTION";

    public HierarchicalGraphRagReceipt {
        Objects.requireNonNull(receiptId, "凭单 ID 不能为空");
        Objects.requireNonNull(sessionId, "会话 ID 不能为空");
        Objects.requireNonNull(query, "查询文本不能为空");
        Objects.requireNonNull(hierarchicalPath, "层次化路径不能为空");
        Objects.requireNonNull(steinerTopologyHash, "Steiner 拓扑哈希不能为空");
        Objects.requireNonNull(executionStatus, "执行状态不能为空");
        Objects.requireNonNull(hmacSha256Signature, "签名不能为空");
    }

    /**
     * 静态工厂方法：构建存证凭单并自动完成 HMAC-SHA256 密码学防伪自签名
     */
    public static HierarchicalGraphRagReceipt create(
            String receiptId,
            String sessionId,
            String query,
            List<String> hierarchicalPath,
            String steinerTopologyHash,
            int steinerNodeCount,
            double pprEntropy,
            double groundingScore,
            String executionStatus,
            long latencyMicros,
            long timestampEpochMs,
            String secretKey
    ) {
        String rawPayload = buildPayloadString(
                receiptId, sessionId, query, hierarchicalPath, steinerTopologyHash,
                steinerNodeCount, pprEntropy, groundingScore, executionStatus, latencyMicros, timestampEpochMs
        );
        String signature = computeHmacSha256(rawPayload, secretKey);
        return new HierarchicalGraphRagReceipt(
                receiptId, sessionId, query, List.copyOf(hierarchicalPath), steinerTopologyHash,
                steinerNodeCount, pprEntropy, groundingScore, executionStatus, latencyMicros, timestampEpochMs, signature
        );
    }

    /**
     * 常量时间自验真方法：检验凭单因果字段是否遭受任何单比特非法篡改
     * 采用 MessageDigest.isEqual 消除时序分析侧信道攻击隐患
     *
     * @param secretKey 验真防伪密钥
     * @return true-合法未篡改, false-已被篡改或密钥不匹配
     */
    public boolean verifySignature(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String expectedPayload = buildPayloadString(
                receiptId, sessionId, query, hierarchicalPath, steinerTopologyHash,
                steinerNodeCount, pprEntropy, groundingScore, executionStatus, latencyMicros, timestampEpochMs
        );
        String expectedSignature = computeHmacSha256(expectedPayload, secretKey);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.hmacSha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String buildPayloadString(
            String receiptId, String sessionId, String query, List<String> path,
            String steinerHash, int nodeCount, double pprEntropy, double score,
            String status, long latency, long timestamp
    ) {
        String pathStr = String.join("->", path);
        return receiptId + "|" + sessionId + "|" + query + "|" + pathStr + "|" + steinerHash + "|"
                + nodeCount + "|" + String.format("%.4f", pprEntropy) + "|" + String.format("%.4f", score) + "|"
                + status + "|" + latency + "|" + timestamp;
    }

    private static String computeHmacSha256(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名计算异常", e);
        }
    }
}
