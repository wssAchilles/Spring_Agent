package tech.qiantong.qknow.hermes.tool.mcp.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 136 核心资产：企业级 MCP 零信任网关不可变密码学审计存证凭单 (McpZeroTrustGatewayReceipt)
 * 落实 Phase 136 规范与零信任安全架构：
 * 1. 纯 Java 21 Record 格式，零对象逃逸与强不可变类型
 * 2. 完整记录多租户配额状态、断路器熔断状态、脱敏命中敏感字段列表
 * 3. 自包含 SHA-256 签名计算与 MessageDigest.isEqual 常量时间自验真 verifySignature()
 *
 * @author Achilles
 * @since 2026-09-25
 */
public record McpZeroTrustGatewayReceipt(
        String receiptId,
        String tenantId,
        String toolName,
        String quotaState,
        String circuitState,
        List<String> maskedFields,
        long timestamp,
        String sha256Signature
) {
    public McpZeroTrustGatewayReceipt {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(quotaState, "quotaState must not be null");
        Objects.requireNonNull(circuitState, "circuitState must not be null");
        Objects.requireNonNull(sha256Signature, "sha256Signature must not be null");
        maskedFields = maskedFields != null ? List.copyOf(maskedFields) : List.of();
    }

    /**
     * 计算全字段规范化 SHA-256 防篡改签名
     */
    public static String calculateSignature(
            String receiptId,
            String tenantId,
            String toolName,
            String quotaState,
            String circuitState,
            List<String> maskedFields,
            long timestamp
    ) {
        String sortedMaskedFields = maskedFields != null ? String.join(",", maskedFields.stream().sorted().toList()) : "";
        String canonicalPayload = String.join("|",
                receiptId,
                tenantId,
                toolName,
                quotaState,
                circuitState,
                sortedMaskedFields,
                String.valueOf(timestamp)
        );
        return computeSha256Hex(canonicalPayload);
    }

    /**
     * 工厂方法：创建并自签发凭单
     */
    public static McpZeroTrustGatewayReceipt create(
            String tenantId,
            String toolName,
            String quotaState,
            String circuitState,
            List<String> maskedFields
    ) {
        long ts = System.currentTimeMillis();
        String receiptId = "RCP-MCP-GW-" + ts + "-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> safeMaskedFields = maskedFields != null ? List.copyOf(maskedFields) : List.of();
        String signature = calculateSignature(
                receiptId,
                tenantId,
                toolName,
                quotaState,
                circuitState,
                safeMaskedFields,
                ts
        );
        return new McpZeroTrustGatewayReceipt(
                receiptId,
                tenantId,
                toolName,
                quotaState,
                circuitState,
                safeMaskedFields,
                ts,
                signature
        );
    }

    /**
     * 基于 MessageDigest.isEqual 的常量时间自验真 (防范侧信道时序分析攻击)
     */
    public boolean verifySignature() {
        String expected = calculateSignature(
                this.receiptId,
                this.tenantId,
                this.toolName,
                this.quotaState,
                this.circuitState,
                this.maskedFields,
                this.timestamp
        );
        return MessageDigest.isEqual(
                this.sha256Signature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }
}
