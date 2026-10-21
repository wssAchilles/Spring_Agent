package tech.qiantong.qknow.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcRequest;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;
import tech.qiantong.qknow.mcp.server.transport.McpHttpAuthGuard;

import java.util.Map;

/**
 * 原生 MCP Server HTTP JSON-RPC 端点（P1：MCP Server 端点接线）。
 * <p>
 * 默认关闭；启用时必须配置 {@code MCP_SERVER_AUTH_TOKEN}，客户端通过
 * {@code Authorization: Bearer <token>} 访问。鉴权核心逻辑位于
 * {@link McpHttpAuthGuard}，使用常量时间比较。
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpServerController {

    private final McpServerRegistry registry;

    @Value("${qknow.mcp.server.enabled:false}")
    private boolean enabled;

    @Value("${qknow.mcp.server.auth-token:${MCP_SERVER_AUTH_TOKEN:}}")
    private String authToken;

    public McpServerController(McpServerRegistry registry) {
        this.registry = registry;
    }

    @PostMapping
    public ResponseEntity<?> handleRequest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody JsonRpcRequest request) {
        McpHttpAuthGuard.Decision decision = new McpHttpAuthGuard(enabled, authToken).check(authorization);
        switch (decision) {
            case DISABLED -> {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "MCP server is disabled"));
            }
            case CONFIG_MISSING -> {
                log.error("MCP Server 已启用但未配置 MCP_SERVER_AUTH_TOKEN，拒绝请求");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "MCP server auth token is not configured"));
            }
            case UNAUTHORIZED -> {
                log.warn("MCP Server 拒绝了未授权请求: method={}", request != null ? request.method() : null);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unauthorized MCP request"));
            }
            default -> {
                JsonRpcResponse response = registry.handleRequest(request);
                return ResponseEntity.ok(response);
            }
        }
    }
}
