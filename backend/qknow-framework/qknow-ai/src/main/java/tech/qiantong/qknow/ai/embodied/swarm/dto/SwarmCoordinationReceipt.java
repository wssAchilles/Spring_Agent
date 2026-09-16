package tech.qiantong.qknow.ai.embodied.swarm.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 具身多智能体集群协同不可变存证凭单 (Java 21 Record)
 * 封装凭单 ID、集群智能体规模、编队拓扑模式、代数连通度、最小避障间距、最大线缆张力、HOCBF 裕度、单步耗时、总线状态与 SHA-256 防篡改签名
 */
public record SwarmCoordinationReceipt(
        String receiptId,
        String swarmSessionId,
        int activeAgentsCount,
        String formationTopologyMode,
        double algebraicConnectivity,
        double minInterAgentDistance,
        double maxTetherTensionN,
        double hocbfSafetyMargin,
        double executionStepTimeMicros,
        String busStatus,
        long timestampEpochMs,
        String sha256Signature
) {

    public SwarmCoordinationReceipt {
        if (receiptId == null || receiptId.isBlank()) {
            throw new IllegalArgumentException("凭单唯一标识 receiptId 不能为空");
        }
        if (swarmSessionId == null || swarmSessionId.isBlank()) {
            throw new IllegalArgumentException("集群会话标识 swarmSessionId 不能为空");
        }
        if (activeAgentsCount <= 0) {
            throw new IllegalArgumentException("活跃智能体数量 activeAgentsCount 必须大于 0");
        }
        if (formationTopologyMode == null || formationTopologyMode.isBlank()) {
            throw new IllegalArgumentException("编队拓扑模式 formationTopologyMode 不能为空");
        }
        if (busStatus == null || busStatus.isBlank()) {
            throw new IllegalArgumentException("总线状态 busStatus 不能为空");
        }
        if (sha256Signature == null || sha256Signature.isBlank()) {
            throw new IllegalArgumentException("密码学签名 sha256Signature 不能为空");
        }
    }

    /**
     * 工厂方法：计算核心字段散列并完成 SHA-256 签名生成凭单
     */
    public static SwarmCoordinationReceipt createAndSign(
            String receiptId,
            String swarmSessionId,
            int activeAgentsCount,
            String formationTopologyMode,
            double algebraicConnectivity,
            double minInterAgentDistance,
            double maxTetherTensionN,
            double hocbfSafetyMargin,
            double executionStepTimeMicros,
            String busStatus,
            long timestampEpochMs
    ) {
        String payload = buildPayload(
                receiptId, swarmSessionId, activeAgentsCount, formationTopologyMode,
                algebraicConnectivity, minInterAgentDistance, maxTetherTensionN,
                hocbfSafetyMargin, executionStepTimeMicros, busStatus, timestampEpochMs
        );
        String signature = computeSha256(payload);
        return new SwarmCoordinationReceipt(
                receiptId, swarmSessionId, activeAgentsCount, formationTopologyMode,
                algebraicConnectivity, minInterAgentDistance, maxTetherTensionN,
                hocbfSafetyMargin, executionStepTimeMicros, busStatus, timestampEpochMs,
                signature
        );
    }

    /**
     * 验真方法：重新计算散列并核对签名，确保全链路不可篡改
     */
    public boolean verifySignature() {
        String payload = buildPayload(
                receiptId, swarmSessionId, activeAgentsCount, formationTopologyMode,
                algebraicConnectivity, minInterAgentDistance, maxTetherTensionN,
                hocbfSafetyMargin, executionStepTimeMicros, busStatus, timestampEpochMs
        );
        String expectedSignature = computeSha256(payload);
        return expectedSignature.equalsIgnoreCase(this.sha256Signature);
    }

    private static String buildPayload(
            String receiptId, String swarmSessionId, int activeAgentsCount, String formationTopologyMode,
            double algebraicConnectivity, double minInterAgentDistance, double maxTetherTensionN,
            double hocbfSafetyMargin, double executionStepTimeMicros, String busStatus, long timestampEpochMs
    ) {
        return receiptId + "|" + swarmSessionId + "|" + activeAgentsCount + "|" + formationTopologyMode + "|"
                + String.format("%.4f", algebraicConnectivity) + "|"
                + String.format("%.4f", minInterAgentDistance) + "|"
                + String.format("%.4f", maxTetherTensionN) + "|"
                + String.format("%.4f", hocbfSafetyMargin) + "|"
                + String.format("%.2f", executionStepTimeMicros) + "|"
                + busStatus + "|" + timestampEpochMs;
    }

    private static String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法在当前 JVM 中不可用", e);
        }
    }
}
