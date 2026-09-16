package tech.qiantong.qknow.ai.embodied.swarm.engine;

import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmAgentStateFrame;
import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmCoordinationReceipt;
import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmTetherWrenchState;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 实时定长 4096 槽位 Disruptor 无锁蜂群控制总线与 JitterGuard (防线四)
 * 采用 2^12 定长环形缓冲、缓存行对齐消减伪共享，实现微秒级流水线并发并监控时钟抖动自动触发去中心化安全悬停驻留
 */
public class SwarmCoordinationControlBus {

    public static final int BUFFER_SIZE = 4096; // 2^12 定长槽位
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;
    private static final long JITTER_THRESHOLD_NS = 2_000_000L; // 2.0ms 时钟抖动阈值
    private static final int MAX_JITTER_CONSECUTIVE_FRAMES = 3;

    public enum BusState {
        NORMAL_COOPERATIVE_SWARM,          // 正常协同编队与牵引
        DEGRADED_DECENTRALIZED_HOVER_HOLD  // 降级去中心化原地悬停/驻留安全保底
    }

    // 缓存行填充消除 False Sharing
    public static class BusSlot {
        public long p1, p2, p3, p4, p5, p6, p7; // 前置填充
        public volatile long sequence = -1L;
        public volatile long timestampNs = 0L;
        public volatile List<SwarmAgentStateFrame> agentFrames = null;
        public volatile SwarmTetherWrenchState wrenchState = null;
        public volatile SwarmCoordinationReceipt receipt = null;
        public long p8, p9, p10, p11, p12, p13, p14; // 后置填充
    }

    private final BusSlot[] ringBuffer;
    private final AtomicLong producerSequence = new AtomicLong(0L);
    private final DynamicTopologyConsensusGovernor consensusGovernor;
    private final DistributedSwarmCollisionSafetyGate safetyGate;
    private final RigidFlexibleTetherCoordinationOperator tetherOperator;

    private volatile BusState currentBusState = BusState.NORMAL_COOPERATIVE_SWARM;
    private int consecutiveJitterCount = 0;
    private long lastTimestampNs = 0L;

    public SwarmCoordinationControlBus() {
        this.ringBuffer = new BusSlot[BUFFER_SIZE];
        for (int i = 0; i < BUFFER_SIZE; i++) {
            this.ringBuffer[i] = new BusSlot();
        }
        this.consensusGovernor = new DynamicTopologyConsensusGovernor();
        this.safetyGate = new DistributedSwarmCollisionSafetyGate();
        this.tetherOperator = new RigidFlexibleTetherCoordinationOperator();
    }

