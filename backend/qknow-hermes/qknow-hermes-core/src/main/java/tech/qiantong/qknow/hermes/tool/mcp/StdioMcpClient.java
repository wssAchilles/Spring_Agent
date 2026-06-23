package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于标准输入输出传输的 MCP JSON-RPC 客户端。
 */
public class StdioMcpClient implements McpClient {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final List<String> command;
    private final Map<String, String> environment;
    private final AtomicLong requestIds = new AtomicLong();

    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    public StdioMcpClient(List<String> command, Map<String, String> environment) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("MCP stdio command must not be empty");
        }
        this.command = List.copyOf(command);
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    StdioMcpClient(BufferedReader reader, BufferedWriter writer) {
        this.command = List.of();
        this.environment = Map.of();
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public synchronized void initialize() throws McpException {
        ensureConnected();

        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "qknow-hermes");
        clientInfo.put("version", "2.2.1");
        JSONObject params = new JSONObject();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", new JSONObject());
        params.put("clientInfo", clientInfo);
        sendRequest("initialize", params);
        sendNotification("notifications/initialized", new JSONObject());
    }

    @Override
    public synchronized List<JSONObject> listTools() throws McpException {
        JSONObject result = sendRequest("tools/list", new JSONObject());
        JSONArray tools = result.getJSONArray("tools");
        return tools == null ? List.of() : tools.toJavaList(JSONObject.class);
    }

    @Override
    public synchronized JSONObject callTool(String toolName, Map<String, Object> arguments) throws McpException {
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        return sendRequest("tools/call", params);
    }

    @Override
    public synchronized void disconnect() {
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

    private void ensureConnected() {
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

    private JSONObject sendRequest(String method, JSONObject params) {
        ensureConnected();
        long id = requestIds.incrementAndGet();
        JSONObject request = baseMessage(method, params);
        request.put("id", id);
        writeMessage(request);

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

    private void sendNotification(String method, JSONObject params) {
        writeMessage(baseMessage(method, params));
    }

    private JSONObject baseMessage(String method, JSONObject params) {
        JSONObject message = new JSONObject();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        return message;
    }

    private void writeMessage(JSONObject message) {
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
            // disconnect is best-effort
        }
    }
}
