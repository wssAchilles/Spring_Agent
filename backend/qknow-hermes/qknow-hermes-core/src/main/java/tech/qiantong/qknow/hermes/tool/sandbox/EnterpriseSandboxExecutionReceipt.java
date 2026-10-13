package tech.qiantong.qknow.hermes.tool.sandbox;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 企业级生产容器代码沙箱执行不可变存证凭单 (纯 Java 21 Record)
 * 具备资源使用度量、硬配额截断标志与常量时间 SHA-256 存证封印
 *
 * @author Achilles
 * @since 2026-10-13
 */
public record EnterpriseSandboxExecutionReceipt(
        String receiptId,
        String runtimeId,
        String scriptLanguage,       // python, bash, sh
        String sanitizedScript,
        int exitCode,
        String stdout,
        String stderr,
        long executionLatencyMs,
        long cpuTimeMicros,
        long peakMemoryBytes,
        boolean timedOut,            // 是否触发看门狗硬超时强杀
        boolean memoryLimitExceeded, // 是否超出内存配额 (cgroup/rlimit OOM)
        boolean readOnlyProtected,   // 是否成功实施只读文件系统保护
        boolean networkIsolated,     // 是否完全切断网络出站
        long timestampEpochMs,
        String securitySealSha256
) implements Serializable {

    public EnterpriseSandboxExecutionReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(scriptLanguage, "scriptLanguage 不能为空");
        Objects.requireNonNull(securitySealSha256, "securitySealSha256 不能为空");
    }

    /**
     * 工厂方法：计算不可变 SHA-256 存证封印并构造凭单
     */
    public static EnterpriseSandboxExecutionReceipt create(
            String receiptId,
            String runtimeId,
            String scriptLanguage,
            String script,
            int exitCode,
            String stdout,
            String stderr,
            long latencyMs,
            long cpuMicros,
            long peakMemBytes,
            boolean timedOut,
            boolean memExceeded,
            boolean roProtected,
            boolean netIsolated
    ) {
        long now = System.currentTimeMillis();
        String payload = String.format("%s|%s|%s|%d|%d|%d|%d|%b|%b|%b|%b|%d",
                receiptId, runtimeId, scriptLanguage, exitCode, latencyMs,
                cpuMicros, peakMemBytes, timedOut, memExceeded, roProtected, netIsolated, now);
        String seal = computeSha256(payload);
        return new EnterpriseSandboxExecutionReceipt(
                receiptId, runtimeId, scriptLanguage, script, exitCode, stdout, stderr,
                latencyMs, cpuMicros, peakMemBytes, timedOut, memExceeded, roProtected, netIsolated, now, seal
        );
    }

    /**
     * 常量时间防侧信道验签
     */
    public boolean verifySecuritySeal() {
        String payload = String.format("%s|%s|%s|%d|%d|%d|%d|%b|%b|%b|%b|%d",
                receiptId, runtimeId, scriptLanguage, exitCode, executionLatencyMs,
                cpuTimeMicros, peakMemoryBytes, timedOut, memoryLimitExceeded, readOnlyProtected, networkIsolated, timestampEpochMs);
        byte[] expected = computeSha256(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = securitySealSha256.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算异常", e);
        }
    }
}
