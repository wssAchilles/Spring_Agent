package tech.qiantong.qknow.ai.htn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 分层任务规划、因果元反思与 SAGA 事务一致性不可变存证凭单 (Java 21 Record, SHA-256 存证留痕)
 */
public record HierarchicalExecutionReceipt(
        String executionId,
        String rootTaskId,
        String decomposedPlanHash,
        List<String> executedActionIds,
        String transactionStatus, // COMMITTED / COMPENSATED / FAILED
        int selfHealingRounds,
        long executionDurationMs,
        String tamperProofHash,
        long timestampMs
) {

    public static HierarchicalExecutionReceipt createReceipt(
            String rootTaskId,
            String decomposedPlanHash,
            List<String> executedActionIds,
            String transactionStatus,
            int selfHealingRounds,
            long executionDurationMs
    ) {
        long now = System.currentTimeMillis();
        String executionId = "HTN-RCPT-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> actions = Collections.unmodifiableList(new ArrayList<>(executedActionIds != null ? executedActionIds : List.of()));
        String status = transactionStatus != null ? transactionStatus : "COMMITTED";
        String planHash = decomposedPlanHash != null ? decomposedPlanHash : "EMPTY_PLAN_HASH";

        String hash = computeHash(executionId, rootTaskId, planHash, actions, status, selfHealingRounds, executionDurationMs, now);

        return new HierarchicalExecutionReceipt(
                executionId, rootTaskId, planHash, actions, status, selfHealingRounds, executionDurationMs, hash, now
        );
    }

    /**
     * 验证存证收据 SHA-256 密码学防篡改完整性
     */
    public boolean verifyIntegrity() {
        String expected = computeHash(executionId, rootTaskId, decomposedPlanHash, executedActionIds, transactionStatus, selfHealingRounds, executionDurationMs, timestampMs);
        return expected.equalsIgnoreCase(tamperProofHash);
    }

    private static String computeHash(
            String execId, String taskId, String planHash,
            List<String> actions, String status, int healingRounds,
            long duration, long ts
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = execId + ":" + taskId + ":" + planHash + ":"
                    + actions.hashCode() + ":" + status + ":"
                    + healingRounds + ":" + duration + ":" + ts;
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "HTN_HASH_" + execId + "_" + ts;
        }
    }
}
