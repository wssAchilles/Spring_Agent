package tech.qiantong.qknow.hermes.flow.hitl.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 不可变工作流交互调试存证凭单 (WorkflowDebugReceipt)
 * 基于纯 Java 21 Record 格式构建
 * 内置全字段 SHA-256 密码学自签名与反篡改自验真能力
 */
public record WorkflowDebugReceipt(
        String receiptId,
        String executionBatchId,
        String workflowId,
        int totalExecutedSteps,
        int breakpointsHitCount,
        int timeTravelStepCount,
        int hitlTicketsHandledCount,
        String operatorUserId,
        long startTimestampMicros,
        long endTimestampMicros,
        String finalStatus,
        String signature
) {

    /**
     * 创建并自动签署工作流调试存证凭单
     */
    public static WorkflowDebugReceipt createSigned(
            String receiptId,
            String executionBatchId,
            String workflowId,
            int totalExecutedSteps,
            int breakpointsHitCount,
            int timeTravelStepCount,
            int hitlTicketsHandledCount,
            String operatorUserId,
            long startTimestampMicros,
            long endTimestampMicros,
            String finalStatus
    ) {
        String signature = computeSignature(
                receiptId, executionBatchId, workflowId, totalExecutedSteps,
                breakpointsHitCount, timeTravelStepCount, hitlTicketsHandledCount,
                operatorUserId, startTimestampMicros, endTimestampMicros, finalStatus
        );
        return new WorkflowDebugReceipt(
                receiptId, executionBatchId, workflowId, totalExecutedSteps,
                breakpointsHitCount, timeTravelStepCount, hitlTicketsHandledCount,
                operatorUserId, startTimestampMicros, endTimestampMicros, finalStatus, signature
        );
    }

    /**
     * 验证凭单 SHA-256 签名一致性（防篡改自验真）
     */
    public boolean verifySignature() {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String expected = computeSignature(
                receiptId, executionBatchId, workflowId, totalExecutedSteps,
                breakpointsHitCount, timeTravelStepCount, hitlTicketsHandledCount,
                operatorUserId, startTimestampMicros, endTimestampMicros, finalStatus
        );
        return expected.equalsIgnoreCase(signature);
    }

    private static String computeSignature(
            String receiptId,
            String executionBatchId,
            String workflowId,
            int totalExecutedSteps,
            int breakpointsHitCount,
            int timeTravelStepCount,
            int hitlTicketsHandledCount,
            String operatorUserId,
            long startTimestampMicros,
            long endTimestampMicros,
            String finalStatus
    ) {
        String raw = String.format("%s:%s:%s:%d:%d:%d:%d:%s:%d:%d:%s",
                receiptId != null ? receiptId : "",
                executionBatchId != null ? executionBatchId : "",
                workflowId != null ? workflowId : "",
                totalExecutedSteps,
                breakpointsHitCount,
                timeTravelStepCount,
                hitlTicketsHandledCount,
                operatorUserId != null ? operatorUserId : "",
                startTimestampMicros,
                endTimestampMicros,
                finalStatus != null ? finalStatus : ""
        );
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
