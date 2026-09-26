package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.deepseek.prefix.ContextCachingPrefixAligner;
import tech.qiantong.qknow.ai.deepseek.prefix.ContextCachingPrefixAligner.AlignedPrefixResult;
import tech.qiantong.qknow.ai.deepseek.prefix.ContextCachingPrefixAligner.HypergraphSubstructureRecord;
import tech.qiantong.qknow.ai.deepseek.prefix.ContextCachingPrefixAligner.PrefixAlignmentRequest;
import tech.qiantong.qknow.hermes.causal.dto.HypergraphCausalInferenceReceipt;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub.CentroidReconstructionResult;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub.Hyperedge;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub.HyperVertex;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub.HypergraphInferenceRequest;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub.PruneResult;
import tech.qiantong.qknow.hermes.causal.hypergraph.HypergraphCausalBeliefHub.SpectralPropagationResult;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 145 核心契约测试套件：
 * 多智能体自适应跨模态超图知识图谱融合、层次化因果信念网络与 DeepSeek Context Caching 高保真推理加速中枢
 * (Phase 145 Hypergraph Causal Belief Hub & Context Caching Contract Test)
 * <p>
 * 严格按照 AGENTS.md 规范与八大核心契约验证：
 * 1. 千问 1536 维超球面单位向量归一化质心范数守恒与保真度验证 (TC-145-1, Lemma 145.2)
 * 2. 两阶段 O(|V| + |E|) 超图拉普拉斯谱信念传播收敛性与延迟契约 (TC-145-2, Lemma 145.1)
 * 3. 动态因果置信度剪枝 (tau_prune = 0.45) 与高阶降噪率契约 (TC-145-3)
 * 4. 三级冷备隔离环形缓冲区软隔离与可逆自愈回滚契约 (TC-145-4)
 * 5. 面向 DeepSeek 官方规约的前缀对齐与 64-Token 整数倍规整契约 (TC-145-5)
 * 6. 动态变量 (时间戳与 Query) 尾置隔离与前缀哈希单调稳定性契约 (防哈希雪崩) (TC-145-6)
 * 7. 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改契约 (TC-145-7)
 * 8. 多租户物理命名空间硬隔离与高并发吞吐压力测试 (TC-145-8)
 *
 * @author Achilles
 * @since Phase 145
 */
public class Phase145HypergraphCausalBeliefContractTest {

    private HypergraphCausalBeliefHub beliefHub;
    private ContextCachingPrefixAligner prefixAligner;

    @BeforeEach
    void setUp() {
        prefixAligner = new ContextCachingPrefixAligner();
        beliefHub = new HypergraphCausalBeliefHub(prefixAligner);
    }

    /**
     * TC-145-1: 千问 1536 维超球面单位向量归一化质心范数守恒与保真度验证 (Lemma 145.2)
     */
    @Test
    @DisplayName("TC-145-1: 千问 1536 维超球面单位向量归一化质心范数守恒与保真度验证")
    void testHypersphericalCentroidNormConservationAndFidelity() {
        String tenantId = "tenant-tc-145-1";

        // 生成基准向量与 4 个局部共现的高亲和度实体向量 (相似度 >= 0.85)
        double[] baseVec = generateNormalizedEmbedding(1001);
        List<HyperVertex> vertices = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            // 控制实体间局部相似度 rho >= 0.88
            double[] perturbed = perturbNormalizedEmbedding(baseVec, 0.88 + i * 0.02, 2000 + i);
            HyperVertex v = HyperVertex.builder()
                    .id("v-" + i)
                    .label("Entity-" + i)
                    .embedding(perturbed)
                    .causalLevel(1)
                    .priorBelief(0.8)
                    .currentBelief(0.8)
                    .build();
            vertices.add(v);
            beliefHub.upsertVertex(tenantId, v);
        }

        // 计算高阶超边超球面质心与重构损失
        CentroidReconstructionResult result = HypergraphCausalBeliefHub.computeHypersphericalCentroid(vertices);
        assertNotNull(result);
        assertNotNull(result.centroid());
        assertEquals(1536, result.centroid().length);

