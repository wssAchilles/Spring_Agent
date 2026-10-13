package tech.qiantong.qknow.hermes.agent.governor.dto;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 夏普利边际价值贡献分配凭单 Record (ShapleyCreditAllocationReceipt)
 * 基于博弈论公理化归因、SHA-256 自签名与 MessageDigest.isEqual 常量时间抗时序侧信道比对。
 */
public record ShapleyCreditAllocationReceipt(
        String receiptId,
        String sessionId,
        int totalAgentsEvaluated,
        int samplePermutations,
        double grandCoalitionValue,
        Map<String, Double> shapleyValues,
        Map<String, Double> normalizedCredits,
        String sha256Signature,
        long timestampMs
) implements Serializable {

    public ShapleyCreditAllocationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不可为空");
        Objects.requireNonNull(sessionId, "sessionId 不可为空");
        shapleyValues = shapleyValues != null ? Map.copyOf(shapleyValues) : Map.of();
        normalizedCredits = normalizedCredits != null ? Map.copyOf(normalizedCredits) : Map.of();
    }

    public static ShapleyCreditAllocationReceipt createSigned(
            String receiptId, String sessionId, int totalAgents,
            int permutations, double grandValue,
            Map<String, Double> shapleyMap, Map<String, Double> creditMap) {
        long now = System.currentTimeMillis();
        String raw = computeRawPayload(receiptId, sessionId, totalAgents, permutations, grandValue, creditMap, now);
        String signature = computeSha256(raw);
        return new ShapleyCreditAllocationReceipt(
                receiptId, sessionId, totalAgents, permutations, grandValue,
                shapleyMap, creditMap, signature, now
        );
    }

    public boolean verifySignature() {
        if (sha256Signature == null || sha256Signature.isBlank()) {
            return false;
        }
        String raw = computeRawPayload(receiptId, sessionId, totalAgentsEvaluated, samplePermutations,
                grandCoalitionValue, normalizedCredits, timestampMs);
        String expected = computeSha256(raw);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sha256Signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeRawPayload(
            String receiptId, String sessionId, int totalAgents,
            int permutations, double grandValue,
            Map<String, Double> creditMap, long timestampMs) {
        String creditsStr = creditMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.format("%s:%.4f", e.getKey(), e.getValue()))
                .collect(Collectors.joining(";"));
        return String.format("%s|%s|%d|%d|%.4f|%s|%d",
                receiptId, sessionId, totalAgents, permutations, grandValue, creditsStr, timestampMs);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
