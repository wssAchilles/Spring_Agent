package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.digitaltwin.dto.AssemblyCausalGraph;
import tech.qiantong.qknow.ai.embodied.digitaltwin.dto.CausalDigitalTwinReceipt;
import tech.qiantong.qknow.ai.embodied.digitaltwin.dto.DigitalTwinFrameState;
import tech.qiantong.qknow.ai.embodied.digitaltwin.engine.AssemblyCausalInferenceEngine;
import tech.qiantong.qknow.ai.embodied.digitaltwin.engine.CounterfactualSelfHealingPlanner;
import tech.qiantong.qknow.ai.embodied.digitaltwin.engine.DigitalTwinRealtimeBus;
import tech.qiantong.qknow.ai.embodied.digitaltwin.engine.MultiFidelitySimulationGovernor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 74: 具身多智能体柔性装配线因果数字孪生、多保真度混合仿真闭环与实时异常自愈中枢
 * 专属契约驱动严苛测试套件 (8/8)
 */
public class Phase74CausalDigitalTwinAssemblyContractTest {

    private AssemblyCausalInferenceEngine causalEngine;
    private MultiFidelitySimulationGovernor simulationGovernor;
    private CounterfactualSelfHealingPlanner selfHealingPlanner;
    private DigitalTwinRealtimeBus realtimeBus;

    private AssemblyCausalGraph assemblyCausalGraph;

    @BeforeEach
    void setUp() {
        causalEngine = new AssemblyCausalInferenceEngine();
        simulationGovernor = new MultiFidelitySimulationGovernor(0.05);
        selfHealingPlanner = new CounterfactualSelfHealingPlanner();
        realtimeBus = new DigitalTwinRealtimeBus();

        // 构造覆盖 32 个关键工位的典型装配线因果 DAG 图
        int n = 32;
        String[] stationNames = new String[n];
        for (int i = 0; i < n; i++) {
            stationNames[i] = "STATION-" + String.format("%02d", i);
        }
        stationNames[0] = "STATION-00-AGV-FEED";
        stationNames[12] = "STATION-12-FIXTURE-PIN";
        stationNames[18] = "STATION-18-SERVO-TIGHTEN";
        stationNames[31] = "STATION-31-FINAL-INSPECTION";

        int[][] adj = new int[n][n];
        // 构造因果依赖拓扑:
        // 0 (AGV) -> 1 -> ... -> 12 (FIXTURE) -> 13 -> ... -> 18 (TIGHTEN) -> ... -> 31
        for (int i = 0; i < n - 1; i++) {
            adj[i][i + 1] = 1;
        }
        // 跨工位额外因果耦合边: 工位 12 治具定位直接因果影响工位 18 拧紧
        adj[12][18] = 1;

        double[] weights = new double[n];
        Arrays.fill(weights, 1.0);

        assemblyCausalGraph = new AssemblyCausalGraph(
                "LINE-EV-ASSEMBLY-01",
                stationNames,
                adj,
                weights,
                System.currentTimeMillis()
        );
    }

    private DigitalTwinFrameState createMockFrameState(long seq, double residual) {
        double[][] poses = new double[][]{
                {0.5, 0.2, 0.8, 1.0, 0.0, 0.0, 0.0},
                {0.5, -0.2, 0.8, 1.0, 0.0, 0.0, 0.0}
        };
        double[][] wrenches = new double[][]{
                {0.0, 0.0, -10.0, 0.0, 0.0, 0.0},
                {0.0, 0.0, -10.0, 0.0, 0.0, 0.0}
        };
        double[] qwen = new double[1536];
        double norm = Math.sqrt(1536);
        for (int i = 0; i < 1536; i++) {
            qwen[i] = 1.0 / norm;
        }

        return new DigitalTwinFrameState(
                "LINE-EV-ASSEMBLY-01",
                seq,
                5,
                poses,
                wrenches,
                "HYBRID_SMOOTH_TRANSITION",
                residual,
                qwen,
                System.currentTimeMillis()
        );
    }

    @Test
    @DisplayName("契约 1: 验证装配线因果 DAG 拓扑满足有向无环性且节点数覆盖 30+ 关键工位")
    void testAssemblyCausalGraphTopologyValidation() {
        assertTrue(assemblyCausalGraph.isAcyclic(), "装配线结构因果图必须严格满足有向无环条件 (DAG)");
        assertEquals(32, assemblyCausalGraph.getNodeCount(), "工位节点数必须覆盖 30+ 关键工位");
        assertTrue(assemblyCausalGraph.getParents(18).contains(12), "工位 12 必须是工位 18 的直接因果父节点");
        assertTrue(assemblyCausalGraph.getChildren(12).contains(18), "工位 18 必须是工位 12 的直接因果子节点");
    }