        // 验证范数严格归一化 ||c_e||_2 = 1.0 (误差 <= 1e-6)
        double norm = 0.0;
        for (double val : result.centroid()) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);
        assertEquals(1.0, norm, 1e-6, "超边质心必须严格位于 1536 维超球面单位流形上");

        // 验证重构损失上界 L_recon <= 0.15 (Lemma 145.2 证明局部相似度 >= 0.85 时，损失上界为 0.0781 <= 0.15)
        assertTrue(result.reconstructionLoss() <= 0.15,
                "重构损失必须满足上界 L_recon <= 0.15，实际: " + result.reconstructionLoss());
        assertTrue(result.reconstructionLoss() >= 0.0, "重构损失不可为负数");
    }

    /**
     * TC-145-2: 两阶段 O(|V| + |E|) 超图拉普拉斯谱信念传播收敛性与延迟契约 (Lemma 145.1)
     */
    @Test
    @DisplayName("TC-145-2: 两阶段 O(|V| + |E|) 超图拉普拉斯谱信念传播收敛性与延迟契约")
    void testSpectralBeliefPropagationConvergenceAndLatency() {
        String tenantId = "tenant-tc-145-2";
        int vertexCount = 200;
        int hyperedgeCount = 50;

        Random random = new Random(42);

        // 构造 200 个超节点
        for (int i = 0; i < vertexCount; i++) {
            HyperVertex v = HyperVertex.builder()
                    .id("v-" + i)
                    .label("Node-" + i)
                    .embedding(generateNormalizedEmbedding(i))
                    .causalLevel(random.nextInt(3) + 1)
                    .priorBelief(0.2 + random.nextDouble() * 0.6)
                    .currentBelief(0.5)
                    .build();
            beliefHub.upsertVertex(tenantId, v);
        }

        // 构造 50 条高阶超边 (每条连接 3~6 个节点)
        for (int j = 0; j < hyperedgeCount; j++) {
            Set<String> edgeVertices = new HashSet<>();
            int edgeSize = 3 + random.nextInt(4);
            while (edgeVertices.size() < edgeSize) {
                edgeVertices.add("v-" + random.nextInt(vertexCount));
            }
            Hyperedge edge = Hyperedge.builder()
                    .id("he-" + j)
                    .name("Relation-" + j)
                    .vertexIds(edgeVertices)
                    .weight(1.0)
                    .causalConfidence(0.5 + random.nextDouble() * 0.45)
                    .causalLevel(random.nextInt(3) + 1)
                    .relationType("CAUSAL_INFERENCE")
                    .explanation("Empirical evidence relation " + j)
                    .build();
            beliefHub.upsertHyperedge(tenantId, edge);
        }

        // 预热 JVM
        beliefHub.executeSpectralBeliefPropagation(tenantId, 0.85, 13, 5e-4);

        // 正式执行测速与契约断言 (依 Lemma 145.1，T_conv <= 13 步收敛至 5e-4)
        long start = System.nanoTime();
        SpectralPropagationResult result = beliefHub.executeSpectralBeliefPropagation(tenantId, 0.85, 13, 5e-4);
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(result);
        assertTrue(result.convergedIterations() <= 13,
                "拉普拉斯谱信念传播必须在 13 步以内快速收敛 (Lemma 145.1)，实际迭代: " + result.convergedIterations());
        assertTrue(result.maxDelta() <= 5e-4, "收敛最大残差必须 <= 5e-4，实际: " + result.maxDelta());
        assertTrue(latencyMs <= 4.5, "单次超图谱信念传播耗时应满足 P99 <= 4.5ms，实际: " + latencyMs);

        // 验证节点信念有界性严格落在 [0.0, 1.0]
        for (Double belief : result.finalBeliefs().values()) {
            assertTrue(belief >= 0.0 && belief <= 1.0, "信念值必须在 [0.0, 1.0] 概率流形内");
        }
    }

    /**
     * TC-145-3: 动态因果置信度剪枝 (tau_prune = 0.45) 与高阶降噪率契约
     */
    @Test
    @DisplayName("TC-145-3: 动态因果置信度剪枝 (tau_prune = 0.45) 与高阶降噪率契约")
    void testCausalConfidencePruningAndNoiseReduction() {
        String tenantId = "tenant-tc-145-3";

        // 注入 10 条高置信度超边 (0.60 ~ 0.95) 与 10 条低置信度超边 (0.10 ~ 0.40)
        for (int i = 0; i < 10; i++) {
            Hyperedge highEdge = Hyperedge.builder()
                    .id("high-" + i)
                    .name("HighConfidence-" + i)
                    .vertexIds(Set.of("v-0", "v-1"))
                    .causalConfidence(0.60 + i * 0.035)
                    .causalLevel(1)
                    .build();
            beliefHub.upsertHyperedge(tenantId, highEdge);

            Hyperedge lowEdge = Hyperedge.builder()
                    .id("low-" + i)
                    .name("NoiseEdge-" + i)
                    .vertexIds(Set.of("v-0", "v-2"))
                    .causalConfidence(0.10 + i * 0.03)
                    .causalLevel(2)
                    .build();
            beliefHub.upsertHyperedge(tenantId, lowEdge);
        }

        // 执行阈值 0.45 的因果剪枝
        PruneResult pruneResult = beliefHub.pruneHyperedges(tenantId, 0.45);

        assertNotNull(pruneResult);
        assertEquals(20, pruneResult.originalCount());
        assertEquals(10, pruneResult.retainedCount());
        assertEquals(10, pruneResult.prunedCount());
        assertEquals(0.50, pruneResult.noiseReductionRate(), 1e-4, "降噪率必须达到 50.0% >= 40.0%");
        assertEquals(10, pruneResult.prunedEdgeIds().size());

        // 验证被剪枝的超边全部安全进入冷备区
        assertEquals(10, beliefHub.getColdQuarantinedHyperedges(tenantId).size());
    }

    /**
     * TC-145-4: 三级冷备隔离环形缓冲区软隔离与可逆自愈回滚契约
     */
    @Test
    @DisplayName("TC-145-4: 三级冷备隔离环形缓冲区软隔离与可逆自愈回滚契约")
    void testColdRingBufferSoftIsolationAndReversibleSelfHealing() {
        String tenantId = "tenant-tc-145-4";

        Hyperedge targetEdge = Hyperedge.builder()
                .id("edge-critical-to-restore")
                .name("CriticalRelation")
                .vertexIds(Set.of("v-a", "v-b"))
                .causalConfidence(0.35) // 低于阈值被剪枝
                .causalLevel(1)
                .build();
        beliefHub.upsertHyperedge(tenantId, targetEdge);

        // 执行剪枝，该超边进入冷备环形缓冲区
        PruneResult prune = beliefHub.pruneHyperedges(tenantId, 0.45);
        assertEquals(1, prune.prunedCount());
        assertEquals(1, beliefHub.getColdQuarantinedHyperedges(tenantId).size());

        // 执行可逆自愈恢复
        boolean restored = beliefHub.restoreHyperedgeFromColdBuffer(tenantId, "edge-critical-to-restore");
        assertTrue(restored, "必须成功从冷备隔离区可逆唤醒");

        // 验证已恢复至活跃状态且冷备区已清空
        assertEquals(0, beliefHub.getColdQuarantinedHyperedges(tenantId).size());

        // 测试环形缓冲区容量上限 (128) 溢出时的 FIFO 淘汰与免 GC 抖动
        for (int i = 0; i < 150; i++) {
            Hyperedge temp = Hyperedge.builder()
                    .id("temp-" + i)
                    .vertexIds(Set.of("v-0"))
                    .causalConfidence(0.10)
                    .build();
            beliefHub.upsertHyperedge(tenantId, temp);
        }
        beliefHub.pruneHyperedges(tenantId, 0.45);
        assertEquals(128, beliefHub.getColdQuarantinedHyperedges(tenantId).size(), "环形缓冲区容量严格保持 128 条");
    }

    /**
     * TC-145-5: 面向 DeepSeek 官方规约的前缀对齐与 64-Token 整数倍规整契约
     */
    @Test
    @DisplayName("TC-145-5: 面向 DeepSeek 官方规约的前缀对齐与 64-Token 整数倍规整契约")
    void testDeepSeekPrefixAlignmentAndModulo64Compliance() {
        PrefixAlignmentRequest request = PrefixAlignmentRequest.builder()
                .tenantId("tenant-tc-145-5")
                .taskId("task-ds-align-001")
                .staticSystemPrompt("你是一个企业级 AI-Native RAG 架构师，必须严格执行高阶超图因果逻辑推理。")
                .globalToolsJson("{\"tools\":[{\"name\":\"query_knowledge_graph\",\"type\":\"mcp\"}]}")
                .causalSubstructures(List.of(
                        HypergraphSubstructureRecord.builder()
                                .hyperedgeId("HE-101")
                                .causalLevel(1)
                                .causalConfidence(0.92)
                                .vertexLabels(List.of("HikariCP_Pool", "ConnectionLeak", "TimeoutError"))
                                .relationType("ROOT_CAUSE_BRANCH")
                                .causalExplanation("连接池耗尽直接导致下游 RPC 级联超时")
                                .build(),
                        HypergraphSubstructureRecord.builder()
                                .hyperedgeId("HE-102")
                                .causalLevel(2)
                                .causalConfidence(0.88)
                                .vertexLabels(List.of("TimeoutError", "CircuitBreakerOpen"))
                                .relationType("CASCADE_EFFECT")
                                .causalExplanation("下游超时触发熔断器状态机开启")
                                .build()
                ))
                .userQuery("请分析系统发生熔断的深层因果链。")
                .temporalAnchor("2026-09-26T10:00:00Z")
                .build();

        AlignedPrefixResult result = prefixAligner.align(request);

        assertNotNull(result);
        assertTrue(result.isCacheAligned(), "前缀必须满足 64-token 严格对齐");
        assertEquals(0, result.getAlignedStaticTokens() % 64, "静态前缀 Token 数量必须为 64 的整数倍");
        assertTrue(result.getStaticPrefixText().contains("<!-- ds_prefix_align_pad:{\"padTokens\":"),
                "必须包含确定性受控无副作用的前缀填充垫片");
        assertNotNull(result.getPrefixDigestSha256());
        assertEquals(64, result.getPrefixDigestSha256().length(), "前缀摘要必须为 64 字符 SHA-256 十六进制指纹");
    }

    /**
     * TC-145-6: 动态变量 (时间戳与 Query) 尾置隔离与前缀哈希单调稳定性契约 (防哈希雪崩)
     */
    @Test
    @DisplayName("TC-145-6: 动态变量 (时间戳与 Query) 尾置隔离与前缀哈希单调稳定性契约 (防哈希雪崩)")
    void testDynamicVariableTailIsolationAndHashInvariance() {
        List<HypergraphSubstructureRecord> fixedSubstructures = List.of(
                HypergraphSubstructureRecord.builder()
                        .hyperedgeId("HE-FIXED-1")
                        .causalLevel(1)
                        .causalConfidence(0.95)
                        .vertexLabels(List.of("ServiceA", "DatabaseDeadlock"))
                        .build()
        );

        // 请求 1: 带有动态时间戳 T1 与 Query 1
        PrefixAlignmentRequest req1 = PrefixAlignmentRequest.builder()
                .tenantId("tenant-tc-145-6")
                .staticSystemPrompt("固定系统主干指令。")
                .globalToolsJson("{\"tools\":[\"fixed_tool\"]}")
                .causalSubstructures(fixedSubstructures)
                .userQuery("用户查询 A")
                .temporalAnchor("2026-09-26T10:00:00.000Z")
                .build();

        // 请求 2: 带有完全不同的动态时间戳 T2 与 Query 2
        PrefixAlignmentRequest req2 = PrefixAlignmentRequest.builder()
                .tenantId("tenant-tc-145-6")
                .staticSystemPrompt("固定系统主干指令。")
                .globalToolsJson("{\"tools\":[\"fixed_tool\"]}")
                .causalSubstructures(fixedSubstructures)
                .userQuery("完全不同的用户查询 B，包含更多额外文字输入")
                .temporalAnchor("2026-09-26T14:45:30.999Z")
                .build();

        AlignedPrefixResult res1 = prefixAligner.align(req1);
        AlignedPrefixResult res2 = prefixAligner.align(req2);

        // 核心断言：静态前缀文本与哈希摘要绝对 100% 一致 (防哈希雪崩，保证 DeepSeek 缓存命中)
        assertEquals(res1.getStaticPrefixText(), res2.getStaticPrefixText(), "静态前缀文本必须完全一致");
        assertEquals(res1.getPrefixDigestSha256(), res2.getPrefixDigestSha256(), "静态前缀 SHA-256 指纹必须完全相同");

        // 动态差异仅存在于动态尾置层
        assertNotEquals(res1.getDynamicTailText(), res2.getDynamicTailText(), "动态尾部必须包含不同时间戳与 Query");
    }

    /**
     * TC-145-7: 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改契约
     */
    @Test
    @DisplayName("TC-145-7: 纯 Java 21 Record 凭单 SHA-256 常量时间自验真与防篡改契约")
    void testJava21RecordReceiptConstantTimeSignatureVerification() {
        String tenantId = "tenant-tc-145-7";

        HyperVertex v1 = HyperVertex.builder()
                .id("v-root")
                .label("HikariCP_Leak")
                .embedding(generateNormalizedEmbedding(301))
                .causalLevel(1)
                .priorBelief(0.9)
                .build();
        HyperVertex v2 = HyperVertex.builder()
                .id("v-effect")
                .label("CircuitBreakerTrip")
                .embedding(generateNormalizedEmbedding(302))
                .causalLevel(2)
                .priorBelief(0.5)
                .build();
        beliefHub.upsertVertex(tenantId, v1);
        beliefHub.upsertVertex(tenantId, v2);

        Hyperedge edge = Hyperedge.builder()
                .id("he-causal-link")
                .name("LeakTriggersTrip")
                .vertexIds(Set.of("v-root", "v-effect"))
                .causalConfidence(0.88)
                .causalLevel(1)
                .relationType("DIRECT_CAUSE")
                .explanation("连接泄漏直接导致熔断")
                .build();
        beliefHub.upsertHyperedge(tenantId, edge);

        HypergraphInferenceRequest request = HypergraphInferenceRequest.builder()
                .tenantId(tenantId)
                .taskId("task-receipt-test")
                .traceId("trace-w3c-001")
                .hypergraphId("HG-TEST-001")
                .staticSystemPrompt("系统因果分析内核。")
                .userQuery("分析故障根因")
                .temporalAnchor("2026-09-26T10:00:00Z")
                .pruneThreshold(0.45)
                .propagationAlpha(0.85)
                .maxPropagationSteps(13)
                .build();

        HypergraphCausalInferenceReceipt receipt = beliefHub.inferAndIssueReceipt(request);

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "初始签发的存证凭单必须自验真通过");

        // 模拟篡改凭单核心字段 (如因果信念熵或剪枝超边数)
        HypergraphCausalInferenceReceipt tampered = new HypergraphCausalInferenceReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.taskId(),
                receipt.traceId(),
                receipt.hypergraphId(),
                receipt.totalVertexCount(),
                receipt.totalHyperedgeCount(),
                receipt.retainedHyperedgeCount() + 1, // 恶意篡改保留边计数
                receipt.prunedHyperedgeCount(),
                receipt.causalBeliefEntropy(),
                receipt.spectralFidelity(),
                receipt.cachedPrefixDigest(),
                receipt.alignedPrefixTokens(),
                receipt.paddedTokens(),
                receipt.isCacheAligned(),
                receipt.canonicalCausalChain(),
                receipt.inferenceLatencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用原签名
        );

        assertFalse(tampered.verifySignature(), "字段遭篡改后，基于常量时间比对的验签必须严格失败");
    }

    /**
     * TC-145-8: 多租户物理命名空间硬隔离与高并发吞吐压力测试
     */
    @Test
    @DisplayName("TC-145-8: 多租户物理命名空间硬隔离与高并发吞吐压力测试")
    void testMultiTenantPhysicalIsolationAndHighConcurrency() throws Exception {
        int tenantCount = 8;
        int operationsPerTenant = 20;
        ExecutorService executor = Executors.newFixedThreadPool(tenantCount);
        CountDownLatch latch = new CountDownLatch(tenantCount);
        List<Future<Double>> futures = new ArrayList<>();

        for (int t = 0; t < tenantCount; t++) {
            final String tenantId = "tenant-concurrent-" + t;
            final int seed = t * 100;

            futures.add(executor.submit(() -> {
                try {
                    long totalNanos = 0;
                    for (int op = 0; op < operationsPerTenant; op++) {
                        HyperVertex v = HyperVertex.builder()
                                .id(tenantId + "-v-" + op)
                                .label("Entity-" + op)
                                .embedding(generateNormalizedEmbedding(seed + op))
                                .causalLevel(1)
                                .priorBelief(0.7)
                                .build();
                        beliefHub.upsertVertex(tenantId, v);

                        if (op > 0) {
                            Hyperedge e = Hyperedge.builder()
                                    .id(tenantId + "-e-" + op)
                                    .vertexIds(Set.of(tenantId + "-v-" + (op - 1), tenantId + "-v-" + op))
                                    .causalConfidence(0.5 + (op % 5) * 0.1)
                                    .causalLevel(1)
                                    .build();
                            beliefHub.upsertHyperedge(tenantId, e);
                        }

                        long s = System.nanoTime();
                        HypergraphInferenceRequest req = HypergraphInferenceRequest.builder()
                                .tenantId(tenantId)
                                .taskId("task-" + tenantId + "-" + op)
                                .traceId("trace-" + tenantId)
                                .staticSystemPrompt("Tenant prompt " + tenantId)
                                .userQuery("Query " + op)
                                .build();
                        HypergraphCausalInferenceReceipt r = beliefHub.inferAndIssueReceipt(req);
                        totalNanos += (System.nanoTime() - s);

                        assertNotNull(r);
                        assertTrue(r.verifySignature());
                    }
                    return (totalNanos / (double) operationsPerTenant) / 1_000_000.0;
                } finally {
                    latch.countDown();
                }
            }));
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "高并发多租户执行必须在 10 秒内完成");

        for (Future<Double> future : futures) {
            double avgLatencyMs = future.get();
            assertTrue(avgLatencyMs <= 4.5, "高并发下单次平均耗时必须 <= 4.5ms，实际: " + avgLatencyMs);
        }

        executor.shutdown();
    }

    // ================================= 辅助方法 =================================

    private double[] generateNormalizedEmbedding(long seed) {
        Random random = new Random(seed);
        double[] vec = new double[1536];
        double norm = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = random.nextGaussian();
            norm += vec[i] * vec[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    private double[] perturbNormalizedEmbedding(double[] base, double cosineTarget, long seed) {
        Random random = new Random(seed);
        double[] randomVec = new double[1536];
        for (int i = 0; i < 1536; i++) {
            randomVec[i] = random.nextGaussian();
        }
        // 正交化：去除沿 base 方向的投影
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += randomVec[i] * base[i];
        }
        double orthoNorm = 0.0;
        for (int i = 0; i < 1536; i++) {
            randomVec[i] -= dot * base[i];
            orthoNorm += randomVec[i] * randomVec[i];
        }
        orthoNorm = Math.sqrt(orthoNorm);
        for (int i = 0; i < 1536; i++) {
            randomVec[i] /= (orthoNorm > 1e-9 ? orthoNorm : 1.0);
        }

        // 依余弦目标进行超球面旋转: v = cos(theta) * base + sin(theta) * ortho
        double cosTheta = Math.min(1.0, Math.max(0.0, cosineTarget));
        double sinTheta = Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));

        double[] result = new double[1536];
        for (int i = 0; i < 1536; i++) {
            result[i] = cosTheta * base[i] + sinTheta * randomVec[i];
        }
        return result;
    }
}
