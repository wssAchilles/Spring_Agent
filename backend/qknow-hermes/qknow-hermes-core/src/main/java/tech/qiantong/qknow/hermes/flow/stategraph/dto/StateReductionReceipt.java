package tech.qiantong.qknow.hermes.flow.stategraph.dto;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 不可变超步并发状态规约凭单 Record (StateReductionReceipt)
 * 具备密码学 SHA-256 自签名与 MessageDigest.isEqual 常量时间抗时序侧信道比对能力。
 */
public record StateReductionReceipt(
        String reductionReceiptId,
        String executionId,
        int superstep,
        String beforeStateHash,
        String afterStateHash,
        int reducedChannelCount,
        int conflictCount,
        int branchDeltaCount,
        long stateVersionToken,
        String sha256Signature,
        long timestamp
) implements Serializable {

    public StateReductionReceipt {
        Objects.requireNonNull(reductionReceiptId, "reductionReceiptId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
    }

    public static StateReductionReceipt createSigned(
            String receiptId,
            String executionId,
            int superstep,
            String beforeStateHash,
            String afterStateHash,
            int reducedChannels,
            int conflicts,
            int branchDeltas,
            long versionToken,
            long timestamp) {

        String rawData = computeRawData(
                receiptId, executionId, superstep,
                beforeStateHash, afterStateHash,
                reducedChannels, conflicts, branchDeltas,
                versionToken, timestamp);

        String signature = computeSha256(rawData);
        return new StateReductionReceipt(
                receiptId, executionId, superstep,
                beforeStateHash, afterStateHash,
                reducedChannels, conflicts, branchDeltas,
                versionToken, signature, timestamp);
    }

    /**
     * 校验自签名完整性（基于 MessageDigest.isEqual 常量时间比对）
     */
    public boolean verifyIntegrity() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String rawData = computeRawData(
                reductionReceiptId, executionId, superstep,
                beforeStateHash, afterStateHash,
                reducedChannelCount, conflictCount, branchDeltaCount,
                stateVersionToken, timestamp);
        byte[] expectedBytes = computeSha256(rawData).toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    public boolean verifySignature() {
        return verifyIntegrity();
    }

    private static String computeRawData(
            String receiptId, String executionId, int superstep,
            String beforeHash, String afterHash,
            int channels, int conflicts, int deltas,
            long versionToken, long timestamp) {
        return String.format("%s|%s|%d|%s|%s|%d|%d|%d|%d|%d",
                receiptId, executionId, superstep,
                beforeHash, afterHash,
                channels, conflicts, deltas,
                versionToken, timestamp);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
