package tech.qiantong.qknow.hermes.agent.harness;

/**
 * MCP/工具中间件切面契约。
 */
public record McpMiddlewarePluginsRecord(
        boolean dataMaskingEnabled,
        boolean sagasCompensationEnabled,
        boolean auditLoggingEnabled
) {
    public String canonical() {
        return dataMaskingEnabled + "\u001f" + sagasCompensationEnabled + "\u001f" + auditLoggingEnabled;
    }

    public static McpMiddlewarePluginsRecord defaults() {
        return new McpMiddlewarePluginsRecord(true, false, true);
    }
}
