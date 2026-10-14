package tech.qiantong.qknow.hermes.search.speculative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.search.graphrag.GraphRagCommunityReceipt;
import tech.qiantong.qknow.hermes.search.graphrag.HierarchicalCommunityNode;
import tech.qiantong.qknow.hermes.search.graphrag.HierarchicalGraphRagEngine;
import tech.qiantong.qknow.hermes.search.hybrid.HybridRetrievalCandidate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第三阶段核心工程契约测试：
 * Speculative RAG 并行草稿与 DeepSeek 思考模式集中验证、GraphRAG 分层社区双层认知路由与密码学凭单验签
 */
public class SpeculativeRagAndGraphRagContractTest {

    private static final int DIM = 1536;

    @Test
    @DisplayName("契约一：Speculative RAG 并行分片草稿生成与 DeepSeek 思考模式集中校验契约")
    public void testSpeculativeRagDraftingAndVerification() {
        SpeculativeRagOrchestrator orchestrator = new SpeculativeRagOrchestrator();

        // 构造 4 篇相关文档
        List<HybridRetrievalCandidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            candidates.add(HybridRetrievalCandidate.of(
                    "doc-" + i, "企业系统核心组件 " + i + " 阐述了高可用集群与零拷贝通信技术规范",
                    0.9f, i, 12.0f, i, 0.015
            ).withFinalRerankScore(2.0f + i * 0.1f));
        }

