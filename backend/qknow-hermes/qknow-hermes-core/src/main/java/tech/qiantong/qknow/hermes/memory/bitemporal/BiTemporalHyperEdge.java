package tech.qiantong.qknow.hermes.memory.bitemporal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/**
 * 双时态超球面记忆超图关系边契约（对应博士论文第 4 章定义 5.1 与定理 4.1）。
 * <p>
 * 同时携带客观有效时间区间 {@code [validFromEpochMs, validToEpochMs)} 与系统事务认知区间 {@code [txFromEpochMs, txToEpochMs)}，
 * 维护源感知引用计数 {@code sourceReferenceCount}（对齐 HippoRAG 2 {@code fact_source_counts}）及三级冷备层级。
 * </p>
 */
public record BiTemporalHyperEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        String relationPredicate,
        boolean functionalExclusive,
        float[] unitEmbedding1024,
        long validFromEpochMs,
        Long validToEpochMs,
        long txFromEpochMs,
        Long txToEpochMs,
        int sourceReferenceCount,
        ColdStandbyTier storageTier,
        byte[] provenanceMac
) {
    public static final int EMBEDDING_DIMENSION = 1024;

    public BiTemporalHyperEdge {
        Objects.requireNonNull(edgeId, "edgeId 不能为空");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId 不能为空");
        Objects.requireNonNull(targetNodeId, "targetNodeId 不能为空");
        Objects.requireNonNull(relationPredicate, "relationPredicate 不能为空");
        Objects.requireNonNull(unitEmbedding1024, "unitEmbedding1024 不能为空");
        Objects.requireNonNull(storageTier, "storageTier 不能为空");
        Objects.requireNonNull(provenanceMac, "provenanceMac 不能为空");
        if (unitEmbedding1024.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("双时态边向量维度必须严格为 1024 维");
        }
        double normSq = 0.0;
        for (float v : unitEmbedding1024) {
            normSq += (double) v * v;
        }
        if (Math.abs(Math.sqrt(normSq) - 1.0) > 1e-3) {
            throw new IllegalArgumentException("双时态边向量未满足 S^1023 单位归一化约束");
        }
        unitEmbedding1024 = Arrays.copyOf(unitEmbedding1024, EMBEDDING_DIMENSION);
        provenanceMac = Arrays.copyOf(provenanceMac, provenanceMac.length);
    }

    public static BiTemporalHyperEdge createSigned(
            String edgeId,
            String sourceNodeId,
            String targetNodeId,
            String relationPredicate,
            boolean functionalExclusive,
            float[] unitEmbedding1024,
            long validFromEpochMs,
            Long validToEpochMs,
            long txFromEpochMs,
            Long txToEpochMs,
            int sourceReferenceCount,
            ColdStandbyTier storageTier,
            String secretKey
    ) {
        byte[] mac = computeMac(
                edgeId, sourceNodeId, targetNodeId, relationPredicate, functionalExclusive,
                validFromEpochMs, validToEpochMs, txFromEpochMs, txToEpochMs,
                sourceReferenceCount, storageTier, secretKey
        );
        return new BiTemporalHyperEdge(
                edgeId, sourceNodeId, targetNodeId, relationPredicate, functionalExclusive,
                unitEmbedding1024, validFromEpochMs, validToEpochMs, txFromEpochMs, txToEpochMs,
                sourceReferenceCount, storageTier, mac
        );
    }

    /**
     * 判定该边在双时态二维截面坐标 (validTime, txTime) 上是否处于有效活跃态。
     */
    public boolean isActiveAt(long validTimeEpochMs, long txTimeEpochMs) {
        boolean validMatch = validTimeEpochMs >= validFromEpochMs
                && (validToEpochMs == null || validTimeEpochMs < validToEpochMs);
        boolean txMatch = txTimeEpochMs >= txFromEpochMs
                && (txToEpochMs == null || txTimeEpochMs < txToEpochMs);
        return validMatch && txMatch && sourceReferenceCount > 0;
    }

    public boolean verifyMac(String secretKey) {
        byte[] expected = computeMac(
                edgeId, sourceNodeId, targetNodeId, relationPredicate, functionalExclusive,
                validFromEpochMs, validToEpochMs, txFromEpochMs, txToEpochMs,
                sourceReferenceCount, storageTier, secretKey
        );
        return MessageDigest.isEqual(expected, provenanceMac);
    }

    @Override
    public float[] unitEmbedding1024() {
        return Arrays.copyOf(unitEmbedding1024, EMBEDDING_DIMENSION);
    }

    @Override
    public byte[] provenanceMac() {
        return Arrays.copyOf(provenanceMac, provenanceMac.length);
    }

    private static byte[] computeMac(
            String edgeId,
            String sourceNodeId,
            String targetNodeId,
            String relationPredicate,
            boolean functionalExclusive,
            long validFromEpochMs,
            Long validToEpochMs,
            long txFromEpochMs,
            Long txToEpochMs,
            int sourceReferenceCount,
            ColdStandbyTier storageTier,
            String secretKey
    ) {
        String key = (secretKey == null || secretKey.isBlank()) ? "hermes-bitemporal-default-secret" : secretKey;
        String payload = edgeId + "|" + sourceNodeId + "|" + targetNodeId + "|" + relationPredicate + "|"
                + functionalExclusive + "|" + validFromEpochMs + "|" + validToEpochMs + "|"
                + txFromEpochMs + "|" + txToEpochMs + "|" + sourceReferenceCount + "|" + storageTier.name();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("双时态边 MAC 签名失败", e);
        }
    }
}
