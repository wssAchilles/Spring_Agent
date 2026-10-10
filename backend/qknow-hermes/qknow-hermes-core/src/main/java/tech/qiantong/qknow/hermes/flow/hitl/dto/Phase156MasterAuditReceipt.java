package tech.qiantong.qknow.hermes.flow.hitl.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Phase 156 全系统主控审计凭单 (纯 Java 21 Record 不可变对象)
 * 记录多智能体交接、状态图超步、PPR 拓扑实体扩散、思维链抗噪净化与 MCP DFA 脱敏核心指标，
 * 并内嵌基于 MessageDigest.isEqual 的常量时间 SHA-256 自验真与防篡改校验。
 */
public record Phase156MasterAuditReceipt(
        String receiptId,
        String taskId,
        String workflowExecutionId,
        int superstepCount,
        int handoffHopCount,
        boolean isAcyclicGuaranteed,
        int cacheAlignedTokenBlocks,
        int pprActivatedEntityCount,
        int causalPurifiedChunkCount,
        int mcpDfaMaskedFieldCount,
        long executionDurationMs,
        long timestampEpochMs,
        String digestSha256
) {
    /**
     * 静态工厂方法，自动计算不可变 SHA-256 摘要
     */
    public static Phase156MasterAuditReceipt create(
            String receiptId,
            String taskId,
            String workflowExecutionId,
            int superstepCount,
            int handoffHopCount,
            boolean isAcyclicGuaranteed,
            int cacheAlignedTokenBlocks,
            int pprActivatedEntityCount,
            int causalPurifiedChunkCount,
            int mcpDfaMaskedFieldCount,
            long executionDurationMs,
            long timestampEpochMs
    ) {
        String payload = buildPayload(
                receiptId, taskId, workflowExecutionId, superstepCount, handoffHopCount,
                isAcyclicGuaranteed, cacheAlignedTokenBlocks, pprActivatedEntityCount,
                causalPurifiedChunkCount, mcpDfaMaskedFieldCount, executionDurationMs, timestampEpochMs
        );
        String digest = computeSha256(payload);
        return new Phase156MasterAuditReceipt(
                receiptId, taskId, workflowExecutionId, superstepCount, handoffHopCount,
                isAcyclicGuaranteed, cacheAlignedTokenBlocks, pprActivatedEntityCount,
                causalPurifiedChunkCount, mcpDfaMaskedFieldCount, executionDurationMs,
                timestampEpochMs, digest
        );
    }

    /**
     * 常量时间自验真方法，防止时序侧信道攻击
     */
    public boolean verifyDigest() {
        String payload = buildPayload(
                receiptId, taskId, workflowExecutionId, superstepCount, handoffHopCount,
                isAcyclicGuaranteed, cacheAlignedTokenBlocks, pprActivatedEntityCount,
                causalPurifiedChunkCount, mcpDfaMaskedFieldCount, executionDurationMs, timestampEpochMs
        );
        String expectedDigest = computeSha256(payload);
        return MessageDigest.isEqual(
                this.digestSha256.getBytes(StandardCharsets.UTF_8),
                expectedDigest.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildPayload(
            String receiptId,
            String taskId,
            String workflowExecutionId,
            int superstepCount,
            int handoffHopCount,
            boolean isAcyclicGuaranteed,
            int cacheAlignedTokenBlocks,
            int pprActivatedEntityCount,
            int causalPurifiedChunkCount,
            int mcpDfaMaskedFieldCount,
            long executionDurationMs,
            long timestampEpochMs
    ) {
        return String.format("%s|%s|%s|%d|%d|%b|%d|%d|%d|%d|%d|%d",
                receiptId, taskId, workflowExecutionId, superstepCount, handoffHopCount,
                isAcyclicGuaranteed, cacheAlignedTokenBlocks, pprActivatedEntityCount,
                causalPurifiedChunkCount, mcpDfaMaskedFieldCount, executionDurationMs, timestampEpochMs);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
