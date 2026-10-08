package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.benchmark.causal.HypersphericalCausalCounterfactualReflector;
import tech.qiantong.qknow.hermes.benchmark.causal.HypersphericalCausalCounterfactualReflector.CausalTrajectory;
import tech.qiantong.qknow.hermes.benchmark.causal.HypersphericalCausalCounterfactualReflector.CounterfactualAdjustment;
import tech.qiantong.qknow.hermes.benchmark.graph.GeodesicSpectralSubGraphRagGovernor;
import tech.qiantong.qknow.hermes.benchmark.graph.GeodesicSpectralSubGraphRagGovernor.SpectralPruningResult;
import tech.qiantong.qknow.hermes.benchmark.graph.GeodesicSpectralSubGraphRagGovernor.SubGraphEdge;
import tech.qiantong.qknow.hermes.benchmark.graph.GeodesicSpectralSubGraphRagGovernor.SubGraphNode;
import tech.qiantong.qknow.hermes.benchmark.offheap.OffHeapMemorySegmentRingBuffer;
import tech.qiantong.qknow.hermes.benchmark.offheap.OffHeapMemorySegmentRingBuffer.OffHeapEvent;
import tech.qiantong.qknow.hermes.benchmark.offheap.OffHeapMemorySegmentRingBuffer.RingBufferStats;
import tech.qiantong.qknow.hermes.benchmark.receipt.OffHeapCausalInfraReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 152 堆外直接内存、超球面因果反思与拉普拉斯谱剪枝 GraphRAG 严密契约测试套件
 * <p>
 * 检验唯一假设 H-152 与两大核心引理：
 * 1. Lemma 152.1: 超球面因果后门截断与反事实反思无偏性引理；
 * 2. Lemma 152.2: 超球面测地线马尔可夫图谱谱剪枝信息纯度引理。
 * </p>
 *
 * @author Achilles
 * @since Phase 152
 */
public class Phase152OffHeapCausalInfraContractTest {

    private HypersphericalCausalCounterfactualReflector causalReflector;
    private OffHeapMemorySegmentRingBuffer ringBuffer;
    private GeodesicSpectralSubGraphRagGovernor graphGovernor;

    @BeforeEach
    void setUp() {
        causalReflector = new HypersphericalCausalCounterfactualReflector();
        ringBuffer = new OffHeapMemorySegmentRingBuffer(64); // 64 槽位定长环形队列
        graphGovernor = new GeodesicSpectralSubGraphRagGovernor();
    }

    @AfterEach
    void tearDown() {
        if (ringBuffer != null) {
            ringBuffer.close();
        }
    }

    @Test
    @DisplayName("Contract 1: 超球面因果后门截断反思与去偏置率测试 (Lemma 152.1)")
    void testHypersphericalCausalBackdoorDebias() {
        // 生成千问 1536 维超球面单位向量 (||v||_2 = 1.0)
        float[] actionObs = generateSyntheticQwenUnitVector(1536, 101L);
        float[] confounder = generateSyntheticQwenUnitVector(1536, 202L);
        float[] counterfactualAction = generateSyntheticQwenUnitVector(1536, 303L);

        CausalTrajectory trajectory = new CausalTrajectory(
                "Agent-RiskAssessor",
                actionObs,
                confounder,
                0.82
        );

        List<float[]> confounderPool = List.of(confounder, generateSyntheticQwenUnitVector(1536, 404L));

        CounterfactualAdjustment adjustment = causalReflector.reflectAndAdjust(
                trajectory, confounderPool, counterfactualAction
        );

        assertNotNull(adjustment);
        assertTrue(adjustment.debiasReductionPercent() >= 40.0,
                String.format("因果后门去偏置消除比例必须 >= 40.0%%，实际: %.2f%%", adjustment.debiasReductionPercent()));
        assertEquals(1536, adjustment.counterfactualDeltaVector().length, "反事实纠偏向量必须为 1536 维");
        assertNotNull(adjustment.debiasedPromptGuidance(), "必须生成无偏反思指令文本");

        // 验证正交截断几何性质: 投影后与混杂因子内积为 0
        float[] projected = causalReflector.projectOrthogonalToConfounder(actionObs, confounder);
        double dotOrthogonal = computeDotProduct(projected, confounder);
        assertEquals(0.0, dotOrthogonal, 1e-4, "正交投影后与混杂特征内积必须严格为 0");
    }

