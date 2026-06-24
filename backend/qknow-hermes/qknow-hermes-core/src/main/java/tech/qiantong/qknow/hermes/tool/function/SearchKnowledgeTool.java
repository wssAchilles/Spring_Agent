package tech.qiantong.qknow.hermes.tool.function;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.chat.model.ToolContext;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;

import java.util.function.BiFunction;

/**
 * 知识库检索工具（Hermes 版本）
 *
 * Phase 1: 接受控制面预检索的 RAG 结果（通过 gRPC ChatRequest.rag_contexts 传入）
 * Phase 2: 改为 HTTP 回调控制面 /internal/api/rag/recall
 */
public class SearchKnowledgeTool implements BiFunction<knowledgeQuery, ToolContext, String> {

    private final String knowledgeId;
    private final String knowledgeName;
    private final String preRetrievedContent;

    public SearchKnowledgeTool(String knowledgeId, String knowledgeName, String preRetrievedContent) {
        this.knowledgeId = knowledgeId;
        this.knowledgeName = knowledgeName;
        this.preRetrievedContent = preRetrievedContent;
    }

    @Override
    public String apply(knowledgeQuery query, ToolContext toolContext) {
        if (preRetrievedContent == null || preRetrievedContent.isEmpty()) {
            return "知识库 [" + knowledgeName + "] 中没有找到 \"" + query.getQuery() + "\" 的相关信息!";
        }
        return "根据知识库检索结果：\n" + preRetrievedContent;
    }
}
