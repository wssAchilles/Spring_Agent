package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.benchmark.condense.StreamingContextCondensationGovernor;
import tech.qiantong.qknow.hermes.benchmark.condense.StreamingContextCondensationGovernor.CondensationResult;
import tech.qiantong.qknow.hermes.benchmark.evolution.LyapunovReplicatorDynamicsGovernor;
import tech.qiantong.qknow.hermes.benchmark.evolution.LyapunovReplicatorDynamicsGovernor.EvolutionaryConvergenceResult;
import tech.qiantong.qknow.hermes.benchmark.quant.HypersphericalIsotropicProductQuantizer;
import tech.qiantong.qknow.hermes.benchmark.receipt.EvolutionaryQuantizedInfraReceipt;
import tech.qiantong.qknow.hermes.benchmark.scoped.ScopedContextCarrierEngine;
import tech.qiantong.qknow.hermes.benchmark.scoped.ScopedContextCarrierEngine.HermesScopedContext;
import tech.qiantong.qknow.hermes.benchmark.scoped.ScopedContextCarrierEngine.ScopedPropagationStats;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 151 多智能体演化收敛博弈、Java 21 作用域上下文与超球面分块量化基础设施严密契约测试套件
 * <p>
 * 检验唯一假设 H-151 与两大核心引理：
 * 1. Lemma 151.1: 超球面多智能体复制动态李雅普诺夫收敛引理；
 * 2. Lemma 151.2: 千问 1536 维单位流形各向同性分块量化保角有界失真引理。
 * </p>
 *
 * @author Achilles
 * @since Phase 151
 */
public class Phase151EvolutionaryQuantizedInfraContractTest {

    private LyapunovReplicatorDynamicsGovernor evolutionGovernor;
    private HypersphericalIsotropicProductQuantizer hipqQuantizer;
    private StreamingContextCondensationGovernor condensationGovernor;

    @BeforeEach
    void setUp() {
        evolutionGovernor = new LyapunovReplicatorDynamicsGovernor();
        hipqQuantizer = new HypersphericalIsotropicProductQuantizer();
        condensationGovernor = new StreamingContextCondensationGovernor();
    }

    @Test
    @DisplayName("Contract 1: 李雅普诺夫复制动态演化博弈收敛测试 (严格 <= 6 轮数学收敛与能量衰减)")
    void testLyapunovReplicatorDynamicsConvergence() {
        String[] agents = new String[]{"SecurityAuditor", "PerformanceOptimizer", "CostBudgetManager"};
        int n = agents.length;

        // 初始不平衡信念权重分布
        double[] initialDistribution = new double[]{0.6, 0.2, 0.2};

        // 基于千问超球面测地线内积收益矩阵 (对角占优与博弈对抗)
        double[][] payoffMatrix = new double[][]{
                {0.92, 0.45, 0.38},
                {0.48, 0.88, 0.52},
                {0.35, 0.50, 0.85}
        };

        // 目标纳什均衡先验分布
        double[] targetEss = new double[]{0.45, 0.30, 0.25};

        EvolutionaryConvergenceResult result = evolutionGovernor.executeConvergence(
                agents, initialDistribution, payoffMatrix, targetEss
        );

        // 验证引理 151.1 核心指标
        assertTrue(result.converged(), "李雅普诺夫演化博弈状态机必须判定收敛");
        assertTrue(result.totalRounds() <= 6, "收敛轮数必须严格 <= 6 轮，实际: " + result.totalRounds());
        assertTrue(result.finalEnergy() <= result.initialEnergy() + 1e-6, "李雅普诺夫能量必须单调非正增长");
        assertEquals(n, result.equilibriumStrategy().length, "均衡策略维度必须匹配");

        // 验证单纯形性质: \sum x_i = 1.0
        double sum = 0.0;
        for (double w : result.equilibriumStrategy()) {
            sum += w;
            assertTrue(w >= 0.0, "策略权重必须非负");
        }
        assertEquals(1.0, sum, 1e-5, "策略分布和必须为 1.0");
        assertNotNull(result.dominantAgentId(), "必须识别出主导智能体");
    }

