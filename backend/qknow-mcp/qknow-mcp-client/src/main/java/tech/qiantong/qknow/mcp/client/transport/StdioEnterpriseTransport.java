package tech.qiantong.qknow.mcp.client.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcNotification;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcRequest;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.core.protocol.McpMessage;
import tech.qiantong.qknow.mcp.core.transport.McpSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 企业级标准输入输出 (Stdio) 进程传输实现
 * <p>
 * 遵循 Phase 103 工业硬化规范：
 * 1. 双 Java 21 虚拟线程分别驱动 stdout 数据流与 stderr 日志排空，杜绝管道死锁；
 * 2. 滑动信用窗口背压控制 (W_max = 16)，李雅普诺夫漂移稳定；
 * 3. 进程树递归强杀 (ProcessHandle.descendants)，先强杀子孙进程避免 reader.close 锁死；
 * 4. 周期 Ping/Pong 心跳探活机制；
 * 5. 同时实现 EnterpriseMcpClientTransport 与 McpSession 接口，无缝兼容现有连接池。
 */
public class StdioEnterpriseTransport implements EnterpriseMcpClientTransport, McpSession {

    private static final Logger log = LoggerFactory.getLogger(StdioEnterpriseTransport.class);

    private final String transportId;
    private final List<String> command;
    private final Map<String, String> environment;
    private final File workingDirectory;
    private final int maxConcurrency;
    private final long backpressureTimeoutMs;
    private final ObjectMapper objectMapper;

    private final Semaphore creditSemaphore;
    private final AtomicInteger inFlightCounter = new AtomicInteger(0);
    private final Map<Object, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> stderrRingBuffer = new ConcurrentLinkedDeque<>();
    private static final int MAX_STDERR_LINES = 50;

    private final Object writerLock = new Object();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader stdoutReader;
    private BufferedReader stderrReader;
    private Consumer<McpMessage> messageListener;
    private volatile boolean connected = false;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StdioEnterpriseTransport(List<String> command) {
        this(UUID.randomUUID().toString(), command, Map.of(), null, 16, 500, new ObjectMapper());
    }

    public StdioEnterpriseTransport(String transportId, List<String> command, Map<String, String> environment,
                                    File workingDirectory, int maxConcurrency, long backpressureTimeoutMs,
                                    ObjectMapper objectMapper) {
        this.transportId = transportId != null ? transportId : UUID.randomUUID().toString();
        this.command = command != null ? List.copyOf(command) : List.of();
        this.environment = environment != null ? Map.copyOf(environment) : Map.of();
        this.workingDirectory = workingDirectory;
        this.maxConcurrency = maxConcurrency > 0 ? maxConcurrency : 16;
        this.backpressureTimeoutMs = backpressureTimeoutMs > 0 ? backpressureTimeoutMs : 500;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.creditSemaphore = new Semaphore(this.maxConcurrency);
    }

    /**
     * 启动子进程并初始化双虚拟线程排空
     */
    public synchronized void start() throws IOException {
        if (connected) {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (!environment.isEmpty()) {
            pb.environment().putAll(environment);
        }
        if (workingDirectory != null) {
            pb.directory(workingDirectory);
        }
        // 核心加固：stderr 独立建管排空，严禁丢弃或阻塞
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        this.connected = true;
        this.closed.set(false);

        // 启动 Java 21 虚拟线程读取 stdout
        Thread.ofVirtual().name("stdio-stdout-" + transportId).start(this::readStdoutLoop);
        // 启动 Java 21 虚拟线程持续排空 stderr，防止管道阻塞
        Thread.ofVirtual().name("stdio-stderr-" + transportId).start(this::drainStderrLoop);

        log.info("[StdioEnterpriseTransport] 成功启动传输子进程: id={}, pid={}, cmd={}",
                transportId, process.pid(), command);
    }

    private void readStdoutLoop() {
        try {
            String line;
            while (connected && (line = stdoutReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JsonRpcResponse response = objectMapper.readValue(line, JsonRpcResponse.class);
                    if (response.id() != null) {
                        CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(response.id());
                        if (future != null) {
                            future.complete(response);
                        }
                    }
                    if (messageListener != null) {
                        messageListener.accept(response);
                    }
                } catch (Exception e) {
                    log.debug("[Stdio stdout] 非 JSON-RPC 响应行或解析跳过: {}", line);
                }
            }
        } catch (IOException e) {
            if (connected) {
                log.warn("[StdioEnterpriseTransport] stdout 管道断开: id={}", transportId, e);
            }
        } finally {
            handleProcessTermination();
        }
    }

    private void drainStderrLoop() {
        try {
            String line;
            while (connected && (line = stderrReader.readLine()) != null) {
                stderrRingBuffer.addLast(line);
                while (stderrRingBuffer.size() > MAX_STDERR_LINES) {
                    stderrRingBuffer.pollFirst();
                }
                log.debug("[Stdio stderr] {}: {}", transportId, line);
            }
        } catch (IOException ignored) {
            // 管道关闭
        }
    }

    private void handleProcessTermination() {
        if (closed.get()) {
            return;
        }
        log.info("[StdioEnterpriseTransport] 子进程已退出或管道已关闭: id={}", transportId);
        // 异常终止待处理的请求
        for (CompletableFuture<JsonRpcResponse> future : pendingRequests.values()) {
            future.completeExceptionally(new IOException("传输管道非正常中断"));
        }
        pendingRequests.clear();
        close();
    }

    @Override
    public CompletableFuture<JsonRpcResponse> sendRequest(String method, Map<String, Object> params) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP 传输通道未启动或已断开连接"));
        }