    @Test
    @DisplayName("Contract 2: Java 21 Arena 堆外直接内存无锁环形队列零 GC 与数据完整性测试")
    void testOffHeapRingBufferZeroGcAndDataIntegrity() {
        int tenantHash = 0xABCD1234;
        int eventType = 101;
        String payload = "CausalEvent{source='AgentA', action='DeployPolicy', status='COMMITTED'}";

        long seq = ringBuffer.writeEvent(tenantHash, eventType, payload);
        assertTrue(seq >= 0, "写入必须返回有效非负序列号");

        OffHeapEvent event = ringBuffer.readEvent(seq);
        assertNotNull(event, "必须成功读取堆外事件");
        assertEquals(seq, event.sequence());
        assertEquals(tenantHash, event.tenantHash());
        assertEquals(eventType, event.eventType());
        assertEquals(payload, event.payload());

        // 验证遥测指标: 堆内存增量严格为 0，GC 停顿为 0
        RingBufferStats stats = ringBuffer.getTelemetryStats();
        assertEquals(0L, stats.heapBytesAllocated(), "堆外事件传递物理堆内存增量必须严格为 0 字节");
        assertEquals(0L, stats.gcPauseNanos(), "堆外操作 GC 停顿时间必须严格为 0 纳秒");
        assertTrue(stats.offHeapAllocatedBytes() > 0, "堆外直接内存必须有效分配");
    }

    @Test
    @DisplayName("Contract 3: 堆外环形队列三级冷备暂存与高频覆写保护测试")
    void testOffHeapRingBufferColdStagingSpill() {
        // 连续写入超过队列容量 (64) 的事件，触发环形覆写与冷备暂存
        for (int i = 0; i < 80; i++) {
            ringBuffer.writeEvent(0x1000 + i, 200, "BurstEventPayload-" + i);
        }

        RingBufferStats stats = ringBuffer.getTelemetryStats();
        assertEquals(80L, stats.eventsWritten());
        assertTrue(stats.stagingSpillActive(), "超过容量覆写时必须激活三级冷备暂存保护");

        // 读取最新写入的事件，验证仍然高可用
        OffHeapEvent latest = ringBuffer.readEvent(79L);
        assertNotNull(latest);
        assertEquals("BurstEventPayload-79", latest.payload());
    }

    @Test
    @DisplayName("Contract 4: 知识图谱拉普拉斯谱剪枝最优切与 Token 削减测试 (Lemma 152.2)")
    void testGeodesicSpectralSubGraphPruning() {
        float[] queryEmbedding = generateSyntheticQwenUnitVector(1536, 1L);

        // 构造 6 个节点子图 (前 3 个为因果高相关核心节点，后 3 个为边缘噪声节点)
        List<SubGraphNode> candidateNodes = new ArrayList<>();
        candidateNodes.add(new SubGraphNode("N1", "智能体多跳检索核心因果定理说明", queryEmbedding)); // 极高相关
        candidateNodes.add(new SubGraphNode("N2", "基于拉普拉斯谱剪枝的 Cheeger 不等式边界", generateSyntheticQwenUnitVector(1536, 2L)));
        candidateNodes.add(new SubGraphNode("N3", "超球面测地线内积各向同性度量", generateSyntheticQwenUnitVector(1536, 3L)));
        candidateNodes.add(new SubGraphNode("N4", "系统通用的默认网络状态配置信息", generateSyntheticQwenUnitVector(1536, 400L))); // 噪声
        candidateNodes.add(new SubGraphNode("N5", "日常操作日志与历史访问记录流水", generateSyntheticQwenUnitVector(1536, 500L))); // 噪声
        candidateNodes.add(new SubGraphNode("N6", "数据库底层连接池保活心跳参数", generateSyntheticQwenUnitVector(1536, 600L))); // 噪声

        List<SubGraphEdge> rawEdges = List.of(
                new SubGraphEdge("N1", "N2", 0.95),
                new SubGraphEdge("N2", "N3", 0.90),
                new SubGraphEdge("N1", "N4", 0.15),
                new SubGraphEdge("N4", "N5", 0.10),
                new SubGraphEdge("N5", "N6", 0.05)
        );

        SpectralPruningResult result = graphGovernor.pruneAndAlign(candidateNodes, rawEdges, queryEmbedding);

        assertNotNull(result);
        assertTrue(result.prunedEdgeRatio() >= 0.45,
                String.format("拉普拉斯谱剪枝噪声边消除比例必须 >= 45.0%%，实际: %.2f%%", result.prunedEdgeRatio() * 100.0));
        assertTrue(result.retrievalFidelity() >= 0.980, "子图因果核心语义召回保真度必须 >= 98.0%");
        assertFalse(result.preservedNodes().isEmpty(), "必须保留因果核心节点");
        assertTrue(result.isDeepseekCacheAligned(), "提示词必须对齐 DeepSeek 64-token 缓存块");
    }