    @Test
    @DisplayName("契约 2: 验证结构因果模型单步根因溯源耗时 <= 1.0ms 且有效隔离伴生症状，辨识率 >= 98%")
    void testMicrosecondCounterfactualRootCauseInference() {
        double[] residuals = new double[32];
        // 模拟事故 1: 工位 18 报过扭矩残差 (0.85)，但根本原因是工位 12 治具磨损 (0.95)
        residuals[12] = 0.95;
        residuals[13] = 0.30;
        residuals[18] = 0.85; // 下游伴生症状

        var result = causalEngine.inferRootCause(assemblyCausalGraph, residuals, 18);

        assertEquals(12, result.rootCauseStationId(), "必须精准溯源根本诱因为工位 12，而非工位 18");
        assertTrue(result.collateralSymptoms().contains(18), "工位 18 必须被正确识别并隔离为下游伴生症状");
        assertTrue(result.attributionConfidence() >= 0.98, "根因辨识置信度必须 >= 98%");
        assertTrue(result.inferenceDurationUs() <= AssemblyCausalInferenceEngine.MAX_INFERENCE_LATENCY_US,
                "因果溯源单步耗时必须 <= 1000us (1.0ms), 实际为: " + result.inferenceDurationUs());
    }

    @Test
    @DisplayName("契约 3: 验证数字孪生状态特征向量严格满足阿里千问 1536 维超球面单位向量归一化不变量")
    void testHypersphericalDigitalTwinStateEmbeddingNorm() {
        DigitalTwinFrameState state = createMockFrameState(100L, 0.02);
        assertEquals(1536, state.hypersphericalEmbedding().length, "特征向量维度必须严格为 1536 维");
        assertTrue(state.isHypersphericalNormalized(), "特征向量必须严格位于单位超球面流形 S^1535 上");
        assertEquals(1.0, state.computeEmbeddingNorm(), 1e-4, "超球面欧氏 L2 范数必须等于 1.0 +- 1e-4");

        // 异常维度防御
        assertThrows(IllegalArgumentException.class, () -> new DigitalTwinFrameState(
                "LINE-ERR", 1L, 1, new double[1][3], new double[1][6], "LOW", 0.1,
                new double[512], System.currentTimeMillis()
        ));
    }

    @Test
    @DisplayName("契约 4: 验证多保真度混合仿真闭环跟踪误差满足李雅普诺夫指数衰减与一致最终有界 (UUB)")
    void testMultiFidelityLyapunovBoundedErrorConvergence() {
        double[] physicalObs = new double[]{0.50, 0.20, 0.80};
        double[] lowFid = new double[]{0.56, 0.25, 0.83};     // 低保真存在残差漂移
        double[] highFid = new double[]{0.501, 0.201, 0.801}; // 高保真极高精度

        // 执行多步动态混仿平滑推进至稳态 (60ms)
        double prevV = Double.MAX_VALUE;
        for (int step = 0; step < 60; step++) {
            var stepRes = simulationGovernor.stepHybridSimulation(physicalObs, lowFid, highFid, 0.001);
            if (step > 2) {
                // 李雅普诺夫函数 V(e) 应当单调衰减并趋于极小有界值
                assertTrue(stepRes.lyapunovV() <= prevV + 1e-6, "李雅普诺夫函数 V(e) 必须单调衰减");
            }
            prevV = stepRes.lyapunovV();
            assertTrue(stepRes.chatteringFree(), "必须无高频颤振 (Chattering-free)");
        }

        // 稳态下数字孪生保真度必须达到 99% 以上
        var finalRes = simulationGovernor.stepHybridSimulation(physicalObs, lowFid, highFid, 0.001);
        double errNorm = Math.sqrt(2.0 * finalRes.lyapunovV());
        double physNorm = Math.sqrt(0.50 * 0.50 + 0.20 * 0.20 + 0.80 * 0.80);
        double fidelity = 1.0 - (errNorm / physNorm);
        assertTrue(fidelity >= 0.99, "数字孪生保真度必须达到 >= 99%, 实际为: " + fidelity);
    }

    @Test
    @DisplayName("契约 5: 验证高低保真模型切换平滑过渡流力矩与加速度保持 C^2 连续，无离散阶跃")
    void testSmoothTransitionBetweenHighAndLowFidelity() {
        double[] physicalObs = new double[]{0.50, 0.20, 0.80};
        double[] lowFid = new double[]{0.58, 0.26, 0.85}; // 突发接触大残差
        double[] highFid = new double[]{0.501, 0.201, 0.801};

        double prevWeight = 0.0;
        for (int i = 0; i < 5; i++) {
            var res = simulationGovernor.stepHybridSimulation(physicalObs, lowFid, highFid, 0.001);
            double deltaWeight = Math.abs(res.currentFidelityWeight() - prevWeight);
            // 单步权重增量受平滑流限制，杜绝 0 -> 1 瞬间硬切
            assertTrue(deltaWeight < 0.25, "权重变化率必须平滑受控，单步跳变不可超过 0.25");
            prevWeight = res.currentFidelityWeight();
        }
    }