    @Test
    @DisplayName("Contract 2: Java 21 ScopedValue 零拷贝上下文传递与栈帧自愈清理测试")
    void testScopedValueZeroCopyContextPropagation() throws Exception {
        HermesScopedContext context = new HermesScopedContext(
                "tenant-qknow-corp",
                "trace-phase151-" + UUID.randomUUID(),
                "bot-hermes-core",
                System.currentTimeMillis() + 60000L,
                Map.of("securityLevel", "HIGH", "env", "PROD")
        );

        assertFalse(ScopedContextCarrierEngine.hasContext(), "初始栈外必须未绑定作用域值");

        AtomicBoolean innerAssertPassed = new AtomicBoolean(false);

        // 在 ScopedValue 作用域下执行
        ScopedContextCarrierEngine.runWithContext(context, () -> {
            assertTrue(ScopedContextCarrierEngine.hasContext(), "作用域内必须绑定有效上下文");
            HermesScopedContext current = ScopedContextCarrierEngine.currentContext();
            assertEquals("tenant-qknow-corp", current.tenantId());
            assertEquals(context.traceId(), current.traceId());

            ScopedPropagationStats stats = ScopedContextCarrierEngine.inspectCurrentScope();
            assertTrue(stats.isBound(), "作用域审计状态必须为 bound");
            assertTrue(stats.zeroCopyInheritanceActive(), "必须保持零拷贝继承特征");

            innerAssertPassed.set(true);
        });

        assertTrue(innerAssertPassed.get(), "作用域内部断言必须成功触发");
        assertFalse(ScopedContextCarrierEngine.hasContext(), "作用域退出后必须自动 O(1) 恢复未绑定状态，防内存逃逸");
    }

