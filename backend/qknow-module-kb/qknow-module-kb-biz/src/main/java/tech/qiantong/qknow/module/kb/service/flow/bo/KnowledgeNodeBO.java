package tech.qiantong.qknow.module.kb.service.flow.bo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索节点业务对象
 * <p>
 * 从知识库中根据语义向量与全文索引召回相关文档切片，将结果注入工作流上下文供下游 LLM 使用
 */
@Slf4j
public class KnowledgeNodeBO extends BaseNodeBO {

    private final IKmcApiService kmcApiService;
    private final tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKnowledgeConcurrentRetriever concurrentRetriever;

    public KnowledgeNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList, IKmcApiService kmcApiService) {
        this(nodeDefinition, edgeList, kmcApiService, null);
    }

    public KnowledgeNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList,
                           IKmcApiService kmcApiService,
                           tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKnowledgeConcurrentRetriever concurrentRetriever) {
        super(nodeDefinition, edgeList);
        this.kmcApiService = kmcApiService;
        this.concurrentRetriever = concurrentRetriever;
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = super.getNodeDefinition();
        JSONObject configJson = StrUtil.isNotBlank(nodeDefinition.getConfig())
                ? JSONObject.parseObject(nodeDefinition.getConfig())
                : new JSONObject();

        // 1. 获取知识库 ID 列表 (支持单库与多库配置)
        List<Long> kbIds = new ArrayList<>();
        if (configJson.containsKey("knowledgeBaseIds")) {
            JSONArray arr = configJson.getJSONArray("knowledgeBaseIds");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    Long id = arr.getLong(i);
                    if (id != null) kbIds.add(id);
                }
            }
        }
        if (configJson.containsKey("knowledgeBaseId")) {
            kbIds.add(configJson.getLong("knowledgeBaseId"));
        } else if (configJson.containsKey("knowledgeId")) {
            kbIds.add(configJson.getLong("knowledgeId"));
        } else if (inputData.containsKey("knowledgeBaseId")) {
            Object kbVal = inputData.get("knowledgeBaseId");
            if (kbVal instanceof Number) {
                kbIds.add(((Number) kbVal).longValue());
            } else if (kbVal != null && StrUtil.isNotBlank(kbVal.toString())) {
                try {
                    kbIds.add(Long.parseLong(kbVal.toString()));
                } catch (Exception ignored) {
                }
            }
        }
        if (kbIds.isEmpty()) {
            kbIds.add(7L); // 默认使用常州工学院总评方案知识库
        }

        // 2. 获取检索查询词 query (优先支持变量替换 format)
        String query = configJson.getString("query");
        if (StrUtil.isBlank(query) && inputData.containsKey("query") && inputData.get("query") != null) {
            query = inputData.get("query").toString();
        }
        if (StrUtil.isBlank(query)) {
            // 从全局变量兜底获取起始节点输入
            JSONObject variables = context.getVariables();
            if (variables != null) {
                query = variables.getString("start_1.query");
                if (StrUtil.isBlank(query)) {
                    query = variables.getString("query");
                }
            }
        }
        if (StrUtil.isNotBlank(query)) {
            query = super.format(query, context);
        }

        log.info("执行知识库检索节点: uuid={}, name={}, kbIds={}, query={}",
                nodeDefinition.getUuid(), nodeDefinition.getName(), kbIds, query);

        if (StrUtil.isBlank(query)) {
            log.warn("知识库检索节点查询词为空: uuid={}", nodeDefinition.getUuid());
            Map<String, Object> outputData = new HashMap<>();
            String emptyMsg = "未提供检索查询词，无法执行知识库召回。";
            outputData.put(nodeDefinition.getUuid() + ".text", emptyMsg);
            outputData.put(nodeDefinition.getUuid() + ".context", emptyMsg);
            outputData.put("text", emptyMsg);
            return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), outputData);
        }

        try {
            // 3. 调用知识库服务召回切片 (优先使用并发检索器)
            List<RetrieveResult> results;
            if (concurrentRetriever != null) {
                var recallRes = concurrentRetriever.retrieveConcurrently(kbIds, query);
                results = recallRes.mergedChunks();
            } else if (kbIds.size() == 1) {
                results = kmcApiService.recallTest(kbIds.get(0), query);
            } else {
                results = new ArrayList<>();
                for (Long kid : kbIds) {
                    try {
                        List<RetrieveResult> part = kmcApiService.recallTest(kid, query);
                        if (part != null) results.addAll(part);
                    } catch (Exception ex) {
                        log.warn("知识库 {} 检索异常: {}", kid, ex.getMessage());
                    }
                }
            }

            StringBuilder contentBuilder = new StringBuilder();
            if (CollUtil.isNotEmpty(results)) {
                int index = 1;
                for (RetrieveResult item : results) {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n\n");
                    }
                    String docName = StrUtil.isNotBlank(item.getDocumentName()) ? item.getDocumentName() : "知识文档";
                    Double score = item.getScore();
                    String scoreText = score != null ? String.format("%.4f", score) : "N/A";

                    contentBuilder.append(String.format("【片段 %d | 来源: %s | 相似度: %s】\n", index++, docName, scoreText));
                    contentBuilder.append(item.getContent() != null ? item.getContent().trim() : "");
                }
            } else {
                contentBuilder.append("知识库中未检索到与“").append(query).append("”相关的有效切片。");
            }

            String formattedResult = contentBuilder.toString();
            log.info("知识库检索完成: uuid={}, 召回切片数={}, 内容长度={}",
                    nodeDefinition.getUuid(), results != null ? results.size() : 0, formattedResult.length());

            Map<String, Object> outputData = new HashMap<>();
            outputData.put(nodeDefinition.getUuid() + ".text", formattedResult);
            outputData.put(nodeDefinition.getUuid() + ".context", formattedResult);
            outputData.put(nodeDefinition.getUuid() + ".count", results != null ? results.size() : 0);
            outputData.put("text", formattedResult);

            return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), outputData);

        } catch (Exception e) {
            log.error("知识库检索节点异常: uuid={}, error={}", nodeDefinition.getUuid(), e.getMessage(), e);
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "知识库检索失败: " + e.getMessage());
        }
    }
}
