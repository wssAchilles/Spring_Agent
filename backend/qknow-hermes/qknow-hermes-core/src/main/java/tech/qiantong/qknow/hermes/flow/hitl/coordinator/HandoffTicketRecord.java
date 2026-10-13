package tech.qiantong.qknow.hermes.flow.hitl.coordinator;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 不可变多智能体移交凭单 Record (HandoffTicketRecord)
 * 纯 Java 21 Record 不可变对象，承载全局任务目标、增量证据链、因果状态签名与常量时间防侧信道散列。
 *
 * @author Achilles
 * @since 2026-10-13
 */
public record HandoffTicketRecord(
        String ticketId,
        String sessionTraceId,
        String rootTaskGoal,
        String sourceAgentId,
        String targetAgentId,
        int currentHop,
        int maxAllowedHops,
        List<String> auditCallChain,
        Map<String, Object> immutableContext,
        long createdAtEpochMs,
        String cryptographicSignature
) implements Serializable {

    /**
     * 紧凑构造函数：强制执行防御性深拷贝，杜绝可变状态逃逸
     */
    public HandoffTicketRecord {
        Objects.requireNonNull(ticketId, "ticketId 不能为空");
        Objects.requireNonNull(sessionTraceId, "sessionTraceId 不能为空");
        Objects.requireNonNull(rootTaskGoal, "rootTaskGoal 不能为空");
        Objects.requireNonNull(sourceAgentId, "sourceAgentId 不能为空");
        Objects.requireNonNull(targetAgentId, "targetAgentId 不能为空");
        Objects.requireNonNull(cryptographicSignature, "cryptographicSignature 不能为空");

        auditCallChain = List.copyOf(auditCallChain != null ? auditCallChain : List.of());
        immutableContext = Map.copyOf(immutableContext != null ? immutableContext : Map.of());
    }

    /**
     * 静态工厂方法：安全装配并自动计算 SHA-256 签名
     */
    public static HandoffTicketRecord create(
            String ticketId,
            String sessionTraceId,
            String rootTaskGoal,
            String sourceAgentId,
            String targetAgentId,
            int currentHop,
            int maxAllowedHops,
            List<String> auditCallChain,
            Map<String, Object> immutableContext
    ) {
        List<String> chain = auditCallChain != null ? new ArrayList<>(auditCallChain) : new ArrayList<>();
        if (!chain.contains(sourceAgentId)) {
            chain.add(sourceAgentId);
        }
        long now = System.currentTimeMillis();

        String payload = buildSignaturePayload(ticketId, sessionTraceId, rootTaskGoal,
                sourceAgentId, targetAgentId, currentHop, maxAllowedHops, chain, now);
        String signature = computeSha256(payload);

        return new HandoffTicketRecord(
                ticketId,
                sessionTraceId,
                rootTaskGoal,
                sourceAgentId,
                targetAgentId,
                currentHop,
                maxAllowedHops,
                chain,
                immutableContext != null ? immutableContext : Map.of(),
                now,
                signature
        );
    }

    /**
     * 常量时间防侧信道验签方法 (严格遵循企业密码学规范，防时序侧信道攻击)
     */
    public boolean verifySignature() {
        if (cryptographicSignature == null || cryptographicSignature.isBlank()) {
            return false;
        }
        String expected = buildSignaturePayload(ticketId, sessionTraceId, rootTaskGoal,
                sourceAgentId, targetAgentId, currentHop, maxAllowedHops, auditCallChain, createdAtEpochMs);
        byte[] expectedBytes = computeSha256(expected).getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = cryptographicSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String buildSignaturePayload(
            String ticketId,
            String sessionTraceId,
            String rootTaskGoal,
            String sourceAgentId,
            String targetAgentId,
            int currentHop,
            int maxAllowedHops,
            List<String> auditCallChain,
            long createdAtEpochMs
    ) {
        return String.format("%s|%s|%s|%s|%s|%d|%d|%s|%d",
                ticketId,
                sessionTraceId,
                rootTaskGoal,
                sourceAgentId,
                targetAgentId,
                currentHop,
                maxAllowedHops,
                String.join("->", auditCallChain != null ? auditCallChain : List.of()),
                createdAtEpochMs
        );
    }

    private static String computeSha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法计算失败", e);
        }
    }
}
