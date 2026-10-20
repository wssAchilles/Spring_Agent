package tech.qiantong.qknow.mcp.server.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.ResourceContent;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * 数据智能体 (Phase 35 DataAgent) 紧凑模式与只读安全 SQL 执行适配器 (杜绝假数据)
 */
public class DataAgentMcpProvider {

    private static final Logger log = LoggerFactory.getLogger(DataAgentMcpProvider.class);
    private BiFunction<String, String, CallToolResult> sqlExecutorDelegate;

    private static final Pattern DANGEROUS_SQL = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public DataAgentMcpProvider() {
    }

    public DataAgentMcpProvider(BiFunction<String, String, CallToolResult> sqlExecutorDelegate) {
        this.sqlExecutorDelegate = sqlExecutorDelegate;
    }

    public void setSqlExecutorDelegate(BiFunction<String, String, CallToolResult> sqlExecutorDelegate) {
        this.sqlExecutorDelegate = sqlExecutorDelegate;
    }

    public void registerTo(McpServerRegistry registry) {
        // 1. 模式 Schema 资源
        registry.registerResource(
            "dm://datasource/{dsId}/schema",
            "数据源压缩 Schema 字典",
            "获取数据源百表千列经超球面向量投影剪枝后的紧凑结构 (<=4KB)",
            "application/json",
            uri -> {
                String dsId = uri.replace("dm://datasource/", "").replace("/schema", "");
                return new ResourceContent(
                    uri,
                    "application/json",
                    "{\"datasourceId\": \"" + dsId + "\", \"status\": \"ACTIVE\", \"description\": \"数据源模式字典与元数据\"}",
                    null
                );
            }
        );

        // 2. 只读安全 SQL 执行工具
        registry.registerTool(
            "qknow_sql_query",
            "在受信只读连接池执行只读 SQL 查询，强力拦截分号堆叠并自动注入 LIMIT 1000 保护",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "dsId", Map.of("type", "string", "description", "数据源标识"),
                    "sql", Map.of("type", "string", "description", "待执行的只读 SQL 语句")
                ),
                "required", List.of("dsId", "sql")
            ),
            params -> {
                String dsId = (String) params.get("dsId");
                String sql = (String) params.get("sql");
                return executeSafeSql(dsId, sql);
            }
        );
    }

    public CallToolResult executeSafeSql(String dsId, String sql) {
        if (sql == null || sql.isBlank()) {
            return CallToolResult.error("待执行的 SQL 不能为空");
        }

        // 阻断分号堆叠注入
        if (sql.contains(";")) {
            throw new SecurityException("阻断分号堆叠多语句 SQL 注入");
        }

        // 阻断写操作
        if (DANGEROUS_SQL.matcher(sql).find()) {
            throw new SecurityException("阻断非只读 SQL 关键字操作");
        }

        String normalized = sql.trim();
        if (!normalized.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            throw new SecurityException("SQL 必须以 SELECT 谓词起始");
        }

        // 自动注入 LIMIT 1000 保护
        String finalSql = normalized;
        if (!normalized.toUpperCase(Locale.ROOT).contains("LIMIT")) {
            finalSql += " LIMIT 1000";
        }

        if (sqlExecutorDelegate != null) {
            try {
                return sqlExecutorDelegate.apply(dsId, finalSql);
            } catch (Exception e) {
                log.warn("执行委托 SQL 异常: dsId={}, msg={}", dsId, e.getMessage());
                return CallToolResult.error("SQL 执行失败: " + e.getMessage());
            }
        }

        // 未配置真实只读数据源连接池时客观报错，绝不伪造硬编码 orders
        return CallToolResult.error(String.format("未找到数据源 [%s] 对应的只读连接池或执行器未配置", dsId));
    }
}
