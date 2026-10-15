package tech.qiantong.qknow.hermes.search.graphrag.causal;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 时间感知因果知识图谱检索不可变存证凭单 (TemporalGraphRetrievalReceipt)
 * 遵循 Java 21 Record 规范与零时序侧信道泄漏工程铁律：
 * 1. 记录查询语句、检索时刻、种子三元组数与拓扑命题链；
 * 2. 记录超球面测地线平均相似度与纳秒级执行延迟；
 * 3. 采用 SHA-256 与 MessageDigest.isEqual 进行常量时间自验真比对。
 */
public record TemporalGraphRetrievalReceipt(
        String receiptId,
        String queryText,
        long queryTimestampEpochMs,
        int seedTripletCount,
        int traversedCausalNodeCount,
        List<String> causalChainPropositions,
        double avgGeodesicSimilarity,
        long latencyNanos,
        long generatedTimestampEpochMs,
        String sha256Signature
) implements Serializable {

    public TemporalGraphRetrievalReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(queryText, "queryText cannot be null");
        Objects.requireNonNull(causalChainPropositions, "causalChainPropositions cannot be null");
        causalChainPropositions = List.copyOf(causalChainPropositions);
        sha256Signature = sha256Signature != null ? sha256Signature : "";
    }

    public static TemporalGraphRetrievalReceipt createSigned(
            String queryText,
            long queryTimestampEpochMs,
            int seedTripletCount,
            int traversedCausalNodeCount,
            List<String> propositions,
            double avgSimilarity,
            long latencyNanos,
            String secretKey
    ) {
        String receiptId = "tgrr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        List<String> safeProps = propositions != null ? List.copyOf(propositions) : List.of();
        String payload = buildRawPayload(
                receiptId, queryText, queryTimestampEpochMs, seedTripletCount,
                traversedCausalNodeCount, safeProps, avgSimilarity, latencyNanos, now, secretKey
        );
        String signature = computeSha256(payload);

        return new TemporalGraphRetrievalReceipt(
                receiptId, queryText, queryTimestampEpochMs, seedTripletCount,
                traversedCausalNodeCount, safeProps, avgSimilarity, latencyNanos, now, signature
        );
    }

    public boolean verifySignature(String secretKey) {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String payload = buildRawPayload(
                receiptId, queryText, queryTimestampEpochMs, seedTripletCount,
                traversedCausalNodeCount, causalChainPropositions, avgGeodesicSimilarity,
                latencyNanos, generatedTimestampEpochMs, secretKey
        );
        String expected = computeSha256(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sha256Signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildRawPayload(
            String receiptId,
            String queryText,
            long queryTimestampEpochMs,
            int seedTripletCount,
            int traversedCausalNodeCount,
            List<String> propositions,
            double avgSimilarity,
            long latencyNanos,
            long generatedTimestampEpochMs,
            String secretKey
    ) {
        String propSummary = String.join(";;", propositions);
        return String.format(Locale.ROOT, "%s|%s|%d|%d|%d|%d|%.4f|%d|%d|%s",
                receiptId, queryText, queryTimestampEpochMs, seedTripletCount,
                traversedCausalNodeCount, propSummary.hashCode(), avgSimilarity,
                latencyNanos, generatedTimestampEpochMs,
                secretKey == null ? "HERMES_DEFAULT_TKG_SECRET" : secretKey);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
