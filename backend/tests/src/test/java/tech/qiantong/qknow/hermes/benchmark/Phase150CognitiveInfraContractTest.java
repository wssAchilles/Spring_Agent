package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.rag.adaptive.EpistemicEntropyAdaptiveRagGovernor;
import tech.qiantong.qknow.ai.rag.adaptive.EpistemicEntropyAdaptiveRagGovernor.AdaptiveRagPath;
import tech.qiantong.qknow.ai.rag.adaptive.EpistemicEntropyAdaptiveRagGovernor.AdaptiveRoutingDecision;
import tech.qiantong.qknow.hermes.benchmark.concurrent.StructuredLoomConcurrencyEngine;
import tech.qiantong.qknow.hermes.benchmark.concurrent.StructuredLoomConcurrencyEngine.StructuredExecutionResult;
import tech.qiantong.qknow.hermes.benchmark.receipt.CognitiveInfraAuditReceipt;
import tech.qiantong.qknow.hermes.benchmark.simd.SimdVectorHypersphericalKernel;
import tech.qiantong.qknow.hermes.swarm.vib.HypersphericalVibCommunicationCompressor;
import tech.qiantong.qknow.hermes.swarm.vib.HypersphericalVibCommunicationCompressor.AgentMessageFrame;
import tech.qiantong.qknow.hermes.swarm.vib.HypersphericalVibCommunicationCompressor.VibCompressedCommunication;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 150 认知基础设施、现代 Java 21 运行时与自适应 RAG 门控严密契约测试套件
 * <p>
 * 检验假设 H-150 与 Lemma 150.1 / 150.2：
 * 1. 结构化虚拟线程并发原子快速短路与孤儿任务截断测试；
 * 2. 首胜竞速并发短路测试；
 * 3. 阿里千问 1536 维超球面 SIMD 硬件极速计算与几何公理恒等式测试；
 * 4. 变分信息瓶颈 (VIB) 认知通信压缩与 Shapley 因果信用归因测试；
 * 5. 局部语义认知熵与超球面曲率自适应 RAG 门控测试；
 * 6. 纯 Java 21 Record 密码学不可变存证凭单与常量时间防侧信道自验真测试；
 * 7. 消融实验矩阵（SIMD 对比标量数值一致性与性能收益）。
 * </p>
 *
 * @author Achilles
 * @since Phase 150
 */
public class Phase150CognitiveInfraContractTest {

    private StructuredLoomConcurrencyEngine concurrencyEngine;
    private SimdVectorHypersphericalKernel simdKernel;
    private HypersphericalVibCommunicationCompressor vibCompressor;
    private EpistemicEntropyAdaptiveRagGovernor adaptiveRagGovernor;

    @BeforeEach
    void setUp() {
        concurrencyEngine = new StructuredLoomConcurrencyEngine();
        simdKernel = new SimdVectorHypersphericalKernel();
        vibCompressor = new HypersphericalVibCommunicationCompressor(simdKernel);
        adaptiveRagGovernor = new EpistemicEntropyAdaptiveRagGovernor();
    }

    @AfterEach
    void tearDown() {
        if (concurrencyEngine != null) {
            concurrencyEngine.close();
        }
    }

    @Test
    @DisplayName("Contract 1: 结构化并发原子级联取消与孤儿任务阻断测试 (All-Or-Nothing)")
    void testStructuredConcurrencyShortCircuitAndOrphanCancellation() {
        AtomicBoolean orphanTaskExecutedToEnd = new AtomicBoolean(false);

        List<Callable<String>> tasks = new ArrayList<>();
        // 9 个慢速任务（模拟慢速工具或长对话 LLM 调用，耗时 800ms）
        for (int i = 0; i < 9; i++) {
            tasks.add(() -> {
                try {
                    Thread.sleep(800);
                    orphanTaskExecutedToEnd.set(true);
                    return "slow_success";
                } catch (InterruptedException e) {
                    // 预期被结构化短路取消中断
                    return "interrupted_cancelled";
                }
            });
        }

        // 1 个快速失败任务（模拟安全护栏熔断或参数错误，30ms 抛错）
        tasks.add(() -> {
            Thread.sleep(30);
            throw new IllegalStateException("护栏快速熔断拦截");
        });

        long startMs = System.currentTimeMillis();
        Exception exception = assertThrows(Exception.class, () -> {
            concurrencyEngine.executeAllOrShortCircuit(tasks, 5000);
        });
        long elapsedMs = System.currentTimeMillis() - startMs;

        // 断言 1: 异常类型正确传出
        assertTrue(exception.getMessage().contains("护栏快速熔断拦截") || exception instanceof IllegalStateException);
        // 断言 2: 毫秒级短路，总耗时远小于 800ms（预期在 200ms 以内完成短路中断）
        assertTrue(elapsedMs < 600, "结构化并发必须在毫秒级快速短路，实际耗时: " + elapsedMs + "ms");
        // 断言 3: 孤儿慢速任务未跑完全程，被有效阻断
        assertFalse(orphanTaskExecutedToEnd.get(), "孤儿任务必须被级联中断，严禁跑完全程浪费 Token");
        // 断言 4: 孤儿任务阻断计数大于 0
        assertTrue(concurrencyEngine.getOrphanTasksPreventedTotal() > 0, "应记录到被阻断的孤儿任务数量");
    }

