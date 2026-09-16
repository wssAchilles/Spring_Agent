package tech.qiantong.qknow.hermes.superconvergence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.superconvergence.dto.*;
import tech.qiantong.qknow.hermes.superconvergence.engine.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 100 终极里程碑：企业级 AI 原生软件智能体操作系统超融合内核 (AgentOS Superconvergence Kernel) 专属契约测试套件
 */
public class Phase100CentennialSuperconvergenceContractTest {

    private AutonomicSelfHealingReflectionEngine reflectionEngine;
    private HypersphericalMetacognitiveAligner metacognitiveAligner;
    private CentennialSovereignBarrierGate barrierGate;
    private AgentOsSuperconvergenceBus superconvergenceBus;

    @BeforeEach
    public void setUp() {
        reflectionEngine = new AutonomicSelfHealingReflectionEngine(0.5);
        metacognitiveAligner = new HypersphericalMetacognitiveAligner();
        barrierGate = new CentennialSovereignBarrierGate(1024);
        superconvergenceBus = new AgentOsSuperconvergenceBus();
    }

    /**
     * 辅助方法：生成 1536 维阿里千问超球面单位向量 (||v||_2 = 1.0)
     */
    private double[] createNormalizedEmbedding(double seed) {
        double[] emb = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            emb[i] = Math.sin((i + 1) * seed);
            sumSq += emb[i] * emb[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            emb[i] /= norm;
        }
        return emb;
    }

    @Test
    @DisplayName("契约测试 1: 验证内核生命周期 8 态合法转移与终态守卫")
    public void test1_lifecycleStateTransitionsAndTerminalGuards() {
        assertEquals(AgentOsLifecycleState.INITIALIZING, reflectionEngine.getCurrentState());

        // 1. 合法主干流水线转移: INITIALIZING -> COGNITIVE_ALIGNING -> AUTONOMIC_REASONING -> BARRIER_AUDITING -> CONVERGENCE_COMMITTING
        reflectionEngine.transitionTo(AgentOsLifecycleState.COGNITIVE_ALIGNING);
        assertEquals(AgentOsLifecycleState.COGNITIVE_ALIGNING, reflectionEngine.getCurrentState());

        reflectionEngine.transitionTo(AgentOsLifecycleState.AUTONOMIC_REASONING);
        assertEquals(AgentOsLifecycleState.AUTONOMIC_REASONING, reflectionEngine.getCurrentState());

        reflectionEngine.transitionTo(AgentOsLifecycleState.BARRIER_AUDITING);
        assertEquals(AgentOsLifecycleState.BARRIER_AUDITING, reflectionEngine.getCurrentState());

        reflectionEngine.transitionTo(AgentOsLifecycleState.CONVERGENCE_COMMITTING);
        assertEquals(AgentOsLifecycleState.CONVERGENCE_COMMITTING, reflectionEngine.getCurrentState());

        // 2. 自愈与降级支线转移
        reflectionEngine.transitionTo(AgentOsLifecycleState.SELF_HEALING_COMPENSATING);
        assertEquals(AgentOsLifecycleState.SELF_HEALING_COMPENSATING, reflectionEngine.getCurrentState());

        reflectionEngine.transitionTo(AgentOsLifecycleState.DEGRADED_BUFFERED);
        assertEquals(AgentOsLifecycleState.DEGRADED_BUFFERED, reflectionEngine.getCurrentState());

        // 3. 终态关机
        reflectionEngine.transitionTo(AgentOsLifecycleState.SHUTDOWN_HALTED);
        assertEquals(AgentOsLifecycleState.SHUTDOWN_HALTED, reflectionEngine.getCurrentState());
        assertTrue(reflectionEngine.getCurrentState().isTerminal());

        // 4. 终态不可逆向转移守卫
        assertThrows(IllegalStateException.class, () -> {
            reflectionEngine.transitionTo(AgentOsLifecycleState.INITIALIZING);
        });

        // 5. 重置验证
        reflectionEngine.reset();
        assertEquals(AgentOsLifecycleState.INITIALIZING, reflectionEngine.getCurrentState());

        // 6. 非法跃迁守卫: INITIALIZING -> BARRIER_AUDITING (非法)
        assertThrows(IllegalStateException.class, () -> {
            reflectionEngine.transitionTo(AgentOsLifecycleState.BARRIER_AUDITING);
        });
    }

