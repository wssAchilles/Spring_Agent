package tech.qiantong.qknow.hermes.benchmark.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Phase 150 认知基础设施与现代运行时不可变审计存证凭单 (Cognitive Infra Audit Receipt)
 * <p>
 * 遵循企业级最高工程法典（AGENTS.md）与第七章工程铁律：
 * 1. 纯 Java 21 Record 强不可变类型；
 * 2. 存证结构化并发原子短路、SIMD 硬件加速微秒耗时、超球面 VIB 压缩率、局部语义熵与 DeepSeek 确定性前缀哈希；
 * 3. 内嵌 SHA-256 递归防篡改签名，使用 {@link MessageDigest#isEqual(byte[], byte[])} 进行常量时间比对，
 *    彻底杜绝时序侧信道（Timing Side-Channel）攻击风险。
 * </p>
 *
 * @author Achilles
 * @since Phase 150
 */
public record CognitiveInfraAuditReceipt(
        String receiptId,
        String tenantId,
        String sessionId,
        boolean structuredConcurrencyActive,
        int orphanTasksPrevented,
        boolean simdAccelerationEnabled,
        long simdDotProductLatencyUs,
        double vibCompressionRatio,
        double causalCreditEntropy,
        double epistemicEntropy,
        String adaptiveRoutingPath,
        boolean deepseekCacheAligned,
        String deepseekPrefixHash,
        long totalLatencyUs,
        long timestamp,
        String sha256Signature
) {

    /**
     * 构造并生成带有密码学 SHA-256 防篡改签名的不可变凭单
     */
    public static CognitiveInfraAuditReceipt create(
            String receiptId,
            String tenantId,
            String sessionId,
            boolean structuredConcurrencyActive,
            int orphanTasksPrevented,
            boolean simdAccelerationEnabled,
            long simdDotProductLatencyUs,
            double vibCompressionRatio,
            double causalCreditEntropy,
            double epistemicEntropy,
            String adaptiveRoutingPath,
            boolean deepseekCacheAligned,
            String deepseekPrefixHash,
            long totalLatencyUs
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format("%s|%s|%s|%b|%d|%b|%d|%.4f|%.4f|%.4f|%s|%b|%s|%d|%d",
                receiptId, tenantId, sessionId,
                structuredConcurrencyActive, orphanTasksPrevented,
                simdAccelerationEnabled, simdDotProductLatencyUs,
                vibCompressionRatio, causalCreditEntropy, epistemicEntropy,
                adaptiveRoutingPath, deepseekCacheAligned, deepseekPrefixHash,
                totalLatencyUs, now);
        String signature = computeSha256(payload);

        return new CognitiveInfraAuditReceipt(
                receiptId, tenantId, sessionId,
                structuredConcurrencyActive, orphanTasksPrevented,
                simdAccelerationEnabled, simdDotProductLatencyUs,
                vibCompressionRatio, causalCreditEntropy, epistemicEntropy,
                adaptiveRoutingPath, deepseekCacheAligned, deepseekPrefixHash,
                totalLatencyUs, now, signature
        );
    }

    /**
     * 执行密码学防篡改自验真 (常量时间校验，防范时序侧信道攻击)
     */
    public boolean verify() {
        String payload = String.format("%s|%s|%s|%b|%d|%b|%d|%.4f|%.4f|%.4f|%s|%b|%s|%d|%d",
                receiptId, tenantId, sessionId,
                structuredConcurrencyActive, orphanTasksPrevented,
                simdAccelerationEnabled, simdDotProductLatencyUs,
                vibCompressionRatio, causalCreditEntropy, epistemicEntropy,
                adaptiveRoutingPath, deepseekCacheAligned, deepseekPrefixHash,
                totalLatencyUs, timestamp);
        String expectedSignature = computeSha256(payload);

        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 算法实现", e);
        }
    }
}