    /**
     * 发布一帧智能体状态并在 1000Hz 周期内执行控制闭环与安全监控
     *
     * @param sessionId 集群协同会话 ID
     * @param agentFrames 当前所有智能体状态帧
     * @param targetOffsets 目标编队相对几何偏移 [agentId -> double[3]]
     * @param leaderPos 领航者参考位置 [3]
     * @param leaderVel 领航者参考速度 [3]
     * @param leaderAcc 领航者参考加速度 [3]
     * @param loadPos 牵引重载工件位置 [3]
     * @param loadVel 牵引重载工件速度 [3]
     * @return 签发并归档的不可变存证凭单
     */
    public SwarmCoordinationReceipt publishAndExecute(
            String sessionId,
            List<SwarmAgentStateFrame> agentFrames,
            Map<String, double[]> targetOffsets,
            double[] leaderPos,
            double[] leaderVel,
            double[] leaderAcc,
            double[] loadPos,
            double[] loadVel
    ) {
        long startNano = System.nanoTime();
        long seq = producerSequence.getAndIncrement();
        int slotIndex = (int) (seq & BUFFER_MASK);
        BusSlot slot = ringBuffer[slotIndex];

        // 1. JitterGuard 时钟抖动守卫
        long currentNano = System.nanoTime();
        if (lastTimestampNs > 0L) {
            long deltaNs = currentNano - lastTimestampNs;
            long expectedPeriodNs = 1_000_000L; // 1.0ms (1000Hz)
            long jitterNs = Math.abs(deltaNs - expectedPeriodNs);
            if (jitterNs > JITTER_THRESHOLD_NS) {
                consecutiveJitterCount++;
                if (consecutiveJitterCount >= MAX_JITTER_CONSECUTIVE_FRAMES) {
                    currentBusState = BusState.DEGRADED_DECENTRALIZED_HOVER_HOLD;
                }
            } else {
                consecutiveJitterCount = 0;
            }
        }
        lastTimestampNs = currentNano;

        // 2. 编队一致性调节器计算
        DynamicTopologyConsensusGovernor.ConsensusOutput consensusOutput =
                consensusGovernor.computeConsensus(agentFrames, targetOffsets, leaderPos, leaderVel, leaderAcc);

        // 若代数连通度跌落为 0 (完全割裂孤岛), 触发安全降级
        if (consensusOutput.algebraicConnectivityLambda2() <= 1e-5) {
            currentBusState = BusState.DEGRADED_DECENTRALIZED_HOVER_HOLD;
        }

        // 3. 分布式互易 HOCBF 安全门禁过滤
        double minSeparation = Double.MAX_VALUE;
        double minMargin = Double.MAX_VALUE;
        boolean anySymmetryBroken = false;
        double[] avgSafeAcc = new double[3];

        for (SwarmAgentStateFrame ego : agentFrames) {
            double[] nomAcc = consensusOutput.desiredAccelerations().getOrDefault(ego.agentId(), new double[]{0.0, 0.0, 0.0});
            DistributedSwarmCollisionSafetyGate.SafetyGateResult gateResult =
                    safetyGate.filterAcceleration(ego, nomAcc, agentFrames);

            if (gateResult.minSeparationDistance() < minSeparation) {
                minSeparation = gateResult.minSeparationDistance();
            }
            if (gateResult.minHocbfSafetyMargin() < minMargin) {
                minMargin = gateResult.minHocbfSafetyMargin();
            }
            if (gateResult.symmetryBroken()) {
                anySymmetryBroken = true;
            }
            for (int k = 0; k < 3; k++) {
                avgSafeAcc[k] += gateResult.safeAcceleration()[k] / agentFrames.size();
            }
        }

        // 4. 刚柔系留线缆悬链线张力解耦
        Map<String, double[]> agentPosMap = new HashMap<>();
        Map<String, double[]> agentVelMap = new HashMap<>();
        for (SwarmAgentStateFrame f : agentFrames) {
            agentPosMap.put(f.agentId(), f.position());
            agentVelMap.put(f.agentId(), f.velocity());
        }
        RigidFlexibleTetherCoordinationOperator.TetherTensionOutput tetherOutput =
                tetherOperator.computeTetherTensions(loadPos, loadVel, leaderAcc, 50.0, agentPosMap, agentVelMap);

        // 5. 组装力学综合状态
        SwarmTetherWrenchState wrenchState = new SwarmTetherWrenchState(
                consensusOutput.maxFormationErrorNorm(),
                consensusOutput.algebraicConnectivityLambda2(),
                minSeparation,
                tetherOutput.maxTensionN(),
                minMargin,
                anySymmetryBroken,
                consensusOutput.topologyCompensated(),
                avgSafeAcc
        );

        long endNano = System.nanoTime();
        double stepTimeMicros = (endNano - startNano) / 1000.0;

        // 6. 签发存证凭单
        String receiptId = "RCP-SWARM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String formationMode = (currentBusState == BusState.NORMAL_COOPERATIVE_SWARM) ? "COOPERATIVE_REGULAR" : "SAFE_DEGRADED_HOLD";
        SwarmCoordinationReceipt receipt = SwarmCoordinationReceipt.createAndSign(
                receiptId,
                sessionId,
                agentFrames.size(),
                formationMode,
                consensusOutput.algebraicConnectivityLambda2(),
                minSeparation,
                tetherOutput.maxTensionN(),
                minMargin,
                stepTimeMicros,
                currentBusState.name(),
                System.currentTimeMillis()
        );

        // 7. 写入无锁槽位
        slot.agentFrames = agentFrames;
        slot.wrenchState = wrenchState;
        slot.receipt = receipt;
        slot.timestampNs = currentNano;
        slot.sequence = seq;

        return receipt;
    }

    public BusState getCurrentBusState() {
        return currentBusState;
    }

    public void setCurrentBusState(BusState state) {
        this.currentBusState = state;
    }
}
