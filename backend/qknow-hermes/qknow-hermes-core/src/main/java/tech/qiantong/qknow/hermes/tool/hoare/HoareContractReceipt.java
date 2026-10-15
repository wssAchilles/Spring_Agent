package tech.qiantong.qknow.hermes.tool.hoare;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 声明式 Hoare 逻辑工具执行不可变凭单
 * 包含前置守卫 {P} 校验、后置不变量 {Q} 校验与 Saga 逆向补偿标识
 * 全程采用常量时间安全比对防御时序侧信道攻击
 */
public record HoareContractReceipt(
        String receiptId,
        String toolName,
        String executionId,
        boolean preconditionPassed,
        String preconditionViolationReason,
        boolean postconditionPassed,
        String postconditionViolationReason,
        boolean compensated,
        long timestamp,
        String signature
) {
    public HoareContractReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(executionId, "executionId cannot be null");
    }

    /**
     * 生成确定性防篡改签名
     */
    public static String calculateSignature(String receiptId, String toolName, String executionId,
                                            boolean prePassed, boolean postPassed, boolean compensated,
                                            long timestamp, String secretKey) {
        try {
            String raw = String.format("%s|%s|%s|%b|%b|%b|%d|%s",
                    receiptId, toolName, executionId, prePassed, postPassed, compensated, timestamp,
                    secretKey == null ? "DEFAULT_HOARE_SECRET" : secretKey);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * 常量时间比对验签，杜绝时序侧信道泄漏
     */
    public boolean verifySignature(String secretKey) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = calculateSignature(receiptId, toolName, executionId,
                preconditionPassed, postconditionPassed, compensated, timestamp, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
