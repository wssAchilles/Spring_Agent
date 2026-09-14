package tech.qiantong.qknow.mcp.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * JSON-RPC 2.0 单向通知对象 (无 id)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcNotification(
    @JsonProperty("jsonrpc") String jsonrpc,
    @JsonProperty("method") String method,
    @JsonProperty("params") Map<String, Object> params
) implements McpMessage {
    public JsonRpcNotification(String method, Map<String, Object> params) {
        this(JSONRPC_VERSION, method, params);
    }
}
