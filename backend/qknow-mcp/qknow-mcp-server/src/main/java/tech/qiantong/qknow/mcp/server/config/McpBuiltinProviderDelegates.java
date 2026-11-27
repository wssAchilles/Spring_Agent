package tech.qiantong.qknow.mcp.server.config;

import tech.qiantong.qknow.mcp.core.model.CallToolResult;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * MCP Server 四大内置工具业务执行委托聚合契约（纯 Java 21 不可变 Record）。
 * <p>
 * 用于消除 {@link McpServerAutoConfiguration} 无参构造导致的内置工具“未绑定”空壳问题，
 * 支持由上层业务模块（KMC / KG / DM / Sandbox）按需通过 Spring 容器注入真实服务委托。
 * </p>
 */
public record McpBuiltinProviderDelegates(
        Function<Map<String, Object>, CallToolResult> knowledgeBaseSearchDelegate,
        Function<String, CallToolResult> knowledgeGraphQueryDelegate,
        BiFunction<String, String, CallToolResult> sqlExecutorDelegate,
        BiFunction<String, String, CallToolResult> sandboxRunnerDelegate
) {
    public static McpBuiltinProviderDelegates empty() {
        return new McpBuiltinProviderDelegates(null, null, null, null);
    }
}