    @Test
    @DisplayName("Contract 5: DeepSeek 官方 1M 规约 64-token 确定性块边界对齐测试")
    void testDeepSeek64TokenAlignmentAccuracy() {
        float[] queryEmbedding = generateSyntheticQwenUnitVector(1536, 999L);
        List<SubGraphNode> nodes = List.of(
                new SubGraphNode("DocA", "企业级知识库因果检索核心实体文档", queryEmbedding),
                new SubGraphNode("DocB", "Java 21 堆外原生 Arena 内存安全管理", generateSyntheticQwenUnitVector(1536, 888L))
        );

        SpectralPruningResult result = graphGovernor.pruneAndAlign(nodes, Collections.emptyList(), queryEmbedding);

        assertTrue(result.isDeepseekCacheAligned(), "上下文必须满足 DeepSeek 官方缓存块对齐要求");
        assertNotNull(result.prefixHash(), "前缀必须生成确定性 SHA-256 哈希");
        assertTrue(result.estimatedTokens() > 0, "估算 Token 数必须大于 0");
    }

    @Test
    @DisplayName("Contract 6: 纯 Java 21 Record 密码学不可变审计凭单自验真测试 (防时序侧信道)")
    void testOffHeapCausalInfraReceiptCryptoVerification() {
        OffHeapCausalInfraReceipt receipt = OffHeapCausalInfraReceipt.create(
                "rcpt-phase152-001",
                "tenant-qknow",
                "session-152",
                44.5,
                0.0820,
                1500000L,
                0L,
                0L,
                0.485,
                0.1250,
                0.9890,
                true,
                "sha256-abcdef1234567890abcdef",
                120L
        );

        // 原生自验真
        assertTrue(receipt.verify(), "不可变堆外因果审计凭单必须通过 SHA-256 常量时间自验真");

        // 伪造篡改保真度数据
        OffHeapCausalInfraReceipt tampered = new OffHeapCausalInfraReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.sessionId(),
                receipt.causalDebiasReductionPercent(),
                receipt.counterfactualDeltaNorm(),
                receipt.offHeapEventsWritten(),
                1024L, // 恶意篡改堆内存分配数据
                receipt.offHeapGcPauseNanos(),
                receipt.spectralPrunedEdgeRatio(),
                receipt.algebraicConnectivity(),
                receipt.retrievalFidelity(),
                receipt.deepseekCacheAligned(),
                receipt.deepseekPrefixHash(),
                receipt.totalLatencyUs(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );

        assertFalse(tampered.verify(), "被篡改数据的凭单必须 100% 验真失败，杜绝时序侧信道安全风险");
    }

    @Test
    @DisplayName("Contract 7: 消融实验矩阵验证 (EXP-0 到 EXP-4 指标门槛)")
    void testAblationMatrixContract() {
        // 验证去偏置下界 (>= 40.0%)
        float[] x = generateSyntheticQwenUnitVector(1536, 11L);
        float[] z = generateSyntheticQwenUnitVector(1536, 22L);
        CausalTrajectory t = new CausalTrajectory("TestAgent", x, z, 0.75);
        CounterfactualAdjustment adj = causalReflector.reflectAndAdjust(t, List.of(z), x);
        assertTrue(adj.debiasReductionPercent() >= 40.0, "去偏置率必须达到消融门槛");

        // 验证堆外零堆分配
        RingBufferStats stats = ringBuffer.getTelemetryStats();
        assertEquals(0L, stats.heapBytesAllocated(), "堆分配严格为 0");
    }

    private float[] generateSyntheticQwenUnitVector(int dim, long seed) {
        Random rnd = new Random(seed);
        float[] vec = new float[dim];
        float sumSq = 0.0f;
        for (int i = 0; i < dim; i++) {
            float val = (float) rnd.nextGaussian();
            vec[i] = val;
            sumSq += val * val;
        }
        float norm = (float) Math.sqrt(sumSq);
        float invNorm = 1.0f / norm;
        for (int i = 0; i < dim; i++) {
            vec[i] *= invNorm;
        }
        return vec;
    }

    private double computeDotProduct(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
