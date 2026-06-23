package tech.qiantong.qknow.hermes.flow.bo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.flow.rag.RagRetrievalService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内嵌 RAG 知识检索节点
 * <p>
 * 从知识库中检索与查询相关的文档片段，并将结果存入工作流上下文
 * <p>
 * config 格式:
 * {
 *   "knowledgeBaseId": "kb_xxx",
 *   "query": "{{ userQuestion }}"
 * }
 */
@Slf4j
public class KnowledgeNodeBO extends BaseNodeBO {

    private final RagRetrievalService ragRetrievalService;

    public KnowledgeNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList,
                           RagRetrievalService ragRetrievalService) {
        super(nodeDefinition, edgeList);
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = getNodeDefinition();
        JSONObject configJson = JSONObject.parseObject(nodeDefinition.getConfig());

        String knowledgeBaseId = configJson.getString("knowledgeBaseId");
        if (StrUtil.isBlank(knowledgeBaseId)) {
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "knowledgeBaseId is required");
        }

        String query = configJson.getString("query");
        // 支持变量替换
        if (StrUtil.isNotBlank(query)) {
            query = format(query, context);
        }

        log.info("执行知识检索节点: {}, knowledgeBaseId: {}, query: {}",
                nodeDefinition.getName(), knowledgeBaseId, query);

        try {
            List<String> results = ragRetrievalService.retrieve(knowledgeBaseId, query);

            Map<String, Object> outputData = new HashMap<>();
            outputData.put(nodeDefinition.getUuid() + ".context", String.join("\n---\n", results));

            log.info("知识检索完成: {}，检索到 {} 条结果", nodeDefinition.getName(), results.size());
            return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), outputData);

        } catch (Exception e) {
            log.error("知识检索失败: {}", nodeDefinition.getName(), e);
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "知识检索失败: " + e.getMessage());
        }
    }
}
