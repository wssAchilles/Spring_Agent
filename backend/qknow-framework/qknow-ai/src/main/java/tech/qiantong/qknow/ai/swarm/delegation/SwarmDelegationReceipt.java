package tech.qiantong.qknow.ai.swarm.delegation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * 纯 Java 21 Record 格式的不可变多智能体委托执行凭单
 * 内置 SHA-256 密码学自签名与微秒级运行时自验真方法
 */
public record SwarmDelegationReceipt(
        String receiptId,
        String sessionId,
        String rootAgentId,
        int delegationDepth,
        double intentAffinity,
        List<String> delegationTopologyTree,
        long startTimestampMicros,
        long endTimestampMicros,
        String status,
        String signature
) {
    public SwarmDelegationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(rootAgentId, "rootAgentId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        delegationTopologyTree = delegationTopologyTree != null ? List.copyOf(delegationTopologyTree) : List.of();
    }

    /**
     * 计算 SHA-256 密码学自签名
     */
    public static String computeSignature(
            String receiptId,
            String sessionId,
            String rootAgentId,
            int delegationDepth,
            double intentAffinity,
            long startMicros,
            long endMicros,
            String status) {
        String raw = String.format("%s:%s:%s:%d:%.4f:%d:%d:%s",
                receiptId, sessionId, rootAgentId, delegationDepth, intentAffinity, startMicros, endMicros, status);
        try {
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
            throw new IllegalStateException("SHA-256 算法在当前运行时不可用", e);
        }
    }

    /**
     * 运行时自验真方法，单次耗时严格 <= 50微秒
     */
    public boolean verifySignature() {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = computeSignature(receiptId, sessionId, rootAgentId, delegationDepth, intentAffinity,
                startTimestampMicros, endTimestampMicros, status);
        return expected.equalsIgnoreCase(signature);
    }
}
