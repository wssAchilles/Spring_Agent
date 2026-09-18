package tech.qiantong.qknow.module.kmc.service.rag.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagSubgraphReasoner;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 105 契约测试集二：测地内积加权 2-跳局部诱导子图 Personalized PageRank 推理引擎测试
 *
 * @author Achilles
 * @version 1.0
 */
public class GraphRagSubgraphReasonerTest {

    private float[] createNormalizedSphericalVector(int dim, long seed) {
        Random rand = new Random(seed);
        float[] v = new float[dim];
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (rand.nextGaussian());
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约 4：种子实体高精度定位 (千问超球面余弦内积最大者精准召回)")
    void testSeedEntityLocalization() {
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();

        float[] queryEmbedding = createNormalizedSphericalVector(1536, 42L);

        // 构造实体池，其中 entity-target 与 queryEmbedding 极其相似
        List<GraphRagSubgraphReasoner.EntityNode> nodes = new ArrayList<>();
        nodes.add(new GraphRagSubgraphReasoner.EntityNode("e-other-1", "无关实体1", "CONCEPT", createNormalizedSphericalVector(1536, 100L)));
        nodes.add(new GraphRagSubgraphReasoner.EntityNode("e-other-2", "无关实体2", "CONCEPT", createNormalizedSphericalVector(1536, 200L)));

        // 构造几乎同向的 target 向量
        float[] targetVector = new float[1536];
        for (int i = 0; i < 1536; i++) {
            targetVector[i] = queryEmbedding[i] + 0.001f;
        }
        // 归一化
        double sumSq = 0.0;
        for (float v : targetVector) sumSq += v * v;
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) targetVector[i] /= norm;

        nodes.add(new GraphRagSubgraphReasoner.EntityNode("e-target", "核心靶向实体", "CORE", targetVector));

        GraphRagSubgraphReasoner.SubgraphReasoningResult result = reasoner.reasonSubgraph(queryEmbedding, nodes, Collections.emptyList());

        assertNotNull(result);
        assertEquals("e-target", result.seedEntityId(), "种子实体必须精准命中测地内积最大者");
        assertEquals(1, result.totalVisitedNodes());
        assertEquals("e-target", result.rankedEntityIds().get(0));
    }

    @Test
    @DisplayName("契约 5：2-跳局部诱导子图邻域抽取 (严格限制在 2 跳内，过滤低相似度边)")
    void testTwoHopNeighborhoodInduction() {
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();

        float[] queryEmbedding = createNormalizedSphericalVector(1536, 999L);

        List<GraphRagSubgraphReasoner.EntityNode> nodes = List.of(
                new GraphRagSubgraphReasoner.EntityNode("e-root", "根种子", "TYPE", queryEmbedding),
                new GraphRagSubgraphReasoner.EntityNode("e-hop1", "一跳邻居", "TYPE", createNormalizedSphericalVector(1536, 1L)),
                new GraphRagSubgraphReasoner.EntityNode("e-hop2", "二跳邻居", "TYPE", createNormalizedSphericalVector(1536, 2L)),
                new GraphRagSubgraphReasoner.EntityNode("e-hop3", "三跳邻居超限", "TYPE", createNormalizedSphericalVector(1536, 3L))
        );

        List<GraphRagSubgraphReasoner.RelationEdge> edges = List.of(
                new GraphRagSubgraphReasoner.RelationEdge("e-root", "e-hop1", "DIRECT_REL", 0.95),
                new GraphRagSubgraphReasoner.RelationEdge("e-hop1", "e-hop2", "EXTEND_REL", 0.88),
                new GraphRagSubgraphReasoner.RelationEdge("e-hop2", "e-hop3", "OVERFLOW_REL", 0.80)
        );

        GraphRagSubgraphReasoner.SubgraphReasoningResult result = reasoner.reasonSubgraph(queryEmbedding, nodes, edges);

        assertNotNull(result);
        assertEquals("e-root", result.seedEntityId());
        // 2-跳限制下只能访问到 root, hop1, hop2，三跳节点 hop3 必须被硬截断排除
        assertTrue(result.rankedEntityIds().contains("e-root"));
        assertTrue(result.rankedEntityIds().contains("e-hop1"));
        assertTrue(result.rankedEntityIds().contains("e-hop2"));
        assertFalse(result.rankedEntityIds().contains("e-hop3"), "3-跳超限节点必须被排除在局部诱导子图之外");
        assertEquals(3, result.totalVisitedNodes());
    }

