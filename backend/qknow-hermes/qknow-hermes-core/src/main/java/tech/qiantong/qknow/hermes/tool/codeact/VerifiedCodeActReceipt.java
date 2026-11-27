package tech.qiantong.qknow.hermes.tool.codeact;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/**
 * 验证型 CodeAct 沙盒执行不可变存证凭单（Java 21 Record，对应定理 8.2）。
 * <p>
 * HMAC-SHA256 签名覆盖脚本摘要、标准输出摘要、错误输出摘要、退出码与隔离存证等级，
 * 全部验签强制使用 {@link MessageDigest#isEqual} 防时序侧信道攻击。
 * </p>
 */
public record VerifiedCodeActReceipt(
        String receiptId,
        String planId,
        CodeActExecutionPlan.ExecutionTier executionTier,
        String language,
        String scriptSha256,
        int exitCode,
        String stdout,
        String stderr,
        String stdoutSha256,
        String stderrSha256,
        boolean truncated,
        boolean timedOut,
        long executionLatencyMicros,
        SandboxIsolationAttestation isolationAttestation,
        long timestampEpochMs,
        byte[] hmacSignature
) {
    public VerifiedCodeActReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(planId, "planId 不能为空");
        Objects.requireNonNull(executionTier, "executionTier 不能为空");
        Objects.requireNonNull(language, "language 不能为空");
        Objects.requireNonNull(scriptSha256, "scriptSha256 不能为空");
        Objects.requireNonNull(stdoutSha256, "stdoutSha256 不能为空");
        Objects.requireNonNull(stderrSha256, "stderrSha256 不能为空");
        Objects.requireNonNull(isolationAttestation, "isolationAttestation 不能为空");
        Objects.requireNonNull(hmacSignature, "hmacSignature 不能为空");
        stdout = stdout != null ? stdout : "";
        stderr = stderr != null ? stderr : "";
        hmacSignature = Arrays.copyOf(hmacSignature, hmacSignature.length);
    }

    /**
     * 判定本次程序化执行是否构成客观确定性通过见证。
     */
    public boolean isSuccess() {
        return !timedOut && exitCode == 0 && isolationAttestation.astInspectorPassed();
    }

    /**
     * 构造带完整内容指纹与 HmacSHA256 签名的不可变执行凭单。
     */
    public static VerifiedCodeActReceipt createSigned(
            String receiptId,
            String planId,
            CodeActExecutionPlan.ExecutionTier executionTier,
            String language,
            String scriptSha256,
            int exitCode,
            String stdout,
            String stderr,
            boolean truncated,
            boolean timedOut,
            long executionLatencyMicros,
            SandboxIsolationAttestation isolationAttestation,
            long timestampEpochMs,
            String secretKey
    ) {
        String safeOut = stdout != null ? stdout : "";
        String safeErr = stderr != null ? stderr : "";
        String outHash = VerifiedCodeActSandboxRuntime.sha256Hex(safeOut);
        String errHash = VerifiedCodeActSandboxRuntime.sha256Hex(safeErr);
        byte[] mac = computeMac(
                receiptId, planId, executionTier, language, scriptSha256,
                exitCode, outHash, errHash, truncated, timedOut,
                executionLatencyMicros, isolationAttestation, timestampEpochMs, secretKey
        );
        return new VerifiedCodeActReceipt(
                receiptId, planId, executionTier, language, scriptSha256,
                exitCode, safeOut, safeErr, outHash, errHash,
                truncated, timedOut, executionLatencyMicros, isolationAttestation,
                timestampEpochMs, mac
        );
    }

    /**
     * 常量时间防侧信道验签（同时校验外露 stdout/stderr 文本未被事后掉包）。
     */
    public boolean verifyMac(String secretKey) {
        String actualOutHash = VerifiedCodeActSandboxRuntime.sha256Hex(stdout);
        String actualErrHash = VerifiedCodeActSandboxRuntime.sha256Hex(stderr);
        if (!MessageDigest.isEqual(
                actualOutHash.getBytes(StandardCharsets.UTF_8),
                stdoutSha256.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        if (!MessageDigest.isEqual(
                actualErrHash.getBytes(StandardCharsets.UTF_8),
                stderrSha256.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        byte[] expected = computeMac(
                receiptId, planId, executionTier, language, scriptSha256,
                exitCode, stdoutSha256, stderrSha256, truncated, timedOut,
                executionLatencyMicros, isolationAttestation, timestampEpochMs, secretKey
        );
        return MessageDigest.isEqual(expected, hmacSignature);
    }

    @Override
    public byte[] hmacSignature() {
        return Arrays.copyOf(hmacSignature, hmacSignature.length);
    }

    private static byte[] computeMac(
            String receiptId,
            String planId,
            CodeActExecutionPlan.ExecutionTier tier,
            String language,
            String scriptSha256,
            int exitCode,
            String stdoutSha256,
            String stderrSha256,
            boolean truncated,
            boolean timedOut,
            long latencyMicros,
            SandboxIsolationAttestation attestation,
            long timestampEpochMs,
            String secretKey
    ) {
        String key = (secretKey == null || secretKey.isBlank()) ? "hermes-codeact-sandbox-secret" : secretKey;
        String payload = String.join("|",
                receiptId,
                planId,
                tier.name(),
                language,
                scriptSha256,
                String.valueOf(exitCode),
                stdoutSha256,
                stderrSha256,
                String.valueOf(truncated),
                String.valueOf(timedOut),
                String.valueOf(latencyMicros),
                attestation.isolationLevel(),
                attestation.effectiveLatticeLevel().name(),
                String.valueOf(attestation.readOnlyFsEnforced()),
                String.valueOf(attestation.networkIsolationEnforced()),
                String.valueOf(attestation.astInspectorPassed()),
                String.valueOf(timestampEpochMs)
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("计算 VerifiedCodeActReceipt HMAC 失败", e);
        }
    }
}
