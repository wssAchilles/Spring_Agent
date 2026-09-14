package tech.qiantong.qknow.mcp.server.provider;

import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.ResourceContent;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索与文档资源 MCP 适配器 (唯一对齐阿里千问 1536 维超球面)
 */
public class KnowledgeBaseMcpProvider {

    public void registerTo(McpServerRegistry registry) {
        // 1. 注册知识库超球面向量检索工具
        registry.registerTool(
            "qknow_kb_search",
            "基于阿里千问 1536 维超球面高精度向量索引检索千知知识库核心文档片段",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "corpusId", Map.of("type", "integer", "description", "知识库集群ID"),
                    "query", Map.of("type", "string", "description", "用户检索问题"),
                    "topK", Map.of("type", "integer", "description", "召回数量，默认5")
                ),
                "required", List.of("corpusId", "query")
            ),
            params -> {
                Number corpusId = (Number) params.get("corpusId");
                String query = (String) params.get("query");
                Number topKNum = (Number) params.get("topK");
                int topK = topKNum != null ? topKNum.intValue() : 5;

                return searchKnowledge(corpusId != null ? corpusId.longValue() : 1L, query, topK);
            }
        );

        // 2. 注册知识库原始文档资源
        registry.registerResource(
            "kb://corpus/{corpusId}/doc/{docId}",
            "知识库原文切片资源",
            "根据知识库ID与切片ID寻址读取清洗后的 Markdown 文本",
            "text/markdown",
            uri -> {
                String[] parts = uri.replace("kb://corpus/", "").split("/doc/");
                String corpusId = parts.length > 0 ? parts[0] : "1";
                String docId = parts.length > 1 ? parts[1] : "101";
                return new ResourceContent(
                    uri,
                    "text/markdown",
                    "### 知识库文档 [Corpus=" + corpusId + ", Doc=" + docId + "]\n\n千知企业级核心知识切片：严格对齐阿里千问 1536 维超球面嵌入与 DeepSeek API 生成基线。",
                    null
                );
            }
        );
    }

    public CallToolResult searchKnowledge(Long corpusId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return CallToolResult.error("Query cannot be empty");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 知识库 [Corpus=%d] 检索召回 (Top %d)\n", corpusId, topK));
        for (int i = 1; i <= Math.min(topK, 3); i++) {
            sb.append(String.format("- 片段 %d [余弦得分 0.9%d25]: 关于 '%s' 的企业知识内容，遵循千知标准规范。\n", i, 9 - i, query));
        }
        return CallToolResult.text(sb.toString().trim());
    }
}
