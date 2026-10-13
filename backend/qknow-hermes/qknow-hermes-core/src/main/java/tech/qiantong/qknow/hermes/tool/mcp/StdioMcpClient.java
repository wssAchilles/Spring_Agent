package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.mcp.client.transport.StdioEnterpriseTransport;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于企业级标准输入输出传输底座 (StdioEnterpriseTransport) 的 MCP JSON-RPC 客户端。
 * 具备双 Java 21 虚拟线程分别排空 stdout/stderr、滑动信用窗口背压控制 (W_max = 16) 与进程树递归强杀特性。
 */
@Slf4j
public class StdioMcpClient implements McpClient {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final List<String> command;
    private final Map<String, String> environment;
    private final StdioEnterpriseTransport enterpriseTransport;
    private final AtomicLong requestIds = new AtomicLong();

    // 针对内存 Reader/Writer 测试的兼容字段 (Fallback 模式)
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    public StdioMcpClient(List<String> command, Map<String, String> environment) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("MCP stdio command must not be empty");
        }
        this.command = List.copyOf(command);
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
        this.enterpriseTransport = new StdioEnterpriseTransport(
                UUID.randomUUID().toString(),
                this.command,
                this.environment,
                null,
                16,
                5000,
                new ObjectMapper()
        );
    }

    // 包内可见构造函数：供单测中使用 mock 流时作为 fallback 兼容
    StdioMcpClient(BufferedReader reader, BufferedWriter writer) {
        this.command = List.of();
        this.environment = Map.of();
        this.enterpriseTransport = null;
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public synchronized void initialize() throws McpException {
        if (enterpriseTransport != null) {
            try {
                enterpriseTransport.start();
                Map<String, Object> clientInfo = Map.of("name", "qknow-hermes", "version", "2.2.1");
                Map<String, Object> params = Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of(),
                        "clientInfo", clientInfo
                );
                JsonRpcResponse response = enterpriseTransport.sendRequest("initialize", params)
                        .get(10, TimeUnit.SECONDS);
                if (response.error() != null) {
                    throw new McpException("MCP 初始化被服务端拒绝: " + response.error().message());
                }
                enterpriseTransport.sendNotification("notifications/initialized", Map.of())
                        .get(5, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                disconnect();
                if (e instanceof McpException mcpException) {
                    throw mcpException;
                }
                throw new McpException("MCP 企业级 stdio 服务初始化失败: " + e.getMessage(), e);
            }
        }

        // Fallback 兼容流读写
        ensureFallbackConnected();
        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "qknow-hermes");
        clientInfo.put("version", "2.2.1");
        JSONObject params = new JSONObject();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", new JSONObject());
        params.put("clientInfo", clientInfo);
        sendFallbackRequest("initialize", params);
        sendFallbackNotification("notifications/initialized", new JSONObject());
    }

    @Override
    public synchronized List<JSONObject> listTools() throws McpException {
        if (enterpriseTransport != null) {
            try {
                JsonRpcResponse response = enterpriseTransport.sendRequest("tools/list", Map.of())
                        .get(10, TimeUnit.SECONDS);
                if (response.error() != null) {
                    throw new McpException("MCP 请求失败: " + response.error().message());
                }
                Object resultObj = response.result();
                if (resultObj == null) {
                    return List.of();
                }
                JSONObject resultJson = JSON.parseObject(JSON.toJSONString(resultObj));
                JSONArray tools = resultJson.getJSONArray("tools");
                return tools == null ? List.of() : tools.toJavaList(JSONObject.class);
            } catch (Exception e) {
                if (e instanceof McpException mcpException) {
                    throw mcpException;
                }
                throw new McpException("读取 MCP 工具列表失败: " + e.getMessage(), e);
            }
        }

        JSONObject result = sendFallbackRequest("tools/list", new JSONObject());
        JSONArray tools = result.getJSONArray("tools");
        return tools == null ? List.of() : tools.toJavaList(JSONObject.class);
    }

    @Override
    public synchronized JSONObject callTool(String toolName, Map<String, Object> arguments) throws McpException {
        if (enterpriseTransport != null) {
            try {
                Map<String, Object> params = Map.of(
                        "name", toolName,
                        "arguments", arguments == null ? Map.of() : arguments
                );
                JsonRpcResponse response = enterpriseTransport.sendRequest("tools/call", params)
                        .get(30, TimeUnit.SECONDS);
                if (response.error() != null) {
                    throw new McpException("MCP 请求失败: " + response.error().message());
                }
                Object resultObj = response.result();
                return resultObj == null ? new JSONObject() : JSON.parseObject(JSON.toJSONString(resultObj));
            } catch (Exception e) {
                if (e instanceof McpException mcpException) {
                    throw mcpException;
                }
                throw new McpException("调用 MCP 工具失败: " + toolName, e);
            }
        }

        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        return sendFallbackRequest("tools/call", params);
    }

    @Override
    public synchronized void disconnect() {
        if (enterpriseTransport != null) {
            enterpriseTransport.close();
        }

        closeQuietly(writer);
        closeQuietly(reader);
        writer = null;
        reader = null;

        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
    }

    @Override
    public boolean isConnected() {
        if (enterpriseTransport != null) {
            return enterpriseTransport.isConnected();
        }
        return process != null && process.isAlive() && writer != null && reader != null;
    }

    // ---------------------- Fallback 兼容模式辅助实现 ----------------------

    private void ensureFallbackConnected() {
        if (reader != null && writer != null) {
            return;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().putAll(environment);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            disconnect();
            throw new McpException("无法启动 MCP stdio 服务: " + command.get(0), e);
        }
    }

    private JSONObject sendFallbackRequest(String method, JSONObject params) {
        ensureFallbackConnected();
        long id = requestIds.incrementAndGet();
        JSONObject request = baseMessage(method, params);
        request.put("id", id);
        writeFallbackMessage(request);

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject response = JSON.parseObject(line);
                if (!Long.valueOf(id).equals(response.getLong("id"))) {
                    continue;
                }
                JSONObject error = response.getJSONObject("error");
                if (error != null) {
                    throw new McpException("MCP 请求失败: " + error.getString("message"));
                }
                JSONObject result = response.getJSONObject("result");
                return result == null ? new JSONObject() : result;
            }
            throw new McpException("MCP stdio 服务已断开");
        } catch (IOException | RuntimeException e) {
            if (e instanceof McpException mcpException) {
                throw mcpException;
            }
            throw new McpException("读取 MCP 响应失败", e);
        }
    }

    private void sendFallbackNotification(String method, JSONObject params) {
        writeFallbackMessage(baseMessage(method, params));
    }

    private JSONObject baseMessage(String method, JSONObject params) {
        JSONObject message = new JSONObject();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        return message;
    }

    private void writeFallbackMessage(JSONObject message) {
        try {
            writer.write(message.toJSONString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new McpException("发送 MCP 请求失败", e);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 优雅关闭尽力而为
        }
    }
}
