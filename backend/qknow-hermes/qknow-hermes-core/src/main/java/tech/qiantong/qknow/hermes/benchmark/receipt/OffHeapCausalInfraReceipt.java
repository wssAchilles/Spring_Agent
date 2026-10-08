package tech.qiantong.qknow.hermes.benchmark.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Phase 152 堆外直接内存与超球面因果谱剪枝基础设施不可变审计凭单 (Off-Heap Causal Infra Receipt)
 * <p>
 * 遵循企业级最高工程法典（AGENTS.md）与第七章工程铁律：
 * 1. 纯 Java 21 Record 强不可变类型；
 * 2. 存证超球面因果后门截断去偏置比例（>= 40%）、反事实增量范数、Java 21 Arena 堆外事件写入数、
 *    堆内存增量分配（严格 0 字节）、GC 停顿（严格 0 纳秒）、拉普拉斯 Fiedler 谱剪枝噪声边比例（>= 45%）、
 *    子图召回保真度（>= 98.0%）与 DeepSeek 64-token 确定性前缀哈希；
 * 3. 内嵌 SHA-256 密码学防篡改签名，使用 {@link MessageDigest#isEqual(byte[], byte[])} 进行常量时间比对，
 *    彻底杜绝时序侧信道（Timing Side-Channel）攻击风险。
 * </p>
 *
 * @author Achilles
 * @since Phase 152
 */
public record OffHeapCausalInfraReceipt(
        String receiptId,
        String tenantId,
        String sessionId,
        double causalDebiasReductionPercent,
        double counterfactualDeltaNorm,
        long offHeapEventsWritten,
        long offHeapHeapAllocationBytes,
        long offHeapGcPauseNanos,
        double spectralPrunedEdgeRatio,
        double algebraicConnectivity,
        double retrievalFidelity,
        boolean deepseekCacheAligned,
        String deepseekPrefixHash,
        long totalLatencyUs,
        long timestamp,
        String sha256Signature
) {

    /**
     * 构造并生成带有密码学 SHA-256 防篡改签名的不可变凭单
     */
    public static OffHeapCausalInfraReceipt create(
            String receiptId,
            String tenantId,
            String sessionId,
            double causalDebiasReductionPercent,
            double counterfactualDeltaNorm,
            long offHeapEventsWritten,
            long offHeapHeapAllocationBytes,
            long offHeapGcPauseNanos,
            double spectralPrunedEdgeRatio,
            double algebraicConnectivity,
            double retrievalFidelity,
            boolean deepseekCacheAligned,
            String deepseekPrefixHash,
            long totalLatencyUs
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format("%s|%s|%s|%.4f|%.4f|%d|%d|%d|%.4f|%.4f|%.4f|%b|%s|%d|%d",
                receiptId, tenantId, sessionId,
                causalDebiasReductionPercent, counterfactualDeltaNorm,
                offHeapEventsWritten, offHeapHeapAllocationBytes, offHeapGcPauseNanos,
                spectralPrunedEdgeRatio, algebraicConnectivity, retrievalFidelity,
                deepseekCacheAligned, deepseekPrefixHash, totalLatencyUs, now);
        String signature = computeSha256(payload);

        return new OffHeapCausalInfraReceipt(
                receiptId, tenantId, sessionId,
                causalDebiasReductionPercent, counterfactualDeltaNorm,
                offHeapEventsWritten, offHeapHeapAllocationBytes, offHeapGcPauseNanos,
                spectralPrunedEdgeRatio, algebraicConnectivity, retrievalFidelity,
                deepseekCacheAligned, deepseekPrefixHash, totalLatencyUs, now, signature
        );
    }

    /**
     * 执行密码学防篡改自验真 (常量时间校验，防范时序侧信道攻击)
     */
    public boolean verify() {
        String payload = String.format("%s|%s|%s|%.4f|%.4f|%d|%d|%d|%.4f|%.4f|%.4f|%b|%s|%d|%d",
                receiptId, tenantId, sessionId,
                causalDebiasReductionPercent, counterfactualDeltaNorm,
                offHeapEventsWritten, offHeapHeapAllocationBytes, offHeapGcPauseNanos,
                spectralPrunedEdgeRatio, algebraicConnectivity, retrievalFidelity,
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
            throw new IllegalStateException("JVM 缺少 SHA-256 算法实现", e);
        }
    }
}
