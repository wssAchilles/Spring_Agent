package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalProbeReceipt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 双时态因果演化检索不可变凭单（Java 21 Record，对应定理 8.1 存证）。
 * <p>
 * 强制使用 HmacSHA256 与常量时间 {@link MessageDigest#isEqual} 防侧信道验签。
 * </p>
 */
public record BiTemporalCausalRetrievalReceipt(
        String receiptId,
        String queryId,
        TemporalAnchorSpec anchorSpec,
        HippocampalProbeReceipt pprReceipt,
        List<CausalEvolutionStep> orderedCausalChain,
        ReadOnlyDatasetView datasetView,
        List<String> recalledErrorBookWarnings,
        long elapsedMicros,
        byte[] provenanceMac
) {
    public BiTemporalCausalRetrievalReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(queryId, "queryId 不能为空");
        Objects.requireNonNull(anchorSpec, "anchorSpec 不能为空");
        Objects.requireNonNull(pprReceipt, "pprReceipt 不能为空");
        Objects.requireNonNull(datasetView, "datasetView 不能为空");
        Objects.requireNonNull(provenanceMac, "provenanceMac 不能为空");
        orderedCausalChain = List.copyOf(orderedCausalChain != null ? orderedCausalChain : List.of());
        recalledErrorBookWarnings = List.copyOf(recalledErrorBookWarnings != null ? recalledErrorBookWarnings : List.of());
        provenanceMac = Arrays.copyOf(provenanceMac, provenanceMac.length);
    }

    /**
     * 构造带 HmacSHA256 签名的不可变检索凭单。
     */
    public static BiTemporalCausalRetrievalReceipt createSigned(
            String receiptId,
            String queryId,
            TemporalAnchorSpec anchorSpec,
            HippocampalProbeReceipt pprReceipt,
            List<CausalEvolutionStep> orderedCausalChain,
            ReadOnlyDatasetView datasetView,
            List<String> recalledErrorBookWarnings,
            long elapsedMicros,
            String secretKey
    ) {
        List<CausalEvolutionStep> safeChain = orderedCausalChain != null ? orderedCausalChain : List.of();
        List<String> safeWarnings = recalledErrorBookWarnings != null ? recalledErrorBookWarnings : List.of();
        byte[] mac = computeMac(
                receiptId, queryId, anchorSpec, pprReceipt, safeChain, datasetView, safeWarnings, elapsedMicros, secretKey
        );
        return new BiTemporalCausalRetrievalReceipt(
                receiptId, queryId, anchorSpec, pprReceipt, safeChain, datasetView, safeWarnings, elapsedMicros, mac
        );
    }

    /**
     * 常量时间防侧信道验签（MessageDigest.isEqual）。
     */
    public boolean verifyMac(String secretKey) {
        byte[] expected = computeMac(
                receiptId, queryId, anchorSpec, pprReceipt, orderedCausalChain, datasetView,
                recalledErrorBookWarnings, elapsedMicros, secretKey
        );
        return MessageDigest.isEqual(expected, provenanceMac);
    }

    @Override
    public byte[] provenanceMac() {
        return Arrays.copyOf(provenanceMac, provenanceMac.length);
    }

    private static byte[] computeMac(
            String receiptId,
            String queryId,
            TemporalAnchorSpec anchorSpec,
            HippocampalProbeReceipt pprReceipt,
            List<CausalEvolutionStep> chain,
            ReadOnlyDatasetView datasetView,
            List<String> warnings,
            long elapsedMicros,
            String secretKey
    ) {
        String key = (secretKey == null || secretKey.isBlank()) ? "hermes-bitemporal-causal-secret" : secretKey;
        StringBuilder sb = new StringBuilder();
        sb.append(receiptId).append('|')
                .append(queryId).append('|')
                .append(anchorSpec.mode().name()).append('|')
                .append(anchorSpec.validFromEpochMs()).append('|')
                .append(anchorSpec.validToEpochMs()).append('|')
                .append(anchorSpec.txAtEpochMs()).append('|')
                .append(pprReceipt.queryId()).append('|')
                .append(datasetView.contentSha256()).append('|')
                .append(elapsedMicros).append('|')
                .append(warnings.size());
        for (CausalEvolutionStep st : chain) {
            sb.append('|').append(st.stepRank()).append(':').append(st.edgeId())
                    .append(':').append(st.validFromEpochMs())
                    .append(':').append(String.format(Locale.ROOT, "%.4f", st.compositeScore()));
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("计算 BiTemporalCausalRetrievalReceipt HMAC 失败", e);
        }
    }
}
