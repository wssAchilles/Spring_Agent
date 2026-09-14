package tech.qiantong.qknow.mcp.core.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 标准 JSON-RPC 2.0 错误体
 */
public record JsonRpcError(
    @JsonProperty("code") int code,
    @JsonProperty("message") String message,
    @JsonProperty("data") Object data
) {
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int SECURITY_VIOLATION = -32001; // 千知平台安全阻断专属错误码
}
