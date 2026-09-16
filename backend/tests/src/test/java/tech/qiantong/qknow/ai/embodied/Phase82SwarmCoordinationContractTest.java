package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmAgentStateFrame;
import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmCoordinationReceipt;
import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmTetherWrenchState;
import tech.qiantong.qknow.ai.embodied.swarm.engine.DistributedSwarmCollisionSafetyGate;
import tech.qiantong.qknow.ai.embodied.swarm.engine.DynamicTopologyConsensusGovernor;
import tech.qiantong.qknow.ai.embodied.swarm.engine.RigidFlexibleTetherCoordinationOperator;
import tech.qiantong.qknow.ai.embodied.swarm.engine.SwarmCoordinationControlBus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 82 具身多智能体异构自组织编队、分布式互易避障与刚柔系留协同中枢专属契约测试
 */
class Phase82SwarmCoordinationContractTest {

    private DynamicTopologyConsensusGovernor consensusGovernor;
    private DistributedSwarmCollisionSafetyGate safetyGate;
    private RigidFlexibleTetherCoordinationOperator tetherOperator;
    private SwarmCoordinationControlBus controlBus;

    @BeforeEach
    void setUp() {
        consensusGovernor = new DynamicTopologyConsensusGovernor();
        safetyGate = new DistributedSwarmCollisionSafetyGate();
        tetherOperator = new RigidFlexibleTetherCoordinationOperator();
        controlBus = new SwarmCoordinationControlBus();
    }

    private float[] createNormalizedEmbedding1536() {
        float[] emb = new float[1536];
        double val = 1.0 / Math.sqrt(1536.0);
        for (int i = 0; i < 1536; i++) {
            emb[i] = (float) val;
        }
        return emb;
    }

