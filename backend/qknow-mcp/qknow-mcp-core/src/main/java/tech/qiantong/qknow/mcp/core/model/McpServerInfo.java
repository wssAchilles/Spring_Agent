package tech.qiantong.qknow.mcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端元数据标识（借鉴 claw-code McpInitializeServerInfo）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerInfo(
        String name,
        String version
) {
}
