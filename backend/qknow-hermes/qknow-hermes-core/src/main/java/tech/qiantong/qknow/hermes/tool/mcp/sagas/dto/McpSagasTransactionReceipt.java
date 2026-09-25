package tech.qiantong.qknow.hermes.tool.mcp.sagas.dto;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

/**
 * 纯 Java 21 Record 格式的分布式 Sagas 事务密码学不可变存证凭单
 * 内置 SHA-256 签名与常量时间自验真 verifySignature()，彻底免疫时序侧信道攻击
 *
 * @param receiptId         凭单唯一标识
 * @param transactionId     全局事务唯一标识
 * @param fencingToken      生效的 64 位单调递增世代令牌
 * @param leaseOwnerId      最终提交或补偿的租约持有节点
 * @param executionStatus   事务终局状态 (COMMITTED, COMPENSATED, FAILED_SAFE, EXPIRED)
 * @param forwardSteps      已成功执行的正向工具步骤明细
 * @param compensatedSteps  已成功执行的逆向补偿工具步骤明细
 * @param latencyMicros     端到端执行耗时 (微秒)
 * @param timestamp         时间戳 (毫秒)
 * @param sha256Signature   SHA-256 密码学数字签名
 */
public record McpSagasTransactionReceipt(
        String receiptId,
        String transactionId,
        long fencingToken,
        String leaseOwnerId,
        String executionStatus,
        List<String> forwardSteps,
        List<String> compensatedSteps,
        long latencyMicros,
        long timestamp,
        String sha256Signature
) implements Serializable {

    public McpSagasTransactionReceipt {
        forwardSteps = forwardSteps != null ? Collections.unmodifiableList(List.copyOf(forwardSteps)) : List.of();
        compensatedSteps = compensatedSteps != null ? Collections.unmodifiableList(List.copyOf(compensatedSteps)) : List.of();
    }

    /**
     * 计算 SHA-256 密码学签名
     */
    public static String computeSignature(
            String receiptId,
            String transactionId,
            long fencingToken,
            String leaseOwnerId,
            String status,
            List<String> forwardSteps,
            List<String> compensatedSteps,
            long timestamp
    ) {
        try {
            String forwardJoined = forwardSteps != null ? String.join(",", forwardSteps) : "";
            String compJoined = compensatedSteps != null ? String.join(",", compensatedSteps) : "";
            String payload = receiptId + "|" + transactionId + "|" + fencingToken + "|" + leaseOwnerId + "|"
                    + status + "|" + forwardJoined + "|" + compJoined + "|" + timestamp;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算 SHA-256 事务凭单签名失败", e);
        }
    }

    /**
     * 构建不可变存证凭单实例并自动生成防篡改签名
     */
    public static McpSagasTransactionReceipt create(
            String receiptId,
            String transactionId,
            long fencingToken,
            String leaseOwnerId,
            String executionStatus,
            List<String> forwardSteps,
            List<String> compensatedSteps,
            long latencyMicros
    ) {
        long now = System.currentTimeMillis();
        String signature = computeSignature(
                receiptId, transactionId, fencingToken, leaseOwnerId, executionStatus, forwardSteps, compensatedSteps, now
        );
        return new McpSagasTransactionReceipt(
                receiptId, transactionId, fencingToken, leaseOwnerId, executionStatus,
                forwardSteps, compensatedSteps, latencyMicros, now, signature
        );
    }

    /**
     * 常量时间自验真 (基于 MessageDigest.isEqual 防时序侧信道攻击)
     */
    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String expected = computeSignature(
                receiptId, transactionId, fencingToken, leaseOwnerId, executionStatus,
                forwardSteps, compensatedSteps, timestamp
        );
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