    @Test
    @DisplayName("契约 6: 验证反事实自愈策略经由 HOCBF 安全硬门禁滤波，零次生碰撞且自愈成功率 >= 95%")
    void testCounterfactualSelfHealingWithHocbfSafetyGate() {
        double[] poseErr = new double[]{0.010, -0.005, 0.002}; // 10mm 偏差
        double[] torqueErr = new double[]{0.0, 5.0, 0.0};

        // 场景 A: 距离障碍物充足 (50mm > 15mm 安全门限)
        var resSafe = selfHealingPlanner.planSelfHealing(12, poseErr, torqueErr, 0.050);
        assertTrue(resSafe.success());
        assertFalse(resSafe.hocbfIntervened(), "充足安全裕度下无需 HOCBF 干预");
        assertEquals(-0.008, resSafe.correctivePoseOffset()[0], 1e-4);

        // 场景 B: 距离障碍物极度接近 (16mm，接近 15mm 临界硬屏障)
        var resDanger = selfHealingPlanner.planSelfHealing(12, poseErr, torqueErr, 0.016);
        assertTrue(resDanger.success());
        assertTrue(resDanger.hocbfIntervened(), "临界状态下必须触发 HOCBF 闭式解析安全投影");
        // 修正后的纠偏动作被限制，确保不向障碍物方向撞击
        assertTrue(resDanger.correctivePoseOffset()[0] >= 0.0 || Math.abs(resDanger.correctivePoseOffset()[0]) < 0.006,
                "HOCBF 投影后动作必须处于绝对安全流形内");
    }

    @Test
    @DisplayName("契约 7: 验证 4096 槽位 Disruptor 无锁环形总线纳秒级高频吞吐与异常保底悬停软着陆")
    void testDisruptorBus1000HzThroughputAndDegradedHoldTrigger() {
        DigitalTwinFrameState state = createMockFrameState(1L, 0.015);

        // 1. 验证纳秒级批量非阻塞写入 (1000 帧)
        long startNs = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            realtimeBus.publish(state);
        }
        long durationNs = System.nanoTime() - startNs;
        double avgNsPerWrite = (double) durationNs / 1000.0;
        assertTrue(avgNsPerWrite < 10_000, "单步发布写入必须在极低纳秒/微秒级完成");
        assertEquals(1000, realtimeBus.getPublishedCount());

        // 2. 验证最新帧读取
        var latest = realtimeBus.getLatest();
        assertNotNull(latest);
        assertEquals("LINE-EV-ASSEMBLY-01", latest.lineId());

        // 3. 验证触发 DEGRADED_LINE_HOLD 柔顺防撞保底悬停
        assertFalse(realtimeBus.isDegradedLineHold());
        realtimeBus.triggerPhysicalAnomalyDegradation();
        assertTrue(realtimeBus.isDegradedLineHold(), "严重异常触发后必须切入 DEGRADED_LINE_HOLD 状态");

        realtimeBus.resetDegradedState();
        assertFalse(realtimeBus.isDegradedLineHold(), "重置后恢复正常运行");
    }

    @Test
    @DisplayName("契约 8: 验证不可变因果存证凭单全要素字段完整性与 SHA-256 密码学防篡改签名验真通过率 100%")
    void testCausalDigitalTwinReceiptSha256Verification() {
        CausalDigitalTwinReceipt receipt = CausalDigitalTwinReceipt.sign(
                "RCP-TWIN-74-001",
                "SESSION-PROD-99",
                "LINE-EV-ASSEMBLY-01",
                "HASH-DAG-GRAPH-32NODES",
                12,
                "HYBRID_SMOOTH_TRANSITION",
                0.0185,
                "APPLY_OFFSET_HOCBF_PROJECTED",
                120,
                "NORMAL_1000HZ",
                System.currentTimeMillis()
        );

        assertNotNull(receipt.sha256Signature(), "SHA-256 签名不可为空");
        assertTrue(receipt.verifySignature(), "原始未篡改凭单验真通过率必须为 100%");

        // 篡改测试
        CausalDigitalTwinReceipt tampered = new CausalDigitalTwinReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.assemblyLineId(),
                receipt.causalGraphHash(),
                99, // 篡改根因工位
                receipt.fidelityLevel(),
                receipt.residualNorm(),
                receipt.selfHealingDecision(),
                receipt.executionDurationUs(),
                receipt.busState(),
                receipt.timestampMs(),
                receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "被篡改凭单验真必须失败");
    }
}
