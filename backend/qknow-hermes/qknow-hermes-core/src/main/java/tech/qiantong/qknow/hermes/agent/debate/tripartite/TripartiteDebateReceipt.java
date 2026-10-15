package tech.qiantong.qknow.hermes.agent.debate.tripartite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * 三方博弈辩论仲裁终决不可变凭单
 * 严格基于 Java 21 Record 规范，提供夏普利边际贡献值量化与常量时间防侧信道比对
 */
public record TripartiteDebateReceipt(
        String debateId,
        String taskTopic,
        int totalRounds,
        boolean isConverged,
        String finalResolutionPlan,
        Map<TripartiteDebateRole, Double> shapleyAttributionMap,
        List<TripartiteDebateTurnRecord> debateTurns,
        long durationMillis,
        String sha256Signature
) {
    public static TripartiteDebateReceipt create(
            String debateId,
            String taskTopic,
            int totalRounds,
            boolean isConverged,
            String finalResolutionPlan,
            Map<TripartiteDebateRole, Double> shapleyAttributionMap,
            List<TripartiteDebateTurnRecord> debateTurns,
            long durationMillis
    ) {
        Map<TripartiteDebateRole, Double> immutableShapley = Map.copyOf(shapleyAttributionMap);
        List<TripartiteDebateTurnRecord> immutableTurns = List.copyOf(debateTurns);
        String signature = generateSignature(debateId, taskTopic, totalRounds, isConverged, finalResolutionPlan, immutableShapley);
        return new TripartiteDebateReceipt(
                debateId,
                taskTopic,
                totalRounds,
                isConverged,
                finalResolutionPlan,
                immutableShapley,
                immutableTurns,
                durationMillis,
                signature
        );
    }

    private static String generateSignature(
            String debateId,
            String topic,
            int rounds,
            boolean converged,
            String plan,
            Map<TripartiteDebateRole, Double> shapleyMap
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s|%s|%d|%b|%s|%s",
                    debateId, topic, rounds, converged, plan, shapleyMap.toString());
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    /**
     * 常量时间比对防时序侧信道攻击验签
     */
    public boolean verifySignature() {
        String expected = generateSignature(debateId, taskTopic, totalRounds, isConverged, finalResolutionPlan, shapleyAttributionMap);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
