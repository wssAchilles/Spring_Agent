package tech.qiantong.qknow.hermes.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * ColBERT MaxSim 密集打分不可变加密防篡改凭单
 * 遵循 Java 21 Record 规范，提供常量时间 SHA-256 签名校验
 */
public record ColbertScoreReceipt(
        int queryLength,
        int numDocs,
        int dimension,
        String executionMode,
        long executionDurationNanos,
        List<Float> scores,
        String signature
) {
    public ColbertScoreReceipt {
        Objects.requireNonNull(executionMode, "executionMode 不能为空");
        Objects.requireNonNull(scores, "scores 不能为空");
        scores = List.copyOf(scores);
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    /**
     * 计算并构造加密防篡改凭单
     */
    public static ColbertScoreReceipt create(
            int queryLength,
            int numDocs,
            int dimension,
            String executionMode,
            long executionDurationNanos,
            float[] scoresArray
    ) {
        Objects.requireNonNull(scoresArray, "scoresArray 不能为空");
        Float[] boxed = new Float[scoresArray.length];
        for (int i = 0; i < scoresArray.length; i++) {
            boxed[i] = scoresArray[i];
        }
        List<Float> scoreList = List.of(boxed);

        String rawPayload = buildScorePayload(
                queryLength, numDocs, dimension, executionMode, executionDurationNanos, scoreList);
        String signature = generateSha256(rawPayload);

        return new ColbertScoreReceipt(
                queryLength,
                numDocs,
                dimension,
                executionMode,
                executionDurationNanos,
                scoreList,
                signature
        );
    }

    /**
     * 使用 MessageDigest.isEqual 进行常量时间签名验签，防范时序侧信道攻击并校验全量分数载荷
     */
    public boolean verifySignature() {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String rawPayload = buildScorePayload(
                queryLength, numDocs, dimension, executionMode, executionDurationNanos, scores);
        String expectedSignature = generateSha256(rawPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 构造包含全量分数 IEEE 754 比特位的规范化签名载荷，防止篡改分数值绕过验签
     */
    private static String buildScorePayload(
            int queryLength,
            int numDocs,
            int dimension,
            String executionMode,
            long executionDurationNanos,
            List<Float> scoreList
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(queryLength).append(':')
                .append(numDocs).append(':')
                .append(dimension).append(':')
                .append(executionMode).append(':')
                .append(executionDurationNanos).append(':')
                .append(scoreList.size());
        for (Float score : scoreList) {
            sb.append(':').append(Float.floatToIntBits(score != null ? score : 0.0f));
        }
        return sb.toString();
    }

    public float[] getScoresArray() {
        float[] arr = new float[scores.size()];
        for (int i = 0; i < scores.size(); i++) {
            arr[i] = scores.get(i);
        }
        return arr;
    }

    private static String generateSha256(String input) {
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
            throw new IllegalStateException("JVM 环境缺少 SHA-256 算法实现", e);
        }
    }
}
