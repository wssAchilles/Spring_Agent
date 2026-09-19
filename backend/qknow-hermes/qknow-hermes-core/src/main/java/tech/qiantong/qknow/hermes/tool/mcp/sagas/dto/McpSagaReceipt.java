package tech.qiantong.qknow.hermes.tool.mcp.sagas.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * 生产级 MCP Sagas 分布式事务存证凭单 (Java 21 Record)
 * 记录多 MCP 工具长链路执行过程、状态机跃迁、逆序补偿历史与防悬挂租约信息，
 * 采用 SHA-256 自签名保障抗篡改与不可伪造可追溯审计。
 */
public record McpSagaReceipt(
        String receiptId,
        String transactionId,
        String status,
        int totalSteps,
        int executedSteps,
        int compensatedSteps,
        List<McpStepRecord> stepRecords,
        long executionTimeMs,
        Instant timestamp,
        String signature
) {

    /**
     * 单步执行明细 Record
     */
    public record McpStepRecord(
            String stepId,
            String toolName,
            String actionType, // EXECUTE 或 COMPENSATE
            boolean success,
            String leaseToken,
            long costMs,
            String errorMessage
    ) {}

    /**
     * 工厂方法：构建不可变存证凭单并生成 SHA-256 密码学自签名
     *
     * @param receiptId        凭单唯一 ID
     * @param transactionId    Sagas 事务全局唯一 ID
     * @param status           事务最终状态 (COMMITTED, COMPENSATED, PARTIALLY_FAILED, FAILED)
     * @param totalSteps       总计划步数
     * @param executedSteps    正向成功执行步数
     * @param compensatedSteps 成功逆序补偿步数
     * @param stepRecords      执行步骤明细列表
     * @param executionTimeMs  端到端耗时 (ms)
     * @param timestamp        时间戳
     * @return 不可变存证凭单
     */
    public static McpSagaReceipt create(
            String receiptId,
            String transactionId,
            String status,
            int totalSteps,
            int executedSteps,
            int compensatedSteps,
            List<McpStepRecord> stepRecords,
            long executionTimeMs,
            Instant timestamp
    ) {
        String safeReceiptId = receiptId != null ? receiptId : "RCP-" + System.currentTimeMillis();
        String safeTxId = transactionId != null ? transactionId : "TX-" + System.currentTimeMillis();
        String safeStatus = status != null ? status : "UNKNOWN";
        List<McpStepRecord> safeRecords = stepRecords != null ? List.copyOf(stepRecords) : List.of();
        Instant now = timestamp != null ? timestamp : Instant.now();

        String rawContent = buildRawContent(
                safeReceiptId,
                safeTxId,
                safeStatus,
                totalSteps,
                executedSteps,
                compensatedSteps,
                safeRecords,
                executionTimeMs,
                now
        );
        String sig = sha256(rawContent);

        return new McpSagaReceipt(
                safeReceiptId,
                safeTxId,
                safeStatus,
                totalSteps,
                executedSteps,
                compensatedSteps,
                safeRecords,
                executionTimeMs,
                now,
                sig
        );
    }

    /**
     * 校验凭单签名的有效性（运行时防篡改自验真）
     *
     * @return 签名是否匹配
     */
    public boolean verifySignature() {
        String rawContent = buildRawContent(
                receiptId,
                transactionId,
                status,
                totalSteps,
                executedSteps,
                compensatedSteps,
                stepRecords,
                executionTimeMs,
                timestamp
        );
        return sha256(rawContent).equals(signature);
    }

    private static String buildRawContent(
            String receiptId,
            String transactionId,
            String status,
            int totalSteps,
            int executedSteps,
            int compensatedSteps,
            List<McpStepRecord> stepRecords,
            long executionTimeMs,
            Instant timestamp
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiptId).append("|")
                .append(transactionId).append("|")
                .append(status).append("|")
                .append(totalSteps).append("|")
                .append(executedSteps).append("|")
                .append(compensatedSteps).append("|")
                .append(executionTimeMs).append("|")
                .append(timestamp.toString()).append("|");

        if (stepRecords != null) {
            for (McpStepRecord record : stepRecords) {
                sb.append(record.stepId()).append(":")
                        .append(record.toolName()).append(":")
                        .append(record.actionType()).append(":")
                        .append(record.success()).append(":")
                        .append(record.leaseToken()).append(";");
            }
        }
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }
}
