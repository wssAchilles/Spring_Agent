package tech.qiantong.qknow.hermes.federation.dto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多租户上下文非干涉性密码学租约 Record
 */
public record TenantIsolationLease(
        String leaseId,
        String tenantId,
        String sessionId,
        String role,
        long issuedTimestamp,
        long expireTimestamp,
        String hmacToken
) {
    private static final ConcurrentHashMap<String, SecretKeySpec> KEY_SPEC_CACHE = new ConcurrentHashMap<>();

    private static SecretKeySpec getKeySpec(String secretKey) {
        return KEY_SPEC_CACHE.computeIfAbsent(secretKey, k ->
                new SecretKeySpec(k.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
        );
    }

    public boolean isValid(String secretKey) {
        if (System.currentTimeMillis() > expireTimestamp) {
            return false;
        }
        String expected = calculateHmac(leaseId, tenantId, sessionId, role, issuedTimestamp, expireTimestamp, secretKey);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), hmacToken.getBytes(StandardCharsets.UTF_8));
    }

    public static String calculateHmac(
            String leaseId, String tenantId, String sessionId,
            String role, long issuedTimestamp, long expireTimestamp, String secretKey
    ) {
        String raw = leaseId + ":" + tenantId + ":" + sessionId + ":" + role + ":" + issuedTimestamp + ":" + expireTimestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(getKeySpec(secretKey));
            byte[] hmacBytes = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}