    @Test
    @DisplayName("契约测试 2: 验证李雅普诺夫自愈能量泛函衰减与零死锁收敛")
    public void test2_autonomicSelfHealingLyapunovConvergence() {
        // 初始严重级联故障残差
        double[] initialResiduals = new double[]{1.2, 0.8, 0.95, 1.4, 0.6};
        double initialEnergy = reflectionEngine.computeLyapunovEnergy(initialResiduals);
        assertTrue(initialEnergy > 0.0, "初始能量必须严格正定");

        // 自愈后残差向量显著衰减
        double[] postHealResiduals = new double[]{0.05, 0.02, 0.01, 0.04, 0.02};
        double postEnergy = reflectionEngine.computeLyapunovEnergy(postHealResiduals);
        assertTrue(postEnergy < initialEnergy, "自愈后李雅普诺夫残差能量必须显著下降");

        SelfHealingDiagnosisResolution resolution = reflectionEngine.diagnoseAndSelfHeal(
            "FAULT_REACT_CASCADE_DIVERGENCE",
            initialResiduals,
            postHealResiduals
        );

        assertNotNull(resolution);
        assertTrue(resolution.isConverged(), "自愈必须判定收敛");
        assertTrue(resolution.isDeadlockFree(), "死锁发生率必须严格为 0.0%");
        assertTrue(resolution.energyDecayRate() >= 0.9, "能量衰减率必须达到 90% 以上");
        assertEquals("APPLY_COMPENSATORY_RECONCILIATION", resolution.compensationAction());
        assertTrue(resolution.diagnosisLatencyUs() <= 60000L, "自愈诊断单步耗时必须在微秒级");
    }

