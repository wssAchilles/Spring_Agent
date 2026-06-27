package tech.qiantong.qknow.module.kb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.tool.mcp.McpToolAdapter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/kb/health")
public class HealthCheckController {

    @Resource
    private DataSource dataSource;

    @Resource
    private McpToolAdapter mcpToolAdapter;

    @Operation(summary = "系统健康检查 (类似 claw doctor)")
    @GetMapping
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> checks = new HashMap<>();

        // 数据库连接检查
        checks.put("database", checkDatabase());

        // MCP 服务检查
        checks.put("mcp", checkMcp());

        // LangFuse 状态
        checks.put("langfuse", checkLangFuse());

        // 系统信息
        checks.put("system", getSystemInfo());

        return CommonResult.success(checks);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> db = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            db.put("status", "ok");
            db.put("url", conn.getMetaData().getURL());
            db.put("driver", conn.getMetaData().getDriverName());
        } catch (Exception e) {
            db.put("status", "error");
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> checkMcp() {
        Map<String, Object> mcp = new HashMap<>();
        var configs = mcpToolAdapter.getServerConfigs();
        mcp.put("servers", configs.size());
        mcp.put("tools", mcpToolAdapter.getMcpTools().size());

        // 每个 server 的详细状态
        java.util.List<Map<String, Object>> serverDetails = new java.util.ArrayList<>();
        for (var config : configs.values()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("name", config.getName());
            detail.put("url", config.getUrl());
            detail.put("command", config.getCommand());
            detail.put("enabled", config.isEnabled());

            // 检查连接状态
            var client = mcpToolAdapter.getClient(config.getName());
            if (client != null) {
                detail.put("connected", client.isConnected());
            } else {
                detail.put("connected", false);
            }
            serverDetails.add(detail);
        }
        mcp.put("details", serverDetails);
        return mcp;
    }

    private Map<String, Object> checkLangFuse() {
        Map<String, Object> langfuse = new HashMap<>();
        String enabled = System.getenv("LANGFUSE_ENABLED");
        String baseUrl = System.getenv("LANGFUSE_BASE_URL");
        langfuse.put("enabled", "true".equalsIgnoreCase(enabled));
        langfuse.put("baseUrl", baseUrl != null ? baseUrl : "https://cloud.langfuse.com");
        return langfuse;
    }

    private Map<String, Object> getSystemInfo() {
        Map<String, Object> sys = new HashMap<>();
        sys.put("javaVersion", System.getProperty("java.version"));
        sys.put("osName", System.getProperty("os.name"));
        sys.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        sys.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        sys.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / 1024 / 1024);
        return sys;
    }
}
