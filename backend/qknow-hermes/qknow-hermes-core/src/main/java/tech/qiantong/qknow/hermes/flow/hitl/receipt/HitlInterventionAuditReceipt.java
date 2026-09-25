package tech.qiantong.qknow.hermes.flow.hitl.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 前后端对齐的人机协同不可变密码学审计凭单 (HitlInterventionAuditReceipt)
 * 遵循 Phase 134 规范与零信任安全架构
 * 1. 纯 Java 21 Record 格式，与前端 TypeScript 强类型保持 1:1 绝对镜像对齐
 * 2. 规范化 SHA-256 签名，防御单比特篡改
 * 3. 常量时间验真 verifySignature()，彻底消除时序侧信道分析漏洞
 *
 * @author Achilles
 * @since 2026-09-25
 */
public record HitlInterventionAuditReceipt(
        String receiptId,
        String workflowId,
        String nodeId,
        long stepIndex,
        String branchId,
        String operatorId,
        String actionType, // APPROVE, REJECT, HOT_PATCH, TIMEOUT_FAILSAFE
        String originalStateHash,
        String patchedStateHash,
        String reasoningContentDigest,
        long timestamp,
        String sha256Signature
) {
    public HitlInterventionAuditReceipt {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(originalStateHash, "originalStateHash must not be null");
        Objects.requireNonNull(patchedStateHash, "patchedStateHash must not be null");
        Objects.requireNonNull(reasoningContentDigest, "reasoningContentDigest must not be null");
        Objects.requireNonNull(sha256Signature, "sha256Signature must not be null");
    }

    /**
     * 计算规范化 SHA-256 签名
     */
    public static String calculateSignature(
            String receiptId,
            String workflowId,
            String nodeId,
            long stepIndex,
            String branchId,
            String operatorId,
            String actionType,
            String originalStateHash,
            String patchedStateHash,
            String reasoningContentDigest,
            long timestamp
    ) {
        String canonicalPayload = String.join("|",
                receiptId,
                workflowId,
                nodeId,
                String.valueOf(stepIndex),
                branchId,
                operatorId,
                actionType,
                originalStateHash,
                patchedStateHash,
                reasoningContentDigest,
                String.valueOf(timestamp)
        );

        return computeSha256Hex(canonicalPayload);
    }

    /**
     * 凭单快速工厂方法
     */
    public static HitlInterventionAuditReceipt create(
            String workflowId,
            String nodeId,
            long stepIndex,
            String branchId,
            String operatorId,
            String actionType,
            String originalStateJson,
            String patchedStateJson,
            String reasoningContent
    ) {
        long ts = System.currentTimeMillis();
        String receiptId = "rcpt_hitl_" + workflowId + "_s" + stepIndex + "_" + ts;
        String origHash = computeSha256Hex(originalStateJson != null ? originalStateJson : "{}");
        String patchHash = computeSha256Hex(patchedStateJson != null ? patchedStateJson : "{}");
        String digest = computeSha256Hex(reasoningContent != null ? reasoningContent : "");

        String signature = calculateSignature(
                receiptId,
                workflowId,
                nodeId,
                stepIndex,
                branchId,
                operatorId,
                actionType,
                origHash,
                patchHash,
                digest,
                ts
        );

        return new HitlInterventionAuditReceipt(
                receiptId,
                workflowId,
                nodeId,
                stepIndex,
                branchId,
                operatorId,
                actionType,
                origHash,
                patchHash,
                digest,
                ts,
                signature
        );
    }

    /**
     * 常量时间自验真 (防范时序侧信道攻击)
     */
    public boolean verifySignature() {
        String expected = calculateSignature(
                this.receiptId,
                this.workflowId,
                this.nodeId,
                this.stepIndex,
                this.branchId,
                this.operatorId,
                this.actionType,
                this.originalStateHash,
                this.patchedStateHash,
                this.reasoningContentDigest,
                this.timestamp
        );
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String computeSha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