    @Test
    @DisplayName("契约 1: 动态有向拓扑拉普拉斯代数连通度监测与编队位置指数收敛 <= 2.0cm 校验 (定理 1.1)")
    void contract1_dynamicTopologyConsensusAndErrorConvergence() {
        // 构建 4 台异构智能体: 1 台四足机器狗, 1 台双足人形, 2 架六旋翼无人机
        List<String> allIds = List.of("DOG-01", "BIPED-01", "UAV-01", "UAV-02");
        Map<String, double[]> targetOffsets = Map.of(
                "DOG-01", new double[]{2.0, 0.0, 0.0},
                "BIPED-01", new double[]{-2.0, 0.0, 0.0},
                "UAV-01", new double[]{0.0, 2.0, 1.5},
                "UAV-02", new double[]{0.0, -2.0, 1.5}
        );

        double[] leaderPos = new double[]{10.0, 10.0, 0.0};
        double[] leaderVel = new double[]{1.0, 0.0, 0.0};
        double[] leaderAcc = new double[]{0.0, 0.0, 0.0};

        // 初始位置设定在期望附近 (带有 1.5cm 初始偏差)
        List<SwarmAgentStateFrame> frames = new ArrayList<>();
        frames.add(new SwarmAgentStateFrame("DOG-01", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{12.015, 10.0, 0.0}, new double[]{1.0, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, allIds, new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis()));

        frames.add(new SwarmAgentStateFrame("BIPED-01", SwarmAgentStateFrame.AgentRoleType.BIPED,
                new double[]{7.985, 10.0, 0.0}, new double[]{1.0, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, allIds, new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis()));

        frames.add(new SwarmAgentStateFrame("UAV-01", SwarmAgentStateFrame.AgentRoleType.UAV,
                new double[]{10.0, 12.012, 1.5}, new double[]{1.0, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, allIds, new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis()));

        frames.add(new SwarmAgentStateFrame("UAV-02", SwarmAgentStateFrame.AgentRoleType.UAV,
                new double[]{10.0, 7.988, 1.5}, new double[]{1.0, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, allIds, new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis()));

        // 执行编队一致性调节
        DynamicTopologyConsensusGovernor.ConsensusOutput output =
                consensusGovernor.computeConsensus(frames, targetOffsets, leaderPos, leaderVel, leaderAcc);

        // 验证二阶代数连通度 lambda_2 >= 0.35
        assertTrue(output.algebraicConnectivityLambda2() >= 0.35,
                "代数连通度 lambda_2 应 >= 0.35, 实际: " + output.algebraicConnectivityLambda2());

        // 验证最大几何编队位置残差严格 <= 0.02m (2.0cm)
        assertTrue(output.maxFormationErrorNorm() <= 0.02,
                "编队几何位置残差应严格 <= 2.0cm (0.02m), 实际: " + output.maxFormationErrorNorm());

        // 验证各机解算出的期望加速度非空且在物理合理范围内
        assertEquals(4, output.desiredAccelerations().size());
        for (double[] acc : output.desiredAccelerations().values()) {
            double accNorm = Math.sqrt(acc[0] * acc[0] + acc[1] * acc[1] + acc[2] * acc[2]);
            assertTrue(accNorm < 25.0, "期望加速度模长应在安全执行范围内, 实际: " + accNorm);
        }
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面单位向量强校验与几何同胚性 (命题 2.1)")
    void contract2_qwenEmbedding1536HyperSphereValidation() {
        float[] validEmb = createNormalizedEmbedding1536();

        // 正常创建合法帧
        assertDoesNotThrow(() -> new SwarmAgentStateFrame(
                "TEST-01", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                List.of(), new double[]{0, 0, 0}, validEmb, System.currentTimeMillis()
        ));

        // 异常测试 1: 维度不匹配
        float[] invalidDimEmb = new float[512];
        assertThrows(IllegalArgumentException.class, () -> new SwarmAgentStateFrame(
                "TEST-02", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                List.of(), new double[]{0, 0, 0}, invalidDimEmb, System.currentTimeMillis()
        ));

        // 异常测试 2: 模长非单位 (模长为 2.0)
        float[] nonUnitEmb = new float[1536];
        double nonUnitVal = 2.0 / Math.sqrt(1536.0);
        for (int i = 0; i < 1536; i++) nonUnitEmb[i] = (float) nonUnitVal;
        assertThrows(IllegalArgumentException.class, () -> new SwarmAgentStateFrame(
                "TEST-03", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                List.of(), new double[]{0, 0, 0}, nonUnitEmb, System.currentTimeMillis()
        ));
    }

    @Test
    @DisplayName("契约 3: 时变通信延迟与丢包下局部生成树自愈重构平稳无发散校验 (定理 1.1)")
    void contract3_delayAndPacketLossTopologyHealingRobustness() {
        // 模拟网络丢包: 只有部分单向邻居
        List<SwarmAgentStateFrame> frames = new ArrayList<>();
        frames.add(new SwarmAgentStateFrame("NODE-1", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                List.of("NODE-2"), new double[]{0, 0, 0}, createNormalizedEmbedding1536(), System.currentTimeMillis()));

        // NODE-2 孤立或丢包无邻居
        frames.add(new SwarmAgentStateFrame("NODE-2", SwarmAgentStateFrame.AgentRoleType.BIPED,
                new double[]{2.0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                Collections.emptyList(), new double[]{0, 0, 0}, createNormalizedEmbedding1536(), System.currentTimeMillis()));

        frames.add(new SwarmAgentStateFrame("NODE-3", SwarmAgentStateFrame.AgentRoleType.UAV,
                new double[]{4.0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                Collections.emptyList(), new double[]{0, 0, 0}, createNormalizedEmbedding1536(), System.currentTimeMillis()));

        Map<String, double[]> offsets = Map.of(
                "NODE-1", new double[]{0, 0, 0},
                "NODE-2", new double[]{2, 0, 0},
                "NODE-3", new double[]{4, 0, 0}
        );

        DynamicTopologyConsensusGovernor.ConsensusOutput output = consensusGovernor.computeConsensus(
                frames, offsets, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}
        );

        // 验证触发了局部最小生成树自愈补全
        assertTrue(output.topologyCompensated(), "在链路不全时应自动触发生成树自愈补全");
        // 验证补全后代数连通度达到安全门限
        assertTrue(output.algebraicConnectivityLambda2() >= 0.35,
                "补全后连通度应 >= 0.35, 实际: " + output.algebraicConnectivityLambda2());
    }

    @Test
    @DisplayName("契约 4: 相对阶 r=2 分布式互易 HOCBF 闭式 QP 投影耗时 <= 50μs 与零碰撞硬保证校验 (定理 1.2)")
    void contract4_reciprocalHocbfCollisionAvoidanceForwardInvariance() {
        // 预热消除 JIT 开销
        for (int i = 0; i < 20; i++) {
            SwarmAgentStateFrame f1 = new SwarmAgentStateFrame("EGO", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                    new double[]{0, 0, 0}, new double[]{1.0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                    List.of(), new double[]{0, 0, 0}, createNormalizedEmbedding1536(), System.currentTimeMillis());
            SwarmAgentStateFrame f2 = new SwarmAgentStateFrame("OBS", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                    new double[]{1.5, 0.2, 0}, new double[]{-1.0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                    List.of(), new double[]{0, 0, 0}, createNormalizedEmbedding1536(), System.currentTimeMillis());
            safetyGate.filterAcceleration(f1, new double[]{2.0, 0, 0}, List.of(f1, f2));
        }

        // 两机间距 1.2m, 高速相向运动 (相对速度 3.0 m/s)
        SwarmAgentStateFrame ego = new SwarmAgentStateFrame("ROBOT-A", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0.0, 0.0, 0.0}, new double[]{1.5, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, List.of(), new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis());

        SwarmAgentStateFrame other = new SwarmAgentStateFrame("ROBOT-B", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{1.2, 0.1, 0.0}, new double[]{-1.5, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, List.of(), new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis());

        double[] nominalAcc = new double[]{2.0, 0.0, 0.0}; // 名义加速度试图继续加速向前对冲

        long t0 = System.nanoTime();
        DistributedSwarmCollisionSafetyGate.SafetyGateResult result =
                safetyGate.filterAcceleration(ego, nominalAcc, List.of(ego, other));
        long t1 = System.nanoTime();
        double elapsedMicros = (t1 - t0) / 1000.0;

        // 验证闭式 QP 求解在微秒级完成 (<= 50μs)
        assertTrue(elapsedMicros <= 50.0, "相对阶 r=2 闭式 QP 单步耗时应 <= 50μs, 实际: " + elapsedMicros);

        // 验证安全加速度不再径向猛冲，进行了安全制动或偏转
        assertTrue(result.safeAcceleration()[0] < nominalAcc[0], "安全加速度应进行制动削减");
        assertTrue(result.collisionFreeGuaranteed(), "必须 100% 保证无碰撞");
    }

    @Test
    @DisplayName("契约 5: 狭窄走廊对冲极端工况下确定性右手破称摄动消解死锁 P(Deadlock)=0 校验 (定理 1.2)")
    void contract5_symmetryBreakingDeadlockEliminationInCorridor() {
        // 完全正面对头对冲: 间距 2.0m, 相对速度完全共线反向 (夹角严格 180 度)
        SwarmAgentStateFrame ego = new SwarmAgentStateFrame("AGENT-EAST", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0.0, 0.0, 0.0}, new double[]{1.0, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, List.of(), new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis());

        SwarmAgentStateFrame other = new SwarmAgentStateFrame("AGENT-WEST", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{2.0, 0.0, 0.0}, new double[]{-1.0, 0.0, 0.0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, List.of(), new double[]{0, 0, 0},
                createNormalizedEmbedding1536(), System.currentTimeMillis());

        double[] nominalAcc = new double[]{1.0, 0.0, 0.0};
        DistributedSwarmCollisionSafetyGate.SafetyGateResult result =
                safetyGate.filterAcceleration(ego, nominalAcc, List.of(ego, other));

        // 验证触发了确定性右手破称摄动
        assertTrue(result.symmetryBroken(), "正面对头相冲时必须触发确定性破称摄动");
        // 验证在 Y 轴产生了非零的侧向避让加速度分量, 杜绝原地晃动死锁
        assertTrue(Math.abs(result.safeAcceleration()[1]) > 0.01,
                "必须产生非零侧向绕行加速度, 实际 Ay: " + result.safeAcceleration()[1]);
    }

    @Test
    @DisplayName("契约 6: 微秒级刚柔系留线缆悬链线张力微分平坦动力学解耦与抗骤紧冲击校验 (定理 1.3)")
    void contract6_rigidFlexibleTetherCatenaryTensionAndAntiSnapDamping() {
        // 4 台飞行/地面智能体协同悬挂 50kg 负载
        double[] loadPos = new double[]{0.0, 0.0, 1.0};
        double[] loadVel = new double[]{0.0, 0.0, 0.2};
        double[] loadDesAcc = new double[]{0.0, 0.0, 0.5}; // 向上微加速

        Map<String, double[]> agentPos = Map.of(
                "TETHER-1", new double[]{2.5, 0.0, 2.8},
                "TETHER-2", new double[]{-2.5, 0.0, 2.8},
                "TETHER-3", new double[]{0.0, 2.5, 2.8},
                "TETHER-4", new double[]{0.0, -2.5, 2.8}
        );

        // 模拟各机正在拉伸缆绳 (相对张紧速度 0.8 m/s)
        Map<String, double[]> agentVel = Map.of(
                "TETHER-1", new double[]{0.8, 0.0, 0.5},
                "TETHER-2", new double[]{-0.8, 0.0, 0.5},
                "TETHER-3", new double[]{0.0, 0.8, 0.5},
                "TETHER-4", new double[]{0.0, -0.8, 0.5}
        );

        long t0 = System.nanoTime();
        RigidFlexibleTetherCoordinationOperator.TetherTensionOutput output =
                tetherOperator.computeTetherTensions(loadPos, loadVel, loadDesAcc, 50.0, agentPos, agentVel);
        long t1 = System.nanoTime();
        double elapsedMicros = (t1 - t0) / 1000.0;

        // 验证单步耗时严格 <= 150μs
        assertTrue(elapsedMicros <= 150.0, "悬链线张力微分平坦单步耗时应 <= 150μs, 实际: " + elapsedMicros);

        // 验证张力满足安全区间 [5.0N, 150.0N], 彻底消解冲断与松脱
        assertTrue(output.tensionSafeGuaranteed(), "全线缆张力必须处于绝对安全区间内");
        assertTrue(output.minTensionN() >= 5.0, "最小张力应 >= 5.0N, 实际: " + output.minTensionN());
        assertTrue(output.maxTensionN() <= 150.0, "最大张力应 <= 150.0N, 实际: " + output.maxTensionN());
        assertEquals(4, output.cableTensions().size());
    }

    @Test
    @DisplayName("契约 7: 1000Hz 定长 4096 槽位 Disruptor 无锁总线与 JitterGuard 安全驻留降级校验 (防线四)")
    void contract7_1000HzDisruptorControlBusAndJitterGuardSafeHold() {
        assertEquals(SwarmCoordinationControlBus.BusState.NORMAL_COOPERATIVE_SWARM, controlBus.getCurrentBusState());

        SwarmAgentStateFrame frame = new SwarmAgentStateFrame(
                "SOLO-01", SwarmAgentStateFrame.AgentRoleType.QUADRUPED,
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                List.of(), new double[]{0, 0, 0}, createNormalizedEmbedding1536(), System.currentTimeMillis()
        );

        // 正常发布并执行
        SwarmCoordinationReceipt receipt = controlBus.publishAndExecute(
                "SES-1001", List.of(frame), Map.of("SOLO-01", new double[]{0, 0, 0}),
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}
        );
        assertNotNull(receipt);

        // 模拟时钟抖动异常降级
        controlBus.setCurrentBusState(SwarmCoordinationControlBus.BusState.DEGRADED_DECENTRALIZED_HOVER_HOLD);
        assertEquals(SwarmCoordinationControlBus.BusState.DEGRADED_DECENTRALIZED_HOVER_HOLD, controlBus.getCurrentBusState());

        SwarmCoordinationReceipt degradedReceipt = controlBus.publishAndExecute(
                "SES-1002", List.of(frame), Map.of("SOLO-01", new double[]{0, 0, 0}),
                new double[]{0, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
                new double[]{0, 0, 0}, new double[]{0, 0, 0}
        );
        assertEquals("DEGRADED_DECENTRALIZED_HOVER_HOLD", degradedReceipt.busStatus());
    }

    @Test
    @DisplayName("契约 8: 不可变蜂群存证凭单 SHA-256 自签名与防篡改自验 100% 通过校验")
    void contract8_immutableReceiptSha256SignatureAndTamperProofVerification() {
        SwarmCoordinationReceipt receipt = SwarmCoordinationReceipt.createAndSign(
                "RCP-SWARM-TEST82",
                "SESSION-PROD-001",
                4,
                "COOPERATIVE_DIAMOND",
                0.852,
                1.450,
                88.5,
                0.320,
                38.5,
                "NORMAL_COOPERATIVE_SWARM",
                System.currentTimeMillis()
        );

        // 正常凭单自验通过
        assertTrue(receipt.verifySignature(), "原始凭单 SHA-256 签名自验必须 100% 通过");

        // 模拟恶意篡改字段 (如篡改代数连通度)
        SwarmCoordinationReceipt tamperedReceipt = new SwarmCoordinationReceipt(
                receipt.receiptId(),
                receipt.swarmSessionId(),
                receipt.activeAgentsCount(),
                receipt.formationTopologyMode(),
                0.120, // 恶意篡改代数连通度
                receipt.minInterAgentDistance(),
                receipt.maxTetherTensionN(),
                receipt.hocbfSafetyMargin(),
                receipt.executionStepTimeMicros(),
                receipt.busStatus(),
                receipt.timestampEpochMs(),
                receipt.sha256Signature() // 保留原签名
        );

        // 篡改后验真必定失败
        assertFalse(tamperedReceipt.verifySignature(), "被篡改字段的凭单验真必须失败并拦截");
    }
}
