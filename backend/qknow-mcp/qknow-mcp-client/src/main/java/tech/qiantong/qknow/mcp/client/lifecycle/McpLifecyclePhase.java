package tech.qiantong.qknow.mcp.client.lifecycle;

/**
 * MCP 客户端全生命周期阶段枚举（借鉴 claw-code 11 阶段模型）
 */
public enum McpLifecyclePhase {
    CONFIG_LOAD("config_load"),
    SERVER_REGISTRATION("server_registration"),
    SPAWN_CONNECT("spawn_connect"),
    INITIALIZE_HANDSHAKE("initialize_handshake"),
    TOOL_DISCOVERY("tool_discovery"),
    RESOURCE_DISCOVERY("resource_discovery"),
    READY("ready"),
    INVOCATION("invocation"),
    ERROR_SURFACING("error_surfacing"),
    SHUTDOWN("shutdown"),
    CLEANUP("cleanup");

    private final String code;

    McpLifecyclePhase(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
