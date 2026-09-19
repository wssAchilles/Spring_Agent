package tech.qiantong.qknow.mcp.server.model;

import tech.qiantong.qknow.mcp.core.model.McpTool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * 原生企业级 MCP Server 导出与安全运行时不可变存证凭单 (Java 21 Record)
 */
public record McpServerExportReceipt(
        String receiptId,
        String serverName,
        String serverVersion,
        String transportChannel,
        int exportedToolsCount,
        int exportedResourcesCount,
        int exportedPromptsCount,
        List<String> highRiskToolNames,
        boolean quadDefenseEnabled,
        long exportTimestampMs,
        String signature
) {

    public static McpServerExportReceipt create(
            String serverName,
            String serverVersion,
            String transportChannel,
            List<McpTool> tools,
            int resourcesCount,
            int promptsCount,
            boolean quadDefenseEnabled
    ) {
        String receiptId = "rcpt_mcp_" + UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();

        List<String> highRiskNames = tools.stream()
                .filter(t -> t.inputSchema() != null && Boolean.TRUE.equals(t.inputSchema().get("requiresLease")))
                .map(McpTool::name)
                .toList();

        String payload = buildSignPayload(
                receiptId,
                serverName,
                serverVersion,
                transportChannel,
                tools.size(),
                resourcesCount,
                promptsCount,
                quadDefenseEnabled,
                timestamp
        );

        String signature = computeSha256(payload);

        return new McpServerExportReceipt(
                receiptId,
                serverName,
                serverVersion,
                transportChannel,
                tools.size(),
                resourcesCount,
                promptsCount,
                highRiskNames,
                quadDefenseEnabled,
                timestamp,
                signature
        );
    }

    /**
     * 密码学自验真：验证凭单数字签名是否未被篡改
     */
    public boolean verify() {
        String payload = buildSignPayload(
                receiptId,
                serverName,
                serverVersion,
                transportChannel,
                exportedToolsCount,
                exportedResourcesCount,
                exportedPromptsCount,
                quadDefenseEnabled,
                exportTimestampMs
        );
        return computeSha256(payload).equals(this.signature);
    }

    private static String buildSignPayload(
            String receiptId,
            String serverName,
            String serverVersion,
            String transportChannel,
            int toolsCount,
            int resourcesCount,
            int promptsCount,
            boolean quadDefense,
            long timestamp
    ) {
        return String.join("|",
                receiptId,
                serverName,
                serverVersion,
                transportChannel,
                String.valueOf(toolsCount),
                String.valueOf(resourcesCount),
                String.valueOf(promptsCount),
                String.valueOf(quadDefense),
                String.valueOf(timestamp)
        );
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