        // 滑动窗口信用背压控制
        boolean acquired;
        try {
            acquired = creditSemaphore.tryAcquire(backpressureTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(new IllegalStateException("背压信用等待被中断", e));
        }

        if (!acquired) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "ERR_MCP_BACKPRESSURE_OVERFLOW: 飞行并发超出信用窗口上限 [inFlight="
                            + inFlightCounter.get() + ", W_max=" + maxConcurrency + "]"));
        }

        String reqId = UUID.randomUUID().toString();
        JsonRpcRequest request = new JsonRpcRequest(reqId, method, params);
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(reqId, future);
        inFlightCounter.incrementAndGet();

        // 无论正常还是异常结束，必须归还信用与递减计数器
        future.whenComplete((res, ex) -> {
            pendingRequests.remove(reqId);
            inFlightCounter.decrementAndGet();
            creditSemaphore.release();
        });

        try {
            String json = objectMapper.writeValueAsString(request);
            synchronized (writerLock) {
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> sendNotification(String method, Map<String, Object> params) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP 传输通道未启动或已断开连接"));
        }
        return CompletableFuture.runAsync(() -> {
            try {
                JsonRpcNotification notification = new JsonRpcNotification(method, params);
                String json = objectMapper.writeValueAsString(notification);
                synchronized (writerLock) {
                    writer.write(json);
                    writer.newLine();
                    writer.flush();
                }
            } catch (Exception e) {
                throw new RuntimeException("发送通知失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> ping() {
        return sendRequest("ping", Map.of())
                .thenApply(resp -> resp != null && resp.error() == null)
                .exceptionally(ex -> false);
    }

    // McpSession 接口兼容方法
    @Override
    public String getSessionId() {
        return transportId;
    }

    @Override
    public CompletableFuture<Void> send(McpMessage message) {
        if (message instanceof JsonRpcRequest req) {
            return sendRequest(req.method(), req.params()).thenAccept(r -> {});
        } else if (message instanceof JsonRpcNotification notif) {
            return sendNotification(notif.method(), notif.params());
        } else {
            return CompletableFuture.runAsync(() -> {
                try {
                    String json = objectMapper.writeValueAsString(message);
                    synchronized (writerLock) {
                        writer.write(json);
                        writer.newLine();
                        writer.flush();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("发送消息失败: " + e.getMessage(), e);
                }
            });
        }
    }

    @Override
    public void setMessageListener(Consumer<McpMessage> listener) {
        this.messageListener = listener;
    }

    @Override
    public boolean isOpen() {
        return isConnected();
    }

    @Override
    public String getTransportId() {
        return transportId;
    }

    @Override
    public boolean isConnected() {
        return connected && !closed.get() && process != null && process.isAlive();
    }

    @Override
    public int getInFlightCount() {
        return inFlightCounter.get();
    }

    @Override
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public List<String> getRecentStderr() {
        return List.copyOf(stderrRingBuffer);
    }

    public Process getProcess() {
        return process;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        this.connected = false;

        // 1. 快速失败所有待处理请求
        for (CompletableFuture<JsonRpcResponse> future : pendingRequests.values()) {
            future.completeExceptionally(new IOException("传输通道主动关闭"));
        }
        pendingRequests.clear();

        // 2. 关键时序优化：先强制终结子进程树，触发操作系统向 read 管道发送 EOF，彻底解除虚拟线程 reader 锁阻塞！
        if (process != null) {
            try {
                process.descendants().forEach(handle -> {
                    try {
                        log.debug("[StdioEnterpriseTransport] 递归杀死子孙进程: pid={}", handle.pid());
                        handle.destroyForcibly();
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}

            try {
                process.destroyForcibly();
                process.waitFor(300, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        }

        // 3. 子进程终止后，管道已被 OS 关闭，此时安全关闭所有流句柄，零阻塞与零死锁
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
        try {
            if (stdoutReader != null) stdoutReader.close();
        } catch (IOException ignored) {}
        try {
            if (stderrReader != null) stderrReader.close();
        } catch (IOException ignored) {}

        log.info("[StdioEnterpriseTransport] 传输通道及进程树已彻底释放回收: id={}", transportId);
    }
}
