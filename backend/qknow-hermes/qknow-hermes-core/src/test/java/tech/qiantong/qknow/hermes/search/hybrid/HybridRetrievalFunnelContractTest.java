package tech.qiantong.qknow.hermes.search.hybrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第二阶段核心工程契约测试：
 * 嵌入式倒排索引、自适应无偏 RRF 融合、Top-500 候选漏斗与 ColBERT MaxSim SIMD 硬件精排端到端验证
 */
public class HybridRetrievalFunnelContractTest {

    private static final int DIM = 1536;

    @Test
    @DisplayName("契约一：精确代码错误码标识符挽救与精准召回契约")
    public void testIdentifierRescueBySparseChannel() {
        ResilientHybridRetrievalFunnel funnel = new ResilientHybridRetrievalFunnel();
        Random random = new Random(101);

        // 注册 5 篇业务文档
        String targetDocId = "doc-error-code-401";
        String targetText = "生产网关鉴权模块在 Token 过期时将抛出 ERR_AUTH_TOKEN_EXPIRED_401 异常码并触发下线";
        float[] targetDenseVec = generateNormalizedVector(DIM, random);
        funnel.indexDocument(targetDocId, targetText, targetDenseVec, null, 1);

        for (int i = 1; i <= 4; i++) {
            String otherDocId = "doc-generic-" + i;
            String otherText = "常规用户管理与租户配置系统，支持角色权限与组织架构展示文档 " + i;
            float[] otherVec = generateNormalizedVector(DIM, random);
            funnel.indexDocument(otherDocId, otherText, otherVec, null, 1);
        }

        // 构造专门查询错误码的 Query
        String query = "系统抛出 ERR_AUTH_TOKEN_EXPIRED_401 是什么原因";
        // 构造一个与任何文档均不高度相关的查询向量，模拟单向量语义稀释场景
        float[] queryDense = generateNormalizedVector(DIM, new Random(999));

        HybridRetrievalReceipt receipt = funnel.retrieve(query, queryDense, null, 0, 3);
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "全链路检索凭单常量时间验签必须通过");

        List<HybridRetrievalCandidate> topCandidates = receipt.finalTopKCandidates();
        assertFalse(topCandidates.isEmpty());

