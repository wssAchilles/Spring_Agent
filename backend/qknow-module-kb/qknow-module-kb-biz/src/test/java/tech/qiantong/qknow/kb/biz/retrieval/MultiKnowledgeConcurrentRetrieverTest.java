package tech.qiantong.qknow.kb.biz.retrieval;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKnowledgeConcurrentRetriever;
import tech.qiantong.qknow.module.kb.service.flow.bo.KnowledgeNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kmc.api.kmcDocument.dto.KmcDocumentRespDTO;
import tech.qiantong.qknow.module.kmc.api.kmcDocument.dto.TreeSelectsDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.*;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业级多知识库并发检索与自适应超时兜底契约测试
 */
public class MultiKnowledgeConcurrentRetrieverTest {

    private static RetrieveResult createMockRetrieveResult(String id, String content, String docName, Double score) {
        RetrieveResult result = new RetrieveResult();
        result.setId(id);
        result.setContent(content);
        result.setDocumentName(docName);
        result.setScore(score);
        return result;
    }

    private static abstract class BaseMockKmcApiService implements IKmcApiService {
        @Override public List<KmcDocumentRespDTO> getKmcDocumentList() { return Collections.emptyList(); }
        @Override public List<KmcKnowledgeBaseRespDTO> getKmcKnowledgeBaseList(Long userId, Boolean isValid) { return Collections.emptyList(); }
        @Override public List<KmcDocumentRespDTO> getKmcDocumentListByIds(List<Long> ids) { return Collections.emptyList(); }
        @Override public KmcDocumentRespDTO getKmcDocumentById(Long id) { return null; }
        @Override public List<TreeSelectsDTO> getCategoryTreeByKnowledgeList(Long knowledgeId) { return Collections.emptyList(); }
        @Override public List<RetrieveResult> recallTest(Long knowledgeId, String query, List<KmcChatTurnDTO> history) { return recallTest(knowledgeId, query); }
        @Override public Optional<SemanticCacheHitDTO> findSemanticAnswer(SemanticCacheLookupReqDTO req) { return Optional.empty(); }
        @Override public void saveSemanticAnswer(SemanticCacheSaveReqDTO req) {}
        @Override public List<GraphRagResult> graphSearch(GraphRagSearchReqDTO req) { return Collections.emptyList(); }
        @Override public List<KmcKnowledgeBaseRespDTO> getKnowledgeBaseList() { return Collections.emptyList(); }
        @Override public List<KmcKnowledgeBaseRespDTO> getKnowledgeBaseByIds(List<Long> ids) { return Collections.emptyList(); }
    }

    @Test
    @DisplayName("测试多知识库并行检索合并与切片去重排序")
    void testConcurrentRetrieve_MultiKbSuccess() {
        IKmcApiService mockKmc = new BaseMockKmcApiService() {
            @Override
            public List<RetrieveResult> recallTest(Long knowledgeId, String query) {
                if (Long.valueOf(101L).equals(knowledgeId)) {
                    return List.of(
                            createMockRetrieveResult("seg-1", "知识库101切片A", "制度汇编.pdf", 0.95),
                            createMockRetrieveResult("seg-2", "公共切片X", "通用通告.docx", 0.88)
                    );
                } else if (Long.valueOf(102L).equals(knowledgeId)) {
                    return List.of(
                            createMockRetrieveResult("seg-2", "公共切片X重复", "通用通告.docx", 0.85),
                            createMockRetrieveResult("seg-3", "知识库102切片B", "技术规范.pdf", 0.92)
                    );
                }
                return Collections.emptyList();
            }
        };

        MultiKnowledgeConcurrentRetriever retriever = new MultiKnowledgeConcurrentRetriever(mockKmc);
        MultiKnowledgeConcurrentRetriever.MultiKbRetrievalResult result = retriever.retrieveConcurrently(
                List.of(101L, 102L), "查询词", 2000L, 3000L
        );

        assertNotNull(result);
        assertEquals(0, result.timedOutKbIds().size(), "不应有超时库");
        assertEquals(0, result.failedKbIds().size(), "不应有失败库");

        // 验证去重与按得分降序排序: seg-1 (0.95) -> seg-3 (0.92) -> seg-2 (0.88)
        List<RetrieveResult> merged = result.mergedChunks();
        assertEquals(3, merged.size(), "去重后应为3条");
        assertEquals("seg-1", merged.get(0).getId());
        assertEquals("seg-3", merged.get(1).getId());
        assertEquals("seg-2", merged.get(2).getId());
    }

