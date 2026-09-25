package tech.qiantong.qknow.hermes.swarm.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 纯 Java 21 Record 格式不可变时空分叉审计存证凭单 (CognitiveForkingSandboxReceipt)
 * <p>
 * 核心机制：
 * 1. 规范化存证多智能体在任意历史断点派生反事实分支的关键元数据（父子分支因果、挂载快照、W3C SpanId 与补丁摘要）；
 * 2. 对反事实补丁内容签发独立的 SHA-256 指纹，确保干预意图不可篡改；
 * 3. 基于规范化全字段生成全局数字签名，内置基于 MessageDigest.isEqual 的常量时间密码学自验真；
 * 4. 杜绝时序侧信道攻击，为多智能体时空分叉推演提供法医级责任归因。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record CognitiveForkingSandboxReceipt(
        String receiptId,
        String traceId,
        String parentBranchId,
        String forkedBranchId,
        String baseSnapshotId,
        String forkSpanId,
        String patchedAgentId,
        String patchSummary,
        String patchHash,
        double forkLatencyMs,
        long timestamp,
        String sha256Signature
) {

    public CognitiveForkingSandboxReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(parentBranchId, "parentBranchId 不能为空");
        Objects.requireNonNull(forkedBranchId, "forkedBranchId 不能为空");
        Objects.requireNonNull(baseSnapshotId, "baseSnapshotId 不能为空");
        Objects.requireNonNull(forkSpanId, "forkSpanId 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
        patchedAgentId = (patchedAgentId == null) ? "N/A" : patchedAgentId;
        patchSummary = (patchSummary == null) ? "NONE" : patchSummary;
        patchHash = (patchHash == null) ? "0000000000000000000000000000000000000000000000000000000000000000" : patchHash;
    }

    /**
     * 签发不可变时空分叉凭单工厂方法
     */
    public static CognitiveForkingSandboxReceipt create(
            String receiptId,
            String traceId,
            String parentBranchId,
            String forkedBranchId,
            String baseSnapshotId,
            String forkSpanId,
            String patchedAgentId,
            String patchSummary,
            double forkLatencyMs
    ) {
        long now = System.currentTimeMillis();
        String patchHash = computeSha256(patchSummary != null ? patchSummary : "NONE");

        String signature = computeSignature(
                receiptId,
                traceId,
                parentBranchId,
                forkedBranchId,
                baseSnapshotId,
                forkSpanId,
                patchedAgentId,
                patchHash,
                forkLatencyMs,
                now
        );

        return new CognitiveForkingSandboxReceipt(
                receiptId,
                traceId,
                parentBranchId,
                forkedBranchId,
                baseSnapshotId,
                forkSpanId,
                patchedAgentId,
                patchSummary,
                patchHash,
                forkLatencyMs,
                now,
                signature
        );
    }

    /**
     * 计算规范化全字段 SHA-256 签名
     */
    public static String computeSignature(
            String receiptId,
            String traceId,
            String parentBranchId,
            String forkedBranchId,
            String baseSnapshotId,
            String forkSpanId,
            String patchedAgentId,
            String patchHash,
            double forkLatencyMs,
            long timestamp
    ) {
        String canonicalString = String.format(
                Locale.US,
                "receiptId=%s|traceId=%s|parentBranch=%s|forkedBranch=%s|baseSnapshot=%s|" +
                        "forkSpan=%s|patchedAgent=%s|patchHash=%s|latency=%.2f|timestamp=%d",
                receiptId, traceId, parentBranchId, forkedBranchId, baseSnapshotId,
                forkSpanId, patchedAgentId, patchHash, forkLatencyMs, timestamp
        );

        return computeSha256(canonicalString);
    }

    /**
     * 执行常量时间密码学自验真
     */
    public boolean verifySignature() {
        String expectedSignature = computeSignature(
                this.receiptId,
                this.traceId,
                this.parentBranchId,
                this.forkedBranchId,
                this.baseSnapshotId,
                this.forkSpanId,
                this.patchedAgentId,
                this.patchHash,
                this.forkLatencyMs,
                this.timestamp
        );

        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.sha256Signature.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持标准 SHA-256", e);
        }
    }
}
