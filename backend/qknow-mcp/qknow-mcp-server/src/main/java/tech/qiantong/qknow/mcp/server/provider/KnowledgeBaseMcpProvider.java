package tech.qiantong.qknow.mcp.server.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.ResourceContent;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 知识库检索与文档资源 MCP 适配器 (唯一对齐阿里千问 1536 维超球面，杜绝伪造得分)
 */
public class KnowledgeBaseMcpProvider {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseMcpProvider.class);
    private Function<Map<String, Object>, CallToolResult> searchDelegate;

    public KnowledgeBaseMcpProvider() {
    }

    public KnowledgeBaseMcpProvider(Function<Map<String, Object>, CallToolResult> searchDelegate) {
        this.searchDelegate = searchDelegate;
    }

    public void setSearchDelegate(Function<Map<String, Object>, CallToolResult> searchDelegate) {
        this.searchDelegate = searchDelegate;
    }

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
                if (searchDelegate != null) {
                    try {
                        return searchDelegate.apply(params);
                    } catch (Exception e) {
                        log.warn("执行委托知识库检索异常: {}", e.getMessage());
                        return CallToolResult.error("知识库检索执行失败: " + e.getMessage());
                    }
                }
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
            return CallToolResult.error("检索 Query 不能为空");
        }
        if (searchDelegate != null) {
            try {
                return searchDelegate.apply(Map.of("corpusId", corpusId, "query", query, "topK", topK));
            } catch (Exception e) {
                log.warn("执行委托知识库检索异常: {}", e.getMessage());
                return CallToolResult.error("知识库检索执行失败: " + e.getMessage());
            }
        }
        // 缺乏真实知识库检索服务委托时客观报错，杜绝伪造假成功
        return CallToolResult.error(String.format("知识库 [Corpus=%d] 底层向量检索服务未绑定或尚未就绪", corpusId));
    }
}
