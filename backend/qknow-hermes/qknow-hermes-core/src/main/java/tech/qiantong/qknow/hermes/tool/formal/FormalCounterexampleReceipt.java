package tech.qiantong.qknow.hermes.tool.formal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 形式化安全验证与极小不可满足核（MUC）反例自愈凭单（纯 Java 21 不可变 Record）。
 * <p>
 * 任何违约反馈均通过 {@code tailAppendRemediationMessage} 以标准工具响应/用户观察消息追加在历史对话最尾部，
 * 绝不修改 {@code SystemPrompt}，从而 100% 保护 DeepSeek 1M Context Caching 前缀。
 * </p>
 */
public record FormalCounterexampleReceipt(
        String executionId,
        String toolName,
        boolean unsatCompliant,
        List<String> minimalUnsatisfiableCore,
        Map<String, Object> counterexampleWitness,
        String tailAppendRemediationMessage,
        long createdAtEpochMs,
        byte[] macSignature
) {
    public FormalCounterexampleReceipt {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(toolName, "toolName 不能为空");
        Objects.requireNonNull(tailAppendRemediationMessage, "tailAppendRemediationMessage 不能为空");
        Objects.requireNonNull(macSignature, "macSignature 不能为空");
        minimalUnsatisfiableCore = List.copyOf(minimalUnsatisfiableCore != null ? minimalUnsatisfiableCore : List.of());
        counterexampleWitness = Map.copyOf(counterexampleWitness != null ? counterexampleWitness : Map.of());
        macSignature = Arrays.copyOf(macSignature, macSignature.length);
    }

    public static FormalCounterexampleReceipt createSigned(
            String executionId,
            String toolName,
            boolean unsatCompliant,
            List<String> muc,
            Map<String, Object> witness,
            String remediation,
            String secretKey
    ) {
        long now = System.currentTimeMillis();
        List<String> safeMuc = List.copyOf(muc != null ? muc : List.of());
        Map<String, Object> safeWitness = Map.copyOf(witness != null ? witness : Map.of());
        byte[] mac = computeMac(executionId, toolName, unsatCompliant, safeMuc, remediation, now, secretKey);
        return new FormalCounterexampleReceipt(
                executionId, toolName, unsatCompliant, safeMuc, safeWitness, remediation, now, mac
        );
    }

    public boolean verifyMac(String secretKey) {
        byte[] expected = computeMac(
                executionId, toolName, unsatCompliant, minimalUnsatisfiableCore,
                tailAppendRemediationMessage, createdAtEpochMs, secretKey
        );
        return MessageDigest.isEqual(expected, macSignature);
    }

    @Override
    public byte[] macSignature() {
        return Arrays.copyOf(macSignature, macSignature.length);
    }

    private static byte[] computeMac(
            String executionId,
            String toolName,
            boolean unsatCompliant,
            List<String> muc,
            String remediation,
            long createdAtEpochMs,
            String secretKey
    ) {
        String key = (secretKey == null || secretKey.isBlank()) ? "hermes-formal-default-secret" : secretKey;
        String payload = executionId + "|" + toolName + "|" + unsatCompliant + "|"
                + String.join(",", muc) + "|" + remediation + "|" + createdAtEpochMs;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("FormalCounterexampleReceipt MAC 计算失败", e);
        }
    }
}
