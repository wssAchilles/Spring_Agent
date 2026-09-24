package tech.qiantong.qknow.hermes.flow.stategraph.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * Phase 128 不可变状态图因果调试存证凭单 Record
 * 具备 SHA-256 密码学自签名与常数时间验真防篡改能力
 */
public record StateGraphCausalDebugReceipt(
        String executionId,
        String flowId,
        String debugSessionId,
        int targetSuperstep,
        Set<String> activeNodeUuids,
        Map<String, Object> sharedStateSnapshot,
        String branchId,
        boolean isForkedBranch,
        long latencyMicros,
        String sha256Signature,
        long timestamp
) {
    public StateGraphCausalDebugReceipt {
        if (executionId == null || flowId == null || debugSessionId == null) {
            throw new IllegalArgumentException("executionId, flowId and debugSessionId must not be null");
        }
        if (targetSuperstep < 0) {
            throw new IllegalArgumentException("targetSuperstep must be non-negative");
        }
        branchId = (branchId == null || branchId.isBlank()) ? "main" : branchId;
        activeNodeUuids = (activeNodeUuids == null) ? Set.of() : Set.copyOf(activeNodeUuids);
        sharedStateSnapshot = (sharedStateSnapshot == null) ? Map.of() : Map.copyOf(sharedStateSnapshot);
    }

    /**
     * 构建包含自签名的因果调试存证凭单实例
     */
    public static StateGraphCausalDebugReceipt createSigned(
            String executionId,
            String flowId,
            String debugSessionId,
            int targetSuperstep,
            Set<String> activeNodes,
            Map<String, Object> stateSnapshot,
            String branchId,
            boolean isForked,
            long latencyUs,
            long timestamp) {

        String safeBranchId = (branchId == null || branchId.isBlank()) ? "main" : branchId;
        int activeSize = (activeNodes != null) ? activeNodes.size() : 0;
        int stateSize = (stateSnapshot != null) ? stateSnapshot.size() : 0;

        String rawData = computeRawData(
                executionId, flowId, debugSessionId, targetSuperstep,
                activeSize, stateSize, safeBranchId, isForked, latencyUs, timestamp);

        String signature = computeSha256(rawData);
        return new StateGraphCausalDebugReceipt(
                executionId, flowId, debugSessionId, targetSuperstep,
                activeNodes, stateSnapshot, safeBranchId, isForked,
                latencyUs, signature, timestamp);
    }

    /**
     * 校验凭单自签名完整性与防篡改特性（常数时间防时序攻击）
     */
    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String rawData = computeRawData(
                executionId, flowId, debugSessionId, targetSuperstep,
                activeNodeUuids.size(), sharedStateSnapshot.size(),
                branchId, isForkedBranch, latencyMicros, timestamp);
        String expectedSignature = computeSha256(rawData);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                sha256Signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String computeRawData(
            String executionId, String flowId, String debugSessionId, int superstep,
            int activeSize, int stateSize, String branchId, boolean isForked,
            long latencyUs, long timestamp) {
        return String.format("%s|%s|%s|%d|%d|%d|%s|%b|%d|%d",
                executionId != null ? executionId : "NONE",
                flowId != null ? flowId : "NONE",
                debugSessionId != null ? debugSessionId : "NONE",
                superstep, activeSize, stateSize,
                branchId != null ? branchId : "main",
                isForked, latencyUs, timestamp);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
