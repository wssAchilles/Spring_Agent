package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.formal.dto.FormalVerificationReceipt;
import tech.qiantong.qknow.ai.embodied.formal.dto.LtlSpecificationFormula;
import tech.qiantong.qknow.ai.embodied.formal.dto.MultiAgentAssemblyState;
import tech.qiantong.qknow.ai.embodied.formal.engine.DeadlockFreePolicySynthesizer;
import tech.qiantong.qknow.ai.embodied.formal.engine.DifferentiableStlRobustnessGovernor;
import tech.qiantong.qknow.ai.embodied.formal.engine.FormalVerificationControlBus;
import tech.qiantong.qknow.ai.embodied.formal.engine.ProductAutomatonModelChecker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 73: 具身多智能体长程装配作业的形式化时序逻辑 (LTL) 验证、模型检测与可微策略综合中枢
 * 专属契约驱动严苛测试套件 (8/8)
 */
public class Phase73FormalLtlAssemblyVerificationContractTest {

    private ProductAutomatonModelChecker modelChecker;
    private DifferentiableStlRobustnessGovernor stlGovernor;
    private DeadlockFreePolicySynthesizer deadlockSynthesizer;
    private FormalVerificationControlBus controlBus;

    private LtlSpecificationFormula assemblySpec;

    @BeforeEach
    void setUp() {
        modelChecker = new ProductAutomatonModelChecker();
        stlGovernor = new DifferentiableStlRobustnessGovernor(10.0);
        deadlockSynthesizer = new DeadlockFreePolicySynthesizer();
        controlBus = new FormalVerificationControlBus();

        // 构造典型长程装配 DFA: 4 状态
        // State 0: APPROACH (初始态)
        // State 1: ALIGN
        // State 2: FASTEN
        // State 3: COMPLETE (接受态)
        Map<String, Integer> props = new HashMap<>();
        props.put("approach", 1); // bitmask 0x01
        props.put("align", 2);    // bitmask 0x02
        props.put("fasten", 4);   // bitmask 0x04
        props.put("lift", 8);     // bitmask 0x08

        int numStates = 4;
        int maxBitmask = 16;
        int[][] transTable = new int[numStates][maxBitmask];
        for (int s = 0; s < numStates; s++) {
            for (int b = 0; b < maxBitmask; b++) {
                transTable[s][b] = LtlSpecificationFormula.TRAP_STATE; // 默认均为非法转移
            }
        }

        // 合法时序推进路径:
        // 0 -> 1 on approach (0x01)
        transTable[0][1] = 1;
        // 1 -> 2 on align (0x02)
        transTable[1][2] = 2;
        // 2 -> 3 on fasten (0x04)
        transTable[2][4] = 3;
        // 3 终态自环保持
        transTable[3][0] = 3;

        Set<Integer> accepting = new HashSet<>();
        accepting.add(3);

        assemblySpec = new LtlSpecificationFormula(
                "SPEC-AIRFRAME-ASSEMBLY-01",
                "G(approach -> F(align & F(fasten))) & G(!fasten -> !lift)",
                props,
                numStates,
                transTable,
                accepting,
                0,
                System.currentTimeMillis()
        );
    }

    private MultiAgentAssemblyState createMockAssemblyState(int dfaState, int activeMask, double stlRob) {
        double[][] poses = new double[][]{
                {0.5, 0.2, 0.8, 1.0, 0.0, 0.0, 0.0},
                {0.5, -0.2, 0.8, 1.0, 0.0, 0.0, 0.0}
        };
        double[][] wrenches = new double[][]{
                {0.0, 0.0, -15.0, 0.0, 0.0, 0.0},
                {0.0, 0.0, -15.0, 0.0, 0.0, 0.0}
        };
        double[] qwen = new double[1536];
        double norm = Math.sqrt(1536);
        for (int i = 0; i < 1536; i++) {
            qwen[i] = 1.0 / norm;
        }

        return new MultiAgentAssemblyState(
                "STATION-WING-01",
                dfaState,
                activeMask,
                poses,
                wrenches,
                stlRob,
                qwen,
                System.currentTimeMillis()
        );
    }

    @Test
    @DisplayName("契约 1: 验证乘积有限自动机单步状态转移无锁查表耗时严格 <= 1.0ms 且接受态判定准确")
    void testProductAutomatonMicrosecondStepValidation() {
        // 0 -> 1
        var res1 = modelChecker.checkTransition(assemblySpec, 0, 1);
        assertTrue(res1.valid(), "单步转移应当合法");
        assertEquals(1, res1.nextState(), "下一状态应推进至 ALIGN (1)");
        assertFalse(res1.isAccepting(), "State 1 不是接受态");
        assertTrue(res1.latencyUs() <= ProductAutomatonModelChecker.MAX_ALLOWABLE_LATENCY_US,
                "单步推演耗时必须 <= 1000us, 实际为: " + res1.latencyUs());

        // 1 -> 2
        var res2 = modelChecker.checkTransition(assemblySpec, 1, 2);
        assertTrue(res2.valid());
        assertEquals(2, res2.nextState());
        assertFalse(res2.isAccepting());

        // 2 -> 3
        var res3 = modelChecker.checkTransition(assemblySpec, 2, 4);
        assertTrue(res3.valid());
        assertEquals(3, res3.nextState());
        assertTrue(res3.isAccepting(), "State 3 必须判定为接受态 (Accepting State)");
    }

