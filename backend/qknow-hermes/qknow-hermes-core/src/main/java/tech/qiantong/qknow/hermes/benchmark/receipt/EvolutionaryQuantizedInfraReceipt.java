package tech.qiantong.qknow.hermes.benchmark.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Phase 151 多智能体演化收敛与零拷贝量化基础设施不可变审计凭单 (Evolutionary Quantized Infra Receipt)
 * <p>
 * 遵循企业级最高工程法典（AGENTS.md）与第七章工程铁律：
 * 1. 纯 Java 21 Record 强不可变类型；
 * 2. 存证李雅普诺夫复制动态演化收敛轮数（<= 6）、ESS 纳什均衡策略分布、千问 1536 维超球面积量化余弦保真度（>= 98.2%）、
 *    ScopedValue 栈帧零拷贝深度与 DeepSeek 64-token 上下文对齐哈希；
 * 3. 内嵌 SHA-256 密码学防篡改签名，使用 {@link MessageDigest#isEqual(byte[], byte[])} 进行常量时间比对，
 *    彻底杜绝时序侧信道（Timing Side-Channel）攻击风险。
 * </p>
 *
 * @author Achilles
 * @since Phase 151
 */
public record EvolutionaryQuantizedInfraReceipt(
        String receiptId,
        String tenantId,
        String sessionId,
        int convergenceRounds,
        double initialLyapunovEnergy,
        double finalLyapunovEnergy,
        double lyapunovEnergyDelta,
        String essStrategyDigest,
        double cosineFidelity,
        double quantizationCompressionRatio,
        double memoryFootprintReductionPercent,
        int scopedStackDepth,
        boolean zeroCopyInheritanceActive,
        boolean deepseekCacheAligned,
        String deepseekPrefixHash,
        long totalLatencyUs,
        long timestamp,
        String sha256Signature
) {

    /**
     * 构造并生成带有密码学 SHA-256 防篡改签名的不可变凭单
     */
    public static EvolutionaryQuantizedInfraReceipt create(
            String receiptId,
            String tenantId,
            String sessionId,
            int convergenceRounds,
            double initialLyapunovEnergy,
            double finalLyapunovEnergy,
            String essStrategyDigest,
            double cosineFidelity,
            double quantizationCompressionRatio,
            double memoryFootprintReductionPercent,
            int scopedStackDepth,
            boolean zeroCopyInheritanceActive,
            boolean deepseekCacheAligned,
            String deepseekPrefixHash,
            long totalLatencyUs
    ) {
        long now = System.currentTimeMillis();
        double energyDelta = initialLyapunovEnergy - finalLyapunovEnergy;
        String payload = String.format("%s|%s|%s|%d|%.6f|%.6f|%.6f|%s|%.4f|%.4f|%.4f|%d|%b|%b|%s|%d|%d",
                receiptId, tenantId, sessionId,
                convergenceRounds, initialLyapunovEnergy, finalLyapunovEnergy, energyDelta,
                essStrategyDigest, cosineFidelity, quantizationCompressionRatio,
                memoryFootprintReductionPercent, scopedStackDepth, zeroCopyInheritanceActive,
                deepseekCacheAligned, deepseekPrefixHash, totalLatencyUs, now);
        String signature = computeSha256(payload);

        return new EvolutionaryQuantizedInfraReceipt(
                receiptId, tenantId, sessionId,
                convergenceRounds, initialLyapunovEnergy, finalLyapunovEnergy, energyDelta,
                essStrategyDigest, cosineFidelity, quantizationCompressionRatio,
                memoryFootprintReductionPercent, scopedStackDepth, zeroCopyInheritanceActive,
                deepseekCacheAligned, deepseekPrefixHash, totalLatencyUs, now, signature
        );
    }

    /**
     * 执行密码学防篡改自验真 (常量时间校验，防范时序侧信道攻击)
     */
    public boolean verify() {
        String payload = String.format("%s|%s|%s|%d|%.6f|%.6f|%.6f|%s|%.4f|%.4f|%.4f|%d|%b|%b|%s|%d|%d",
                receiptId, tenantId, sessionId,
                convergenceRounds, initialLyapunovEnergy, finalLyapunovEnergy, lyapunovEnergyDelta,
                essStrategyDigest, cosineFidelity, quantizationCompressionRatio,
                memoryFootprintReductionPercent, scopedStackDepth, zeroCopyInheritanceActive,
                deepseekCacheAligned, deepseekPrefixHash, totalLatencyUs, timestamp);
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
            throw new IllegalStateException("JVM 环境缺少 SHA-256 摘要算法实现", e);
        }
    }
}
