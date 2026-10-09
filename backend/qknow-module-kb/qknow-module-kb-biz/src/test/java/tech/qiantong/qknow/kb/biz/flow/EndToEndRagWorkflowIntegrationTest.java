package tech.qiantong.qknow.kb.biz.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.ContextualRetrievalChunkProcessor;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.EvidenceChainStreamPresenter;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.HypersphericalRrfReranker;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKnowledgeConcurrentRetriever;
import tech.qiantong.qknow.module.kb.service.flow.bo.*;
import tech.qiantong.qknow.module.kmc.api.kmcDocument.dto.KmcDocumentRespDTO;
import tech.qiantong.qknow.module.kmc.api.kmcDocument.dto.TreeSelectsDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.*;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase P3: 企业级 RAG 知识检索与智能体工作流端到端全链路集成契约评测
 * <p>
 * 验证完整闭环数据流：
 * 1. StartNodeBO (用户输入与参数注入)
 * 2. MultiKnowledgeConcurrentRetriever (多知识库虚拟线程并发检索与超时隔离)
 * 3. HypersphericalRrfReranker (千问 1536 维超球面 RRF 融合重排与 Token 预算截断)
 * 4. ContextualRetrievalChunkProcessor (Anthropic 风格语境面包屑前缀增强)
 * 5. LLMNodeBO / DeepSeek API (长思维链 reasoning 与正文引用生成)
 * 6. EvidenceChainStreamPresenter (双轨流式隔离、引文角标核验与多模态证据链卡片渲染)
 * 7. ReplyNodeBO (最终回复输出与凭单自验真)
 * </p>
 */
public class EndToEndRagWorkflowIntegrationTest {

    private static RetrieveResult createMockChunk(String id, String docName, String section, String content, Double score, int tokens) {
        RetrieveResult r = new RetrieveResult();
        r.setId(id);
        r.setDocumentName(docName);
        r.setContent(content);
        r.setScore(score);
        r.setTokens(tokens);
        r.setDocMetadata("{\"section\":\"" + section + "\"}");
        return r;
    }

    private static class StubKmcApiService implements IKmcApiService {
        @Override
        public List<RetrieveResult> recallTest(Long knowledgeId, String query) {
            if (Long.valueOf(101L).equals(knowledgeId)) {
                // 财务库
                return List.of(
                        createMockChunk("chunk-fin-1", "2024年度财报.pdf", "第三章 研发资本化",
                                "2024年度公司研发总投入达到 4.5 亿元人民币，同比增长 12.8%，重点用于 AI 平台核心底座自研。", 0.95, 80),
                        createMockChunk("chunk-fin-2", "审计意见书.pdf", "第五章 费用核算",
                                "各项研发支出凭证齐备，研发费用核算真实合规。", 0.75, 50)
                );
            } else if (Long.valueOf(102L).equals(knowledgeId)) {
                // 制度规范库
                return List.of(
                        createMockChunk("chunk-rule-1", "研发合规立项手册.docx", "第2条 审批流程",
                                "单项超过 100 万元的科研攻关项目需经技术委员会及财务负责人双签审批。", 0.92, 70),
                        createMockChunk("chunk-rule-2", "通用保密规定.pdf", "第8条 知识产权",
                                "所有研发成果源代码归属企业独占享有。", 0.68, 60)
                );
            }
            return Collections.emptyList();
        }

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
    @DisplayName("测试端到端 RAG 完整闭环流水线集成评测")
    void testEndToEndRagPipelineExecution() {
        // 1. 初始化各核心组件
        IKmcApiService kmcApiService = new StubKmcApiService();
        MultiKnowledgeConcurrentRetriever concurrentRetriever = new MultiKnowledgeConcurrentRetriever(kmcApiService);
        HypersphericalRrfReranker rrfReranker = new HypersphericalRrfReranker();
        ContextualRetrievalChunkProcessor chunkProcessor = new ContextualRetrievalChunkProcessor();
        EvidenceChainStreamPresenter streamPresenter = new EvidenceChainStreamPresenter();

        // 2. 模拟工作流运行上下文
        RuntimeContextBO context = new RuntimeContextBO();
        JSONObject globalVars = new JSONObject();
        globalVars.put("query", "请查询2024年研发投入规模及超百万项目的审批要求");
        context.setVariables(globalVars);

        // ------------------ 节点 1: Start 节点 ------------------
        KbFlowNodeDO startNodeDef = new KbFlowNodeDO();
        startNodeDef.setUuid("node-start");
        startNodeDef.setName("开始节点");
        JSONArray startInputs = new JSONArray();
        JSONObject queryField = new JSONObject();
        queryField.put("name", "query");
        queryField.put("defaultValue", "请查询2024年研发投入规模及超百万项目的审批要求");
        startInputs.add(queryField);
        startNodeDef.setInput(startInputs.toJSONString());

        StartNodeBO startNode = new StartNodeBO(startNodeDef, Collections.emptyList());
        NodeRunResultBO startRes = startNode.execute(context);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), startRes.getStatus());
        String userQuery = (String) startRes.getOutput().get("node-start.query");
        assertNotNull(userQuery);

