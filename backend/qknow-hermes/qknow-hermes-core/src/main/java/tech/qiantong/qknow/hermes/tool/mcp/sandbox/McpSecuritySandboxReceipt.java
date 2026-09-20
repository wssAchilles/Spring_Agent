package tech.qiantong.qknow.hermes.tool.mcp.sandbox;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Phase 122 核心资产：不可变 MCP 安全沙箱存证凭单 (McpSecuritySandboxReceipt)
 * 纯 Java 21 Record 格式，固化工具沙箱执行、多租户零信任鉴权与流控判定全量因果证据
 * 内置基于 HMAC-SHA256 的密码学自签名与常量时间抗侧信道自验真方法
 */
public record McpSecuritySandboxReceipt(
        String receiptId,
        String sandboxId,
        String tenantId,
        String toolName,
        String callerAgent,
        String executionStatus,
        long latencyMicros,
        String blockedReason,
        long quotaRemaining,
        long timestampEpochMs,
        String sha256Signature
) {

    // 默认空阻断原因占位符
    public static final String NONE_BLOCKED_REASON = "NONE";

    // 状态常量定义
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_BLOCKED_UNAUTHORIZED = "BLOCKED_UNAUTHORIZED";
    public static final String STATUS_BLOCKED_RATE_LIMIT = "BLOCKED_RATE_LIMIT";
    public static final String STATUS_BLOCKED_INSPECTION = "BLOCKED_INSPECTION";
    public static final String STATUS_FAILED_TIMEOUT = "FAILED_TIMEOUT";
    public static final String STATUS_FAILED_EXECUTION = "FAILED_EXECUTION";

    public McpSecuritySandboxReceipt {
        Objects.requireNonNull(receiptId, "凭单 ID 不能为空");
        Objects.requireNonNull(sandboxId, "沙箱 ID 不能为空");
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(toolName, "工具名称不能为空");
        Objects.requireNonNull(callerAgent, "调用智能体标识不能为空");
        Objects.requireNonNull(executionStatus, "执行状态不能为空");
        Objects.requireNonNull(blockedReason, "阻断原因不能为空");
        Objects.requireNonNull(sha256Signature, "签名不能为空");
    }

    /**
     * 静态工厂方法：构建不可变存证凭单并自动完成 HMAC-SHA256 密码学签名
     *
     * @param receiptId        凭单唯一标识
     * @param sandboxId        沙箱执行环境标识
     * @param tenantId         租户标识
     * @param toolName         工具名称
     * @param callerAgent      调用智能体标识
     * @param executionStatus  执行判定状态
     * @param latencyMicros    执行耗时 (微秒)
     * @param blockedReason    阻断原因 (通过时为 NONE)
     * @param quotaRemaining   剩余可用配额
     * @param timestampEpochMs 时间戳毫秒
     * @param secretKey        防伪签名密钥
     * @return 签名完成的不可变凭单实例
     */
    public static McpSecuritySandboxReceipt create(
            String receiptId,
            String sandboxId,
            String tenantId,
            String toolName,
            String callerAgent,
            String executionStatus,
            long latencyMicros,
            String blockedReason,
            long quotaRemaining,
            long timestampEpochMs,
            String secretKey
    ) {
        String effectiveReason = (blockedReason != null && !blockedReason.isBlank()) ? blockedReason : NONE_BLOCKED_REASON;
        String rawPayload = buildPayloadString(receiptId, sandboxId, tenantId, toolName, callerAgent,
                executionStatus, latencyMicros, effectiveReason, quotaRemaining, timestampEpochMs);
        String signature = computeHmacSha256(rawPayload, secretKey);
        return new McpSecuritySandboxReceipt(
                receiptId, sandboxId, tenantId, toolName, callerAgent, executionStatus,
                latencyMicros, effectiveReason, quotaRemaining, timestampEpochMs, signature
        );
    }

    /**
     * 常量时间自验真方法：检验凭单因果字段是否遭受非法篡改
     * 采用 MessageDigest.isEqual 消除时序分析侧信道攻击风险
     *
     * @param secretKey 防伪验真密钥
     * @return true-完整合法未篡改, false-已被篡改或密钥错误
     */
    public boolean verifySignature(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String expectedPayload = buildPayloadString(receiptId, sandboxId, tenantId, toolName, callerAgent,
                executionStatus, latencyMicros, blockedReason, quotaRemaining, timestampEpochMs);
        String expectedSignature = computeHmacSha256(expectedPayload, secretKey);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String buildPayloadString(
            String receiptId, String sandboxId, String tenantId, String toolName, String callerAgent,
            String executionStatus, long latencyMicros, String blockedReason, long quotaRemaining, long timestampEpochMs
    ) {
        return receiptId + ":" + sandboxId + ":" + tenantId + ":" + toolName + ":" + callerAgent + ":"
                + executionStatus + ":" + latencyMicros + ":" + blockedReason + ":" + quotaRemaining + ":" + timestampEpochMs;
    }

    private static String computeHmacSha256(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名计算异常", e);
        }
    }
}