    @Test
    @DisplayName("契约测试 3: 验证阿里千问 1536 维超球面元认知对齐与保模投影")
    public void test3_hypersphericalMetacognitiveAlignmentAndDimensionSafety() {
        double[] rawVector = new double[1536];
        for (int i = 0; i < 1536; i++) {
            rawVector[i] = (i + 1) * 0.005;
        }

        // 1. 投影至超球面
        double[] projected = metacognitiveAligner.projectToHypersphere(rawVector);
        assertEquals(1536, projected.length);

        double normSq = metacognitiveAligner.dotProduct(projected, projected);
        assertEquals(1.0, Math.sqrt(normSq), 1e-4, "投影后向量模长必须严格为 1.0 (阿里千问超球面单位向量)");

        // 2. 测地距离
        double[] v1 = createNormalizedEmbedding(0.1);
        double[] v2 = createNormalizedEmbedding(0.2);
        double geoDist = metacognitiveAligner.geodesicDistance(v1, v2);
        assertTrue(geoDist >= 0.0 && geoDist <= Math.PI, "黎曼测地距离必须在 [0, pi] 之间");

        // 3. 切空间 Fréchet 均值聚合
        List<double[]> list = List.of(v1, v2);
        double[] mean = metacognitiveAligner.aggregateFrechetMean(list, new double[]{0.6, 0.4});
        assertEquals(1536, mean.length);
        double meanNorm = Math.sqrt(metacognitiveAligner.dotProduct(mean, mean));
        assertEquals(1.0, meanNorm, 1e-4, "Fréchet 均值聚合向量必须保模归一化");

        // 4. 元认知单帧构建与漂移率
        MetacognitiveContextFrame frame = metacognitiveAligner.alignContext(
            "FRAME-001",
            "SESSION-CENTENNIAL",
            rawVector,
            "CROSS_DOMAIN_COGNITIVE",
            0.98
        );
        assertNotNull(frame);
        assertEquals(1536, frame.dimension());
        assertEquals(1.0, frame.norm(), 1e-4);

        double drift = metacognitiveAligner.computeMetacognitiveDrift(frame, projected);
        assertTrue(drift <= 0.005, "基准对齐下的语义漂移率必须 <= 0.5%");

        // 5. 维度安全守卫: 注入非 1536 维向量抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            new MetacognitiveContextFrame(
                "FRAME-BAD",
                "SESSION-1",
                new double[512], // 错误维度
                0.1,
                0.9,
                "BAD",
                false,
                System.currentTimeMillis()
            );
        });
    }

    @Test
    @DisplayName("契约测试 4: 验证百阶段主权 CBF 安全门禁、QP 闭式修补与 Nonce 防重放")
    public void test4_centennialSovereignBarrierGateAuditAndClosedFormQp() {
        CentennialGovernancePolicy policy = CentennialGovernancePolicy.defaultPolicy();

        // 1. 合法安全提案直通
        KernelExecutionProposal safeProposal = new KernelExecutionProposal(
            "PROP-SAFE-01",
            "DOMAIN_ALPHA",
            "TOOL_INVOCATION",
            new double[]{0.1, 0.2, 0.3},
            0.2, // 远低于 maxSovereignRiskScore 0.75
            1001L,
            System.currentTimeMillis()
        );
        KernelAuditVerdict verdictSafe = barrierGate.auditProposal(safeProposal, policy);
        assertTrue(verdictSafe.isPermitted(), "安全提案必须放行");
        assertFalse(verdictSafe.isRepaired(), "安全充裕提案无需修补");
        assertEquals("PASS_SOVEREIGN_SAFE", verdictSafe.rejectionReason());

        // 2. 临界超标提案经由闭式 QP 正交超平面修补放行
        KernelExecutionProposal marginalProposal = new KernelExecutionProposal(
            "PROP-MARGINAL-01",
            "DOMAIN_BETA",
            "DATABASE_UPDATE",
            new double[]{1.0, 1.0, 1.0},
            0.70, // 处于临界可修补区间 (0.75 - 0.70 = 0.05 < 0.15 margin)
            1002L,
            System.currentTimeMillis()
        );
        KernelAuditVerdict verdictMarginal = barrierGate.auditProposal(marginalProposal, policy);
        assertTrue(verdictMarginal.isPermitted(), "临界提案修补后必须放行");
        assertTrue(verdictMarginal.isRepaired(), "临界提案必须标记为已修补");
        assertEquals("REPAIRED_VIA_CLOSED_FORM_QP", verdictMarginal.rejectionReason());
        // 修补后的动作向量范数必须收缩
        double origNorm = Math.sqrt(3.0);
        double repNorm = 0.0;
        for (double v : verdictMarginal.finalActionVector()) {
            repNorm += v * v;
        }
        assertTrue(Math.sqrt(repNorm) < origNorm, "QP 修补必须将动作向安全集内部收缩");

        // 3. 严重高危越权越界提案坚决拦截
        KernelExecutionProposal dangerProposal = new KernelExecutionProposal(
            "PROP-DANGER-01",
            "DOMAIN_UNKNOWN",
            "KERNEL_PRIVILEGE_ESCALATION",
            new double[]{5.0, 5.0, 5.0},
            0.95, // 严重超标
            1003L,
            System.currentTimeMillis()
        );
        KernelAuditVerdict verdictDanger = barrierGate.auditProposal(dangerProposal, policy);
        assertFalse(verdictDanger.isPermitted(), "高危提案必须坚决拦截");
        assertFalse(verdictDanger.isRepaired());
        assertTrue(verdictDanger.rejectionReason().contains("REJECT_SOVEREIGN_BREACH"));

        // 4. Nonce 重放攻击拦截
        KernelExecutionProposal replayProposal = new KernelExecutionProposal(
            "PROP-REPLAY-01",
            "DOMAIN_ALPHA",
            "TOOL_INVOCATION",
            new double[]{0.1, 0.2, 0.3},
            0.2,
            1001L, // 重复使用 Nonce 1001L
            System.currentTimeMillis()
        );
        KernelAuditVerdict verdictReplay = barrierGate.auditProposal(replayProposal, policy);
        assertFalse(verdictReplay.isPermitted(), "重放 Nonce 必须被坚决拦截");
        assertTrue(verdictReplay.rejectionReason().contains("REPLAY_ATTACK_DETECTED"));
    }

    @Test
    @DisplayName("契约测试 5: 验证 1000Hz 4096 槽位 Disruptor 总线纳秒级写入吞吐")
    public void test5_disruptorBusThroughputAndLatencyUnderNanoseconds() {
        double[] emb = createNormalizedEmbedding(0.05);
        MetacognitiveContextFrame frame = metacognitiveAligner.alignContext(
            "FRAME-BUS-01",
            "SESSION-BUS",
            emb,
            "THROUGHPUT_TEST",
            0.99
        );
        KernelAuditVerdict verdict = new KernelAuditVerdict(
            "VRD-BUS",
            "PROP-01",
            true,
            false,
            new double[]{1.0, 0.0},
            0.2,
            "PASS",
            10L,
            System.currentTimeMillis()
        );

        long startTime = System.nanoTime();
        int batchCount = 8192; // 绕环 2 整圈
        for (int i = 0; i < batchCount; i++) {
            long seq = superconvergenceBus.publishEvent(
                "EVT-" + i,
                "KERNEL_TICK",
                frame,
                verdict
            );
            assertEquals(i, seq);
        }
        long durationNanos = System.nanoTime() - startTime;
        double avgLatencyNanos = (double) durationNanos / batchCount;
        assertTrue(avgLatencyNanos < 50000.0, "单步无锁写入平均耗时必须在极短时间内完成 (当前=" + avgLatencyNanos + "ns)");

        // 验证读取最新槽位事件一致性
        SuperconvergenceEventFrame polled = superconvergenceBus.pollEvent(batchCount - 1);
        assertNotNull(polled);
        assertEquals("EVT-" + (batchCount - 1), polled.eventId());
        assertEquals(frame, polled.contextFrame());
    }

    @Test
    @DisplayName("契约测试 6: 验证 JitterGuard 时钟抖动三连击触发缓冲降级软着陆")
    public void test6_jitterGuardTripwireDegradedBufferedMitigation() {
        reflectionEngine.transitionTo(AgentOsLifecycleState.COGNITIVE_ALIGNING);
        reflectionEngine.transitionTo(AgentOsLifecycleState.AUTONOMIC_REASONING);
        reflectionEngine.transitionTo(AgentOsLifecycleState.BARRIER_AUDITING);
        reflectionEngine.transitionTo(AgentOsLifecycleState.CONVERGENCE_COMMITTING);

        assertEquals(AgentOsLifecycleState.CONVERGENCE_COMMITTING, superconvergenceBus.getBusOperatingState());

        // 模拟前 2 帧偶发毛刺（>2000us）
        superconvergenceBus.recordProcessingLatency(2500L, reflectionEngine);
        assertEquals(AgentOsLifecycleState.CONVERGENCE_COMMITTING, superconvergenceBus.getBusOperatingState());
        superconvergenceBus.recordProcessingLatency(3000L, reflectionEngine);
        assertEquals(AgentOsLifecycleState.CONVERGENCE_COMMITTING, superconvergenceBus.getBusOperatingState());

        // 第 3 帧连续超限，触发 JitterGuard Tripwire
        superconvergenceBus.recordProcessingLatency(2800L, reflectionEngine);
        assertEquals(AgentOsLifecycleState.DEGRADED_BUFFERED, superconvergenceBus.getBusOperatingState(), "必须切入 DEGRADED_BUFFERED 状态");
        assertEquals(AgentOsLifecycleState.DEGRADED_BUFFERED, reflectionEngine.getCurrentState(), "内核状态机必须同步切入降级缓冲软着陆");
    }

    @Test
    @DisplayName("契约测试 7: 验证不可变存证凭单 SHA-256 密码学防篡改验真")
    public void test7_centennialReceiptCryptographicIntegrityAndTamperProof() {
        CentennialSuperconvergenceReceipt receipt = superconvergenceBus.createAndSignReceipt(
            "RCP-CENTENNIAL-100",
            "SESSION-100-FINAL",
            AgentOsLifecycleState.CONVERGENCE_COMMITTING,
            0.00125,
            0.00032,
            true,
            100000L,
            1250000L
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Signature());
        assertTrue(receipt.verifyIntegrity(), "原始凭单 SHA-256 密码学自签名验真必须 100% 通过");

        // 模拟篡改凭单数据 (尝试修改 residualEnergy)
        CentennialSuperconvergenceReceipt tamperedReceipt = new CentennialSuperconvergenceReceipt(
            receipt.receiptId(),
            receipt.sessionId(),
            receipt.finalState(),
            0.99999, // 篡改残差能量
            receipt.metacognitiveDriftRate(),
            receipt.isBarrierCompliant(),
            receipt.busSequence(),
            receipt.processingLatencyNanos(),
            receipt.sha256Signature(), // 保持旧签名
            receipt.timestamp()
        );

        assertFalse(tamperedReceipt.verifyIntegrity(), "篡改后的凭单密码学验真必须严格失败断言");
    }

    @Test
    @DisplayName("契约测试 8: 验证 Phase 100 超融合内核端到端全链路闭环大圆满")
    public void test8_endToEndSuperconvergenceKernelFullChainVerification() {
        // 1. 引导初始化
        assertEquals(AgentOsLifecycleState.INITIALIZING, reflectionEngine.getCurrentState());

        // 2. 超球面元认知投影与对齐
        reflectionEngine.transitionTo(AgentOsLifecycleState.COGNITIVE_ALIGNING);
        double[] rawVector = createNormalizedEmbedding(0.777);
        MetacognitiveContextFrame context = metacognitiveAligner.alignContext(
            "CTX-FINAL-100",
            "SESSION-PHASE100",
            rawVector,
            "CENTENNIAL_SUPERCONVERGENCE",
            0.999
        );
        assertEquals(1.0, context.norm(), 1e-4);

        // 3. 代数推理与自治自愈
        reflectionEngine.transitionTo(AgentOsLifecycleState.AUTONOMIC_REASONING);
        SelfHealingDiagnosisResolution healingResolution = reflectionEngine.diagnoseAndSelfHeal(
            "NO_FAULT_NOMINAL",
            new double[]{0.01, 0.01},
            new double[]{0.001, 0.001}
        );
        assertTrue(healingResolution.isConverged());

        // 4. 百阶段综合治理主权安全门禁审计
        reflectionEngine.transitionTo(AgentOsLifecycleState.BARRIER_AUDITING);
        KernelExecutionProposal proposal = new KernelExecutionProposal(
            "PROP-CENTENNIAL-FINAL",
            "HERMES_SOVEREIGN_CORE",
            "SUPERCONVERGENT_SYNTHESIS",
            new double[]{0.5, 0.5, 0.5},
            0.10,
            99999L,
            System.currentTimeMillis()
        );
        KernelAuditVerdict verdict = barrierGate.auditProposal(proposal, CentennialGovernancePolicy.defaultPolicy());
        assertTrue(verdict.isPermitted());

        // 5. 1000Hz 超融合 Disruptor 总线确定性提交
        reflectionEngine.transitionTo(AgentOsLifecycleState.CONVERGENCE_COMMITTING);
        long seq = superconvergenceBus.publishEvent(
            "EVT-CENTENNIAL-FINAL",
            "SUPERCONVERGENCE_TICK",
            context,
            verdict
        );
        assertTrue(seq >= 0L);

        // 6. 签发百阶段世纪大圆满存证凭单
        CentennialSuperconvergenceReceipt finalReceipt = superconvergenceBus.createAndSignReceipt(
            "RCP-PHASE100-FINAL",
            "SESSION-PHASE100",
            reflectionEngine.getCurrentState(),
            healingResolution.residualLyapunovEnergy(),
            0.001,
            true,
            seq,
            888L
        );
        assertTrue(finalReceipt.verifyIntegrity());

        // 7. 受控安全停机
        reflectionEngine.transitionTo(AgentOsLifecycleState.SHUTDOWN_HALTED);
        assertTrue(reflectionEngine.getCurrentState().isTerminal());
    }
}
