package tech.qiantong.qknow.module.kmc.service.rag.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.StreamingPlaybackState;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagSubgraphReasoner;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.HierarchicalDocumentChunker;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.StreamingTypewriterAlignBuffer;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.service.SpatiotemporalKnowledgeMetacenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 105 契约测试集五：知识中枢全链路端到端协同集成测试
 * <p>
 * 契约 14：超长文档摄取分层解析 -> 语义命中父级展开 -> 2-跳局部子图 PPR 推理 ->
 * 时空流形对齐 -> 控制总线发布与凭单签发 -> 流式打字机闭环排空全链路协同集成验证
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase105KnowledgeMetacenterIntegrationTest {

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
    @DisplayName("契约 14：端到端长文档分层、局部诱导子图推理与流式打字机全链路闭环验证")
    void testEndToEndKnowledgeMetacenterPipeline() {
        SpatiotemporalKnowledgeMetacenter metacenter = new SpatiotemporalKnowledgeMetacenter();

        // 1. 文档摄取与分层树构建
        String docContent = """
                第一章 企业级智能体知识中枢架构
                知识中枢由自适应四级树状切片器、局部子图推理引擎以及流式打字机对齐缓冲器共同构成。
                该架构彻底攻克了传统长文档碎片化与全图社区爆炸两大致命瓶颈。

                第二章 运行时高保真度保证
                通过向上父指针展开，叶子节点能够在 50 微秒内无损回溯所属段落与章节。
                基于千问 1536 维超球面测地内积加权局部子图，5 轮迭代收敛，杜绝语义漫游。
                """;

        HierarchicalDocumentChunker.ChunkNode root = metacenter.ingestDocument("spec-doc-105", docContent);
        assertNotNull(root);
        assertEquals("doc-spec-doc-105", root.id());

        // 挑选一个第二章中的叶子句子
        String leafSentenceId = "doc-spec-doc-105-sec-2-p-1-s-1";

        // 2. 准备千问 1536 维超球面向量与知识图谱实体/关系
        float[] queryEmbedding = createNormalizedSphericalVector(1536, 8888L);

        List<GraphRagSubgraphReasoner.EntityNode> graphNodes = new ArrayList<>();
        graphNodes.add(new GraphRagSubgraphReasoner.EntityNode("ent-kmc", "知识中枢", "CORE_ARCH", queryEmbedding));
        graphNodes.add(new GraphRagSubgraphReasoner.EntityNode("ent-chunker", "四级树状切片器", "COMPONENT", createNormalizedSphericalVector(1536, 1111L)));
        graphNodes.add(new GraphRagSubgraphReasoner.EntityNode("ent-ppr", "局部子图推理", "COMPONENT", createNormalizedSphericalVector(1536, 2222L)));
        graphNodes.add(new GraphRagSubgraphReasoner.EntityNode("ent-align", "打字机对齐缓冲", "COMPONENT", createNormalizedSphericalVector(1536, 3333L)));

        List<GraphRagSubgraphReasoner.RelationEdge> graphEdges = List.of(
                new GraphRagSubgraphReasoner.RelationEdge("ent-kmc", "ent-chunker", "CONTAINS", 0.96),
                new GraphRagSubgraphReasoner.RelationEdge("ent-kmc", "ent-ppr", "CONTAINS", 0.94),
                new GraphRagSubgraphReasoner.RelationEdge("ent-ppr", "ent-align", "FEEDS", 0.89)
        );

        // 3. 执行端到端查询与推理
        SpatiotemporalKnowledgeMetacenter.MetacenterQueryResult result = metacenter.processQuery(
                "sess-pipeline-001",
                "请问知识中枢由哪些核心组件构成，且如何保证高保真度？",
                queryEmbedding,
                leafSentenceId,
                graphNodes,
                graphEdges
        );

        assertNotNull(result);
        assertEquals("sess-pipeline-001", result.sessionId());

        // 4. 验证防线一：父上下文无损展开
        HierarchicalDocumentChunker.ExpandedContext expanded = result.expandedContext();
        assertNotNull(expanded);
        assertTrue(expanded.parentSectionTitle().contains("运行时高保真度保证"));
        assertTrue(expanded.fullEnrichedContext().contains("【所属章节：第二章 运行时高保真度保证】"));
        assertTrue(expanded.contextPreservationScore() >= 0.95);

        // 5. 验证防线二：局部子图 PPR 推理
        GraphRagSubgraphReasoner.SubgraphReasoningResult reasoning = result.reasoningResult();
        assertNotNull(reasoning);
        assertEquals("ent-kmc", reasoning.seedEntityId(), "测地内积最大实体应精准作为根种子");
        assertEquals("ent-kmc", reasoning.rankedEntityIds().get(0));
        assertTrue(reasoning.totalVisitedNodes() >= 3);

        // 6. 验证防线三：不可变凭单与签名自验真
        assertNotNull(result.receipt());
        assertTrue(result.receipt().verifySignature(), "全链路存证凭单 SHA-256 自签名验真必须 100% 成立");
        assertEquals("BUS_HEALTHY", result.receipt().busStatus());

        // 7. 验证防线四：流式打字机平滑排空
        StreamingTypewriterAlignBuffer buffer = result.alignBuffer();
        assertNotNull(buffer);

        String streamResponse = "根据企业级知识中枢设计规范，该中枢由四级树状切片器、局部子图推理引擎以及流式打字机对齐缓冲器共同构成。";
        buffer.ingestChunk(streamResponse);
        buffer.markUpstreamComplete();

        StringBuilder reconstructed = new StringBuilder();
        int drainSteps = 0;
        while (buffer.getState() != StreamingPlaybackState.COMPLETED && drainSteps < 100) {
            reconstructed.append(buffer.drainStep(100));
            drainSteps++;
        }

        assertEquals(StreamingPlaybackState.COMPLETED, buffer.getState());
        assertEquals(streamResponse, reconstructed.toString(), "流式打字机最终吐字必须与大模型输出完全无损对齐");
    }
}
