package tech.qiantong.qknow.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.client.lifecycle.*;
import tech.qiantong.qknow.mcp.core.model.*;
import tech.qiantong.qknow.mcp.core.protocol.*;
import tech.qiantong.qknow.mcp.core.transport.McpSession;
import tech.qiantong.qknow.mcp.core.util.McpNamingConvention;

import java.util.*;
import java.util.concurrent.*;

/**
 * 原生企业级 MCP Client 动态连接池与生命周期硬化管理器（深度融合 claw-code 核心思想）
 */
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, ClientSessionHolder> clients = new ConcurrentHashMap<>();
    private final List<McpErrorSurface> failureLedger = new CopyOnWriteArrayList<>();

    public static class ClientSessionHolder {
        private final String serverId;
        private final McpSession session;
        private final boolean required;
        private volatile McpLifecyclePhase phase = McpLifecyclePhase.SERVER_REGISTRATION;
        private volatile McpErrorSurface lastError;
        private final List<McpTool> cachedTools = new CopyOnWriteArrayList<>();
        private final List<McpResource> cachedResources = new CopyOnWriteArrayList<>();
        private final Map<Object, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        public ClientSessionHolder(String serverId, McpSession session, boolean required) {
            this.serverId = serverId;
            this.session = session;
            this.required = required;
            this.phase = McpLifecyclePhase.SPAWN_CONNECT;
            this.session.setMessageListener(this::onMessageReceived);
        }

        private void onMessageReceived(McpMessage msg) {
            if (msg instanceof JsonRpcResponse resp) {
                CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(resp.id());
                if (future != null) {
                    future.complete(resp);
                }
            }
        }

        public CompletableFuture<JsonRpcResponse> sendRequest(String method, Map<String, Object> params) {
            String reqId = UUID.randomUUID().toString();
            JsonRpcRequest req = new JsonRpcRequest(reqId, method, params);
            CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
            pendingRequests.put(reqId, future);

            session.send(req).exceptionally(ex -> {
                pendingRequests.remove(reqId);
                future.completeExceptionally(ex);
                return null;
            });

            return future;
        }

        public String getServerId() { return serverId; }
        public McpSession getSession() { return session; }
        public boolean isRequired() { return required; }
        public McpLifecyclePhase getPhase() { return phase; }
        public void setPhase(McpLifecyclePhase phase) { this.phase = phase; }
        public McpErrorSurface getLastError() { return lastError; }
        public void setLastError(McpErrorSurface lastError) { this.lastError = lastError; }
        public List<McpTool> getCachedTools() { return cachedTools; }
        public List<McpResource> getCachedResources() { return cachedResources; }
    }

    public void registerSession(String serverId, McpSession session) {
        registerSession(serverId, session, true);
    }

    public void registerSession(String serverId, McpSession session, boolean required) {
        ClientSessionHolder holder = new ClientSessionHolder(serverId, session, required);
        clients.put(serverId, holder);
        log.info("[MCP Client] 注册客户端通道: {}, required={}", serverId, required);
    }

    public InitializeResult initialize(String serverId) throws Exception {
        ClientSessionHolder holder = getHolder(serverId);
        holder.setPhase(McpLifecyclePhase.INITIALIZE_HANDSHAKE);

        Map<String, Object> params = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of("roots", Map.of("listChanged", true)),
            "clientInfo", Map.of("name", "qknow-hermes-agent", "version", "2.2.1")
        );

        try {
            JsonRpcResponse resp = holder.sendRequest("initialize", params).get(5, TimeUnit.SECONDS);
            if (resp.error() != null) {
                throw new RuntimeException("MCP Initialize 失败: " + resp.error().message());
            }

            Object res = resp.result();
            InitializeResult ir = (res instanceof InitializeResult existing)
                    ? existing
                    : objectMapper.convertValue(res, InitializeResult.class);

            // 发送 initialized 通知完成握手（对标 claw-code 与规范）
            holder.getSession().send(new JsonRpcNotification("notifications/initialized", Map.of()));
            holder.setPhase(McpLifecyclePhase.READY);
            return ir;
        } catch (Exception e) {
            McpErrorSurface errorSurface = McpErrorSurface.of(
                McpLifecyclePhase.INITIALIZE_HANDSHAKE,
                serverId,
                e.getMessage(),
                Map.of("method", "initialize", "protocolVersion", "2024-11-05"),
                false
            );
            holder.setLastError(errorSurface);
            holder.setPhase(McpLifecyclePhase.ERROR_SURFACING);
            failureLedger.add(errorSurface);
            throw e;
        }
    }

    public List<McpTool> refreshTools(String serverId) throws Exception {
        ClientSessionHolder holder = getHolder(serverId);
        holder.setPhase(McpLifecyclePhase.TOOL_DISCOVERY);

        try {
            JsonRpcResponse resp = holder.sendRequest("tools/list", Map.of()).get(5, TimeUnit.SECONDS);
            if (resp.error() != null) {
                throw new RuntimeException("获取工具列表失败: " + resp.error().message());
            }

            List<McpTool> tools = new ArrayList<>();
            Object res = resp.result();
            if (res instanceof Map<?, ?> resMap) {
                Object toolsObj = resMap.get("tools");
                if (toolsObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof McpTool mt) {
                            tools.add(mt);
                        } else {
                            tools.add(objectMapper.convertValue(o, McpTool.class));
                        }
                    }
                }
            }

            holder.cachedTools.clear();
            holder.cachedTools.addAll(tools);
            holder.setPhase(McpLifecyclePhase.READY);
            return tools;
        } catch (Exception e) {
            McpErrorSurface errorSurface = McpErrorSurface.of(
                McpLifecyclePhase.TOOL_DISCOVERY,
                serverId,
                e.getMessage(),
                Map.of("method", "tools/list"),
                true
            );
            holder.setLastError(errorSurface);
            holder.setPhase(McpLifecyclePhase.ERROR_SURFACING);
            failureLedger.add(errorSurface);
            throw e;
        }
    }

    public CallToolResult callTool(String serverId, String toolName, Map<String, Object> arguments) throws Exception {
        ClientSessionHolder holder = getHolder(serverId);
        holder.setPhase(McpLifecyclePhase.INVOCATION);

        Map<String, Object> params = Map.of(
            "name", toolName,
            "arguments", arguments != null ? arguments : Map.of()
        );

        try {
            JsonRpcResponse resp = holder.sendRequest("tools/call", params).get(8, TimeUnit.SECONDS);
            holder.setPhase(McpLifecyclePhase.READY);

            if (resp.error() != null) {
                return CallToolResult.error("调用工具失败 [" + resp.error().code() + "]: " + resp.error().message());
            }

            Object res = resp.result();
            if (res instanceof CallToolResult ctr) {
                return ctr;
            }
            return objectMapper.convertValue(res, CallToolResult.class);
        } catch (Exception e) {
            McpErrorSurface errorSurface = McpErrorSurface.of(
                McpLifecyclePhase.INVOCATION,
                serverId,
                e.getMessage(),
                Map.of("toolName", toolName),
                true
            );
            holder.setLastError(errorSurface);
            holder.setPhase(McpLifecyclePhase.ERROR_SURFACING);
            failureLedger.add(errorSurface);
            throw e;
        }
    }

    /**
     * 根据 claw-code 命名空间合格名称 (mcp__{server}__{tool}) 自动路由并调用目标工具
     */
    public CallToolResult callQualifiedTool(String qualifiedToolName, Map<String, Object> arguments) throws Exception {
        McpNamingConvention.ToolRoute route = McpNamingConvention.parseToolRoute(qualifiedToolName);
        String targetServer = findMatchingServerId(route.serverName());
        if (targetServer == null) {
            return CallToolResult.error("未找到合格工具对应的 MCP Server: " + route.serverName() + " (Qualified: " + qualifiedToolName + ")");
        }
        return callTool(targetServer, route.rawToolName(), arguments);
    }

    private String findMatchingServerId(String normalizedOrRawServerName) {
        if (clients.containsKey(normalizedOrRawServerName)) {
            return normalizedOrRawServerName;
        }
        for (String id : clients.keySet()) {
            if (McpNamingConvention.normalizeName(id).equalsIgnoreCase(normalizedOrRawServerName)
                || id.equalsIgnoreCase(normalizedOrRawServerName)) {
                return id;
            }
        }
        return null;
    }

    /**
     * 生成自省健康与发现报告（对标 claw mcp / claw doctor）
     */
    public McpDiscoveryReport generateDiscoveryReport() {
        List<McpServerState> serverStates = new ArrayList<>();
        int activeCount = 0;
        int totalTools = 0;
        boolean isDegraded = false;

        for (ClientSessionHolder holder : clients.values()) {
            boolean connected = holder.getSession() != null && holder.getSession().isOpen();
            if (connected) {
                activeCount++;
            } else if (holder.isRequired()) {
                isDegraded = true;
            }
            totalTools += holder.getCachedTools().size();

            serverStates.add(new McpServerState(
                holder.getServerId(),
                holder.getPhase(),
                connected,
                holder.isRequired(),
                List.copyOf(holder.getCachedTools()),
                List.copyOf(holder.getCachedResources()),
                holder.getLastError()
            ));
        }

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("status", isDegraded ? "degraded" : (activeCount == clients.size() ? "healthy" : "partial"));
        summary.put("servers_ready", activeCount + "/" + clients.size());
        summary.put("total_tools", String.valueOf(totalTools));

        return new McpDiscoveryReport(
            clients.size(),
            activeCount,
            totalTools,
            isDegraded,
            serverStates,
            List.copyOf(failureLedger),
            summary
        );
    }

    public ClientSessionHolder getHolder(String serverId) {
        ClientSessionHolder holder = clients.get(serverId);
        if (holder == null) {
            String matched = findMatchingServerId(serverId);
            if (matched != null) {
                holder = clients.get(matched);
            }
        }
        if (holder == null) {
            throw new IllegalStateException("未找到对应的 MCP 客户端通道: " + serverId);
        }
        return holder;
    }
}
