package tech.qiantong.qknow.hermes.tool.mcp.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 密码学工具投影存证凭单 (Java 21 Record)
 * 记录每次意图驱动的工具投影决策、Schema 压缩率与断路器状态快照，使用 SHA-256 自签名保证防篡改与合规可审计
 */
public record McpToolProjectionReceipt(
        String queryHash,
        List<String> projectedTools,
        int totalRegisteredTools,
        int projectedCount,
        double compressionRatio,
        Map<String, String> circuitBreakerStates,
        Instant timestamp,
        String signature
) {
    /**
     * 工厂方法：构建不可变存证凭单并生成密码学自签名
     *
     * @param userQuery            用户查询或上下文意图文本
     * @param projectedTools       经由超球面检索选中的工具标识列表
     * @param totalRegisteredTools 当前注册的总工具数
     * @param compressionRatio     Token 压缩率
     * @param circuitBreakerStates 当前选中工具的断路器状态映射
     * @return 不可变存证凭单
     */
    public static McpToolProjectionReceipt create(
            String userQuery,
            List<String> projectedTools,
            int totalRegisteredTools,
            double compressionRatio,
            Map<String, String> circuitBreakerStates
    ) {
        Instant now = Instant.now();
        String qHash = sha256(userQuery != null ? userQuery : "");
        List<String> safeTools = projectedTools != null ? List.copyOf(projectedTools) : List.of();
        Map<String, String> safeStates = circuitBreakerStates != null ? Map.copyOf(circuitBreakerStates) : Map.of();

        String rawContent = String.join("|",
                qHash,
                String.join(",", safeTools),
                String.valueOf(totalRegisteredTools),
                String.valueOf(safeTools.size()),
                String.format("%.4f", compressionRatio),
                safeStates.toString(),
                now.toString()
        );
        String sig = sha256(rawContent);

        return new McpToolProjectionReceipt(
                qHash,
                safeTools,
                totalRegisteredTools,
                safeTools.size(),
                compressionRatio,
                safeStates,
                now,
                sig
        );
    }

    /**
     * 校验凭单签名的有效性
     *
     * @return 签名是否匹配
     */
    public boolean verifySignature() {
        String rawContent = String.join("|",
                queryHash,
                String.join(",", projectedTools),
                String.valueOf(totalRegisteredTools),
                String.valueOf(projectedCount),
                String.format("%.4f", compressionRatio),
                circuitBreakerStates.toString(),
                timestamp.toString()
        );
        return sha256(rawContent).equals(signature);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
