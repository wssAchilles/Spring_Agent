package tech.qiantong.qknow.mcp.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * JSON-RPC 2.0 请求对象
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
    @JsonProperty("jsonrpc") String jsonrpc,
    @JsonProperty("id") Object id,
    @JsonProperty("method") String method,
    @JsonProperty("params") Map<String, Object> params
) implements McpMessage {
    public JsonRpcRequest(Object id, String method, Map<String, Object> params) {
        this(JSONRPC_VERSION, id, method, params);
    }
}