    @Test
    @DisplayName("Contract 2: 首胜竞速短路截断测试 (Race-To-First / ShutdownOnSuccess)")
    void testStructuredConcurrencyRaceToFirst() throws Exception {
        List<Callable<String>> branches = new ArrayList<>();

        // 慢速候选分支 (耗时 500ms)
        for (int i = 0; i < 4; i++) {
            final int id = i;
            branches.add(() -> {
                Thread.sleep(500);
                return "slow_branch_" + id;
            });
        }

        // 极速有效分支 (耗时 25ms)
        branches.add(() -> {
            Thread.sleep(25);
            return "fast_champion_branch";
        });

        long startMs = System.currentTimeMillis();
        String champion = concurrencyEngine.executeRaceToFirst(branches, 2000);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertEquals("fast_champion_branch", champion);
        assertTrue(elapsedMs < 400, "首胜竞速应在快速分支完成后立即返回，实际耗时: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("Contract 3: 千问 1536 维超球面 SIMD 向量内核精度与几何公理测试")
    void testSimdVectorHypersphericalKernelGeometryAndPerformance() {
        int dim = SimdVectorHypersphericalKernel.EMBEDDING_DIM;
        assertEquals(1536, dim);

        // 生成测试向量 A 与 B
        double[] vecA = generateNormalizedVector(dim, 101);
        double[] vecB = generateNormalizedVector(dim, 202);

        // 1. 验证单位球范数公理 ||v||_2 = 1.0
        double normA = Math.sqrt(simdKernel.cosineSimilarity(vecA, vecA));
        double normB = Math.sqrt(simdKernel.cosineSimilarity(vecB, vecB));
        assertEquals(1.0, normA, 1e-4, "超球面向量 A 范数必须严格为 1.0");
        assertEquals(1.0, normB, 1e-4, "超球面向量 B 范数必须严格为 1.0");

        // 2. 验证测地余弦与欧氏几何恒等式：d_Euc^2 = 2(1 - cos\theta)
        double cosine = simdKernel.cosineSimilarity(vecA, vecB);
        double eucDist = simdKernel.euclideanDistance(vecA, vecB);
        double expectedEucSq = 2.0 * (1.0 - cosine);
        assertEquals(expectedEucSq, eucDist * eucDist, 1e-5, "欧氏距离与余弦内积必须严格满足几何变换恒等式");

        // 3. 验证 Gram-Schmidt 正交投影
        double[] orthoB = simdKernel.gramSchmidtOrthogonalize(vecB, List.of(vecA));
        double orthoOverlap = simdKernel.cosineSimilarity(vecA, orthoB);
        assertEquals(0.0, orthoOverlap, 1e-5, "Gram-Schmidt 正交投影后内积必须严格为 0");

        // 4. 连续 500 次高频微基准压测，度量平均计算耗时
        long startNs = System.nanoTime();
        int iterations = 500;
        double dummyAccumulator = 0.0;
        for (int i = 0; i < iterations; i++) {
            dummyAccumulator += simdKernel.cosineSimilarity(vecA, vecB);
        }
        long durationNs = System.nanoTime() - startNs;
        double avgUs = (durationNs / (double) iterations) / 1000.0;

        assertTrue(dummyAccumulator != 0.0);
        // 单次 1536 维向量点积耗时必须压制在 300 微秒 (0.3ms) 以内
        assertTrue(avgUs <= 300.0, "单次 1536 维向量余弦点积耗时应 <= 300us，实测平均耗时: " + avgUs + "us");
    }

    @Test
    @DisplayName("Contract 4: 变分信息瓶颈 (VIB) 认知通信压缩与 Shapley 因果信用测试 (Lemma 150.1)")
    void testVibCommunicationCompressionAndShapleyCredits() {
        int dim = 1536;
        double[] targetVec = generateNormalizedVector(dim, 999);

        List<AgentMessageFrame> frames = List.of(
                new AgentMessageFrame("agent-intent", "意图解耦专员", "识别到查询核心涉及成绩总评平时与期末比例条款",
                        generateNearVector(targetVec, 0.88, 11), 180),
                new AgentMessageFrame("agent-retrieval", "知识检索专员", "从知识库中精准召回教务规程第三条考核占比切片",
                        generateNearVector(targetVec, 0.92, 22), 240),
                new AgentMessageFrame("agent-chitchat", "闲聊兜底专员", "今天天气不错很高兴为您服务这是一段冗余文本",
                        generateNearVector(targetVec, 0.15, 33), 320),
                new AgentMessageFrame("agent-arbitrator", "综合仲裁专员", "仲裁平时40%期末60%且无违纪事实完备可信",
                        generateNearVector(targetVec, 0.95, 44), 210)
        );

        VibCompressedCommunication vibResult = vibCompressor.compressAndAssignCredits(frames, targetVec, 2.0);

        // 断言 1: 通信带宽压缩率达标 (>= 50.0%)
        assertTrue(vibResult.compressionRatio() >= 0.50, "VIB 压缩率应 >= 50%，实际: " + vibResult.compressionRatio());
        // 断言 2: 保留互信息达标
        assertTrue(vibResult.mutualInformationRetained() >= 0.50);
        // 断言 3: 高相关专员获得的 Shapley 信用显著高于闲聊专员
        Map<String, Double> credits = vibResult.agentShapleyCreditMap();
        double chitchatCredit = credits.getOrDefault("agent-chitchat", 0.0);
        double arbitratorCredit = credits.getOrDefault("agent-arbitrator", 0.0);
        assertTrue(arbitratorCredit > chitchatCredit, "高因果相关 Agent 获得的 Shapley 信用应显著高于低质 Agent");
        // 断言 4: 信用分布熵为正数
        assertTrue(vibResult.causalCreditEntropy() > 0.0);
        // 断言 5: 摘要骨架包含高质量内容
        assertTrue(vibResult.condensedSummary().contains("VIB 认知压缩骨架"));
    }

    @Test
    @DisplayName("Contract 5: 局部语义认知熵与自适应 RAG 门控测试 (Lemma 150.2)")
    void testEpistemicEntropyAdaptiveRagRouting() {
        int dim = 1536;
        double[] queryVec = generateNormalizedVector(dim, 555);

        // 场景 A: 紧密聚集的高置信度候选切片集合（模拟低熵常识查询）
        List<double[]> lowEntropySlices = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            lowEntropySlices.add(generateNearVector(queryVec, 0.95, 100 + i));
        }

        AdaptiveRoutingDecision decisionA = adaptiveRagGovernor.evaluateRouting(
                "常州工学院校址位于江苏省常州市新北区",
                queryVec,
                lowEntropySlices
        );
        assertEquals(AdaptiveRagPath.FAST_DIRECT, decisionA.selectedPath(), "低认知熵常识查询应自适应分流至快速直答通道");
        assertTrue(decisionA.epistemicEntropy() <= EpistemicEntropyAdaptiveRagGovernor.DEFAULT_FAST_ENTROPY_THRESHOLD);

        // 场景 B: 高度分散矛盾且包含反事实推演查询（模拟高熵多跳深度问题）
        List<double[]> highEntropySlices = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            highEntropySlices.add(generateNormalizedVector(dim, 5000 + i * 7));
        }

        AdaptiveRoutingDecision decisionB = adaptiveRagGovernor.evaluateRouting(
                "如果平时成绩占40%，但是实验课有缺勤冲突，能否通过总评及格判定？",
                queryVec,
                highEntropySlices
        );
        assertEquals(AdaptiveRagPath.DEEP_GRAPH_RAG, decisionB.selectedPath(), "反事实与高认知熵查询应自适应分流至深度 GraphRAG 通道");
        assertTrue(decisionB.isCounterfactualQuery());

        // 场景 C: 确定性前缀哈希验证
        assertNotNull(decisionA.deterministicPrefixHash());
        assertNotNull(decisionB.deterministicPrefixHash());
        assertEquals(64, decisionA.deterministicPrefixHash().length(), "SHA-256 确定性前缀哈希长度必须为 64 位十六进制");
    }

