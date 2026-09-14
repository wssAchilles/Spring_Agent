package tech.qiantong.qknow.mcp.core.protocol;

/**
 * MCP 协议顶级密封消息接口 (Java 21 Sealed Interface)
 * 严格基于 JSON-RPC 2.0 规范，保证协议不可变与类型穷举检查
 */
public sealed interface McpMessage permits JsonRpcRequest, JsonRpcResponse, JsonRpcNotification {
    String JSONRPC_VERSION = "2.0";
}
