package tech.qiantong.qknow.hermes.cognitive.context;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * HDZ-PCF 滞回双区前缀守恒因果折叠不可变审计凭单（Java 21 Record）。
 * <p>
 * 强制采用 {@link MessageDigest#isEqual(byte[], byte[])} 常量时间比对验签，严防时序侧信道泄漏。
 * </p>
 */
public record ContextFoldingReceipt(
        String requestId,
        boolean zone0PrefixPreserved,
        boolean hysteresisTriggered,
        boolean inPlaceCwlMode,
        int tokensBeforeFold,
        int tokensAfterFold,
        int foldedEpisodeCount,
        int coldStandbyArchivedCount,
        double semanticReconstructionLoss,
        List<String> foldedEpisodeIds,
        long timestampMs,
        String receiptMac
) {

    public ContextFoldingReceipt {
        foldedEpisodeIds = foldedEpisodeIds == null ? List.of() : List.copyOf(foldedEpisodeIds);
    }

    public static ContextFoldingReceipt createSigned(
            String requestId,
            boolean zone0PrefixPreserved,
            boolean hysteresisTriggered,
            boolean inPlaceCwlMode,
            int tokensBeforeFold,
            int tokensAfterFold,
            int foldedEpisodeCount,
            int coldStandbyArchivedCount,
            double semanticReconstructionLoss,
            List<String> foldedEpisodeIds,
            long timestampMs,
            String secretKey
    ) {
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-context-fold-secret" : secretKey;
        String mac = computeMac(
                requestId, zone0PrefixPreserved, hysteresisTriggered, inPlaceCwlMode,
                tokensBeforeFold, tokensAfterFold, foldedEpisodeCount, coldStandbyArchivedCount,
                semanticReconstructionLoss, timestampMs, safeKey
        );
        return new ContextFoldingReceipt(
                requestId, zone0PrefixPreserved, hysteresisTriggered, inPlaceCwlMode,
                tokensBeforeFold, tokensAfterFold, foldedEpisodeCount, coldStandbyArchivedCount,
                semanticReconstructionLoss, foldedEpisodeIds, timestampMs, mac
        );
    }

    public boolean verifyMac(String secretKey) {
        if (receiptMac == null || receiptMac.isBlank()) {
            return false;
        }
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-context-fold-secret" : secretKey;
        String expected = computeMac(
                requestId, zone0PrefixPreserved, hysteresisTriggered, inPlaceCwlMode,
                tokensBeforeFold, tokensAfterFold, foldedEpisodeCount, coldStandbyArchivedCount,
                semanticReconstructionLoss, timestampMs, safeKey
        );
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receiptMac.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeMac(
            String requestId,
            boolean zone0PrefixPreserved,
            boolean hysteresisTriggered,
            boolean inPlaceCwlMode,
            int tokensBeforeFold,
            int tokensAfterFold,
            int foldedEpisodeCount,
            int coldStandbyArchivedCount,
            double semanticReconstructionLoss,
            long timestampMs,
            String secretKey
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = String.format(
                    Locale.ROOT,
                    "%s|%b|%b|%b|%d|%d|%d|%d|%.6f|%d",
                    requestId, zone0PrefixPreserved, hysteresisTriggered, inPlaceCwlMode,
                    tokensBeforeFold, tokensAfterFold, foldedEpisodeCount, coldStandbyArchivedCount,
                    semanticReconstructionLoss, timestampMs
            );
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ContextFoldingReceipt HMAC 计算失败", e);
        }
    }
}
