package tech.qiantong.qknow.ai.superagent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Phase 60: 超级智能体生态全局主权不可变存证账本 (Record)
 * <p>
 * 遵循密码学不可篡改约束，内置 SHA-256 签名自校验逻辑。
 */
public record SuperAgentAuditLedger(
        String ledgerId,
        long governanceEpoch,
        double clusterCognitiveEntropy,
        int activeAgentCount,
        int isolatedAgentCount,
        double paretoFitnessScore,
        boolean promotionApproved,
        boolean emergencyHalted,
        String sovereignDecision,
        long timestamp,
        String sha256Signature
) {

    /**
     * 工厂方法：计算并创建带有 SHA-256 签名的存证账本
     */
    public static SuperAgentAuditLedger create(
            String ledgerId,
            long governanceEpoch,
            double clusterCognitiveEntropy,
            int activeAgentCount,
            int isolatedAgentCount,
            double paretoFitnessScore,
            boolean promotionApproved,
            boolean emergencyHalted,
            String sovereignDecision,
            long timestamp
    ) {
        String signature = computeHash(
                ledgerId, governanceEpoch, clusterCognitiveEntropy,
                activeAgentCount, isolatedAgentCount, paretoFitnessScore,
                promotionApproved, emergencyHalted, sovereignDecision, timestamp
        );
        return new SuperAgentAuditLedger(
                ledgerId, governanceEpoch, clusterCognitiveEntropy,
                activeAgentCount, isolatedAgentCount, paretoFitnessScore,
                promotionApproved, emergencyHalted, sovereignDecision,
                timestamp, signature
        );
    }

    /**
     * 校验账本签名是否真实有效且未被篡改
     */
    public boolean verifySignature() {
        String expectedHash = computeHash(
                ledgerId, governanceEpoch, clusterCognitiveEntropy,
                activeAgentCount, isolatedAgentCount, paretoFitnessScore,
                promotionApproved, emergencyHalted, sovereignDecision, timestamp
        );
        return Objects.equals(this.sha256Signature, expectedHash);
    }

    private static String computeHash(
            String ledgerId,
            long governanceEpoch,
            double clusterCognitiveEntropy,
            int activeAgentCount,
            int isolatedAgentCount,
            double paretoFitnessScore,
            boolean promotionApproved,
            boolean emergencyHalted,
            String sovereignDecision,
            long timestamp
    ) {
        String raw = String.format("%s|%d|%.6f|%d|%d|%.6f|%b|%b|%s|%d",
                ledgerId, governanceEpoch, clusterCognitiveEntropy,
                activeAgentCount, isolatedAgentCount, paretoFitnessScore,
                promotionApproved, emergencyHalted, sovereignDecision, timestamp);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("系统缺失 SHA-256 摘要算法", e);
        }
    }
}
