package tech.qiantong.qknow.kb.biz.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.HypersphericalRrfReranker;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阿里千问 1536 维超球面 RRF 融合重排与 Token 预算截断算子契约测试
 */
public class HypersphericalRrfRerankerTest {

    private final HypersphericalRrfReranker reranker = new HypersphericalRrfReranker();

    private static RetrieveResult createTestChunk(String id, String content, Integer tokens, Double score) {
        RetrieveResult r = new RetrieveResult();
        r.setId(id);
        r.setContent(content);
        r.setTokens(tokens);
        r.setScore(score);
        return r;
    }

    /**
     * 生成千问 1536 维单位超球面向量 (||v||_2 = 1.0)
     */
    private static double[] createNormalizedVector(int seed) {
        double[] vec = new double[HypersphericalRrfReranker.EMBEDDING_DIM];
        Random rnd = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < vec.length; i++) {
            vec[i] = rnd.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < vec.length; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("测试千问 1536 维超球面单位向量校验与余弦几何恒等式")
    void testHypersphericalVectorGeometry() {
        double[] u = createNormalizedVector(42);
        double[] v = createNormalizedVector(99);

        assertTrue(reranker.isValidHypersphericalVector(u), "u 应为合法单位超球面向量");
        assertTrue(reranker.isValidHypersphericalVector(v), "v 应为合法单位超球面向量");

        // 自身内积余弦应为 1.0
        double cosSelf = reranker.computeCosineSimilarity(u, u);
        assertEquals(1.0, cosSelf, 1e-5);

        // 两向量内积在 [-1, 1] 之间
        double cosUV = reranker.computeCosineSimilarity(u, v);
        assertTrue(cosUV >= -1.0 && cosUV <= 1.0);

        // 验证超球面几何恒等式: d_Euc^2 = 2(1 - cos\theta)
        double eucSq = 0.0;
        for (int i = 0; i < u.length; i++) {
            double diff = u[i] - v[i];
            eucSq += diff * diff;
        }
        double expectedEucSq = 2.0 * (1.0 - cosUV);
        assertEquals(expectedEucSq, eucSq, 1e-4, "欧氏距离平方必须等于 2(1 - cos\\theta)");
    }

    @Test
    @DisplayName("测试多通道倒数排名融合 (RRF) 聚合排序")
    void testMultiChannelRrfRanking() {
        RetrieveResult c1 = createTestChunk("doc-A", "内容A", 50, 0.9);
        RetrieveResult c2 = createTestChunk("doc-B", "内容B", 50, 0.8);
        RetrieveResult c3 = createTestChunk("doc-C", "内容C", 50, 0.7);

        // 通道 1: Dense 稠密向量排名 [doc-A, doc-B, doc-C]
        HypersphericalRrfReranker.ChannelRankList denseChannel =
                new HypersphericalRrfReranker.ChannelRankList("dense", 1.0, List.of(c1, c2, c3));

        // 通道 2: Sparse 关键词排名 [doc-B, doc-A, doc-C]
        HypersphericalRrfReranker.ChannelRankList sparseChannel =
                new HypersphericalRrfReranker.ChannelRankList("sparse", 1.0, List.of(c2, c1, c3));

        HypersphericalRrfReranker.RrfRerankResult res = reranker.rerankMultiChannels(
                List.of(denseChannel, sparseChannel),
                null,
                null,
                60.0,
                0.0, // 纯 RRF
                1000
        );

        assertNotNull(res);
        assertEquals(3, res.selectedCount());
        // doc-A 与 doc-B 排名总和均为 1+2=3，doc-C 为 3+3=6
        // 因此 doc-A 与 doc-B 综合得分显著高于 doc-C
        List<HypersphericalRrfReranker.RerankedChunk> selected = res.selectedChunks();
        assertTrue(selected.get(0).finalScore() > selected.get(2).finalScore());
        assertEquals("doc-C", selected.get(2).chunk().getId());
    }

    @Test
    @DisplayName("测试超球面向量余弦相似度非线性加权调制")
    void testHypersphericalCosineModulation() {
        double[] qVec = createNormalizedVector(100);

        RetrieveResult c1 = createTestChunk("doc-1", "内容1", 60, 0.5);
        RetrieveResult c2 = createTestChunk("doc-2", "内容2", 60, 0.5);

        // 构造切片向量: doc-1 与查询向量极度接近 (余弦约等于 1.0)
        double[] v1 = qVec.clone();
        // doc-2 为随机向量
        double[] v2 = createNormalizedVector(200);

        Map<String, double[]> chunkVectors = Map.of(
                "doc-1", v1,
                "doc-2", v2
        );

        // 初始通道中将 doc-2 排在第1，doc-1 排在第2
        HypersphericalRrfReranker.ChannelRankList channel =
                new HypersphericalRrfReranker.ChannelRankList("default", 1.0, List.of(c2, c1));

        // 启用超球面调制 alpha = 0.5
        HypersphericalRrfReranker.RrfRerankResult res = reranker.rerankMultiChannels(
                List.of(channel),
                qVec,
                chunkVectors,
                60.0,
                0.5,
                1000
        );

        assertNotNull(res);
        // doc-1 凭借超高超球面余弦相似度逆袭登顶第一
        assertEquals("doc-1", res.selectedChunks().get(0).chunk().getId());
        assertNotNull(res.selectedChunks().get(0).cosineSimilarity());
        assertEquals(1.0, res.selectedChunks().get(0).cosineSimilarity(), 1e-4);
    }

    @Test
    @DisplayName("测试 Token 预算截断机制与溢出抛弃防护")
    void testTokenBudgetTruncation() {
        RetrieveResult c1 = createTestChunk("c1", "重要段落1", 60, 0.95);
        RetrieveResult c2 = createTestChunk("c2", "重要段落2", 50, 0.90);
        RetrieveResult c3 = createTestChunk("c3", "次要段落3", 40, 0.80);
        RetrieveResult c4 = createTestChunk("c4", "冗余段落4", 80, 0.70);

        List<RetrieveResult> list = List.of(c1, c2, c3, c4);

        // 设定最大预算为 120 Tokens (c1: 60 + c2: 50 = 110 Tokens，后续 c3: 40 将超额)
        HypersphericalRrfReranker.RrfRerankResult res = reranker.rerankAndTruncate(list, 120);

        assertNotNull(res);
        assertTrue(res.budgetExceeded(), "必须触发预算截断");
        assertEquals(2, res.selectedCount(), "仅前2个高分切片入选");
        assertEquals(2, res.droppedChunks().size(), "2个切片被截断丢弃");
        assertEquals(110, res.consumedTokens(), "消耗Token必须严格 <= 120");
        assertEquals("c1", res.selectedChunks().get(0).chunk().getId());
        assertEquals("c2", res.selectedChunks().get(1).chunk().getId());
        assertTrue(res.droppedChunks().get(0).truncated());
    }
}