    @Test
    @DisplayName("契约 2: 验证在发生非法时序跃迁时，模型检测器拦截率达到 100% 并锁定至安全陷阱态")
    void testIllegalStateTransitionInterception() {
        // 场景 1: 在 State 1 (ALIGN) 阶段，未紧固即企图执行 lift (0x08)
        var resIllegalLift = modelChecker.checkTransition(assemblySpec, 1, 8);
        assertFalse(resIllegalLift.valid(), "未紧固执行吊运必须被形式化拦截");
        assertEquals(LtlSpecificationFormula.TRAP_STATE, resIllegalLift.nextState(), "非法转移必须锁定至 TRAP 态");
        assertTrue(resIllegalLift.violationReason().contains("ILLEGAL_TEMPORAL_TRANSITION"));

        // 场景 2: 在 State 0 (APPROACH) 阶段，试图越级直接紧固 (0x04)
        var resSkippedAlign = modelChecker.checkTransition(assemblySpec, 0, 4);
        assertFalse(resSkippedAlign.valid(), "越级跳步必须被拦截");
        assertEquals(LtlSpecificationFormula.TRAP_STATE, resSkippedAlign.nextState());
    }

    @Test
    @DisplayName("契约 3: 验证多智能体装配时空状态特征向量严格满足阿里千问 1536 维超球面单位向量归一化不变量")
    void testHypersphericalAssemblyStateEmbeddingNorm() {
        MultiAgentAssemblyState state = createMockAssemblyState(0, 1, 0.25);
        assertEquals(1536, state.hypersphericalEmbedding().length, "特征向量维度必须严格为 1536 维");
        assertTrue(state.isHypersphericalNormalized(), "特征向量必须严格位于单位超球面流形 S^1535 上");
        assertEquals(1.0, state.computeEmbeddingNorm(), 1e-4, "欧氏范数必须严格等于 1.0");

        // 异常测试: 维度不符合规范时必须抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new MultiAgentAssemblyState(
                "STATION-ERR", 0, 1, new double[1][3], new double[1][6], 0.1, new double[1024], System.currentTimeMillis()
        ));
    }

    @Test
    @DisplayName("契约 4: 验证基于 Softmin Log-Sum-Exp 的平滑 STL 鲁棒度连续可微且逼近误差有界于 ln(m)/beta")
    void testSmoothStlRobustnessSoftminDifferentiability() {
        double[] distances = new double[]{0.15, 0.20, 0.08, 0.25}; // 真实最小值为 0.08, m=4
        double[][] jacobians = new double[][]{
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}, // 关键约束：Z 方向安全裕度最小
                {0.5, 0.5, 0.0}
        };

        var eval = stlGovernor.evaluateRobustness(distances, jacobians);
        double minReal = 0.08;
        double errorBound = Math.log(4) / 10.0; // ln(4)/10.0 ~ 0.1386

        // 理论不变量: minReal - errorBound <= smoothRob <= minReal
        assertTrue(eval.smoothRobustness() <= minReal, "平滑下界近似不得高于真实最小值");
        assertTrue(eval.smoothRobustness() >= minReal - errorBound, "平滑近似误差必须严格有界于 ln(m)/beta");

        // 空间梯度校验: 临界谓词在 Z 轴 (index 2)，其梯度在 Z 轴权重必须占主导
        assertTrue(eval.spatialGradient()[2] > eval.spatialGradient()[0], "梯度必须严格指向最具危险性的空间几何方向");
        assertTrue(eval.spatialGradient()[2] > eval.spatialGradient()[1]);
    }

    @Test
    @DisplayName("契约 5: 验证当系统逼近时序违反临界点时，梯度引导修正器毫秒级输出前馈修正速度有效阻断违规")
    void testDifferentiableStlGradientVelocityCorrection() {
        // 危险态: 谓词裕度降至 0.02 (< 0.05 临界门限)
        double[] nearViolationDists = new double[]{0.02, 0.10};
        double[][] jacs = new double[][]{
                {0.0, 0.0, 1.0},
                {1.0, 0.0, 0.0}
        };

        var critEval = stlGovernor.evaluateRobustness(nearViolationDists, jacs);
        assertTrue(critEval.interventionTriggered(), "逼近临界点必须触发前馈主动干预");
        assertTrue(critEval.correctiveVelocity()[2] > 0.0, "必须产生沿危险梯度反方向的正向避让速度");
        assertTrue(critEval.correctiveVelocity()[2] <= DifferentiableStlRobustnessGovernor.MAX_CORRECTION_SPEED,
                "前馈修正速度模长必须有界于物理限制");

        // 安全态: 谓词裕度为 0.20 (> 0.05)
        double[] safeDists = new double[]{0.20, 0.30};
        var safeEval = stlGovernor.evaluateRobustness(safeDists, jacs);
        assertFalse(safeEval.interventionTriggered(), "处于安全域内无需干预");
        assertEquals(0.0, safeEval.correctiveVelocity()[2], 1e-6);
    }

    @Test
    @DisplayName("契约 6: 验证在多智能体狭窄工位发生资源互斥死锁前夕，自愈综合器在 <= 2ms 内捕获反例并消除死锁")
    void testCounterexampleGuidedDeadlockResolution() {
        // 场景: Agent 0 持有治具 101 请求治具 102; Agent 1 持有治具 102 请求治具 101
        int[] holding = new int[]{101, 102};
        int[] requesting = new int[]{102, 101};
        int[] priorities = new int[]{10, 5}; // Agent 0 优先级更高

        var result = deadlockSynthesizer.synthesizeDeadlockResolution(holding, requesting, priorities);

        assertTrue(result.deadlockDetected(), "必须成功检出资源互斥等待死锁环路");
        assertTrue(result.resolved(), "自愈综合器必须成功消解死锁");
        assertEquals(0, result.priorityAgentId(), "优先级胜出者应为 Agent 0");
        assertEquals(1, result.yieldingAgentId(), "让步避让者应为 Agent 1");
        assertTrue(result.resolutionLatencyUs() <= DeadlockFreePolicySynthesizer.MAX_RESOLUTION_LATENCY_US,
                "死锁自愈耗时必须 <= 2ms, 实际为: " + result.resolutionLatencyUs() + "us");

        // 让步智能体必须获得切向退出避让速度
        double[][] evasion = result.evasionVelocities();
        assertNotEquals(0.0, evasion[1][1], "让步智能体必须获得侧向避让速度");
        assertTrue(evasion[1][2] > 0.0, "让步智能体必须获得垂直抬升速度以释放空间");
    }

    @Test
    @DisplayName("契约 7: 验证 4096 槽位 Disruptor 无锁环形总线非阻塞高频吞吐与时钟抖动/非法跃迁软着陆触发")
    void testLockFreeBus1000HzThroughputAndDegradedStandstillTrigger() {
        MultiAgentAssemblyState state = createMockAssemblyState(1, 2, 0.18);

        // 1. 验证纳秒级吞吐: 批量写入 1000 帧
        long startNs = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            controlBus.publish(state);
        }
        long durationNs = System.nanoTime() - startNs;
        double avgNsPerWrite = (double) durationNs / 1000.0;
        assertTrue(avgNsPerWrite < 10_000, "总线单次写入耗时必须处于极低纳秒/微秒级");
        assertEquals(1000, controlBus.getPublishedCount());

        // 2. 验证最新状态读取
        var latest = controlBus.getLatest();
        assertNotNull(latest);
        assertEquals("STATION-WING-01", latest.stationId());

        // 3. 验证触发非法转移/软着陆安全挂起
        assertFalse(controlBus.isDegradedSafeStandstill(), "初始总线不处于软着陆态");
        controlBus.triggerIllegalTransitionDegradation();
        assertTrue(controlBus.isDegradedSafeStandstill(), "触发非法时序跃迁拦截后必须切入软着陆状态");

        controlBus.resetDegradedState();
        assertFalse(controlBus.isDegradedSafeStandstill(), "重置后恢复正常执行状态");
    }

    @Test
    @DisplayName("契约 8: 验证不可变存证凭单全要素字段完整性与 SHA-256 密码学防篡改签名验真通过率 100%")
    void testFormalVerificationReceiptSha256Verification() {
        FormalVerificationReceipt receipt = FormalVerificationReceipt.sign(
                "RCP-FORMAL-73-001",
                "SESSION-ASSEMBLY-88",
                "STATION-WING-01",
                "HASH-SPEC-LTL-V3",
                "HASH-DFA-PATH-0123",
                0.125,
                0.045,
                true,
                85,
                false,
                System.currentTimeMillis()
        );

        assertNotNull(receipt.sha256Signature(), "SHA-256 签名不可为空");
        assertTrue(receipt.verifySignature(), "原始未篡改凭单验真通过率必须为 100%");

        // 尝试篡改凭单字段 (如篡改单步延迟)
        FormalVerificationReceipt tampered = new FormalVerificationReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.stationId(),
                receipt.specHash(),
                receipt.dfaPathHash(),
                receipt.instantaneousStlRobustness(),
                receipt.minRobustnessMargin(),
                receipt.deadlockRecovered(),
                999999, // 篡改耗时
                receipt.degradedSafeStandstill(),
                receipt.timestampMs(),
                receipt.sha256Signature() // 沿用原签名
        );
        assertFalse(tampered.verifySignature(), "被篡改凭单验真必须失败");
    }
}
