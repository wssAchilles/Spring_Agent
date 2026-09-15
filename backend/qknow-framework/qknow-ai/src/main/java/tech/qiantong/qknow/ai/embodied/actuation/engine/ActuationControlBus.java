package tech.qiantong.qknow.ai.embodied.actuation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.actuation.dto.ContinuousActuationReceipt;
import tech.qiantong.qknow.ai.embodied.actuation.dto.ContinuousTrajectoryCommand;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 生产级 1000Hz 定长 4096 槽位无锁执行控制总线 (Disruptor 架构)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class ActuationControlBus {

    private static final Logger log = LoggerFactory.getLogger(ActuationControlBus.class);

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    public enum BusState {
        NORMAL,
        WARNING_JITTER,
        DEGRADED_SOFT_LANDING
    }

    private final ContinuousTrajectorySmoother smoother;
    private final HighOrderBarrierGovernor governor;
    private final AdaptiveImpedanceActuator actuator;

    private final AtomicLong sequence = new AtomicLong(0);
    private final ContinuousActuationReceipt[] ringBuffer = new ContinuousActuationReceipt[BUFFER_SIZE];

    private volatile BusState currentState = BusState.NORMAL;
    private long lastCycleTimestampNs = 0L;
    private int consecutiveJitterCount = 0;

    public ActuationControlBus(ContinuousTrajectorySmoother smoother,
                               HighOrderBarrierGovernor governor,
                               AdaptiveImpedanceActuator actuator) {
        this.smoother = smoother;
        this.governor = governor;
        this.actuator = actuator;
    }

    /**
     * 1000Hz 单周期驱动派发
     */
    public ContinuousActuationReceipt executeCycle(ContinuousTrajectoryCommand command,
                                                   double currentTime,
                                                   double[] measuredForce) {
        long now = System.nanoTime();

        // 1. 时延抖动监控器 (Jitter Guard)
        if (lastCycleTimestampNs > 0) {
            long deltaNs = now - lastCycleTimestampNs;
            long jitterNs = Math.abs(deltaNs - 1_000_000L); // 目标 1ms
            if (jitterNs > 5_000_000L) { // 抖动超过 5ms
                consecutiveJitterCount++;
                if (consecutiveJitterCount >= 3) {
                    currentState = BusState.DEGRADED_SOFT_LANDING;
                    log.warn("总线严重时延抖动超限 (>5ms) 连续触发，进入软着陆降级状态！");
                } else {
                    currentState = BusState.WARNING_JITTER;
                }
            } else {
                consecutiveJitterCount = 0;
                if (currentState == BusState.WARNING_JITTER) {
                    currentState = BusState.NORMAL;
                }
            }
        }
        lastCycleTimestampNs = now;

        // 2. 若处于软着陆状态，执行平滑抱闸制动
        if (currentState == BusState.DEGRADED_SOFT_LANDING) {
            ContinuousActuationReceipt receipt = ContinuousActuationReceipt.generate(
                    command.getCommandId(), "DEGRADED_HASH",
                    0.0, -command.getMaxAcceleration(), 0.0,
                    0.0, 0.0, "BUS_DEGRADED"
            );
            recordReceipt(receipt);
            return receipt;
        }

        // 3. 五次样条平滑器采样
        SpatialPose3D startPose = command.getWaypoints().isEmpty()
                ? new SpatialPose3D(0, 0, 0, 0, 0, 0)
                : command.getWaypoints().get(0);
        SpatialPose3D endPose = command.getWaypoints().size() > 1
                ? command.getWaypoints().get(command.getWaypoints().size() - 1)
                : startPose;

        ContinuousTrajectorySmoother.QuinticCoefficients spline = smoother.fitQuinticSpline(
                startPose, endPose, command.getDurationSeconds()
        );
        ContinuousTrajectorySmoother.TrajectoryState state = spline.evaluate(currentTime);

        // 4. HOCBF 安全屏障二次规划滤波
        double[] pos = new double[]{state.position().getX(), state.position().getY(), state.position().getZ()};
        HighOrderBarrierGovernor.GovernorResult govRes = governor.filter(pos, state.velocity(), state.acceleration());

        // 5. 自适应阻抗力控闭环
        AdaptiveImpedanceActuator.ActuatorResponse actRes = actuator.update(
                new double[]{0, 0, 0}, state.velocity(), measuredForce, 0.001
        );

        // 6. 生成并存证不可变收据
        String status = govRes.isIntervened() ? govRes.status() : "NORMAL_EXECUTED";
        double velNorm = Math.sqrt(state.velocity()[0] * state.velocity()[0]
                + state.velocity()[1] * state.velocity()[1]
                + state.velocity()[2] * state.velocity()[2]);
        double accNorm = Math.sqrt(govRes.safeAcceleration()[0] * govRes.safeAcceleration()[0]
                + govRes.safeAcceleration()[1] * govRes.safeAcceleration()[1]
                + govRes.safeAcceleration()[2] * govRes.safeAcceleration()[2]);

        ContinuousActuationReceipt receipt = ContinuousActuationReceipt.generate(
                command.getCommandId(),
                "CMD_HASH_" + command.getCommandId().hashCode(),
                velNorm, accNorm, state.maxJerkNorm(),
                govRes.barrierMargin(), actRes.forceMagnitude(),
                status
        );

        recordReceipt(receipt);
        return receipt;
    }

    private void recordReceipt(ContinuousActuationReceipt receipt) {
        long seq = sequence.getAndIncrement();
        int slot = (int) (seq & BUFFER_MASK);
        ringBuffer[slot] = receipt;
    }

    public BusState getCurrentState() { return currentState; }
    public void setCurrentState(BusState state) { this.currentState = state; }
    public long getCommittedCount() { return sequence.get(); }
}
