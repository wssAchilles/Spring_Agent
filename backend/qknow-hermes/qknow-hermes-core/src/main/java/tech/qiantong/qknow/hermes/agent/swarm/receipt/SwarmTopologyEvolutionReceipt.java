package tech.qiantong.qknow.hermes.agent.swarm.receipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 多智能体动态拓扑演化不可变密码学存证凭单 (SwarmTopologyEvolutionReceipt)
 * 遵循 Phase 135 规范与零信任安全架构
 * 1. 纯 Java 21 Record 格式，零对象逃逸与强不可变类型
 * 2. 完整记录多智能体动态热插拔、边重连与自愈演化事件
 * 3. 常量时间验真 verifySignature()，防范时序侧信道分析攻击
 *
 * @author Achilles
 * @since 2026-09-25
 */
public record SwarmTopologyEvolutionReceipt(
        String evolutionId,
        String sessionId,
        String tenantId,
        String eventType, // PLUG_IN, UNPLUG, REWIRE, HEAL
        String sourceNodeId,
        String targetNodeId,
        double edgeWeight,
        long timestamp,
        String sha256Signature
) {
    public SwarmTopologyEvolutionReceipt {
        Objects.requireNonNull(evolutionId, "evolutionId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
        Objects.requireNonNull(sha256Signature, "sha256Signature must not be null");
    }

    /**
     * 计算规范化 SHA-256 签名
     */
    public static String calculateSignature(
            String evolutionId,
            String sessionId,
            String tenantId,
            String eventType,
            String sourceNodeId,
            String targetNodeId,
            double edgeWeight,
            long timestamp
    ) {
        String canonicalPayload = String.join("|",
                evolutionId,
                sessionId,
                tenantId,
                eventType,
                sourceNodeId,
                targetNodeId,
                String.format("%.4f", edgeWeight),
                String.valueOf(timestamp)
        );

        return computeSha256Hex(canonicalPayload);
    }

    /**
     * 凭单工厂方法
     */
    public static SwarmTopologyEvolutionReceipt create(
            String sessionId,
            String tenantId,
            String eventType,
            String sourceNodeId,
            String targetNodeId,
            double edgeWeight
    ) {
        long ts = System.currentTimeMillis();
        String evolutionId = "evol_" + eventType.toLowerCase() + "_" + ts + "_" + Math.abs(Objects.hash(sourceNodeId, targetNodeId));
        String signature = calculateSignature(
                evolutionId,
                sessionId,
                tenantId,
                eventType,
                sourceNodeId,
                targetNodeId,
                edgeWeight,
                ts
        );

        return new SwarmTopologyEvolutionReceipt(
                evolutionId,
                sessionId,
                tenantId,
                eventType,
                sourceNodeId,
                targetNodeId,
                edgeWeight,
                ts,
                signature
        );
    }

    /**
     * 常量时间自验真 (防范时序侧信道攻击)
     */
    public boolean verifySignature() {
        String expected = calculateSignature(
                this.evolutionId,
                this.sessionId,
                this.tenantId,
                this.eventType,
                this.sourceNodeId,
                this.targetNodeId,
                this.edgeWeight,
                this.timestamp
        );
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String computeSha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
