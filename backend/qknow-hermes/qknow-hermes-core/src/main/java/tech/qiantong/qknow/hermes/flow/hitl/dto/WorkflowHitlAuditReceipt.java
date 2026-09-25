package tech.qiantong.qknow.hermes.flow.hitl.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 人机协同 (HITL) 动态干预与时间旅行审计存证凭单
 * <p>
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间验真，
 * 记录工单 ID、工作流与分支 ID、节点 ID、裁决动作、原始入参与补丁入参、审批人信息与租约执行延迟。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record WorkflowHitlAuditReceipt(
        String receiptId,
        String workflowId,
        String branchId,
        String nodeId,
        DecisionAction decisionAction,
        Map<String, Object> originalInputs,
        Map<String, Object> patchedInputs,
        String approverId,
        long leaseDurationMs,
        long latencyMicros,
        long timestamp,
        String sha256Signature
) {
    public enum DecisionAction {
        APPROVE,
        REJECT,
        PATCH_AND_APPROVE,
        TIMEOUT_FAILSAFE
    }

    public WorkflowHitlAuditReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(workflowId, "workflowId 不能为空");
        Objects.requireNonNull(branchId, "branchId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(decisionAction, "decisionAction 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 静态工厂方法，自动计算 SHA-256 密码学自签名
     */
    public static WorkflowHitlAuditReceipt create(
            String workflowId,
            String branchId,
            String nodeId,
            DecisionAction decisionAction,
            Map<String, Object> originalInputs,
            Map<String, Object> patchedInputs,
            String approverId,
            long leaseDurationMs,
            long latencyMicros
    ) {
        String receiptId = "HITL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = System.currentTimeMillis();
        String safeWf = workflowId != null ? workflowId : "DEFAULT_WF";
        String safeBranch = branchId != null ? branchId : "main";
        String safeNode = nodeId != null ? nodeId : "NODE_UNKNOWN";
        String safeApprover = approverId != null ? approverId : "SYSTEM_WATCHDOG";

        Map<String, Object> safeOrig = originalInputs != null ? Map.copyOf(originalInputs) : Map.of();
        Map<String, Object> safePatched = patchedInputs != null ? Map.copyOf(patchedInputs) : Map.of();

        String canonicalPayload = buildCanonicalPayload(
                receiptId, safeWf, safeBranch, safeNode, decisionAction,
                safeOrig, safePatched, safeApprover, leaseDurationMs, latencyMicros, timestamp
        );
        String signature = computeSha256(canonicalPayload);

        return new WorkflowHitlAuditReceipt(
                receiptId,
                safeWf,
                safeBranch,
                safeNode,
                decisionAction,
                safeOrig,
                safePatched,
                safeApprover,
                leaseDurationMs,
                latencyMicros,
                timestamp,
                signature
        );
    }

    /**
     * 验证凭单 SHA-256 自签名，使用常量时间比较杜绝时序攻击
     */
    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(
                receiptId, workflowId, branchId, nodeId, decisionAction,
                originalInputs, patchedInputs, approverId, leaseDurationMs, latencyMicros, timestamp
        );
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                sha256Signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String wf,
            String branch,
            String node,
            DecisionAction action,
            Map<String, Object> orig,
            Map<String, Object> patched,
            String approver,
            long leaseMs,
            long latency,
            long ts
    ) {
        return receiptId + "|" + wf + "|" + branch + "|" + node + "|" + action.name() + "|"
                + canonicalizeMap(orig) + "|" + canonicalizeMap(patched) + "|"
                + approver + "|" + leaseMs + "|" + latency + "|" + ts;
    }

    private static String canonicalizeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            sb.append(k).append("=").append(String.valueOf(map.get(k)));
            if (i < keys.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("JVM 环境缺少 SHA-256 算法实现", e);
        }
    }
}
