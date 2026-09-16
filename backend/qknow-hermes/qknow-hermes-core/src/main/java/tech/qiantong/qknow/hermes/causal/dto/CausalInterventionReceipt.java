package tech.qiantong.qknow.hermes.causal.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 不可变因果推演与自主干预密码学存证凭单
 *
 * @param receiptId        凭单全局唯一标识
 * @param sessionId        业务会话标识
 * @param causalGraphHash  因果拓扑图与模型状态哈希
 * @param totalBranches    沙盘推演总分支数
 * @param finalAction      最终下发执行动作
 * @param intervened       是否发生了自主干预
 * @param barrierMargin    控制屏障裕度
 * @param elapsedNanos     全链路总耗时 (纳秒)
 * @param timestamp        生成时间戳 (毫秒)
 * @param signature        基于 SHA-256 的密码学防篡改自签名
 */
public record CausalInterventionReceipt(
        String receiptId,
        String sessionId,
        String causalGraphHash,
        int totalBranches,
        String finalAction,
        boolean intervened,
        double barrierMargin,
        long elapsedNanos,
        long timestamp,
        String signature
) {
    public CausalInterventionReceipt {
        if (receiptId == null || receiptId.isBlank()) {
            throw new IllegalArgumentException("receiptId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("signature 不能为空");
        }
    }

    /**
     * 校验凭单数字签名与数据完整性
     */
    public boolean verifyIntegrity() {
        try {
            String expected = generateSignature(
                    receiptId, sessionId, causalGraphHash, totalBranches, finalAction, intervened, barrierMargin, elapsedNanos, timestamp
            );
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateSignature(
            String receiptId,
            String sessionId,
            String causalGraphHash,
            int totalBranches,
            String finalAction,
            boolean intervened,
            double barrierMargin,
            long elapsedNanos,
            long timestamp
    ) {
        try {
            String payload = receiptId + "|" + sessionId + "|" + causalGraphHash + "|" + totalBranches + "|" + finalAction + "|"
                    + intervened + "|" + String.format(Locale.US, "%.4f", barrierMargin) + "|" + elapsedNanos + "|" + timestamp;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 签名生成失败", e);
        }
    }
}
