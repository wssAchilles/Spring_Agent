package tech.qiantong.qknow.mcp.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 客户端元数据标识（借鉴 claw-code McpInitializeClientInfo）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpClientInfo(
        String name,
        String version
) {
}
