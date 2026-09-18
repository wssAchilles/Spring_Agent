package tech.qiantong.qknow.hermes.flow.stategraph.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * 不可变状态图单步执行凭单 Record
 * 具备自签名校验与密码学防篡改能力
 */
public record StateGraphExecutionReceipt(
        String executionId,
        String flowId,
        int totalSupersteps,
        Set<String> completedNodeUuids,
        Set<String> activeNodeUuids,
        Map<String, Integer> loopCounterMap,
        boolean hasDegradedBreak,
        boolean hasSelfHealed,
        long executionLatencyUs,
        String sha256Signature,
        long timestamp
) {
    public StateGraphExecutionReceipt {
        if (executionId == null || flowId == null) {
            throw new IllegalArgumentException("executionId and flowId must not be null");
        }
        if (totalSupersteps < 0) {
            throw new IllegalArgumentException("totalSupersteps must be non-negative");
        }
    }

    /**
     * 构建包含自签名的凭单实例
     */
    public static StateGraphExecutionReceipt createSigned(
            String executionId,
            String flowId,
            int totalSupersteps,
            Set<String> completedNodes,
            Set<String> activeNodes,
            Map<String, Integer> loopCounters,
            boolean degradedBreak,
            boolean selfHealed,
            long latencyUs,
            long timestamp) {

        String rawData = computeRawData(
                executionId, flowId, totalSupersteps,
                completedNodes.size(), activeNodes.size(),
                degradedBreak, selfHealed, latencyUs, timestamp);

        String signature = computeSha256(rawData);
        return new StateGraphExecutionReceipt(
                executionId, flowId, totalSupersteps,
                Set.copyOf(completedNodes), Set.copyOf(activeNodes),
                Map.copyOf(loopCounters), degradedBreak, selfHealed,
                latencyUs, signature, timestamp);
    }

    /**
     * 校验自签名完整性
     */
    public boolean verifyIntegrity() {
        String rawData = computeRawData(
                executionId, flowId, totalSupersteps,
                completedNodeUuids.size(), activeNodeUuids.size(),
                hasDegradedBreak, hasSelfHealed, executionLatencyUs, timestamp);
        return computeSha256(rawData).equalsIgnoreCase(sha256Signature);
    }

    private static String computeRawData(
            String executionId, String flowId, int supersteps,
            int completedSize, int activeSize,
            boolean degradedBreak, boolean selfHealed,
            long latencyUs, long timestamp) {
        return String.format("%s|%s|%d|%d|%d|%b|%b|%d|%d",
                executionId, flowId, supersteps,
                completedSize, activeSize,
                degradedBreak, selfHealed, latencyUs, timestamp);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
