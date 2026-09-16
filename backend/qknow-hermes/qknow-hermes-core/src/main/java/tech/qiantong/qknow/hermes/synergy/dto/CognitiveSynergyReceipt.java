package tech.qiantong.qknow.hermes.synergy.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 不可变认知协同与涌现决策密码学存证凭单
 *
 * @param receiptId              凭单唯一标识
 * @param sessionId              会话标识
 * @param topologyHash           拓扑重组与架构快照哈希
 * @param algebraicConnectivity 代数连通度
 * @param finalPlan              最终下发执行方案
 * @param intervened             是否发生了 CBF 安全干预
 * @param barrierMargin          控制屏障裕度
 * @param elapsedNanos           全链路总耗时 (纳秒)
 * @param timestamp              生成时间戳 (毫秒)
 * @param signature              基于 SHA-256 的密码学防篡改自签名
 */
public record CognitiveSynergyReceipt(
        String receiptId,
        String sessionId,
        String topologyHash,
        double algebraicConnectivity,
        String finalPlan,
        boolean intervened,
        double barrierMargin,
        long elapsedNanos,
        long timestamp,
        String signature
) {
    public CognitiveSynergyReceipt {
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

    public boolean verifyIntegrity() {
        try {
            String expected = generateSignature(
                    receiptId, sessionId, topologyHash, algebraicConnectivity, finalPlan, intervened, barrierMargin, elapsedNanos, timestamp
            );
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateSignature(
            String receiptId,
            String sessionId,
            String topologyHash,
            double algebraicConnectivity,
            String finalPlan,
            boolean intervened,
            double barrierMargin,
            long elapsedNanos,
            long timestamp
    ) {
        try {
            String payload = receiptId + "|" + sessionId + "|" + topologyHash + "|"
                    + String.format(Locale.US, "%.4f", algebraicConnectivity) + "|" + finalPlan + "|"
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
