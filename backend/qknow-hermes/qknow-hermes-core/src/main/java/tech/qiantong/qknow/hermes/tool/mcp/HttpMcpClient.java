package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP HTTP 客户端 — Streamable HTTP Transport
 * 参考：MCP Java SDK v2.0.0（3.5k⭐）
 * 参考：MCP 规范 2025-03-26
 *
 * 通过 HTTP POST 发送 JSON-RPC 2.0 消息到 MCP Server。
 */
@Slf4j
public class HttpMcpClient implements McpClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private String sessionId;

    public HttpMcpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void initialize() throws McpException {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", "2025-03-26");
        params.put("capabilities", new JSONObject());
        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "qknow-hermes");
        clientInfo.put("version", "1.0.0");
        params.put("clientInfo", clientInfo);

        sendRequest("initialize", params);
        sendNotification("notifications/initialized", new JSONObject());
        log.info("MCP HTTP 初始化成功: server={}", baseUrl);
    }

    @Override
    public List<JSONObject> listTools() throws McpException {
        JSONObject result = sendRequest("tools/list", new JSONObject());
        List<JSONObject> tools = new ArrayList<>();
        if (result != null && result.containsKey("tools")) {
            JSONArray toolsArray = result.getJSONArray("tools");
            for (int i = 0; i < toolsArray.size(); i++) {
                tools.add(toolsArray.getJSONObject(i));
            }
        }
        return tools;
    }

    @Override
    public JSONObject callTool(String toolName, Map<String, Object> arguments) throws McpException {
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments);
        return sendRequest("tools/call", params);
    }

    @Override
    public void disconnect() {
        try {
            sendNotification("notifications/cancelled", new JSONObject());
        } catch (Exception e) {
            log.debug("MCP HTTP disconnect error", e);
        }
    }

    private JSONObject sendRequest(String method, JSONObject params) throws McpException {
        try {
            JSONObject request = new JSONObject();
            request.put("jsonrpc", "2.0");
            request.put("id", UUID.randomUUID().toString());
            request.put("method", method);
            if (params != null && !params.isEmpty()) {
                request.put("params", params);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toJSONString()))
                    .timeout(Duration.ofSeconds(30));

            if (sessionId != null) {
                builder.header("mcp-session-id", sessionId);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            String newSessionId = response.headers().firstValue("mcp-session-id").orElse(null);
            if (newSessionId != null) {
                this.sessionId = newSessionId;
            }

            if (response.statusCode() != 200) {
                throw new McpException("MCP HTTP error: status=" + response.statusCode());
            }

            JSONObject jsonResponse = JSON.parseObject(response.body());
            if (jsonResponse.containsKey("error")) {
                throw new McpException("MCP error: " + jsonResponse.getJSONObject("error"));
            }
            return jsonResponse.getJSONObject("result");
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("MCP request failed: " + e.getMessage(), e);
        }
    }

    private void sendNotification(String method, JSONObject params) {
        try {
            JSONObject notification = new JSONObject();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            if (params != null && !params.isEmpty()) {
                notification.put("params", params);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/mcp"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(notification.toJSONString()))
                    .timeout(Duration.ofSeconds(10));

            if (sessionId != null) {
                builder.header("mcp-session-id", sessionId);
            }

            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("MCP notification failed: {}", method, e);
        }
    }
}
