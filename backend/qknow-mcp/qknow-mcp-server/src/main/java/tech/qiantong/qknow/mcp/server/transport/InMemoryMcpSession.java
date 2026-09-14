package tech.qiantong.qknow.mcp.server.transport;

import tech.qiantong.qknow.mcp.core.protocol.*;
import tech.qiantong.qknow.mcp.core.transport.McpSession;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 进程内/本地高保真内存 MCP 会话通道
 */
public class InMemoryMcpSession implements McpSession {

    private final String sessionId = UUID.randomUUID().toString();
    private final McpServerRegistry registry;
    private Consumer<McpMessage> listener;
    private volatile boolean open = true;

    public InMemoryMcpSession(McpServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public CompletableFuture<Void> send(McpMessage message) {
        if (!open) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session is closed"));
        }

        return CompletableFuture.runAsync(() -> {
            if (message instanceof JsonRpcRequest req) {
                JsonRpcResponse resp = registry.handleRequest(req);
                if (listener != null) {
                    listener.accept(resp);
                }
            }
        });
    }

    @Override
    public void setMessageListener(Consumer<McpMessage> listener) {
        this.listener = listener;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        this.open = false;
    }
}
