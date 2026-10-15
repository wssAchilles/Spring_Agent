package tech.qiantong.qknow.hermes.tool.hoare;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 紧凑 ACI (Agent-Computer Interface) 观察量凭单 (Java 21 不可变 Record)
 * 遵循 Princeton SWE-agent ACI 原则与零时序泄漏工程铁律：
 * 1. 结构化捕获前置守卫拦截与语法/参数校验差量；
 * 2. 构造面向大模型的带 Schema 差量与修正指引的友好 Observation 文本；
 * 3. 采用 SHA-256 与 MessageDigest.isEqual 进行密码学自验真，防止篡改。
 */
public record AciObservationReceipt(
        String receiptId,
        String toolName,
        String executionId,
        boolean passed,
        String violationType,
        List<String> missingParameters,
        Map<String, String> parameterErrors,
        String observationText,
        String suggestedRemedy,
        long timestamp,
        String signature
) {
    public AciObservationReceipt {
        Objects.requireNonNull(receiptId, "receiptId cannot be null");
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(executionId, "executionId cannot be null");
        missingParameters = missingParameters == null ? Collections.emptyList() : Collections.unmodifiableList(missingParameters);
        parameterErrors = parameterErrors == null ? Collections.emptyMap() : Collections.unmodifiableMap(parameterErrors);
        observationText = observationText == null ? "" : observationText;
        suggestedRemedy = suggestedRemedy == null ? "" : suggestedRemedy;
        signature = signature == null ? "" : signature;
    }

    /**
     * 计算凭单不可变 SHA-256 签名
     */
    public static String calculateSignature(
            String receiptId,
            String toolName,
            String executionId,
            boolean passed,
            String violationType,
            int missingCount,
            int errorCount,
            long timestamp,
            String secretKey
    ) {
        String payload = String.join("|",
                receiptId,
                toolName,
                executionId,
                String.valueOf(passed),
                violationType == null ? "NONE" : violationType,
                String.valueOf(missingCount),
                String.valueOf(errorCount),
                String.valueOf(timestamp),
                secretKey == null ? "HERMES_ACI_KEY" : secretKey
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256 signature for ACI receipt", e);
        }
    }

    /**
     * 常量时间比对验签，防时序侧信道攻击
     */
    public boolean verifySignature(String secretKey) {
        String expected = calculateSignature(
                receiptId, toolName, executionId, passed, violationType,
                missingParameters.size(), parameterErrors.size(), timestamp, secretKey);
        byte[] a = this.signature.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /**
     * 构造标准成功 ACI 观察凭单
     */
    public static AciObservationReceipt createSuccess(
            String receiptId,
            String toolName,
            String executionId,
            String observationText,
            long timestamp,
            String secretKey
    ) {
        String sig = calculateSignature(receiptId, toolName, executionId, true, "NONE", 0, 0, timestamp, secretKey);
        return new AciObservationReceipt(
                receiptId, toolName, executionId, true, "NONE",
                Collections.emptyList(), Collections.emptyMap(),
                observationText, "Execution successful. Proceed to the next step.", timestamp, sig);
    }

    /**
     * 构造单步自纠错 ACI 观察凭单
     */
    public static AciObservationReceipt createLintFailure(
            String receiptId,
            String toolName,
            String executionId,
            String violationType,
            List<String> missingParameters,
            Map<String, String> parameterErrors,
            String suggestedRemedy,
            long timestamp,
            String secretKey
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ACI-LINT-ERROR]: Precondition violation on tool '").append(toolName).append("'.\n");
        sb.append("Violation Category: ").append(violationType).append("\n");
        if (missingParameters != null && !missingParameters.isEmpty()) {
            sb.append("Missing Required Parameter(s): ").append(String.join(", ", missingParameters)).append("\n");
        }
        if (parameterErrors != null && !parameterErrors.isEmpty()) {
            sb.append("Parameter Value Error(s):\n");
            parameterErrors.forEach((k, v) -> sb.append("  * ").append(k).append(": ").append(v).append("\n"));
        }
        sb.append("[REMEDY-GUIDE]: ").append(suggestedRemedy != null ? suggestedRemedy : "Please correct arguments and retry.");

        String sig = calculateSignature(
                receiptId, toolName, executionId, false, violationType,
                missingParameters == null ? 0 : missingParameters.size(),
                parameterErrors == null ? 0 : parameterErrors.size(),
                timestamp, secretKey);

        return new AciObservationReceipt(
                receiptId, toolName, executionId, false, violationType,
                missingParameters, parameterErrors, sb.toString(), suggestedRemedy, timestamp, sig);
    }
}