    @Test
    @DisplayName("Contract 6: 纯 Java 21 Record 不可变凭单与常量时间防侧信道自验真测试")
    void testCognitiveInfraAuditReceiptCryptographicVerification() {
        CognitiveInfraAuditReceipt receipt = CognitiveInfraAuditReceipt.create(
                "rec-phase150-test-001",
                "tenant-qknow-enterprise",
                "sess-loom-simd-01",
                true,
                9,
                true,
                185,
                0.625,
                1.386,
                0.485,
                "FAST_DIRECT",
                true,
                "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
                3250
        );

        // 1. 验证签名不可为空且自验真通过
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length());
        assertTrue(receipt.verify(), "不可变凭单初始状态必须自验真通过");

        // 2. 模拟黑客篡改凭单参数（反事实防伪测试）
        CognitiveInfraAuditReceipt tamperedReceipt = new CognitiveInfraAuditReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.sessionId(),
                receipt.structuredConcurrencyActive(),
                receipt.orphanTasksPrevented(),
                receipt.simdAccelerationEnabled(),
                receipt.simdDotProductLatencyUs(),
                receipt.vibCompressionRatio(),
                receipt.causalCreditEntropy(),
                receipt.epistemicEntropy(),
                "TAMPERED_PATH", // 篡改路由路径
                receipt.deepseekCacheAligned(),
                receipt.deepseekPrefixHash(),
                receipt.totalLatencyUs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用原签名
        );

        assertFalse(tamperedReceipt.verify(), "被篡改参数的凭单必须被常量时间自验真立刻识破拒绝");
    }

    @Test
    @DisplayName("Contract 7: 消融测试 (Ablation Test: SIMD 并行 vs 标量循环数值绝对一致性)")
    void testAblationSimdVsScalarNumericalParity() {
        int dim = 1536;
        SimdVectorHypersphericalKernel kernelSimd = new SimdVectorHypersphericalKernel(true);
        SimdVectorHypersphericalKernel kernelScalar = new SimdVectorHypersphericalKernel(false);

        double[] vecX = generateNormalizedVector(dim, 777);
        double[] vecY = generateNormalizedVector(dim, 888);

        double dotSimd = kernelSimd.cosineSimilarity(vecX, vecY);
        double dotScalar = kernelScalar.cosineSimilarity(vecX, vecY);

        // 验证两种模式的代数计算结果严格一致（浮点误差 <= 1e-9）
        assertEquals(dotScalar, dotSimd, 1e-9, "SIMD 并行模式与标量展开模式必须保持数值代数绝对一致");
    }

    // ==================== 辅助测试超球面向量构造工具 ====================

    private double[] generateNormalizedVector(int dim, long seed) {
        Random rand = new Random(seed);
        double[] v = new double[dim];
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = rand.nextGaussian();
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] /= norm;
        }
        return v;
    }

    private double[] generateNearVector(double[] base, double targetCosine, long seed) {
        int dim = base.length;
        double[] randomVec = generateNormalizedVector(dim, seed);
        // 正交化
        double proj = simdKernel.cosineSimilarity(randomVec, base);
        double[] ortho = new double[dim];
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            ortho[i] = randomVec[i] - proj * base[i];
            sumSq += ortho[i] * ortho[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            ortho[i] /= norm;
        }

        // 混合合成具有指定余弦内积的超球面向量: targetCosine * base + sqrt(1 - targetCosine^2) * ortho
        double[] result = new double[dim];
        double sinVal = Math.sqrt(Math.max(0.0, 1.0 - targetCosine * targetCosine));
        for (int i = 0; i < dim; i++) {
            result[i] = targetCosine * base[i] + sinVal * ortho[i];
        }
        return simdKernel.normalizeToUnitSphere(result);
    }
}
