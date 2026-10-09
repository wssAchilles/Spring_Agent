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
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * MCP HTTP 客户端 — Streamable HTTP Transport
 * 参考：MCP Java SDK v2.0.0（3.5k⭐）
 * 参考：MCP 规范 2024-11 / 2025-03
 *
 * 支持通过 HTTP POST 发送 JSON-RPC 2.0 消息到 MCP Server，
 * 并支持通过 HTTP GET SSE 建立双向长连接，处理服务端通知与 Sampling 反向推理调用。
 */
@Slf4j
public class HttpMcpClient implements McpClient {

    private final String baseUrl;
    private final String apiKey;
    private final Map<String, String> customHeaders;
    private final HttpClient httpClient;
    private String sessionId;

    private volatile McpSamplingHandler samplingHandler;
    private final List<Consumer<JSONObject>> eventListeners = new CopyOnWriteArrayList<>();
    private volatile boolean sseConnected = false;
    private volatile String ssePostEndpoint = null;
    private volatile Thread sseListenerThread;

    public HttpMcpClient(String baseUrl) {
        this(baseUrl, null, Collections.emptyMap());
    }

    public HttpMcpClient(String baseUrl, String apiKey, Map<String, String> headers) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.customHeaders = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setSamplingHandler(McpSamplingHandler samplingHandler) {
        this.samplingHandler = samplingHandler;
    }

    public void addEventListener(Consumer<JSONObject> listener) {
        if (listener != null) {
            this.eventListeners.add(listener);
        }
    }

    public boolean isSseConnected() {
        return sseConnected;
    }

    public String getSsePostEndpoint() {
        return ssePostEndpoint;
    }

    /**
     * 建立真实的 HTTP GET SSE 长连接通道
     */
    public synchronized void connectSse(String ssePath) {
        if (sseConnected) {
            return;
        }

        String fullSseUrl = baseUrl + (ssePath != null ? (ssePath.startsWith("/") ? ssePath : "/" + ssePath) : "/sse");
        log.info("MCP 正在建立 HTTP GET SSE 长连接: {}", fullSseUrl);

        sseListenerThread = Thread.ofVirtual().name("mcp-sse-listener-" + baseUrl).start(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(fullSseUrl))
                        .header("Accept", "text/event-stream")
                        .GET();
                applyHeaders(builder);

                httpClient.send(builder.build(), HttpResponse.BodyHandlers.fromLineSubscriber(new java.util.concurrent.Flow.Subscriber<String>() {
                    private java.util.concurrent.Flow.Subscription subscription;
                    private String currentEvent = "message";

                    @Override
                    public void onSubscribe(java.util.concurrent.Flow.Subscription sub) {
                        this.subscription = sub;
                        sseConnected = true;
                        sub.request(1);
                    }

                    @Override
                    public void onNext(String line) {
                        try {
                            if (line.startsWith("event:")) {
                                currentEvent = line.substring(6).trim();
                            } else if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if ("endpoint".equals(currentEvent)) {
                                    ssePostEndpoint = data.startsWith("http") ? data : (baseUrl + (data.startsWith("/") ? data : "/" + data));
                                    log.info("MCP SSE 接收到 POST 目标端点: {}", ssePostEndpoint);
                                } else {
                                    handleIncomingSseMessage(data);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("解析 SSE 事件行异常", e);
                        } finally {
                            subscription.request(1);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.warn("MCP SSE 连接异常: {}", throwable.getMessage());
                        sseConnected = false;
                    }

                    @Override
                    public void onComplete() {
                        log.info("MCP SSE 连接已完成关闭");
                        sseConnected = false;
                    }
                }));
            } catch (Exception e) {
                log.warn("建立 MCP SSE 失败: {}", e.getMessage());
                sseConnected = false;
            }
        });
    }

    /**
     * 处理服务端推过来的 JSON-RPC 事件 (支持 Sampling 反向生成)
     */
    private void handleIncomingSseMessage(String data) {
        if (data == null || data.isBlank()) return;
        try {
            JSONObject json = JSON.parseObject(data);
            String method = json.getString("method");

            // 1. 若为 sampling/createMessage 原语，委托 samplingHandler 自动反向生成并响应
            if ("sampling/createMessage".equals(method) && samplingHandler != null) {
                String id = json.getString("id");
                JSONObject params = json.getJSONObject("params");
                JSONObject responseResult = samplingHandler.handleSamplingRequest(params);

                if (id != null) {
                    JSONObject rpcResponse = new JSONObject();
                    rpcResponse.put("jsonrpc", "2.0");
                    rpcResponse.put("id", id);
                    rpcResponse.put("result", responseResult);
                    sendNotification("notifications/message", rpcResponse);
                }
            }

            // 2. 广播至所有事件监听器
            for (Consumer<JSONObject> listener : eventListeners) {
                try {
                    listener.accept(json);
                } catch (Exception ex) {
                    log.debug("分发 MCP SSE 监听器异常", ex);
                }
            }
        } catch (Exception e) {
            log.debug("解析 MCP SSE 数据包异常", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Map<String, String> getCustomHeaders() {
        return Collections.unmodifiableMap(customHeaders);
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

    @Override
    public boolean isConnected() {
        return httpClient != null;
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
                    .uri(resolveEndpointUri())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toJSONString()))
                    .timeout(Duration.ofSeconds(30));

            applyHeaders(builder);

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
                    .uri(resolveEndpointUri())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(notification.toJSONString()))
                    .timeout(Duration.ofSeconds(10));

            applyHeaders(builder);

            if (sessionId != null) {
                builder.header("mcp-session-id", sessionId);
            }

            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("MCP notification failed: {}", method, e);
        }
    }

    private void applyHeaders(HttpRequest.Builder builder) {
        if (apiKey != null && !apiKey.isBlank() && !customHeaders.containsKey("Authorization")) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        for (Map.Entry<String, String> header : customHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
    }

    private URI resolveEndpointUri() {
        if (baseUrl.contains("/mcp") || baseUrl.contains("/sse") || baseUrl.contains("/api")) {
            return URI.create(baseUrl);
        }
        return URI.create(baseUrl + "/mcp");
    }
}
