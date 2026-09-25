package tech.qiantong.qknow.hermes.swarm.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 纯 Java 21 Record 格式不可变记忆巩固与修剪存证凭单 (EpisodicMemoryConsolidationReceipt)
 * <p>
 * 核心机制：
 * 1. 规范化存证多智能体长程协作中微观情节聚类巩固、艾宾浩斯衰减软修剪与高阶反思元规则沉淀的全局状态；
 * 2. 存证租户隔离标识、原始节点数、质心主题簇数、软修剪淘汰数、压缩率与端到端因果连通路径保持率 (CRR)；
 * 3. 对提炼的语义主题簇与反思规则签发独立的 SHA-256 指纹；
 * 4. 基于规范化全字段生成全局数字签名，内置基于 MessageDigest.isEqual 的常量时间密码学自验真，有效抵御时序侧信道反向篡改攻击。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record EpisodicMemoryConsolidationReceipt(
        String receiptId,
        String tenantId,
        String traceId,
        int originalEpisodes,
        int consolidatedClusters,
        int prunedEpisodes,
        double compressionRatioPercent,
        double causalReachabilityPercent,
        String clustersDigest,
        double latencyMs,
        long timestamp,
        String sha256Signature
) {

    public EpisodicMemoryConsolidationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(clustersDigest, "clustersDigest 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 签发不可变记忆巩固与修剪存证凭单工厂方法
     */
    public static EpisodicMemoryConsolidationReceipt create(
            String receiptId,
            String tenantId,
            String traceId,
            int originalEpisodes,
            int consolidatedClusters,
            int prunedEpisodes,
            double compressionRatioPercent,
            double causalReachabilityPercent,
            String clustersDigest,
            double latencyMs,
            long timestamp
    ) {
        String signature = computeSignature(
                receiptId, tenantId, traceId, originalEpisodes, consolidatedClusters,
                prunedEpisodes, compressionRatioPercent, causalReachabilityPercent,
                clustersDigest, latencyMs, timestamp
        );

        return new EpisodicMemoryConsolidationReceipt(
                receiptId, tenantId, traceId, originalEpisodes, consolidatedClusters,
                prunedEpisodes, compressionRatioPercent, causalReachabilityPercent,
                clustersDigest, latencyMs, timestamp, signature
        );
    }

    /**
     * 规范化全字段计算 SHA-256 数字签名
     */
    public static String computeSignature(
            String receiptId,
            String tenantId,
            String traceId,
            int originalEpisodes,
            int consolidatedClusters,
            int prunedEpisodes,
            double compressionRatioPercent,
            double causalReachabilityPercent,
            String clustersDigest,
            double latencyMs,
            long timestamp
    ) {
        String payload = String.format(
                Locale.ROOT,
                "%s|%s|%s|%d|%d|%d|%.4f|%.4f|%s|%.4f|%d",
                receiptId, tenantId, traceId, originalEpisodes, consolidatedClusters,
                prunedEpisodes, compressionRatioPercent, causalReachabilityPercent,
                clustersDigest, latencyMs, timestamp
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("SHA-256 算法在当前运行时不可用", e);
        }
    }

    /**
     * 常量时间密码学自验真 (抵御时序侧信道攻击)
     *
     * @return 若凭单全字段未经篡改且签名匹配则返回 true，否则返回 false
     */
    public boolean verifySignature() {
        String expected = computeSignature(
                receiptId, tenantId, traceId, originalEpisodes, consolidatedClusters,
                prunedEpisodes, compressionRatioPercent, causalReachabilityPercent,
                clustersDigest, latencyMs, timestamp
        );

        byte[] a = this.sha256Signature.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
