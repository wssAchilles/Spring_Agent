package tech.qiantong.qknow.hermes.cognitive.treesearch;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 有界因果反思树搜索（BCBT）不可变审计凭单（Java 21 Record）。
 * <p>
 * 记录 Zone-0 前缀冻结不变性、持久化分叉扩展统计、三级冷备软隔离分支数、
 * Error-Book 错题本写入数与最优因果路径轨迹，全部验签强制采用 {@link MessageDigest#isEqual} 防侧信道泄漏。
 * </p>
 */
public record CausalTreeSearchReceipt(
        String receiptId,
        String executionId,
        String zone0PrefixSha256,
        boolean zone0PrefixPreserved,
        int totalExpandedBranches,
        int prunedAndQuarantinedBranches,
        int maxDepthReached,
        boolean epistemicEarlyStopped,
        String winningBranchId,
        double winningValue,
        int errorBookEntriesWritten,
        List<String> optimalTrajectoryNodeIds,
        long elapsedMicros,
        long timestampEpochMs,
        byte[] hmacSignature
) {
    public CausalTreeSearchReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(zone0PrefixSha256, "zone0PrefixSha256 不能为空");
        Objects.requireNonNull(winningBranchId, "winningBranchId 不能为空");
        Objects.requireNonNull(optimalTrajectoryNodeIds, "optimalTrajectoryNodeIds 不能为空");
        Objects.requireNonNull(hmacSignature, "hmacSignature 不能为空");

        optimalTrajectoryNodeIds = List.copyOf(optimalTrajectoryNodeIds);
        hmacSignature = Arrays.copyOf(hmacSignature, hmacSignature.length);
    }

    @Override
    public byte[] hmacSignature() {
        return Arrays.copyOf(hmacSignature, hmacSignature.length);
    }

    /**
     * 构造带 HMAC-SHA256 完整存证签名的搜索凭单。
     */
    public static CausalTreeSearchReceipt createSigned(
            String receiptId,
            String executionId,
            String zone0PrefixSha256,
            boolean zone0PrefixPreserved,
            int totalExpandedBranches,
            int prunedAndQuarantinedBranches,
            int maxDepthReached,
            boolean epistemicEarlyStopped,
            String winningBranchId,
            double winningValue,
            int errorBookEntriesWritten,
            List<String> optimalTrajectoryNodeIds,
            long elapsedMicros,
            long timestampEpochMs,
            String secretKey
    ) {
        List<String> safeTrajectory = optimalTrajectoryNodeIds == null ? List.of() : List.copyOf(optimalTrajectoryNodeIds);
        byte[] mac = computeMac(
                receiptId,
                executionId,
                zone0PrefixSha256,
                zone0PrefixPreserved,
                totalExpandedBranches,
                prunedAndQuarantinedBranches,
                maxDepthReached,
                epistemicEarlyStopped,
                winningBranchId,
                winningValue,
                errorBookEntriesWritten,
                safeTrajectory,
                elapsedMicros,
                timestampEpochMs,
                secretKey
        );
        return new CausalTreeSearchReceipt(
                receiptId,
                executionId,
                zone0PrefixSha256,
                zone0PrefixPreserved,
                totalExpandedBranches,
                prunedAndQuarantinedBranches,
                maxDepthReached,
                epistemicEarlyStopped,
                winningBranchId,
                winningValue,
                errorBookEntriesWritten,
                safeTrajectory,
                elapsedMicros,
                timestampEpochMs,
                mac
        );
    }

    /**
     * 常量时间防时序侧信道 HMAC 验签。
     */
    public boolean verifyMac(String secretKey) {
        byte[] expected = computeMac(
                receiptId,
                executionId,
                zone0PrefixSha256,
                zone0PrefixPreserved,
                totalExpandedBranches,
                prunedAndQuarantinedBranches,
                maxDepthReached,
                epistemicEarlyStopped,
                winningBranchId,
                winningValue,
                errorBookEntriesWritten,
                optimalTrajectoryNodeIds,
                elapsedMicros,
                timestampEpochMs,
                secretKey
        );
        return MessageDigest.isEqual(expected, hmacSignature);
    }

    private static byte[] computeMac(
            String receiptId,
            String executionId,
            String zone0PrefixSha256,
            boolean zone0PrefixPreserved,
            int totalExpandedBranches,
            int prunedAndQuarantinedBranches,
            int maxDepthReached,
            boolean epistemicEarlyStopped,
            String winningBranchId,
            double winningValue,
            int errorBookEntriesWritten,
            List<String> optimalTrajectoryNodeIds,
            long elapsedMicros,
            long timestampEpochMs,
            String secretKey
    ) {
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-bcbt-tree-search-secret" : secretKey;
        String payload = String.format(
                Locale.ROOT,
                "%s|%s|%s|%b|%d|%d|%d|%b|%s|%.6f|%d|%s|%d|%d",
                receiptId,
                executionId,
                zone0PrefixSha256,
                zone0PrefixPreserved,
                totalExpandedBranches,
                prunedAndQuarantinedBranches,
                maxDepthReached,
                epistemicEarlyStopped,
                winningBranchId,
                winningValue,
                errorBookEntriesWritten,
                String.join(",", optimalTrajectoryNodeIds),
                elapsedMicros,
                timestampEpochMs
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(safeKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("计算 CausalTreeSearchReceipt HMAC 失败", e);
        }
    }
}
