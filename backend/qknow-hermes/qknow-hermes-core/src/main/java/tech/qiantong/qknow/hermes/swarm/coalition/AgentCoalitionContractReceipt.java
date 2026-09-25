package tech.qiantong.qknow.hermes.swarm.coalition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 纯 Java 21 Record 格式不可变多智能体联盟博弈存证凭单 (Agent Coalition Contract Receipt)
 * 严格对齐 Phase 143 密码学零时序泄漏工程铁律与 W3C TraceContext 全链路因果追踪。
 *
 * @author Achilles
 * @since Phase 143
 */
public record AgentCoalitionContractReceipt(
        String receiptId,
        String tenantId,
        String taskId,
        String traceId,
        String coalitionId,
        List<String> memberAgentIds,
        Map<String, Double> shapleyPayoffs,
        Map<String, Double> reputationScores,
        double coalitionValue,
        double synergyRatio,
        boolean isQuarantined,
        double latencyMs,
        long timestamp,
        String sha256Signature
) {

    public AgentCoalitionContractReceipt {
        // 保证内部集合不可变
        memberAgentIds = memberAgentIds != null ? List.copyOf(memberAgentIds) : List.of();
        shapleyPayoffs = shapleyPayoffs != null ? Collections.unmodifiableMap(new TreeMap<>(shapleyPayoffs)) : Map.of();
        reputationScores = reputationScores != null ? Collections.unmodifiableMap(new TreeMap<>(reputationScores)) : Map.of();
    }

    /**
     * 静态工厂：创建并自动计算常量时间防篡改 SHA-256 签名
     */
    public static AgentCoalitionContractReceipt create(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            String coalitionId,
            List<String> memberAgentIds,
            Map<String, Double> shapleyPayoffs,
            Map<String, Double> reputationScores,
            double coalitionValue,
            double synergyRatio,
            boolean isQuarantined,
            double latencyMs,
            long timestamp
    ) {
        String payload = buildNormalizedPayload(
                receiptId, tenantId, taskId, traceId, coalitionId,
                memberAgentIds, shapleyPayoffs, reputationScores,
                coalitionValue, synergyRatio, isQuarantined, latencyMs, timestamp
        );
        String signature = computeSha256Hex(payload);
        return new AgentCoalitionContractReceipt(
                receiptId, tenantId, taskId, traceId, coalitionId,
                memberAgentIds, shapleyPayoffs, reputationScores,
                coalitionValue, synergyRatio, isQuarantined, latencyMs,
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
                receiptId, tenantId, taskId, traceId, coalitionId,
                memberAgentIds, shapleyPayoffs, reputationScores,
                coalitionValue, synergyRatio, isQuarantined, latencyMs, timestamp
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
            String coalitionId,
            List<String> memberAgentIds,
            Map<String, Double> shapleyPayoffs,
            Map<String, Double> reputationScores,
            double coalitionValue,
            double synergyRatio,
            boolean isQuarantined,
            double latencyMs,
            long timestamp
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiptId).append("|")
                .append(tenantId).append("|")
                .append(taskId).append("|")
                .append(traceId).append("|")
                .append(coalitionId).append("|")
                .append(memberAgentIds != null ? String.join(",", memberAgentIds) : "").append("|")
                .append(formatPayoffs(shapleyPayoffs)).append("|")
                .append(formatPayoffs(reputationScores)).append("|")
                .append(String.format("%.6f", coalitionValue)).append("|")
                .append(String.format("%.6f", synergyRatio)).append("|")
                .append(isQuarantined).append("|")
                .append(String.format("%.4f", latencyMs)).append("|")
                .append(timestamp);
        return sb.toString();
    }

    private static String formatPayoffs(Map<String, Double> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        TreeMap<String, Double> sorted = new TreeMap<>(map);
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Double> entry : sorted.entrySet()) {
            sb.append(entry.getKey()).append("=").append(String.format("%.6f", entry.getValue())).append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append("}");
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
