package tech.qiantong.qknow.ai.consensus.nash.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 21 Record 格式纳什博弈辩论决策不可变存证凭单
 * 包含完整辩论轨迹、收益矩阵快照、纳什均衡解与 SHA-256 密码学自签名
 */
public record NashDebateReceipt(
        String receiptId,
        String sessionId,
        String debateTopic,
        int totalRounds,
        boolean converged,
        double finalNashResidual,
        String winningStrategy,
        Map<String, Double> strategyDistribution,
        List<String> participatingAgents,
        String arbitratorVerdict,
        long executionTimeMs,
        long timestamp,
        String signature
) {
    public NashDebateReceipt(
            String receiptId,
            String sessionId,
            String debateTopic,
            int totalRounds,
            boolean converged,
            double finalNashResidual,
            String winningStrategy,
            Map<String, Double> strategyDistribution,
            List<String> participatingAgents,
            String arbitratorVerdict,
            long executionTimeMs,
            long timestamp
    ) {
        this(
                receiptId, sessionId, debateTopic, totalRounds, converged,
                finalNashResidual, winningStrategy,
                Map.copyOf(strategyDistribution),
                List.copyOf(participatingAgents),
                arbitratorVerdict, executionTimeMs, timestamp,
                calculateSha256(receiptId, sessionId, debateTopic, totalRounds,
                        converged, finalNashResidual, winningStrategy,
                        strategyDistribution, participatingAgents, arbitratorVerdict,
                        executionTimeMs, timestamp)
        );
    }

    /**
     * 运行时验真：校验凭单是否被篡改
     */
    public boolean verifySignature() {
        String expected = calculateSha256(receiptId, sessionId, debateTopic, totalRounds,
                converged, finalNashResidual, winningStrategy,
                strategyDistribution, participatingAgents, arbitratorVerdict,
                executionTimeMs, timestamp);
        return expected.equalsIgnoreCase(this.signature);
    }

    private static String calculateSha256(
            String receiptId, String sessionId, String debateTopic, int totalRounds,
            boolean converged, double finalNashResidual, String winningStrategy,
            Map<String, Double> strategyDistribution, List<String> participatingAgents,
            String arbitratorVerdict, long executionTimeMs, long timestamp
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = String.join("|",
                    receiptId, sessionId, debateTopic, String.valueOf(totalRounds),
                    String.valueOf(converged), String.format("%.6f", finalNashResidual),
                    winningStrategy, strategyDistribution.toString(),
                    String.join(",", participatingAgents),
                    arbitratorVerdict != null ? arbitratorVerdict : "N/A",
                    String.valueOf(executionTimeMs), String.valueOf(timestamp)
            );
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