    @Test
    @DisplayName("契约 6：PPR 幂迭代收敛性验证 (5 轮迭代收敛，概率守恒 sum(p_i)=1.0，耗时 <= 5.0ms)")
    void testPprConvergenceAndProbabilityConservation() {
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();

        float[] queryEmbedding = createNormalizedSphericalVector(1536, 777L);

        // 构建一个包含 50 个节点、120 条边的局部复杂子图
        List<GraphRagSubgraphReasoner.EntityNode> nodes = new ArrayList<>();
        nodes.add(new GraphRagSubgraphReasoner.EntityNode("seed", "种子节点", "ROOT", queryEmbedding));
        for (int i = 1; i < 50; i++) {
            nodes.add(new GraphRagSubgraphReasoner.EntityNode("n-" + i, "实体-" + i, "NODE", createNormalizedSphericalVector(1536, i)));
        }

        List<GraphRagSubgraphReasoner.RelationEdge> edges = new ArrayList<>();
        Random rand = new Random(12345L);
        for (int i = 1; i < 50; i++) {
            // 连接部分到 seed
            if (i <= 10) {
                edges.add(new GraphRagSubgraphReasoner.RelationEdge("seed", "n-" + i, "CONNECT", 0.85));
            }
            // 互联边
            int target = 1 + rand.nextInt(49);
            edges.add(new GraphRagSubgraphReasoner.RelationEdge("n-" + i, "n-" + target, "INTERACT", 0.75));
        }

        long startNs = System.nanoTime();
        GraphRagSubgraphReasoner.SubgraphReasoningResult result = reasoner.reasonSubgraph(queryEmbedding, nodes, edges);
        long elapsedUs = (System.nanoTime() - startNs) / 1000;

        assertNotNull(result);
        // 单步局部子图推理耗时严格有界 <= 5.0ms (5000us)
        assertTrue(elapsedUs <= 5000, "单步局部子图推理耗时必须 <= 5.0ms，实际耗时: " + elapsedUs + "us");

        // 校验 PPR 稳态概率总和是否守恒 sum(p_i) == 1.0 (容差 1e-4)
        double sumProb = 0.0;
        for (Double score : result.pprScoreMap().values()) {
            sumProb += score;
        }
        assertEquals(1.0, sumProb, 1e-4, "PPR 概率分布必须严格守恒且总和为 1.0");

        // 种子节点由于重启概率注入，其得分应显著处于顶尖梯队
        Double seedScore = result.pprScoreMap().get("seed");
        assertNotNull(seedScore);
        assertEquals("seed", result.rankedEntityIds().get(0), "种子实体应因高频重启位居第一");
    }

    @Test
    @DisplayName("契约 7：极端孤立节点与悬挂节点自适应归一化防御 (零 NaN、零除以零异常)")
    void testDanglingAndIsolatedNodesDefense() {
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();

        float[] queryEmbedding = createNormalizedSphericalVector(1536, 123L);

        // 极端场景：仅有一批孤立节点，毫无关系边
        List<GraphRagSubgraphReasoner.EntityNode> nodes = List.of(
                new GraphRagSubgraphReasoner.EntityNode("iso-1", "孤立节点1", "ISO", queryEmbedding),
                new GraphRagSubgraphReasoner.EntityNode("iso-2", "孤立节点2", "ISO", createNormalizedSphericalVector(1536, 2L))
        );

        GraphRagSubgraphReasoner.SubgraphReasoningResult result = reasoner.reasonSubgraph(queryEmbedding, nodes, Collections.emptyList());
        assertNotNull(result);
        assertEquals("iso-1", result.seedEntityId());
        for (Double score : result.pprScoreMap().values()) {
            assertFalse(score.isNaN(), "得分绝不能出现 NaN 异常");
            assertFalse(score.isInfinite(), "得分绝不能出现 Infinite 异常");
        }

        // 极端场景：空节点集
        GraphRagSubgraphReasoner.SubgraphReasoningResult emptyResult = reasoner.reasonSubgraph(queryEmbedding, Collections.emptyList(), Collections.emptyList());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.totalVisitedNodes());
    }
}