    @Test
    @DisplayName("测试单库慢查询超时隔离与Fail-Open降级保证")
    void testConcurrentRetrieve_SlowKbTimeoutIsolation() {
        IKmcApiService mockKmc = new BaseMockKmcApiService() {
            @Override
            public List<RetrieveResult> recallTest(Long knowledgeId, String query) {
                if (Long.valueOf(101L).equals(knowledgeId)) {
                    // 模拟慢库休眠 350ms
                    try {
                        Thread.sleep(350);
                    } catch (InterruptedException ignored) {
                    }
                    return List.of(createMockRetrieveResult("seg-slow", "慢库内容", "归档卷宗.pdf", 0.99));
                } else if (Long.valueOf(102L).equals(knowledgeId)) {
                    // 正常库立即返回
                    return List.of(createMockRetrieveResult("seg-fast", "快库内容", "即时公告.pdf", 0.91));
                }
                return Collections.emptyList();
            }
        };

        MultiKnowledgeConcurrentRetriever retriever = new MultiKnowledgeConcurrentRetriever(mockKmc);
        // 单库超时阈值设为 120ms
        MultiKnowledgeConcurrentRetriever.MultiKbRetrievalResult result = retriever.retrieveConcurrently(
                List.of(101L, 102L), "查询词", 120L, 800L
        );

        assertNotNull(result);
        assertTrue(result.timedOutKbIds().contains(101L), "慢库101应被标记超时");
        assertEquals(1, result.mergedChunks().size(), "应仅包含快库102的结果");
        assertEquals("seg-fast", result.mergedChunks().get(0).getId());
    }

    @Test
    @DisplayName("测试异常库Fail-Open不影响其他正常库召回")
    void testConcurrentRetrieve_FailedKbExceptionIsolation() {
        IKmcApiService mockKmc = new BaseMockKmcApiService() {
            @Override
            public List<RetrieveResult> recallTest(Long knowledgeId, String query) {
                if (Long.valueOf(101L).equals(knowledgeId)) {
                    throw new RuntimeException("底层网络异常或知识库未就绪");
                }
                return List.of(createMockRetrieveResult("seg-normal", "正常库内容", "正常手册.pdf", 0.89));
            }
        };

        MultiKnowledgeConcurrentRetriever retriever = new MultiKnowledgeConcurrentRetriever(mockKmc);
        MultiKnowledgeConcurrentRetriever.MultiKbRetrievalResult result = retriever.retrieveConcurrently(
                List.of(101L, 102L), "测试", 1000L, 2000L
        );

        assertNotNull(result);
        assertTrue(result.failedKbIds().contains(101L), "库101应被标记失败");
        assertEquals(1, result.mergedChunks().size());
        assertEquals("seg-normal", result.mergedChunks().get(0).getId());
    }

    @Test
    @DisplayName("测试 KnowledgeNodeBO 多库配置通过 MultiKnowledgeConcurrentRetriever 并发执行")
    void testKnowledgeNodeBO_MultiKbExecution() {
        IKmcApiService mockKmc = new BaseMockKmcApiService() {
            @Override
            public List<RetrieveResult> recallTest(Long knowledgeId, String query) {
                return List.of(createMockRetrieveResult("seg-" + knowledgeId, "内容-" + knowledgeId, "文档-" + knowledgeId, 0.9));
            }
        };

        MultiKnowledgeConcurrentRetriever retriever = new MultiKnowledgeConcurrentRetriever(mockKmc);

        KbFlowNodeDO nodeDef = new KbFlowNodeDO();
        nodeDef.setUuid("kb-node-1");
        nodeDef.setName("多库检索节点");

        JSONObject config = new JSONObject();
        JSONArray kbIds = new JSONArray();
        kbIds.add(201L);
        kbIds.add(202L);
        config.put("knowledgeBaseIds", kbIds);
        config.put("query", "企业管理规范");
        nodeDef.setConfig(config.toJSONString());
        nodeDef.setInput("[]");

        KnowledgeNodeBO bo = new KnowledgeNodeBO(nodeDef, Collections.emptyList(), mockKmc, retriever);

        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());

        NodeRunResultBO runResult = bo.execute(context);

        assertNotNull(runResult);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), runResult.getStatus());
        Map<String, Object> output = runResult.getOutput();
        assertNotNull(output);
        assertEquals(2, output.get("kb-node-1.count"));
        assertTrue(output.get("kb-node-1.text").toString().contains("内容-201"));
        assertTrue(output.get("kb-node-1.text").toString().contains("内容-202"));
    }
}