        String query = "如何实现系统的高可用与零拷贝通信？";
        SpeculativeRagReceipt receipt = orchestrator.orchestrate(query, candidates);

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "推测性 RAG 凭单必须通过 MessageDigest.isEqual 常量时间验签");
        assertEquals(2, receipt.draftCount(), "4 篇文档按 partitionSize=2 必须划分生成 2 个独立分片草稿");

        // 验证 DeepSeek 官方思考模式推理链内容 (reasoning_content)
        assertNotNull(receipt.reasoningContent());
        assertTrue(receipt.reasoningContent().contains("DeepSeek 官方思考模式推理"));
        assertTrue(receipt.reasoningContent().contains("一致性后验校准"));

        // 验证最终综合回答内容
        assertNotNull(receipt.finalSynthesizedAnswer());
        assertTrue(receipt.finalSynthesizedAnswer().contains("推测性 RAG 联合校验综合结论"));
        assertTrue(receipt.verificationScore() > 0.0f);

        // 验证凭单不可变性
        assertThrows(UnsupportedOperationException.class, () -> receipt.draftRecords().add(
                SpeculativeDraftRecord.of("p-bad", List.of(), "bad", "bad", 0.0f)
        ), "凭单内部 draftRecords 必须为不可变 List");
    }

    @Test
    @DisplayName("契约二：Speculative RAG 分片异常隔离与 Fail-open 自愈容错契约")
    public void testSpeculativeRagPartitionFaultTolerance() {
        // 自定义 Drafter：模拟其中一个分片发生异常
        SpeculativeRagOrchestrator.DraftDrafter faultDrafter = (partId, q, cands) -> {
            if ("partition-0".equals(partId)) {
                throw new RuntimeException("模拟网络抖动导致子分片草稿生成超时");
            }
            return SpeculativeDraftRecord.of(partId, List.of("doc-ok"), "正常分片假设", "正常证据", 0.95f);
        };

        SpeculativeRagOrchestrator orchestrator = new SpeculativeRagOrchestrator(
                faultDrafter, new SpeculativeRagOrchestrator.DefaultMasterVerifier()
        );

        List<HybridRetrievalCandidate> candidates = List.of(
                HybridRetrievalCandidate.of("doc-1", "内容1", 0.8f, 1, 10f, 1, 0.01).withFinalRerankScore(1.0f),
                HybridRetrievalCandidate.of("doc-2", "内容2", 0.7f, 2, 9f, 2, 0.01).withFinalRerankScore(0.9f)
        );

        // 执行编排：断言系统不崩溃，且基于可用分片自愈完成回答
        SpeculativeRagReceipt receipt = orchestrator.orchestrate("测试容错查询", candidates);
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature());
        assertEquals(0, receipt.draftCount(), "故障分片被安全隔离过滤");
    }

    @Test
    @DisplayName("契约三：GraphRAG 宏观全局全景总结意图路由契约")
    public void testGraphRagGlobalSummarizationRouting() {
        HierarchicalGraphRagEngine engine = new HierarchicalGraphRagEngine();
        Random random = new Random(303);

        // 注册 Level 2 宏观社区总结节点
        float[] globalEmbedding = generateNormalizedVector(DIM, random);
        HierarchicalCommunityNode globalCommunity = HierarchicalCommunityNode.of(
                "comm-macro-01",
                2,
                List.of("ent-cluster", "ent-governance", "ent-storage"),
                "【企业级系统全景演进脉络综述】近半年来系统在多智能体协同、状态机事务与硬件级加速三大方向完成深层重构",
                globalEmbedding,
                List.of("comm-meso-01", "comm-meso-02")
        );
        engine.registerCommunity(globalCommunity);

        // 注册 Level 0 微观实体社区节点
        float[] localEmbedding = generateNormalizedVector(DIM, random);
        HierarchicalCommunityNode localCommunity = HierarchicalCommunityNode.of(
                "comm-micro-01",
                0,
                List.of("ent-token-expired"),
                "【微观局部事实】Token 过期时抛出 ERR_TOKEN_EXPIRED 错误",
                localEmbedding,
                List.of()
        );
        engine.registerCommunity(localCommunity);

        // 提出全局概括性问题
        String globalQuery = "请全面概括系统的架构演进脉络与整体方案总结";
        GraphRagCommunityReceipt receipt = engine.retrieveCommunityKnowledge(globalQuery, globalEmbedding);

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "GraphRAG 凭单常量时间验签必须通过");
        assertEquals("GLOBAL_SUMMARIZATION", receipt.queryIntent(), "意图必须精准识别为 GLOBAL_SUMMARIZATION");
        assertEquals(2, receipt.hitLevel(), "全局总结必须命中 Level 2 宏观社区节点");
        assertEquals("comm-macro-01", receipt.hitCommunityId());
        assertTrue(receipt.retrievedSummary().contains("企业级系统全景演进脉络综述"));
    }

    @Test
    @DisplayName("契约四：GraphRAG 微观精准事实下钻意图路由契约")
    public void testGraphRagLocalFactualRouting() {
        HierarchicalGraphRagEngine engine = new HierarchicalGraphRagEngine();
        Random random = new Random(404);

        float[] globalEmbedding = generateNormalizedVector(DIM, random);
        HierarchicalCommunityNode globalCommunity = HierarchicalCommunityNode.of(
                "comm-macro-01", 2, List.of("ent-cluster"), "全景综述", globalEmbedding, List.of()
        );
        engine.registerCommunity(globalCommunity);

        float[] localEmbedding = generateNormalizedVector(DIM, random);
        HierarchicalCommunityNode localCommunity = HierarchicalCommunityNode.of(
                "comm-micro-01", 0, List.of("ent-config-detail"),
                "【微观具体事实】内存最大缓冲区大小配置项为 buffer_size_mb=512",
                localEmbedding, List.of()
        );
        engine.registerCommunity(localCommunity);

        // 提出微观具体事实问题 (无总结关键词)
        String factualQuery = "内存最大缓冲区配置数值是多少？";
        GraphRagCommunityReceipt receipt = engine.retrieveCommunityKnowledge(factualQuery, localEmbedding);

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature());
        assertEquals("LOCAL_FACTUAL", receipt.queryIntent(), "具体事实意图必须精准识别为 LOCAL_FACTUAL");
        assertEquals(0, receipt.hitLevel(), "微观事实必须命中 Level 0 社区叶子节点");
        assertEquals("comm-micro-01", receipt.hitCommunityId());
        assertTrue(receipt.retrievedSummary().contains("buffer_size_mb=512"));
    }

    @Test
    @DisplayName("契约五：千问 1536 维超球面单位向量强类型与防侧信道时序攻击验签契约")
    public void testVectorDimensionAndReceiptSecurity() {
        Random random = new Random(505);
        float[] validVec = generateNormalizedVector(DIM, random);

        // 验证正常维度构造
        HierarchicalCommunityNode node = HierarchicalCommunityNode.of(
                "comm-test", 1, List.of("ent-1"), "测试报告", validVec, List.of()
        );
        assertNotNull(node);

        // 验证非法维度防御 (非 1536 维抛出 IllegalArgumentException)
        float[] invalidVec = new float[128];
        assertThrows(IllegalArgumentException.class, () -> HierarchicalCommunityNode.of(
                "comm-bad", 1, List.of("ent-1"), "非法维度测试", invalidVec, List.of()
        ), "非 1536 维千问向量必须被强类型防御拒绝");

        // 验证凭单伪造签名被常量时间防御拒绝
        GraphRagCommunityReceipt receipt = GraphRagCommunityReceipt.create(
                "查询", "LOCAL_FACTUAL", "comm-test", 1, "报告", List.of("ent-1"), 0.95f, 1000L
        );
        assertTrue(receipt.verifySignature());

        GraphRagCommunityReceipt forged = new GraphRagCommunityReceipt(
                receipt.query(), receipt.queryIntent(), receipt.hitCommunityId(), receipt.hitLevel(),
                receipt.retrievedSummary(), receipt.matchedEntityIds(), receipt.cosineSimilarity(),
                receipt.executionDurationNanos(), "forged_malicious_signature"
        );
        assertFalse(forged.verifySignature(), "伪造签名凭单必须被 MessageDigest.isEqual 常量时间拒绝");
    }

    private static float[] generateNormalizedVector(int dim, Random random) {
        float[] arr = new float[dim];
        float sumSq = 0.0f;
        for (int i = 0; i < dim; i++) {
            float v = random.nextFloat() * 2.0f - 1.0f;
            arr[i] = v;
            sumSq += v * v;
        }
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 1e-6f) {
            for (int i = 0; i < dim; i++) {
                arr[i] /= norm;
            }
        }
        return arr;
    }
}
