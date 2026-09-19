package tech.qiantong.qknow.hermes.trace.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 全链路不可变追踪存证凭单 (Trace Execution Receipt)
 * <p>
 * 纯 Java 21 Record 格式，固化链路调用总览、关键路径耗时、Token 消耗与异常计数。
 * 内置基于 SHA-256 的密码学自签名与验真方法，满足等保三级不可抵赖证据链要求。
 * </p>
 *
 * @param receiptId              存证凭单唯一 ID (如 "RCPT-TRACE-xxxx")
 * @param traceId                追踪链路唯一 ID
 * @param workflowId             所属工作流/智能体任务 ID
 * @param totalSpans             链路总 Span 节点数
 * @param rootDurationUs         根执行总耗时 (微秒)
 * @param criticalPathDurationUs 关键路径累计耗时 (微秒)
 * @param totalTokens            全链路累计消耗 Token 总数
 * @param errorCount             全链路异常失败节点数
 * @param timestampMs            签发物理时间戳 (毫秒)
 * @param signature              SHA-256 密码学防篡改签名
 * @author Achilles
 * @version 1.0
 */
public record TraceExecutionReceipt(
        String receiptId,
        String traceId,
        String workflowId,
        int totalSpans,
        long rootDurationUs,
        long criticalPathDurationUs,
        int totalTokens,
        int errorCount,
        long timestampMs,
        String signature
) {
    public TraceExecutionReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(workflowId, "workflowId 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    /**
     * 计算凭单内容的 SHA-256 签名
     */
    public static String computeSignature(
            String receiptId,
            String traceId,
            String workflowId,
            int totalSpans,
            long rootDurationUs,
            long criticalPathDurationUs,
            int totalTokens,
            int errorCount,
            long timestampMs
    ) {
        String payload = String.format("%s|%s|%s|%d|%d|%d|%d|%d|%d",
                receiptId, traceId, workflowId, totalSpans,
                rootDurationUs, criticalPathDurationUs, totalTokens,
                errorCount, timestampMs);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 工厂方法：自动计算签名构建存证凭单
     */
    public static TraceExecutionReceipt create(
            String receiptId,
            String traceId,
            String workflowId,
            int totalSpans,
            long rootDurationUs,
            long criticalPathDurationUs,
            int totalTokens,
            int errorCount,
            long timestampMs
    ) {
        String sig = computeSignature(receiptId, traceId, workflowId, totalSpans,
                rootDurationUs, criticalPathDurationUs, totalTokens, errorCount, timestampMs);
        return new TraceExecutionReceipt(receiptId, traceId, workflowId, totalSpans,
                rootDurationUs, criticalPathDurationUs, totalTokens, errorCount, timestampMs, sig);
    }

    /**
     * 校验自身密码学签名是否合法
     */
    public boolean verifySignature() {
        String expectedSig = computeSignature(receiptId, traceId, workflowId, totalSpans,
                rootDurationUs, criticalPathDurationUs, totalTokens, errorCount, timestampMs);
        return Objects.equals(this.signature, expectedSig);
    }
}
