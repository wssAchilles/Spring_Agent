package tech.qiantong.qknow.mcp.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-RPC 2.0 响应对象
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
    @JsonProperty("jsonrpc") String jsonrpc,
    @JsonProperty("id") Object id,
    @JsonProperty("result") Object result,
    @JsonProperty("error") JsonRpcError error
) implements McpMessage {
    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse(JSONRPC_VERSION, id, result, null);
    }

    public static JsonRpcResponse error(Object id, int code, String message, Object data) {
        return new JsonRpcResponse(JSONRPC_VERSION, id, null, new JsonRpcError(code, message, data));
    }
}
