package tech.qiantong.qknow.hermes.cognitive.supernet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 超球面认知超网路由不可变审计凭单（Java 21 Record）。
 * <p>
 * 强制采用 {@link MessageDigest#isEqual(byte[], byte[])} 常量时间比对验签，严防时序侧信道泄漏。
 * </p>
 */
public record SupernetRoutingReceipt(
        String requestId,
        int stepIndex,
        SupernetTopologyOperator topologyOperator,
        DeepSeekReasoningEffort reasoningEffort,
        double chordDriftSquared,
        boolean replanTriggered,
        boolean earlyExitTriggered,
        double expectedUtilityPosterior,
        double vmfAttentionEntropy,
        long timestampMs,
        String receiptMac
) {

    public static SupernetRoutingReceipt createSigned(
            String requestId,
            int stepIndex,
            SupernetTopologyOperator topologyOperator,
            DeepSeekReasoningEffort reasoningEffort,
            double chordDriftSquared,
            boolean replanTriggered,
            boolean earlyExitTriggered,
            double expectedUtilityPosterior,
            double vmfAttentionEntropy,
            long timestampMs,
            String secretKey
    ) {
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-supernet-default-secret" : secretKey;
        String mac = computeMacHex(
                requestId, stepIndex, topologyOperator, reasoningEffort,
                chordDriftSquared, replanTriggered, earlyExitTriggered,
                expectedUtilityPosterior, vmfAttentionEntropy, timestampMs, safeKey
        );
        return new SupernetRoutingReceipt(
                requestId, stepIndex, topologyOperator, reasoningEffort,
                chordDriftSquared, replanTriggered, earlyExitTriggered,
                expectedUtilityPosterior, vmfAttentionEntropy, timestampMs, mac
        );
    }

    public boolean verifyMac(String secretKey) {
        if (receiptMac == null || receiptMac.isBlank()) {
            return false;
        }
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-supernet-default-secret" : secretKey;
        String expected = computeMacHex(
                requestId, stepIndex, topologyOperator, reasoningEffort,
                chordDriftSquared, replanTriggered, earlyExitTriggered,
                expectedUtilityPosterior, vmfAttentionEntropy, timestampMs, safeKey
        );
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receiptMac.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeMacHex(
            String requestId,
            int stepIndex,
            SupernetTopologyOperator topologyOperator,
            DeepSeekReasoningEffort reasoningEffort,
            double chordDriftSquared,
            boolean replanTriggered,
            boolean earlyExitTriggered,
            double expectedUtilityPosterior,
            double vmfAttentionEntropy,
            long timestampMs,
            String secretKey
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = String.format(
                    Locale.ROOT,
                    "%s|%d|%s|%s|%.6f|%b|%b|%.6f|%.6f|%d",
                    requestId,
                    stepIndex,
                    topologyOperator.name(),
                    reasoningEffort.name(),
                    chordDriftSquared,
                    replanTriggered,
                    earlyExitTriggered,
                    expectedUtilityPosterior,
                    vmfAttentionEntropy,
                    timestampMs
            );
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("生成超网路由 HMAC 签名异常", e);
        }
    }
}
