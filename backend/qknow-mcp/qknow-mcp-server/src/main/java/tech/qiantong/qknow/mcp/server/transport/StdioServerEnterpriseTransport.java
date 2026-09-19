package tech.qiantong.qknow.mcp.server.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcRequest;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;
import tech.qiantong.qknow.mcp.server.security.QuadDefenseSecurityPipeline;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 生产级标准输入输出 (Stdio) 服务端隔离传输栈
 * 严格限制单行 16KB 截断、避免污染 stdout、多线程安全
 */
public class StdioServerEnterpriseTransport implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(StdioServerEnterpriseTransport.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final McpServerRegistry registry;
    private final QuadDefenseSecurityPipeline securityPipeline;
    private final InputStream in;
    private final PrintStream out;
    private final ExecutorService workerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StdioServerEnterpriseTransport(McpServerRegistry registry, QuadDefenseSecurityPipeline securityPipeline) {
        this(registry, securityPipeline, System.in, System.out);
    }

    public StdioServerEnterpriseTransport(
            McpServerRegistry registry,
            QuadDefenseSecurityPipeline securityPipeline,
            InputStream in,
            PrintStream out
    ) {
        this.registry = registry;
        this.securityPipeline = securityPipeline;
        this.in = in;
        this.out = out;
        this.workerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mcp-stdio-server-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            workerExecutor.submit(this::processInputLoop);
            log.info("[MCP Stdio Server] 服务端标准传输通道已启动");
        }
    }

    private void processInputLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
                try {
                    // 防线一：物理大小强校验
                    if (securityPipeline != null) {
                        securityPipeline.validatePayloadSize(lineBytes);
                    }

                    JsonRpcRequest request = objectMapper.readValue(line, JsonRpcRequest.class);
                    JsonRpcResponse response = registry.handleRequest(request);

                    synchronized (out) {
                        String respJson = objectMapper.writeValueAsString(response);
                        out.println(respJson);
                        out.flush();
                    }
                } catch (QuadDefenseSecurityPipeline.SecurityPipelineException spe) {
                    JsonRpcResponse errResp = JsonRpcResponse.error(null, spe.getErrorCode(), spe.getMessage(), null);
                    synchronized (out) {
                        out.println(objectMapper.writeValueAsString(errResp));
                        out.flush();
                    }
                } catch (Exception ex) {
                    log.error("[MCP Stdio Server] 报文解析处理异常: {}", ex.getMessage(), ex);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("[MCP Stdio Server] 读取标准输入失败: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            workerExecutor.shutdownNow();
            log.info("[MCP Stdio Server] 服务端传输通道已安全关闭");
        }
    }
}
