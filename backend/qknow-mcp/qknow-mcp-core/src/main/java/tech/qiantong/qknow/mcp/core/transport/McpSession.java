package tech.qiantong.qknow.mcp.core.transport;

import tech.qiantong.qknow.mcp.core.protocol.McpMessage;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * MCP 会话通道接口
 */
public interface McpSession extends Closeable {
    String getSessionId();
    CompletableFuture<Void> send(McpMessage message);
    void setMessageListener(Consumer<McpMessage> listener);
    boolean isOpen();
}
