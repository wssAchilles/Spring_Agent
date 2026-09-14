package tech.qiantong.qknow.mcp.core.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.core.protocol.McpMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 基于本地子进程标准 I/O (stdin/stdout) 的 MCP 传输会话通道（借鉴 claw-code mcp_stdio.rs）
 */
public class StdioMcpSession implements McpSession {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpSession.class);
    private final String sessionId = UUID.randomUUID().toString();
    private final ObjectMapper objectMapper;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Consumer<McpMessage> listener;
    private volatile boolean open = false;

    public StdioMcpSession() {
        this(new ObjectMapper());
    }

    public StdioMcpSession(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 启动子进程并建立标准输入输出通信
     */
    public synchronized void start(List<String> command, Map<String, String> env, File workingDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        if (workingDir != null) {
            pb.directory(workingDir);
        }
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.open = true;

        // 使用虚拟线程启动异步读取循环
        Thread.ofVirtual().name("stdio-mcp-reader-" + sessionId).start(this::readLoop);
    }

    private void readLoop() {
        try {
            String line;
            while (open && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JsonRpcResponse response = objectMapper.readValue(line, JsonRpcResponse.class);
                    if (listener != null) {
                        listener.accept(response);
                    }
                } catch (Exception e) {
                    log.warn("解析子进程 Stdio JSON-RPC 响应失败: {}", line, e);
                }
            }
        } catch (IOException e) {
            if (open) {
                log.warn("子进程 Stdio 管道读取中断", e);
            }
        } finally {
            close();
        }
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public CompletableFuture<Void> send(McpMessage message) {
        if (!isOpen()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Stdio session 未启动或已关闭"));
        }
        return CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(message);
                synchronized (writer) {
                    writer.write(json);
                    writer.newLine();
                    writer.flush();
                }
            } catch (Exception e) {
                throw new RuntimeException("发送消息至子进程失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void setMessageListener(Consumer<McpMessage> listener) {
        this.listener = listener;
    }

    @Override
    public boolean isOpen() {
        return open && process != null && process.isAlive();
    }

    @Override
    public synchronized void close() {
        if (!open) return;
        this.open = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
        } catch (IOException ignored) {}
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }
}
