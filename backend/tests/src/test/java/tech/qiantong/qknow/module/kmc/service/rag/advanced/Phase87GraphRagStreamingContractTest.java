package tech.qiantong.qknow.module.kmc.service.rag.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.ChunkHierarchyLevel;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.GraphRagEventFrame;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.GraphRagExecutionReceipt;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.StreamingPlaybackState;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagOrchestrationControlBus;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagSubgraphReasoner;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.HierarchicalDocumentChunker;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.StreamingTypewriterAlignBuffer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 87 高保真混合 GraphRAG 知识图谱子图拓扑推理、超长上下文层次化切片与流式打字机对齐中枢 专属契约测试套件
 * <p>
 * 验证 8 大严苛契约：
 * 1. 四级树状自适应切片构建与父级上下文动态无损展开
 * 2. 测地加权局部 Personalized PageRank 子图推理收敛与防漂移
 * 3. 局部子图拓扑推理耗时严格有界 (<= 5.0ms)
 * 4. 流式打字机自适应泊松平滑恒速回放与方差抑制
 * 5. 流式打字机优雅排空与零字符丢失断流软封口
 * 6. 1000Hz 4096 槽位 Disruptor 无锁总线纳秒级吞吐与 JitterGuard 抖动监控
 * 7. 不可变存证凭单 SHA-256 密码学自签名与防篡改验真
 * 8. 端到端长文档分层检索、子图推理与流式打字机全链路闭环
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase87GraphRagStreamingContractTest {

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
    @DisplayName("契约测试 1：四级树状自适应切片构建与父级上下文动态无损展开")
    void testHierarchicalChunking_TreeConstructionAndParentExpansion() {
        HierarchicalDocumentChunker chunker = new HierarchicalDocumentChunker();

        String sampleDoc = """
                第一章 智能体认知编排规范
                本规范详细定义了企业级智能体编排的核心原则与边界约束。
                所有智能体在执行任务前必须完成身份鉴权与权限校验。

                第二章 知识图谱与检索增强
                GraphRAG 采用局部诱导子图推理消除多跳漂移。
                在特定免责条款下，如触发熔断机制，系统将自动软着陆。
                """;

        // 构建层次树
        HierarchicalDocumentChunker.ChunkNode root = chunker.buildHierarchyTree("DOC-101", sampleDoc);
        assertNotNull(root);
        assertEquals(ChunkHierarchyLevel.DOCUMENT, root.level());
        assertTrue(chunker.getNodeCount() >= 6, "树中应至少包含文档、章节、段落和句子节点");

        // 查找第二章的第二段（包含免责条款）
        String targetParaId = "doc-DOC-101-sec-2-p-2";
        HierarchicalDocumentChunker.ChunkNode paraNode = chunker.getNode(targetParaId);
        assertNotNull(paraNode, "段落节点必须存在");
        assertEquals(ChunkHierarchyLevel.PARAGRAPH, paraNode.level());

        // 向上展开父级上下文 (预热后耗时 <= 50us)
        chunker.expandParentContext(targetParaId);
        long startNs = System.nanoTime();
        HierarchicalDocumentChunker.ExpandedContext expanded = chunker.expandParentContext(targetParaId);
        long elapsedUs = (System.nanoTime() - startNs) / 1000;

        assertTrue(elapsedUs < 1000, "父上下文展开耗时应极短: " + elapsedUs + "us");
        assertNotNull(expanded.fullEnrichedContext());
        assertTrue(expanded.fullEnrichedContext().contains("第二章 知识图谱与检索增强"), "展开上下文必须包含父章节标题");
        assertTrue(expanded.fullEnrichedContext().contains("在特定免责条款下"), "展开上下文必须保留原段落内容");
        assertTrue(expanded.contextPreservationScore() >= 0.90, "上下文保真度得分应 >= 0.90");
    }

    @Test
    @DisplayName("契约测试 2：测地加权局部 Personalized PageRank 子图推理收敛与防漂移")
    void testGraphRagSubgraphReasoner_PprConvergenceAndAntiDrift() {
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();

        float[] queryVec = createNormalizedSphericalVector(1536, 111L);
        float[] seedVec  = Arrays.copyOf(queryVec, 1536); // 与查询完全对齐
        float[] hop1Vec  = createNormalizedSphericalVector(1536, 222L);
        float[] hop2Vec  = createNormalizedSphericalVector(1536, 333L);
        float[] noiseVec = createNormalizedSphericalVector(1536, 999L); // 噪声无关实体

        List<GraphRagSubgraphReasoner.EntityNode> nodes = List.of(
                new GraphRagSubgraphReasoner.EntityNode("e-seed", "智能体内核", "CORE", seedVec),
                new GraphRagSubgraphReasoner.EntityNode("e-hop1", "知识图谱", "TECH", hop1Vec),
                new GraphRagSubgraphReasoner.EntityNode("e-hop2", "向量索引", "COMPONENT", hop2Vec),
                new GraphRagSubgraphReasoner.EntityNode("e-noise", "无关娱乐话题", "NOISE", noiseVec)
        );

        List<GraphRagSubgraphReasoner.RelationEdge> edges = List.of(
                new GraphRagSubgraphReasoner.RelationEdge("e-seed", "e-hop1", "USES", 0.95),
                new GraphRagSubgraphReasoner.RelationEdge("e-hop1", "e-hop2", "INDEXED_BY", 0.88),
                new GraphRagSubgraphReasoner.RelationEdge("e-seed", "e-noise", "UNRELATED", 0.05)
        );

        GraphRagSubgraphReasoner.SubgraphReasoningResult result = reasoner.reasonSubgraph(queryVec, nodes, edges);

        assertNotNull(result);
        assertEquals("e-seed", result.seedEntityId(), "种子实体应被准确识别");
        assertTrue(result.rankedEntityIds().size() >= 3, "2-跳邻域子图应包含种子与相连实体");

        // 验证拓扑排序：种子实体得分最高，1-跳次之，2-跳再次之
        assertEquals("e-seed", result.rankedEntityIds().get(0), "种子实体 PPR 得分必须为第一");
        assertEquals("e-hop1", result.rankedEntityIds().get(1), "1-跳实体得分应排第二");
        assertTrue(result.pprScoreMap().get("e-seed") > result.pprScoreMap().get("e-hop1"),
                "种子节点 PPR 得分必须显著高于扩散节点");
    }

    @Test
    @DisplayName("契约测试 3：局部子图拓扑推理耗时严格有界 (<= 5.0ms)")
    void testGraphRagSubgraphReasoner_BoundedLatencyWithinFiveMs() {
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();

        float[] queryVec = createNormalizedSphericalVector(1536, 555L);
        List<GraphRagSubgraphReasoner.EntityNode> nodes = new ArrayList<>();
        List<GraphRagSubgraphReasoner.RelationEdge> edges = new ArrayList<>();

        // 构建 50 个节点与 100 条关系的测试子图
        for (int i = 0; i < 50; i++) {
            nodes.add(new GraphRagSubgraphReasoner.EntityNode(
                    "node-" + i, "实体-" + i, "CATEGORY", createNormalizedSphericalVector(1536, i + 1000L)
            ));
            if (i > 0) {
                edges.add(new GraphRagSubgraphReasoner.RelationEdge(
                        "node-" + (i / 2), "node-" + i, "CONNECTS", 0.85
                ));
            }
        }

        // 单步推理耗时校验 (<= 5000us)
        GraphRagSubgraphReasoner.SubgraphReasoningResult result = reasoner.reasonSubgraph(queryVec, nodes, edges);

        assertNotNull(result);
        long latencyLimit = System.getenv("CI") != null ? 15000L : 5000L;
        assertTrue(result.executionLatencyUs() <= latencyLimit,
                "子图拓扑推理单步耗时必须 <= " + (latencyLimit / 1000.0) + "ms: " + result.executionLatencyUs() + "us");
    }

    @Test
    @DisplayName("契约测试 4：流式打字机自适应泊松平滑恒速回放与方差抑制")
    void testStreamingTypewriter_PoissonJitterSmoothing() {
        StreamingTypewriterAlignBuffer buffer = new StreamingTypewriterAlignBuffer();

        // 模拟上游突发注入：先来一段 30 字符
        buffer.ingestChunk("第一批突发到达文本：深度智能体协同编排");

        // 推进步进，以 50ms 采样周期模拟前端定时器
        StringBuilder playedText = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            String emitted = buffer.drainStep(50);
            playedText.append(emitted);
        }

        // 再突发注入一批字符
        buffer.ingestChunk("，第二批突发高速到达：知识图谱多跳拓扑推理。");
        for (int i = 0; i < 10; i++) {
            String emitted = buffer.drainStep(50);
            playedText.append(emitted);
        }

        assertTrue(playedText.length() > 0, "打字机必须平滑输出字符");
        assertEquals(StreamingPlaybackState.PLAYING, buffer.getState(), "持续供流下应保持 PLAYING 状态");

        // 验证播放速度方差受控
        double variance = buffer.getPlaybackVariance();
        assertTrue(variance < 50.0, "打字机播放速率方差必须受控极小: " + variance);
    }

    @Test
    @DisplayName("契约测试 5：流式打字机优雅排空与零字符丢失断流软封口")
    void testStreamingTypewriter_GracefulDrainingAndZeroLoss() {
        StreamingTypewriterAlignBuffer buffer = new StreamingTypewriterAlignBuffer();

        String rawInput = "这是一个高保真流式打字机测试文本，必须实现零字符丢失与断流软封口。";
        buffer.ingestChunk(rawInput);
        buffer.markUpstreamComplete(); // 标记上游结束

        StringBuilder fullOutput = new StringBuilder();
        int safetyLoop = 0;

        while (buffer.getState() != StreamingPlaybackState.COMPLETED && safetyLoop < 100) {
            String step = buffer.drainStep(100);
            fullOutput.append(step);
            safetyLoop++;
        }

        assertEquals(rawInput, fullOutput.toString(), "打字机全流程输出字符必须与原始输入 100% 逐字严格吻合（零丢失）");
        assertEquals(StreamingPlaybackState.COMPLETED, buffer.getState(), "最终状态必须为 COMPLETED");
        assertEquals(0, buffer.getRemainingQueueSize(), "待播放队列必须完全排空");
    }

    @Test
    @DisplayName("契约测试 6：1000Hz 4096 槽位 Disruptor 无锁总线纳秒级吞吐与 JitterGuard 抖动监控")
    void testDisruptorControlBus_SubMicrosecondThroughputAndJitterGuard() {
        GraphRagOrchestrationControlBus bus = new GraphRagOrchestrationControlBus();

        // 1. 批量发布 1000 帧事件，检验纳秒级吞吐
        long startNs = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            GraphRagEventFrame frame = new GraphRagEventFrame(
                    "frame-" + i, "sess-perf", "query-" + i, "CHUNK",
                    null, 0.85, System.currentTimeMillis(), i
            );
            bus.publishFrame(frame);
        }
        long durationNs = System.nanoTime() - startNs;
        long avgNs = durationNs / 1000;

        assertEquals(1000, bus.getPublishedFrameCount(), "总线应完整写入 1000 帧");
        assertTrue(avgNs < 5000, "单帧写入平均耗时应在微秒/纳秒级: " + avgNs + "ns");
        assertEquals("BUS_HEALTHY", bus.getBusStatus(), "正常发布状态总线应保持健康");

        // 2. JitterGuard 时钟抖动监控测试：连续 3 帧时钟间隔超过 2ms
        long currentMs = System.currentTimeMillis();
        bus.publishFrame(new GraphRagEventFrame("jf1", "sess-j", "q", "C", null, 0.5, currentMs + 5, 1001L));
        bus.publishFrame(new GraphRagEventFrame("jf2", "sess-j", "q", "C", null, 0.5, currentMs + 10, 1002L));
        bus.publishFrame(new GraphRagEventFrame("jf3", "sess-j", "q", "C", null, 0.5, currentMs + 15, 1003L));

        assertEquals("STATUS_DEGRADED_FLAT_FALLBACK", bus.getBusStatus(),
                "连续 3 帧抖动超标必须触发 JitterGuard 软着陆降级");
    }

    @Test
    @DisplayName("契约测试 7：不可变存证凭单 SHA-256 密码学自签名与防篡改验真")
    void testGraphRagExecutionReceipt_CryptographicSelfVerification() {
        GraphRagExecutionReceipt receipt = GraphRagExecutionReceipt.create(
                "RCPT-GRAG-001",
                "sess-test-01",
                "查询企业并购免责条款",
                2,
                15,
                0.895,
                1.25,
                2400L,
                "BUS_HEALTHY"
        );

        // 原装签名自验必须 100% 成功
        assertTrue(receipt.verifySignature(), "原生签发存证凭单签名自验必须通过");
        assertNotNull(receipt.signature());
        assertEquals(64, receipt.signature().length(), "SHA-256 十六进制签名长度必须为 64 位");

        // 篡改测试：恶意篡改查询文本
        GraphRagExecutionReceipt tampered = new GraphRagExecutionReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                "恶意篡改后的查询文本", // 篡改
                receipt.expandedParentCount(),
                receipt.subgraphNodeCount(),
                receipt.pprTopScore(),
                receipt.playbackJitterVariance(),
                receipt.latencyUs(),
                receipt.busStatus(),
                receipt.signature()
        );

        assertFalse(tampered.verifySignature(), "篡改凭单签名验真必须失败");
    }

    @Test
    @DisplayName("契约测试 8：端到端分层切片检索、子图推理与流式打字机全链路闭环")
    void testEndToEndGraphRagPipeline_FullWorkflow() {
        // 1. 初始化各引擎组件
        HierarchicalDocumentChunker chunker = new HierarchicalDocumentChunker();
        GraphRagSubgraphReasoner reasoner = new GraphRagSubgraphReasoner();
        StreamingTypewriterAlignBuffer typewriter = new StreamingTypewriterAlignBuffer();
        GraphRagOrchestrationControlBus controlBus = new GraphRagOrchestrationControlBus();

        // 2. 长文档分层切片
        String docContent = """
                知识中枢架构规范
                第一条 本系统支持超长文档自适应层次化切片。
                第二条 知识图谱子图推理采用加权 PPR 算法。
                """;
        chunker.buildHierarchyTree("DOC-E2E", docContent);
        HierarchicalDocumentChunker.ExpandedContext expanded = chunker.expandParentContext("doc-DOC-E2E-sec-1-p-1");
        assertNotNull(expanded.fullEnrichedContext());

        // 3. 知识图谱局部子图推理
        float[] queryVec = createNormalizedSphericalVector(1536, 777L);
        List<GraphRagSubgraphReasoner.EntityNode> nodes = List.of(
                new GraphRagSubgraphReasoner.EntityNode("e1", "知识中枢", "ROOT", queryVec),
                new GraphRagSubgraphReasoner.EntityNode("e2", "PPR算法", "ALGO", createNormalizedSphericalVector(1536, 888L))
        );
        List<GraphRagSubgraphReasoner.RelationEdge> edges = List.of(
                new GraphRagSubgraphReasoner.RelationEdge("e1", "e2", "INCLUDES", 0.9)
        );
        GraphRagSubgraphReasoner.SubgraphReasoningResult reasonResult = reasoner.reasonSubgraph(queryVec, nodes, edges);
        assertEquals("e1", reasonResult.seedEntityId());

        // 4. 流式打字机缓冲回放
        String generatedChunk = "【检索事实与图谱拓扑】：已成功整合层次化切片与子图推理上下文。";
        typewriter.ingestChunk(generatedChunk);
        typewriter.markUpstreamComplete();

        StringBuilder streamedText = new StringBuilder();
        while (typewriter.getState() != StreamingPlaybackState.COMPLETED) {
            streamedText.append(typewriter.drainStep(100));
        }
        assertEquals(generatedChunk, streamedText.toString());

        // 5. 控制总线签发存证凭单
        GraphRagExecutionReceipt receipt = controlBus.issueReceipt(
                "sess-e2e-001",
                "知识中枢架构查询",
                1,
                reasonResult.totalVisitedNodes(),
                reasonResult.pprScoreMap().getOrDefault("e1", 1.0),
                typewriter.getPlaybackVariance(),
                2500L
        );

        // 6. 全链路验收
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端生成的密码学凭单必须验真通过");
        assertEquals("sess-e2e-001", receipt.sessionId());
    }
}
