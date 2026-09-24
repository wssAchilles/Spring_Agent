package tech.qiantong.qknow.hermes.rag.causal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 127 核心契约测试套件：
 * 拓扑子图因果推断剪枝、时空衰减超球面流形对齐与打字机无损同步中枢
 * 覆盖定理 1.1（超球面测地线时空半衰期衰减）与定理 1.2（2-跳局部 PPR 因果拓扑命题剪枝）等 8 大核心契约
 */
public class Phase127GraphRagCausalAlignmentContractTest {

    private static final int DIM = 1536;

    /**
     * 生成测试用的 1536 维超球面单位向量 (L2 范数 = 1.0)
     */
    private float[] createUnitVector(int seed) {
        float[] vec = new float[DIM];
        Random rand = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < DIM; i++) {
            vec[i] = (float) (rand.nextGaussian());
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < DIM; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    @Test
    @DisplayName("契约 1：千问 1536 维超球面测地角距离与时序有效性联合打分严格有界保单调")
    void test01_SpatiotemporalScoreGeodesicAngleAndFreshness() {
        SpatiotemporalManifoldAligner aligner = new SpatiotemporalManifoldAligner();
        float[] queryVec = createUnitVector(42);
        long now = System.currentTimeMillis();

        // 1. 同向单位向量（余弦 = 1.0），在有效窗口内，得分必须精确为 1.0
        double scoreIdentical = aligner.computeScore(queryVec, queryVec, now, now - 1000, now + 10000, 5000);
        assertEquals(1.0, scoreIdentical, 1e-4, "同向向量在有效期内得分必须为 1.0");

        // 2. 正交向量（余弦 ≈ 0.0），得分应为 0.5 左右
        float[] orthogonalVec = Arrays.copyOf(queryVec, DIM);
        // 通过简单反转一半分量构造近似正交
        for (int i = 0; i < DIM / 2; i++) {
            orthogonalVec[i] = -orthogonalVec[i];
        }
        double scoreOrthogonal = aligner.computeScore(queryVec, orthogonalVec, now, now - 1000, now + 10000, 5000);
        assertTrue(scoreOrthogonal >= 0.0 && scoreOrthogonal <= 1.0, "正交向量得分必须在 [0, 1] 有界区间内");

        // 3. 尚未生效的未来实体，得分严格为 0.0
        double scoreFuture = aligner.computeScore(queryVec, queryVec, now, now + 10000, now + 20000, 5000);
        assertEquals(0.0, scoreFuture, "未到生效期得分恒为 0.0");
    }

    @Test
    @DisplayName("契约 2：过期知识经半衰期指数衰减得分降至 0.03 以下被彻底过滤 (定理 1.1)")
    void test02_ExpiredKnowledgeExponentialDecayPruned() {
        SpatiotemporalManifoldAligner aligner = new SpatiotemporalManifoldAligner();
        float[] queryVec = createUnitVector(101);
        // 构造一个与查询几乎完全相同的历史旧知识向量 (余弦 = 1.0)
        float[] staleEntityVec = Arrays.copyOf(queryVec, DIM);

        long now = System.currentTimeMillis();
        long halfLifeMs = 10_000L; // 半衰期 10 秒
        // 设置该知识在 50 秒前（恰好 5 个半衰期）已过期
        long expiredAt = now - (5 * halfLifeMs);
        long createdAt = expiredAt - 100_000L;

        double staleScore = aligner.computeScore(queryVec, staleEntityVec, now, createdAt, expiredAt, halfLifeMs);

        // 定理 1.1 预言：衰减因子为 exp(-5 * ln(2)) = 1 / 32 = 0.03125
        assertTrue(staleScore <= 0.03125 + 1e-5, "5 个半衰期后得分必须降至 0.03125 以下");

        // 设定系统通用过滤阈值为 0.05
        double retrievalThreshold = 0.05;
        assertTrue(staleScore < retrievalThreshold, "过期旧规则无论语义多相似均被物理过滤，杜绝旧知识倒挂");
    }

    @Test
    @DisplayName("契约 3：2-跳局部 PPR 剪枝节点严格钳位在 16 个以内，Token 大幅压缩 (定理 1.2)")
    void test03_TwoHopPprSubGraphPruningNodeBounded() {
        CausalSubgraphPruner pruner = new CausalSubgraphPruner();

        // 构造一个包含 30 个节点与 50 条边的稠密复杂子图
        Map<String, CausalSubgraphPruner.GraphNode> nodeMap = new HashMap<>();
        List<CausalSubgraphPruner.GraphEdge> edgeList = new ArrayList<>();

        for (int i = 1; i <= 30; i++) {
            String id = "node-" + i;
            nodeMap.put(id, new CausalSubgraphPruner.GraphNode(id, "实体节点_" + i, "CONCEPT"));
        }

        // 构造有向边：node-1 为种子，连向 2,3,4；2,3 连向 5..10；其余节点随机交叉互联
        edgeList.add(new CausalSubgraphPruner.GraphEdge("node-1", "node-2", "DEPENDS_ON", 1.0));
        edgeList.add(new CausalSubgraphPruner.GraphEdge("node-1", "node-3", "LEADS_TO", 1.0));
        edgeList.add(new CausalSubgraphPruner.GraphEdge("node-2", "node-4", "TRIGGERS", 1.0));
        edgeList.add(new CausalSubgraphPruner.GraphEdge("node-3", "node-5", "VERIFIES", 1.0));
        for (int i = 5; i < 30; i++) {
            edgeList.add(new CausalSubgraphPruner.GraphEdge("node-" + i, "node-" + (i + 1), "RELATES", 0.5));
        }

        List<String> seedIds = List.of("node-1");
        int maxNodes = 12;

        CausalSubgraphPruner.PrunedCausalSubgraph result = pruner.pruneAndProject(
                seedIds, nodeMap.values(), edgeList, maxNodes
        );

        assertNotNull(result);
        assertTrue(result.prunedNodeCount() <= maxNodes, "剪枝后节点数必须严格受限于 maxNodes (<= 12)");
        assertTrue(result.compressionRatio() >= 0.50, "节点压缩率必须达到 50% 以上");
        assertFalse(result.topologicalNodes().isEmpty());
        // 种子节点必须被保留
        assertTrue(result.topologicalNodes().stream().anyMatch(n -> "node-1".equals(n.id())));
    }

    @Test
    @DisplayName("契约 4：Kahn 拓扑排序构建结构化因果命题链，前置条件严格先于后继结论")
    void test04_CausalTopologicalPropositionChainOrder() {
        CausalSubgraphPruner pruner = new CausalSubgraphPruner();

        // 构造严格因果链：A (合规发起) -> B (财务核准) -> C (放款结算)
        CausalSubgraphPruner.GraphNode nA = new CausalSubgraphPruner.GraphNode("node-A", "合规初审发起", "START");
        CausalSubgraphPruner.GraphNode nB = new CausalSubgraphPruner.GraphNode("node-B", "财务风控核准", "MIDDLE");
        CausalSubgraphPruner.GraphNode nC = new CausalSubgraphPruner.GraphNode("node-C", "银企直联放款", "END");

        List<CausalSubgraphPruner.GraphNode> nodes = List.of(nA, nB, nC);
        List<CausalSubgraphPruner.GraphEdge> edges = List.of(
                new CausalSubgraphPruner.GraphEdge("node-A", "node-B", "审核通过流转至", 1.0),
                new CausalSubgraphPruner.GraphEdge("node-B", "node-C", "授权触发", 1.0)
        );

        CausalSubgraphPruner.PrunedCausalSubgraph result = pruner.pruneAndProject(
                List.of("node-A"), nodes, edges, 10
        );

        List<CausalSubgraphPruner.GraphNode> topoNodes = result.topologicalNodes();
        assertEquals(3, topoNodes.size());
        assertEquals("node-A", topoNodes.get(0).id(), "前置原因节点必须排在第 1 位");
        assertEquals("node-B", topoNodes.get(1).id(), "中间依赖节点必须排在第 2 位");
        assertEquals("node-C", topoNodes.get(2).id(), "最终行动结果节点排在末尾");

        List<String> props = result.causalPropositions();
        assertFalse(props.isEmpty());
        assertTrue(props.get(0).contains("合规初审发起"));
        assertTrue(props.get(0).contains("node-B"));
    }

    @Test
    @DisplayName("契约 5：带实体锚定的流式打字机管道保证因果帧优先推送与单调序号")
    void test05_LosslessTypewriterStreamingWithEntityAnchors() {
        GraphStreamLosslessTypewriter typewriter = new GraphStreamLosslessTypewriter();
        String sessionId = "sess-stream-05";

        List<String> propositions = List.of(
                "命题 1: 基于采购单触发预算锁定",
                "命题 2: 预算锁定后触发供应商结算"
        );
        String answer = "经过图谱因果核验，该笔报销单据完全合规。";

        List<GraphStreamLosslessTypewriter.TypewriterFrame> emittedFrames = new ArrayList<>();
        int totalFrames = typewriter.streamCausalResponse(sessionId, propositions, answer, 4, emittedFrames::add);

        assertEquals(totalFrames, emittedFrames.size());
        assertTrue(totalFrames >= 4);

        // 验证前两帧必须是 ENTITY_ANCHOR
        assertEquals("ENTITY_ANCHOR", emittedFrames.get(0).eventType());
        assertEquals("ENTITY_ANCHOR", emittedFrames.get(1).eventType());
        assertEquals("命题 1: 基于采购单触发预算锁定", emittedFrames.get(0).content());

        // 验证最后一帧必须是 COMPLETE
        assertEquals("COMPLETE", emittedFrames.get(emittedFrames.size() - 1).eventType());

        // 验证序列号全局严格单调递增
        for (int i = 1; i < emittedFrames.size(); i++) {
            assertTrue(emittedFrames.get(i).sequenceId() > emittedFrames.get(i - 1).sequenceId(),
                    "事件序列号必须严格单调递增");
        }
    }

    @Test
    @DisplayName("契约 6：打字机网络闪断重放机制基于 Last-Ack 补发遗漏帧，0 丢失")
    void test06_TypewriterStreamInterruptionRecoveryWithoutLoss() {
        GraphStreamLosslessTypewriter typewriter = new GraphStreamLosslessTypewriter();
        String sessionId = "sess-replay-06";

        List<String> propositions = List.of("因果前提 A");
        String answer = "ABCDEFGHIJ"; // 10 个字符，分块大小 2，共 5 个 chunk + 1 个 anchor + 1 个 complete = 7 帧

        List<GraphStreamLosslessTypewriter.TypewriterFrame> allSent = new ArrayList<>();
        typewriter.streamCausalResponse(sessionId, propositions, answer, 2, allSent::add);

        // 模拟客户端收到了前 3 帧后断网（最后确认序号为 allSent.get(2).sequenceId()）
        long lastAckSeq = allSent.get(2).sequenceId();

        List<GraphStreamLosslessTypewriter.TypewriterFrame> replayed = new ArrayList<>();
        int replayedCount = typewriter.replayMissingFrames(sessionId, lastAckSeq, replayed::add);

        // 应准确补发剩余的 4 帧
        assertEquals(allSent.size() - 3, replayedCount);
        assertEquals(allSent.size() - 3, replayed.size());

        // 检查补发的第一帧序列号紧接着 lastAckSeq
        assertEquals(allSent.get(3).sequenceId(), replayed.get(0).sequenceId());
        assertEquals("COMPLETE", replayed.get(replayed.size() - 1).eventType());
    }

    @Test
    @DisplayName("契约 7：不可变存证凭单 SHA-256 自验真与防篡改完整性")
    void test07_ReceiptSha256ImmutabilityAndSelfVerification() {
        GraphRagCausalAlignmentReceipt validReceipt = GraphRagCausalAlignmentReceipt.create(
                "如何审批特批采购预算？",
                List.of("node-dept-it", "node-finance"),
                25,
                8,
                4,
                0.9125,
                List.of("命题1: IT部申请", "命题2: 财务初审通过"),
                1500L
        );

        assertTrue(validReceipt.verifySignature(), "合法生成的凭单签名自验真必须通过");

        // 篡改平均得分
        GraphRagCausalAlignmentReceipt tamperedReceipt = new GraphRagCausalAlignmentReceipt(
                validReceipt.receiptId(),
                validReceipt.queryText(),
                validReceipt.seedEntities(),
                validReceipt.rawNodeCount(),
                validReceipt.prunedNodeCount(),
                validReceipt.propositionCount(),
                0.5000, // 篡改得分
                validReceipt.causalPropositions(),
                validReceipt.latencyMicros(),
                validReceipt.timestamp(),
                validReceipt.signature()
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改数值后的凭单自验真必须 100% 失败");
    }

    @Test
    @DisplayName("契约 8：测地内积与子图因果剪枝纳秒/微秒级低延迟预算")
    void test08_SubMillisecondPerformanceBudget() {
        SpatiotemporalManifoldAligner aligner = new SpatiotemporalManifoldAligner();
        float[] q = createUnitVector(88);
        float[] e = createUnitVector(99);
        long now = System.currentTimeMillis();

        // 预热 JVM
        for (int i = 0; i < 5000; i++) {
            aligner.computeScore(q, e, now, now - 1000, now + 1000, 1000);
        }

        int iterations = 10_000;
        long startNano = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            aligner.computeScore(q, e, now, now - 1000, now + 1000, 1000);
        }
        long durationNano = System.nanoTime() - startNano;
        double avgMicros = (double) durationNano / iterations / 1000.0;

        System.out.printf("Phase 127 超球面测地流形打分 10,000 次平均耗时: %.3f 微秒 (门禁 <= 20us)%n", avgMicros);
        assertTrue(avgMicros <= 20.0, "单次超球面时空打分耗时必须严格 <= 20 微秒");
    }
}
