package tech.qiantong.qknow.ai.federated;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 57 多智能体拓扑流形收缩与联邦记忆蒸馏 专属契约单元测试
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
class Phase57FederatedExecutionContractTest {

    private TopologicalManifoldContractor manifoldContractor;
    private InformationBottleneckCompressor ibCompressor;
    private LocalMemoryDistiller localDistiller;
    private FederatedMemoryAggregator aggregator;
    private FederatedDistillationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        manifoldContractor = new TopologicalManifoldContractor();
        ibCompressor = new InformationBottleneckCompressor();
        localDistiller = new LocalMemoryDistiller();
        aggregator = new FederatedMemoryAggregator(0.01, 2); // 差分噪声 sigma=0.01, 最小法定 Quorum=2
        coordinator = new FederatedDistillationCoordinator(
                manifoldContractor, ibCompressor, localDistiller, aggregator
        );
    }

    private static float[] generateNormalizedVector(int seed) {
        float[] v = new float[1536];
        Random rnd = new Random(seed);
        float norm = 0.0f;
        for (int i = 0; i < 1536; i++) {
            v[i] = rnd.nextFloat() - 0.5f;
            norm += v[i] * v[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < 1536; i++) {
            v[i] /= norm;
        }
        return v;
    }

    @Test
    @DisplayName("契约测试 1: 凭单不可变性与 SHA-256 密码学自验一致性")
    void testReceiptIntegrityAndSha256Verification() {
        FederatedDistillationReceipt receipt = FederatedDistillationReceipt.create(
                "FED-001", "ROUND-100", List.of("AgentA", "AgentB", "AgentC"),
                100, 25, 0.75, "hash-meta-abc-123", System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "凭单 SHA-256 自验必须通过");

        FederatedDistillationReceipt tampered = new FederatedDistillationReceipt(
                receipt.receiptId(), receipt.roundId(), receipt.participatingAgentIds(),
                receipt.originalEdgesCount(), 99, // 篡改边数
                receipt.compressionRatio(), receipt.globalMetaMemoryHash(),
                receipt.timestamp(), receipt.receiptHash()
        );
        assertFalse(tampered.verifyIntegrity(), "被篡改数据的凭单验真必须失败");
    }

    @Test
    @DisplayName("契约测试 2: 谱图采样边数压缩率 ≥ 70% 且保持图连通性")
    void testTopologicalManifoldContractionSparsification() {
        // 构建 10 个智能体的全连接图：10 * 9 / 2 = 45 条边
        List<String> nodes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            nodes.add("Agent-" + i);
        }
        List<TopologicalManifoldContractor.Edge> originalEdges = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                originalEdges.add(new TopologicalManifoldContractor.Edge(nodes.get(i), nodes.get(j), 1.0));
            }
        }
        assertEquals(45, originalEdges.size());

        // 执行 70% 目标稀疏化收缩
        TopologicalManifoldContractor.ContractedGraph result =
                manifoldContractor.contractTopology(nodes, originalEdges, 0.70);

        assertNotNull(result);
        assertTrue(result.isConnected(), "收缩后的子图必须保持单连通");
        assertTrue(result.contractedEdgesCount() <= 14, "45 条边压缩 70% 后边数应 ≤ 14");
        assertTrue(result.actualCompressionRatio() >= 0.68, "实际边数压缩率应接近或达到 70%");
    }

    @Test
    @DisplayName("契约测试 3: 语义压缩率 ≥ 75% 达标")
    void testInformationBottleneckCompressionRatio() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append("经过我们团队的深入思考，在当前上下文环境下，正如之前所讨论的那样，" +
                    "总的来说，毫无疑问的是，我们可以看到系统当前的瓶颈所在。" +
                    "换句话说，需要特别说明的一点是，请注意当前处于多智能体长程反思循环阶段。");
        }
        sb.append("ACTION: OPTIMIZE_QUERY | SQL: SELECT * FROM knowledge_recall_log WHERE latency > 500 | " +
                "STATUS: EXECUTED | RESULT: SUCCESS | 85.5% | TABLE: kmc_segment | " +
                "基本上可以认为大体而言一切运行正常。");
        String verboseContext = sb.toString();

        InformationBottleneckCompressor.CompressedContext comp = ibCompressor.compress(verboseContext);

        assertNotNull(comp);
        assertTrue(comp.compressionRatio() >= 0.75, "长文本上下文压缩率应达到或超过 75%");
        assertTrue(comp.compressedText().startsWith("[SKELETON]"), "压缩骨架必须打上前缀标记");
    }

    @Test
    @DisplayName("契约测试 4: 关键因果实体与决策谓词 100% 无损保真")
    void testInformationBottleneckCausalEntityPreservation() {
        String context = "一些前置修饰词 ACTION: DEPLOY_MODEL 还有一些说明 SQL: UPDATE cluster_status SET active = 1 " +
                "最后结论 STATUS: HEALTHY RESULT: 100%";

        InformationBottleneckCompressor.CompressedContext comp = ibCompressor.compress(context);

        assertNotNull(comp);
        List<String> entities = comp.preservedCausalEntities();
        assertTrue(entities.stream().anyMatch(e -> e.contains("ACTION: DEPLOY_MODEL")));
        assertTrue(entities.stream().anyMatch(e -> e.contains("SQL: UPDATE cluster_status")));
        assertTrue(entities.stream().anyMatch(e -> e.contains("STATUS: HEALTHY")));
        assertTrue(entities.stream().anyMatch(e -> e.contains("100%")));
    }

    @Test
    @DisplayName("契约测试 5: 本地私有记忆映射至千问 1536 维超球面流形单位向量")
    void testLocalMemoryDistillationAnchorMapping() {
        List<String> privateMem = List.of(
                "User preferred Java 21 LTS",
                "SQL slow query fixed with indexing",
                "Memory leak avoided in netty"
        );
        float[] base = generateNormalizedVector(42);

        LocalMemoryDistiller.LocalInsight insight =
                localDistiller.distill("AgentAlpha", "优化策略推荐", privateMem, base);

        assertNotNull(insight);
        assertEquals("AgentAlpha", insight.agentId());
        assertEquals(3, insight.localMemoryCount());
        assertNotNull(insight.embeddingVector());
        assertEquals(1536, insight.embeddingVector().length);

        // 验证模长为 1.0 (超球面保模归一化)
        float norm = 0.0f;
        for (float v : insight.embeddingVector()) {
            norm += v * v;
        }
        assertEquals(1.0f, norm, 1e-4f, "输出向量必须严格归一化在超球面 ||v||_2 = 1.0");
    }

    @Test
    @DisplayName("契约测试 6: LDP 差分高斯扰动注入与保模重投影")
    void testFederatedAggregationLdpNoiseAndHypersphereProjection() {
        float[] v1 = generateNormalizedVector(101);
        float[] v2 = generateNormalizedVector(202);

        LocalMemoryDistiller.LocalInsight ins1 =
                new LocalMemoryDistiller.LocalInsight("A1", "AnchorQuery", v1, 5, 0.9);
        LocalMemoryDistiller.LocalInsight ins2 =
                new LocalMemoryDistiller.LocalInsight("A2", "AnchorQuery", v2, 8, 0.85);

        FederatedMemoryAggregator.GlobalMetaMemoryCard metaCard =
                aggregator.aggregate("ROUND-01", "AnchorQuery", List.of(ins1, ins2));

        assertNotNull(metaCard);
        assertEquals(2, metaCard.participatingAgentCount());
        assertEquals(1536, metaCard.aggregatedVector().length);

        float norm = 0.0f;
        for (float val : metaCard.aggregatedVector()) {
            norm += val * val;
        }
        assertEquals(1.0f, norm, 1e-4f, "聚合后向量必须重新完成超球面保模归一化");
        assertNotNull(metaCard.metaMemoryHash());
    }

    @Test
    @DisplayName("契约测试 7: 部分节点离线故障容灾与法定 Quorum 聚合放行")
    void testNodeDisconnectionFaultToleranceInFederation() {
        // 当参与节点为 1 个且法定 Quorum=2 时，抛出异常
        float[] v = generateNormalizedVector(999);
        LocalMemoryDistiller.LocalInsight ins =
                new LocalMemoryDistiller.LocalInsight("SoloAgent", "Anchor", v, 1, 0.8);

        assertThrows(IllegalStateException.class, () -> {
            aggregator.aggregate("ROUND-FAIL", "Anchor", List.of(ins));
        }, "节点数低于法定 Quorum 时必须抛出异常保护");

        // 当满足 Quorum=2 时正常放行
        LocalMemoryDistiller.LocalInsight ins2 =
                new LocalMemoryDistiller.LocalInsight("BackupAgent", "Anchor", v, 2, 0.85);
        FederatedMemoryAggregator.GlobalMetaMemoryCard card =
                aggregator.aggregate("ROUND-PASS", "Anchor", List.of(ins, ins2));
        assertNotNull(card);
    }

    @Test
    @DisplayName("契约测试 8: 全链路联邦记忆蒸馏与存证凭单签发闭环")
    void testEndToEndFederatedDistillationCoordinationSuccess() {
        List<String> nodes = List.of("Agent-1", "Agent-2", "Agent-3", "Agent-4");
        List<TopologicalManifoldContractor.Edge> edges = List.of(
                new TopologicalManifoldContractor.Edge("Agent-1", "Agent-2", 1.0),
                new TopologicalManifoldContractor.Edge("Agent-2", "Agent-3", 1.0),
                new TopologicalManifoldContractor.Edge("Agent-3", "Agent-4", 1.0),
                new TopologicalManifoldContractor.Edge("Agent-4", "Agent-1", 1.0),
                new TopologicalManifoldContractor.Edge("Agent-1", "Agent-3", 0.5),
                new TopologicalManifoldContractor.Edge("Agent-2", "Agent-4", 0.5)
        );

        String rawContext = "我们经过认真思考，ACTION: RUN_BENCHMARK | SQL: SELECT count(*) FROM memory_store | STATUS: PASS | 99.8%";
        Map<String, List<String>> privateMems = Map.of(
                "Agent-1", List.of("mem1", "mem2"),
                "Agent-2", List.of("mem3"),
                "Agent-3", List.of("mem4", "mem5"),
                "Agent-4", List.of("mem6")
        );
        Map<String, float[]> embeddings = Map.of(
                "Agent-1", generateNormalizedVector(1),
                "Agent-2", generateNormalizedVector(2),
                "Agent-3", generateNormalizedVector(3),
                "Agent-4", generateNormalizedVector(4)
        );

        FederatedDistillationCoordinator.DistillationRequest req =
                new FederatedDistillationCoordinator.DistillationRequest(
                        "ROUND-E2E-57", nodes, edges, rawContext, "公共知识库评测意图",
                        privateMems, embeddings
                );

        FederatedDistillationCoordinator.DistillationResult res = coordinator.executeRound(req);

        assertTrue(res.success(), "联邦记忆蒸馏全流程必须执行成功");
        assertNotNull(res.contractedGraph());
        assertTrue(res.contractedGraph().isConnected());
        assertNotNull(res.compressedContext());
        assertNotNull(res.globalMetaMemoryCard());
        assertNotNull(res.receipt());
        assertTrue(res.receipt().verifyIntegrity(), "全流程存证凭单自验必须有效");
    }
}
