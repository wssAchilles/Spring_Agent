package tech.qiantong.qknow.ai.embodied.formal.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 不可变形式化验证与策略综合密码学存证凭单 (Java 21 Record)
 * <p>
 * 封装凭单唯一 ID、会话 ID、工位 ID、LTL 规范哈希、DFA 路径序列哈希、
 * 瞬时平滑 STL 鲁棒度、全流程最小安全裕度、死锁自愈标记、单步模型检测耗时微秒数、
 * 控制总线软着陆降级状态、时间戳与 SHA-256 密码学防篡改签名。
 */
public record FormalVerificationReceipt(
        String receiptId,
        String sessionId,
        String stationId,
        String specHash,
        String dfaPathHash,
        double instantaneousStlRobustness,
        double minRobustnessMargin,
        boolean deadlockRecovered,
        long stepLatencyUs,
        boolean degradedSafeStandstill,
        long timestampMs,
        String sha256Signature
) {
    public FormalVerificationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(stationId, "stationId 不能为空");
        Objects.requireNonNull(specHash, "specHash 不能为空");
        Objects.requireNonNull(dfaPathHash, "dfaPathHash 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 工厂签名方法：自动计算并签署 SHA-256 防篡改签名
     */
    public static FormalVerificationReceipt sign(
            String receiptId,
            String sessionId,
            String stationId,
            String specHash,
            String dfaPathHash,
            double instantaneousStlRobustness,
            double minRobustnessMargin,
            boolean deadlockRecovered,
            long stepLatencyUs,
            boolean degradedSafeStandstill,
            long timestampMs
    ) {
        String raw = String.format(Locale.US, "%s|%s|%s|%s|%s|%.6f|%.6f|%b|%d|%b|%d",
                receiptId, sessionId, stationId, specHash, dfaPathHash,
                instantaneousStlRobustness, minRobustnessMargin, deadlockRecovered,
                stepLatencyUs, degradedSafeStandstill, timestampMs);
        String signature = computeSha256(raw);
        return new FormalVerificationReceipt(
                receiptId, sessionId, stationId, specHash, dfaPathHash,
                instantaneousStlRobustness, minRobustnessMargin, deadlockRecovered,
                stepLatencyUs, degradedSafeStandstill, timestampMs, signature
        );
    }

    /**
     * 密码学验真：重新计算并比对 SHA-256 签名一致性
     */
    public boolean verifySignature() {
        String raw = String.format(Locale.US, "%s|%s|%s|%s|%s|%.6f|%.6f|%b|%d|%b|%d",
                receiptId, sessionId, stationId, specHash, dfaPathHash,
                instantaneousStlRobustness, minRobustnessMargin, deadlockRecovered,
                stepLatencyUs, degradedSafeStandstill, timestampMs);
        String expected = computeSha256(raw);
        return expected.equalsIgnoreCase(this.sha256Signature);
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
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
