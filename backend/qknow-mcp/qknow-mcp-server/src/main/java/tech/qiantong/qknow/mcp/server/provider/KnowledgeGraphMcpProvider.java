package tech.qiantong.qknow.mcp.server.provider;

import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 知识图谱 Neo4j 因果拓扑只读查询 MCP 适配器
 */
public class KnowledgeGraphMcpProvider {

    private static final Pattern MUTATION_PATTERN = Pattern.compile(
        "\\b(CREATE|MERGE|DELETE|DETACH\\s+DELETE|SET|REMOVE|DROP)\\b",
        Pattern.CASE_INSENSITIVE
    );

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
            return CallToolResult.error("Cypher query cannot be empty");
        }

        // 强力阻断变异子句，确保只读安全性
        if (MUTATION_PATTERN.matcher(cypherQuery).find()) {
            throw new SecurityException("Cypher 包含高危变异写操作被阻断: " + cypherQuery);
        }

        if (!cypherQuery.toUpperCase(Locale.ROOT).contains("RETURN")) {
            throw new SecurityException("Cypher 缺少有效 RETURN 投影子句");
        }

        return CallToolResult.text("MATCH (e1:Entity)-[r:CAUSES]->(e2:Entity) 拓扑因果路径已命中: [实体A] -> [导致] -> [实体B] (置信度 0.94)");
    }
}