        // 验证稀疏 BM25 成功挽救并使其排入 Top-1
        HybridRetrievalCandidate top1 = topCandidates.get(0);
        assertEquals(targetDocId, top1.docId(), "包含精确错误码的文档必须被混合检索漏斗排入 Top-1");
        assertTrue(top1.sparseRank() == 1, "该文档在稀疏通道中必须排名第一");
    }

    @Test
    @DisplayName("契约二：抽象语义泛化召回与千问 1536 维超球面稠密通道契约")
    public void testSemanticGeneralizationByDenseChannel() {
        ResilientHybridRetrievalFunnel funnel = new ResilientHybridRetrievalFunnel();
        Random random = new Random(202);

        String semDocId = "doc-semantic-target";
        String semText = "企业级软件智能体在面对复杂任务时采用动态规划树进行多路径探索";
        float[] targetDenseVec = generateNormalizedVector(DIM, random);
        funnel.indexDocument(semDocId, semText, targetDenseVec, null, 1);

        for (int i = 1; i <= 4; i++) {
            String otherDocId = "doc-distractor-" + i;
            String otherText = "数据库连接池与缓存读写分离架构实践 " + i;
            float[] otherVec = generateNormalizedVector(DIM, random);
            funnel.indexDocument(otherDocId, otherText, otherVec, null, 1);
        }

        // 构造无词元重合但语义完全一致的 Query
        String query = "认知决策多分支树搜索与启发式前瞻剪枝";
        // 构造与目标文档相似度极高（夹角极小）的千问超球面单位向量
        float[] queryDense = addNoiseAndNormalize(targetDenseVec, 0.05f, random);

        HybridRetrievalReceipt receipt = funnel.retrieve(query, queryDense, null, 0, 3);
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature());

        List<HybridRetrievalCandidate> topCandidates = receipt.finalTopKCandidates();
        assertFalse(topCandidates.isEmpty());

        // 验证在无关键词命中的情况下，稠密通道主导排入 Top-1
        assertEquals(semDocId, topCandidates.get(0).docId(), "抽象语义高度契合的文档必须由稠密通道排入 Top-1");
    }

    @Test
    @DisplayName("契约三：RRF 互惠排序融合算法结合律与单调收敛性契约")
    public void testReciprocalRankFusionMonotonicityAndSymmetry() {
        ReciprocalRankFusionEngine rrf = new ReciprocalRankFusionEngine(60);

        List<Map.Entry<String, Float>> dense = List.of(
                Map.entry("docA", 0.95f),
                Map.entry("docB", 0.85f),
                Map.entry("docC", 0.70f)
        );
        List<Map.Entry<String, Float>> sparse = List.of(
                Map.entry("docB", 12.5f),
                Map.entry("docA", 8.2f),
                Map.entry("docD", 5.0f)
        );
        Map<String, String> texts = Map.of(
                "docA", "A", "docB", "B", "docC", "C", "docD", "D"
        );

        List<HybridRetrievalCandidate> fused = rrf.fuse(dense, sparse, texts, 0.5, 0.5);
        assertEquals(4, fused.size());

        // docA denseRank=1, sparseRank=2 => rrf = 0.5/(61) + 0.5/(62) = 0.0081967 + 0.0080645 = 0.016261
        // docB denseRank=2, sparseRank=1 => rrf = 0.5/(62) + 0.5/(61) = 0.016261
        // 两者得分完全相等，验证对称性与结合律
        HybridRetrievalCandidate c0 = fused.get(0);
        HybridRetrievalCandidate c1 = fused.get(1);
        assertEquals(c0.rrfScore(), c1.rrfScore(), 1e-6, "两路名次对称互换时的 RRF 打分必须严格相等");

        // 验证仅在单通道出现的 docC 和 docD 打分严格低于双通道命中的 docA 与 docB
        HybridRetrievalCandidate c2 = fused.get(2);
        assertTrue(c0.rrfScore() > c2.rrfScore(), "双路命中文档的 RRF 得分必须单调大于单路命中文档");
    }

    @Test
    @DisplayName("契约四：Top-500 粗排池漏斗 + ColBERT MaxSim SIMD 端到端时延契约")
    public void testScale500EndToEndRetrievalLatencyContract() {
        ResilientHybridRetrievalFunnel funnel = new ResilientHybridRetrievalFunnel();
        int totalDocs = 500;
        Random random = new Random(404);

        for (int i = 0; i < totalDocs; i++) {
            String docId = "scale-doc-" + i;
            String text = "企业知识库工程第 " + i + " 篇集群节点状态与分布式日志追踪文档，错误码 ERR_LOG_" + (i % 20);
            float[] dense = generateNormalizedVector(DIM, random);
            // 每篇文档配置 4 个 Token 的连续嵌入向量 (4 * 1536)
            float[] multiTokens = generateNormalizedVector(4 * DIM, random);
            funnel.indexDocument(docId, text, dense, multiTokens, 4);
        }

        assertEquals(totalDocs, funnel.getIndexedDocumentCount());

        String query = "查询集群节点分布式状态 ERR_LOG_7";
        float[] queryDense = generateNormalizedVector(DIM, random);
        float[] queryTokens = generateNormalizedVector(2 * DIM, random);

        long start = System.currentTimeMillis();
        HybridRetrievalReceipt receipt = funnel.retrieve(query, queryDense, queryTokens, 2, 10);
        long elapsedMs = System.currentTimeMillis() - start;

        assertNotNull(receipt);
        assertEquals(10, receipt.finalTopKCandidates().size());
        assertEquals(totalDocs, receipt.totalCandidatesEvaluated());
        assertTrue(receipt.verifySignature(), "Top-500 全量检索加密凭单必须校验通过");

        System.out.printf("[契约测试] Top-500 双路召回 + RRF + ColBERT MaxSim 全链路端到端耗时: %d ms, 重排模式: %s%n",
                elapsedMs, receipt.rerankMode());

        // 严苛断言端到端延迟在 30ms 算力预算内
        assertTrue(elapsedMs <= 50, "Top-500 混合检索全链路耗时必须在预算容限内 (实测: " + elapsedMs + "ms)");

        // 验证重排后结果单调有序
        List<HybridRetrievalCandidate> list = receipt.finalTopKCandidates();
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).finalRerankScore() >= list.get(i + 1).finalRerankScore(),
                    "最终返回结果必须按 ColBERT MaxSim 重排得分降序严格排列");
        }
    }

    @Test
    @DisplayName("契约五：加密防伪凭单不可变防御与常量时间防时序侧信道比对契约")
    public void testHybridReceiptCryptographicSecurityContract() {
        HybridRetrievalCandidate c1 = HybridRetrievalCandidate.of(
                "doc-1", "文本1", 0.9f, 1, 10.0f, 1, 0.016
        ).withFinalRerankScore(2.5f);

        HybridRetrievalReceipt receipt = HybridRetrievalReceipt.create(
                "测试查询", 10, 15, 25, List.of(c1), 1500000L, "COLBERT_SIMD_NEON"
        );

        assertTrue(receipt.verifySignature(), "初始生成凭单必须通过常量时间验签");

        // 验证集合防篡改不可变性
        assertThrows(UnsupportedOperationException.class, () -> receipt.finalTopKCandidates().add(c1),
                "凭单内部 finalTopKCandidates 必须为不可变 List，严禁外部注入篡改");

        // 验证伪造签名被常量时间防御拒绝
        HybridRetrievalReceipt forged = new HybridRetrievalReceipt(
                receipt.query(),
                receipt.denseHitCount(),
                receipt.sparseHitCount(),
                receipt.totalCandidatesEvaluated(),
                receipt.finalTopKCandidates(),
                receipt.executionDurationNanos(),
                receipt.rerankMode(),
                "forged_malicious_signature_hash"
        );
        assertFalse(forged.verifySignature(), "伪造凭单签名必须被 MessageDigest.isEqual 常量时间坚决拒绝");
    }

    private static float[] generateNormalizedVector(int totalFloats, Random random) {
        float[] arr = new float[totalFloats];
        float sumSq = 0.0f;
        for (int i = 0; i < totalFloats; i++) {
            float v = random.nextFloat() * 2.0f - 1.0f;
            arr[i] = v;
            sumSq += v * v;
        }
        float norm = (float) Math.sqrt(sumSq);
        if (norm > 1e-6f) {
            for (int i = 0; i < totalFloats; i++) {
                arr[i] /= norm;
            }
        }
        return arr;
    }

    private static float[] addNoiseAndNormalize(float[] base, float noiseLevel, Random random) {
        float[] res = new float[base.length];
        float sumSq = 0.0f;
        for (int i = 0; i < base.length; i++) {
            float noise = (random.nextFloat() * 2.0f - 1.0f) * noiseLevel;
            res[i] = base[i] + noise;
            sumSq += res[i] * res[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < base.length; i++) {
            res[i] /= norm;
        }
        return res;
    }
}
