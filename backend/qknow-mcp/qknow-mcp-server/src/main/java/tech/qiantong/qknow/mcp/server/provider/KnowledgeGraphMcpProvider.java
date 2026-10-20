package tech.qiantong.qknow.mcp.server.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 知识图谱 Neo4j 因果拓扑只读查询 MCP 适配器 (杜绝假三元组)
 */
public class KnowledgeGraphMcpProvider {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphMcpProvider.class);
    private Function<String, CallToolResult> queryDelegate;

    private static final Pattern MUTATION_PATTERN = Pattern.compile(
        "\\b(CREATE|MERGE|DELETE|DETACH\\s+DELETE|SET|REMOVE|DROP)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public KnowledgeGraphMcpProvider() {
    }

    public KnowledgeGraphMcpProvider(Function<String, CallToolResult> queryDelegate) {
        this.queryDelegate = queryDelegate;
    }

    public void setQueryDelegate(Function<String, CallToolResult> queryDelegate) {
        this.queryDelegate = queryDelegate;
    }

    public void registerTo(McpServerRegistry registry) {
        registry.registerTool(
            "qknow_kg_query",
            "在千知知识图谱中执行只读 Cypher 图查询，获取实体拓扑与因果依赖链",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "cypherQuery", Map.of("type", "string", "description", "只读 Cypher 语句 (MATCH ... RETURN)")
                ),
                "required", List.of("cypherQuery")
            ),
            params -> {
                String cypher = (String) params.get("cypherQuery");
                return queryGraph(cypher);
            }
        );
    }

    public CallToolResult queryGraph(String cypherQuery) {
        if (cypherQuery == null || cypherQuery.isBlank()) {
            return CallToolResult.error("Cypher 查询语句不能为空");
        }

        // 强力阻断变异子句，确保只读安全性
        if (MUTATION_PATTERN.matcher(cypherQuery).find()) {
            throw new SecurityException("Cypher 包含高危变异写操作被阻断: " + cypherQuery);
        }

        if (!cypherQuery.toUpperCase(Locale.ROOT).contains("RETURN")) {
            throw new SecurityException("Cypher 缺少有效 RETURN 投影子句");
        }

        if (queryDelegate != null) {
            try {
                return queryDelegate.apply(cypherQuery);
            } catch (Exception e) {
                log.warn("执行委托 Cypher 查询异常: {}", e.getMessage());
                return CallToolResult.error("执行 Cypher 图查询失败: " + e.getMessage());
            }
        }

        // 缺乏真实 Neo4j 执行器委托时客观报错，杜绝伪造假成功
        return CallToolResult.error("底层 Neo4j 图数据库连接未就绪或未配置执行器委托");
    }
}
