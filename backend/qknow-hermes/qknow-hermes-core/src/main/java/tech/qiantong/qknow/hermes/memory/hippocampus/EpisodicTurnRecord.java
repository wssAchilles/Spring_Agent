package tech.qiantong.qknow.hermes.memory.hippocampus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 海马体单轮回合情境记忆不可变凭单
 * 承载用户或 Agent 的交互情境事实，并严格绑定阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
 */
public record EpisodicTurnRecord(
        String turnId,
        String sessionId,
        String role,
        String content,
        float[] sphericalVector,
        Map<String, String> keyFacts,
        long timestamp,
        String signature
) {
    public static final int VECTOR_DIMENSION = 1536;
    private static final double NORM_TOLERANCE = 1e-3;

    public EpisodicTurnRecord {
        Objects.requireNonNull(turnId, "turnId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(sphericalVector, "sphericalVector cannot be null");
        if (sphericalVector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("sphericalVector must have exactly " + VECTOR_DIMENSION + " dimensions, got: " + sphericalVector.length);
        }
        // 超球面单位球约束校验 ||v||_2 = 1.0
        double normSq = 0.0;
        for (float f : sphericalVector) {
            normSq += f * f;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > NORM_TOLERANCE) {
            throw new IllegalArgumentException("sphericalVector must be L2-normalized unit vector (||v||_2 = 1.0), got norm: " + norm);
        }
        keyFacts = keyFacts == null ? Collections.emptyMap() : Collections.unmodifiableMap(keyFacts);
    }

    public static String calculateSignature(String turnId, String sessionId, String role,
                                            String content, long timestamp, String secretKey) {
        try {
            String raw = String.format("%s|%s|%s|%s|%d|%s",
                    turnId, sessionId, role, content, timestamp,
                    secretKey == null ? "DEFAULT_EPISODIC_SECRET" : secretKey);
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

    public boolean verifySignature(String secretKey) {
        if (signature == null || signature.isBlank()) return false;
        String expected = calculateSignature(turnId, sessionId, role, content, timestamp, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
