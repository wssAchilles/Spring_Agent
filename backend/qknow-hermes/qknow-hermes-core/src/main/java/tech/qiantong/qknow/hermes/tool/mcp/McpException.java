package tech.qiantong.qknow.hermes.tool.mcp;

/**
 * MCP 协议异常
 */
public class McpException extends RuntimeException {

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}
