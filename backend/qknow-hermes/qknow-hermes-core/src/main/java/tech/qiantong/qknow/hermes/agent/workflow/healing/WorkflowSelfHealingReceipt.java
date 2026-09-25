package tech.qiantong.qknow.hermes.agent.workflow.healing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 工作流自愈与断点恢复密码学存证凭单 (Phase 138 存证防线)
 * <p>
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间防篡改验真。
 * 记录死锁环路节点、自愈类型、单调防护令牌 (Fencing Token)、断点恢复起始步、执行延迟与时间戳。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record WorkflowSelfHealingReceipt(
        String receiptId,
        String workflowId,
        String actionType,
        long fencingToken,
        List<String> deadlockCycleNodes,
        int resumedStepIndex,
        double latencyMs,
        String diagnosticSummary,
        long timestamp,
        String sha256Signature
) {
    public WorkflowSelfHealingReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(workflowId, "workflowId 不能为空");
        Objects.requireNonNull(actionType, "actionType 不能为空");
        Objects.requireNonNull(deadlockCycleNodes, "deadlockCycleNodes 不能为空");
        Objects.requireNonNull(diagnosticSummary, "diagnosticSummary 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
        deadlockCycleNodes = List.copyOf(deadlockCycleNodes);
    }

    /**
     * 静态工厂方法，自动计算 SHA-256 密码学防篡改签名
     */
    public static WorkflowSelfHealingReceipt create(
            String workflowId,
            String actionType,
            long fencingToken,
            List<String> deadlockCycleNodes,
            int resumedStepIndex,
            double latencyMs,
            String diagnosticSummary
    ) {
        String receiptId = "RCP-SELF-HEAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        long timestamp = System.currentTimeMillis();
        String safeWorkflowId = workflowId != null ? workflowId : "EMPTY_WORKFLOW";
        String safeAction = actionType != null ? actionType : "UNKNOWN_ACTION";
        List<String> safeNodes = deadlockCycleNodes != null ? List.copyOf(deadlockCycleNodes) : List.of();
        String safeSummary = diagnosticSummary != null ? diagnosticSummary : "";

        String canonicalPayload = buildCanonicalPayload(
                receiptId, safeWorkflowId, safeAction, fencingToken, safeNodes,
                resumedStepIndex, latencyMs, safeSummary, timestamp
        );
        String signature = computeSha256(canonicalPayload);

        return new WorkflowSelfHealingReceipt(
                receiptId,
                safeWorkflowId,
                safeAction,
                fencingToken,
                safeNodes,
                resumedStepIndex,
                latencyMs,
                safeSummary,
                timestamp,
                signature
        );
    }

    /**
     * 验证凭单 SHA-256 签名，采用常量时间比较杜绝时序侧信道攻击
     */
    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(
                receiptId, workflowId, actionType, fencingToken, deadlockCycleNodes,
                resumedStepIndex, latencyMs, diagnosticSummary, timestamp
        );
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                sha256Signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String workflowId,
            String actionType,
            long fencingToken,
            List<String> nodes,
            int stepIndex,
            double latencyMs,
            String summary,
            long timestamp
    ) {
        return receiptId + "|" +
                workflowId + "|" +
                actionType + "|" +
                fencingToken + "|" +
                String.join(",", nodes) + "|" +
                stepIndex + "|" +
                String.format(Locale.ROOT, "%.4f", latencyMs) + "|" +
                computeSha256(summary) + "|" +
                timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法在标准 JVM 中不可用", e);
        }
    }
}
