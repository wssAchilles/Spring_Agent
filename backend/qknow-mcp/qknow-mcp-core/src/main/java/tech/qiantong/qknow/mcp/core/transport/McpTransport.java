package tech.qiantong.qknow.mcp.core.transport;

import java.util.function.Consumer;

/**
 * MCP 传输层 SPI
 */
public interface McpTransport {
    void start(Consumer<McpSession> sessionAcceptor) throws Exception;
    void stop();
}