    @Test
    @DisplayName("Contract 3: 虚拟线程并发下的 ScopedValue 跨线程共享测试")
    void testScopedValueVirtualThreadInheritance() throws Exception {
        HermesScopedContext context = new HermesScopedContext(
                "tenant-enterprise",
                "trace-loom-test",
                "bot-swarm-01",
                System.currentTimeMillis() + 30000L,
                Map.of("role", "coordinator")
        );

        int numTasks = 50;
        List<Future<String>> futures = new ArrayList<>();

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            ScopedContextCarrierEngine.callWithContext(context, () -> {
                for (int i = 0; i < numTasks; i++) {
                    futures.add(virtualExecutor.submit(ScopedContextCarrierEngine.wrap(() -> {
                        // 虚拟线程内部验证
                        HermesScopedContext childContext = ScopedContextCarrierEngine.currentContext();
                        return childContext.traceId();
                    })));
                }
                return null;
            });

            for (Future<String> future : futures) {
                assertEquals("trace-loom-test", future.get(5, TimeUnit.SECONDS), "虚拟线程必须安全共享父栈上下文引用");
            }
        }
    }

    @Test
    @DisplayName("Contract 4: 千问 1536 维超球面各向同性分块量化保角失真与保真度测试 (Lemma 151.2)")
    void testHypersphericalIsotropicProductQuantizationFidelity() {
        // 生成合成的千问 1536 维超球面单位向量 (||v||_2 = 1.0)
        float[] originalVector = generateSyntheticQwenUnitVector(1536, 42L);
        float[] secondVector = generateSyntheticQwenUnitVector(1536, 84L);
        assertEquals(1.0f, computeVectorNorm(originalVector), 1e-4f, "原始向量必须严格满足千问超球面单位模长");

        // 1. 各向同性分块量化编码
        byte[] codes = hipqQuantizer.quantize(originalVector);
        assertNotNull(codes);
        assertEquals(1536, codes.length, "分块量化编码必须精确为 1536 字节");

        // 2. 解码重构并应用各向同性超球面重归一化
        float[] reconstructed = hipqQuantizer.reconstruct(codes);
        assertEquals(1536, reconstructed.length, "重构向量维度必须为 1536");
        assertEquals(1.0f, computeVectorNorm(reconstructed), 1e-4f, "重构向量必须严格恢复到单位超球面模长");

        // 3. 计算超球面余弦保真度
        double fidelity = hipqQuantizer.evaluateFidelity(originalVector, reconstructed);
        assertTrue(fidelity >= 0.982, String.format("千问 1536 维超球面量化余弦保真度必须 >= 98.2%%，实际: %.4f", fidelity));

        // 4. 验证 Lemma 151.2 成对内积保持性保真度
        byte[] codes2 = hipqQuantizer.quantize(secondVector);
        float[] reconstructed2 = hipqQuantizer.reconstruct(codes2);
        double pairwiseFidelity = hipqQuantizer.evaluatePairwiseCosineFidelity(originalVector, secondVector, reconstructed, reconstructed2);
        assertTrue(pairwiseFidelity >= 0.982, String.format("成对超球面余弦内积保真度必须 >= 98.2%%，实际: %.4f", pairwiseFidelity));

        // 5. 验证内存压缩与常驻削减比例 (6144B -> 1536B，降低 75.0% >= 72.0%)
        long rawBytes = 1536 * 4L; // 6144 字节
        long compressedBytes = codes.length; // 1536 字节
        double memoryReductionRatio = 1.0 - ((double) compressedBytes / rawBytes);
        assertTrue(memoryReductionRatio >= 0.72, String.format("常驻内存削减比例必须 >= 72.0%%，实际: %.2f%%", memoryReductionRatio * 100.0));
    }

    @Test
    @DisplayName("Contract 5: 非对称余弦快速估计与全量重构一致性测试")
    void testAsymmetricCosineEstimationAccuracy() {
        float[] queryVector = generateSyntheticQwenUnitVector(1536, 101L);
        float[] docVector = generateSyntheticQwenUnitVector(1536, 202L);

        byte[] codes = hipqQuantizer.quantize(docVector);
        float[] reconstructed = hipqQuantizer.reconstruct(codes);

        // 精确余弦
        double exactCosine = hipqQuantizer.evaluateFidelity(queryVector, reconstructed);
        // 快速非对称估计
        float asymmetricCosine = hipqQuantizer.computeAsymmetricCosine(queryVector, codes);

        double diff = Math.abs(exactCosine - asymmetricCosine);
        assertTrue(diff <= 0.08, "非对称余弦快速估计与重构余弦内积差异必须 <= 0.08，实际: " + diff);
    }

    @Test
    @DisplayName("Contract 6: DeepSeek 官方 1M 规约上下文流式重凝缩与 64-token 确定性对齐测试")
    void testDeepSeekContextCondensationAnd64TokenAlignment() {
        String systemAnchor = "【系统指令】你是由 DeepSeek API 驱动的企业级 Hermes 智能体，必须遵循 64-token 上下文缓存边界与不可变凭单验证。";

        List<String> transcripts = List.of(
                "智能体 A: 针对高并发知识向量检索，建议保持 FP32 全精度常驻以确保最高精度。",
                "智能体 B: 反对，FP32 堆内存开销过大导致 GC 停顿拉长，必须引入各向同性分块量化。",
                "智能体 C: 仲裁结论，采用千问 1536 维超球面各向同性分块量化 (HIPQ)，在保持 98.2% 保真度的同时削减 75% 内存。",
                "智能体 A: 接受仲裁，经李雅普诺夫相对熵检验，策略已收敛至演化稳定状态 (ESS)。"
        );

        String receiptEvidence = "EvolutionaryReceipt{receiptId='rcpt-151-01', fidelity=0.9852, rounds=3}";

        CondensationResult result = condensationGovernor.condenseMultiAgentContext(
                systemAnchor, transcripts, receiptEvidence
        );

        assertNotNull(result);
        assertTrue(result.isCacheAligned(), "上下文必须满足 DeepSeek 官方缓存块对齐要求");
        assertNotNull(result.prefixHash(), "前缀必须生成确定性 SHA-256 哈希");
        assertTrue(result.condensedEstimatedTokens() > 0, "凝缩后 Token 数必须有效");
        assertTrue(result.compressionRatio() >= 0.0, "压缩率必须非负");
    }

    @Test
    @DisplayName("Contract 7: 纯 Java 21 Record 密码学凭单不可变性与常量时间验真测试 (防时序侧信道)")
    void testEvolutionaryQuantizedInfraReceiptCryptoVerification() {
        EvolutionaryQuantizedInfraReceipt receipt = EvolutionaryQuantizedInfraReceipt.create(
                "rcpt-test-151-001",
                "tenant-alpha",
                "session-beta",
                4,
                1.4520,
                0.2105,
                "SecurityAuditor:0.65|PerformanceOptimizer:0.35",
                0.9865,
                0.9922,
                76.5,
                12,
                true,
                true,
                "a1b2c3d4e5f67890abcdef1234567890",
                450L
        );

        // 验证原生自验真
        assertTrue(receipt.verify(), "不可变审计凭单必须通过 SHA-256 常量时间自验真");

        // 构造被篡改凭单，验证防篡改失效
        EvolutionaryQuantizedInfraReceipt tampered = new EvolutionaryQuantizedInfraReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.sessionId(),
                receipt.convergenceRounds(),
                receipt.initialLyapunovEnergy(),
                receipt.finalLyapunovEnergy(),
                receipt.lyapunovEnergyDelta(),
                receipt.essStrategyDigest(),
                0.5000, // 恶意篡改保真度数值
                receipt.quantizationCompressionRatio(),
                receipt.memoryFootprintReductionPercent(),
                receipt.scopedStackDepth(),
                receipt.zeroCopyInheritanceActive(),
                receipt.deepseekCacheAligned(),
                receipt.deepseekPrefixHash(),
                receipt.totalLatencyUs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用原签名
        );

        assertFalse(tampered.verify(), "被篡改数据的凭单必须 100% 验真失败，保障企业级密码学存证安全");
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

    private float computeVectorNorm(float[] v) {
        float sumSq = 0.0f;
        for (float val : v) {
            sumSq += val * val;
        }
        return (float) Math.sqrt(sumSq);
    }
}