        // ------------------ 节点 2: Knowledge 节点并发多库检索 ------------------
        KbFlowNodeDO kbNodeDef = new KbFlowNodeDO();
        kbNodeDef.setUuid("node-kb");
        kbNodeDef.setName("多知识库并发检索节点");
        JSONObject kbConfig = new JSONObject();
        JSONArray kbIds = new JSONArray();
        kbIds.add(101L);
        kbIds.add(102L);
        kbConfig.put("knowledgeBaseIds", kbIds);
        kbConfig.put("query", userQuery);
        kbNodeDef.setConfig(kbConfig.toJSONString());
        kbNodeDef.setInput("[]");

        KnowledgeNodeBO knowledgeNode = new KnowledgeNodeBO(kbNodeDef, Collections.emptyList(), kmcApiService, concurrentRetriever);
        NodeRunResultBO kbRes = knowledgeNode.execute(context);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), kbRes.getStatus());
        assertEquals(4, kbRes.getOutput().get("node-kb.count"), "多库并发召回应召回全部4条候选切片");

        // ------------------ 步骤 3: 提取召回切片并执行超球面 RRF 重排与 Token 预算截断 ------------------
        MultiKnowledgeConcurrentRetriever.MultiKbRetrievalResult rawMultiRecall =
                concurrentRetriever.retrieveConcurrently(List.of(101L, 102L), userQuery);

        // 设定 Token 预算为 200 Tokens (输入4个切片总共 80+70+50+60=260 Tokens，将自适应截断后部低分切片)
        HypersphericalRrfReranker.RrfRerankResult rerankResult =
                rrfReranker.rerankAndTruncate(rawMultiRecall.mergedChunks(), 200);

        assertTrue(rerankResult.budgetExceeded(), "必须触发 Token 预算保护机制");
        assertTrue(rerankResult.selectedCount() >= 2, "应保留最高价值的前2个切片");
        assertTrue(rerankResult.consumedTokens() <= 200, "总消耗 Token 必须严格受限于预算");
        List<RetrieveResult> selectedChunks = rerankResult.getSelectedRetrieveResults();

        // ------------------ 步骤 4: 执行 Anthropic 风格 Contextual Retrieval 语境前缀增强 ------------------
        ContextualRetrievalChunkProcessor.ContextualBatchResult contextualBatch =
                chunkProcessor.enrichBatch(selectedChunks, Map.of(
                        "2024年度财报.pdf", "集团2024年度经审计年度财务决算报告",
                        "研发合规立项手册.docx", "企业科技创新项目管理办法与内控要求"
                ));

        assertEquals(selectedChunks.size(), contextualBatch.totalProcessed());
        String enrichedDoc1 = contextualBatch.enrichedChunks().get(0).enrichedContent();
        assertTrue(enrichedDoc1.contains(ContextualRetrievalChunkProcessor.CONTEXT_PREFIX_START));
        assertTrue(enrichedDoc1.contains("第三章 研发资本化"));

        // ------------------ 节点 5: LLM 推理与 DeepSeek 思考链生成 ------------------
        // 模拟 DeepSeek 返回带有思考链、正确角标与捏造越界角标的输出
        String mockModelOutput = "<think>\n"
                + "根据财报切片[^1]，2024年研发总投入为4.5亿元；根据立项手册切片[^2]，超百万项目需双签审批。\n"
                + "注意：过滤掉无关联证据。\n"
                + "</think>\n"
                + "经核查权威知识库，2024年度公司研发总投入达到 4.5 亿元人民币[^1]；"
                + "同时，单项超过 100 万元的科研攻关项目需经技术委员会及财务负责人双签审批[^2]。"
                + "此外有虚假捏造角标[^99]需要被过滤。";

        // ------------------ 步骤 6: EvidenceChainStreamPresenter 双轨流式呈现与自验真 ------------------
        // 规范化引文角标并剔除虚假捏造角标 [^99]
        String normalizedAnswer = streamPresenter.normalizeAndValidateCitations(mockModelOutput, selectedChunks);
        assertTrue(normalizedAnswer.contains("[^1]"));
        assertTrue(normalizedAnswer.contains("[^2]"));
        assertFalse(normalizedAnswer.contains("[^99]"), "虚假角标99必须被过滤清除");

        // 渲染 Markdown 折叠式证据链卡片
        String evidenceCard = streamPresenter.renderEvidenceChainCard(selectedChunks);
        assertTrue(evidenceCard.contains("知识依据与核验证据链"));
        assertTrue(evidenceCard.contains("2024年度财报.pdf"));
        assertTrue(evidenceCard.contains("研发合规立项手册.docx"));

        // 生成不可变审计凭单并自验真
        EvidenceChainStreamPresenter.EvidenceChainReceipt receipt = streamPresenter.generateReceipt(
                normalizedAnswer, "深度推理链内容", selectedChunks, 280L
        );
        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "SHA-256 审计凭单自验真必须通过");

        // ------------------ 节点 7: Reply 节点最终响应 ------------------
        KbFlowNodeDO replyNodeDef = new KbFlowNodeDO();
        replyNodeDef.setUuid("node-reply");
        replyNodeDef.setName("最终回复节点");
        replyNodeDef.setConfig("{}");
        replyNodeDef.setOutput("[]");

        context.setExecuteMode(tech.qiantong.qknow.module.kb.dal.enums.BotExecuteModeEnum.STREAM);
        ReplyNodeBO replyNode = new ReplyNodeBO(replyNodeDef, Collections.emptyList());
        context.getVariables().put("node-llm.content", normalizedAnswer + evidenceCard);
        NodeRunResultBO replyRes = replyNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), replyRes.getStatus());
        assertNotNull(replyRes.getOutput());
    }
}
