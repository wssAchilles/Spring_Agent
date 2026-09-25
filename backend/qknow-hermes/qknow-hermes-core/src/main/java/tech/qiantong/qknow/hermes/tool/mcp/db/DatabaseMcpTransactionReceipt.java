package tech.qiantong.qknow.hermes.tool.mcp.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 数据库 MCP 工具事务执行与沙箱安全审计存证凭单 (Phase 139 存证防线)
 * <p>
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间防篡改验真。
 * 记录租户标识、原始 SQL、沙箱重写 SQL、操作类型、Undo Log 计数、是否触发回滚、耗时与时间戳。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record DatabaseMcpTransactionReceipt(
        String receiptId,
        String tenantId,
        String originalSql,
        String rewrittenSql,
        String operationType,
        int undoLogCount,
        boolean isRolledBack,
        double latencyMs,
        long timestamp,
        String sha256Signature
) {
    public DatabaseMcpTransactionReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(originalSql, "originalSql 不能为空");
        Objects.requireNonNull(rewrittenSql, "rewrittenSql 不能为空");
        Objects.requireNonNull(operationType, "operationType 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 静态工厂方法，自动计算 SHA-256 密码学防篡改签名
     */
    public static DatabaseMcpTransactionReceipt create(
            String tenantId,
            String originalSql,
            String rewrittenSql,
            String operationType,
            int undoLogCount,
            boolean isRolledBack,
            double latencyMs
    ) {
        String receiptId = "RCP-DB-TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        long timestamp = System.currentTimeMillis();
        String safeTenant = tenantId != null ? tenantId : "DEFAULT_TENANT";
        String safeOrig = originalSql != null ? originalSql : "";
        String safeRewritten = rewrittenSql != null ? rewrittenSql : "";
        String safeOp = operationType != null ? operationType : "UNKNOWN";

        String canonicalPayload = buildCanonicalPayload(
                receiptId, safeTenant, safeOrig, safeRewritten, safeOp,
                undoLogCount, isRolledBack, latencyMs, timestamp
        );
        String signature = computeSha256(canonicalPayload);

        return new DatabaseMcpTransactionReceipt(
                receiptId,
                safeTenant,
                safeOrig,
                safeRewritten,
                safeOp,
                undoLogCount,
                isRolledBack,
                latencyMs,
                timestamp,
                signature
        );
    }

    /**
     * 验证凭单 SHA-256 签名，采用常量时间比较杜绝时序攻击
     */
    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(
                receiptId, tenantId, originalSql, rewrittenSql, operationType,
                undoLogCount, isRolledBack, latencyMs, timestamp
        );
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                sha256Signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String tenantId,
            String origSql,
            String rewrittenSql,
            String op,
            int logCount,
            boolean rolledBack,
            double latencyMs,
            long timestamp
    ) {
        return receiptId + "|" +
                tenantId + "|" +
                computeSha256(origSql) + "|" +
                computeSha256(rewrittenSql) + "|" +
                op + "|" +
                logCount + "|" +
                rolledBack + "|" +
                String.format(Locale.ROOT, "%.4f", latencyMs) + "|" +
                timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法在标准 JVM 中不可用", e);
        }
    }
}
