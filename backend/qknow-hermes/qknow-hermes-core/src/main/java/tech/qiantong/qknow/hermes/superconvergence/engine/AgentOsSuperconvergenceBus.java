package tech.qiantong.qknow.hermes.superconvergence.engine;

import tech.qiantong.qknow.hermes.superconvergence.dto.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 4096 槽位 Disruptor 超融合总线（无锁定长环形队列与 JitterGuard 抖动监控）
 */
public class AgentOsSuperconvergenceBus {

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;
    private static final long JITTER_THRESHOLD_US = 2000; // 2ms 抖动门限

    private final SuperconvergenceEventFrame[] ringBuffer;
    private final AtomicLong publishSequence;
    private int consecutiveJitterCount;
    private volatile AgentOsLifecycleState busOperatingState;

    public AgentOsSuperconvergenceBus() {
        this.ringBuffer = new SuperconvergenceEventFrame[BUFFER_SIZE];
        this.publishSequence = new AtomicLong(0);
        this.consecutiveJitterCount = 0;
        this.busOperatingState = AgentOsLifecycleState.CONVERGENCE_COMMITTING;
    }

    /**
     * 发布超融合事件至环形缓冲区（纳秒级无锁写入）
     */
    public long publishEvent(
        String eventId,
        String eventType,
        MetacognitiveContextFrame contextFrame,
        KernelAuditVerdict auditVerdict
    ) {
        long seq = publishSequence.getAndIncrement();
        int slot = (int) (seq & BUFFER_MASK);
        long nowNanos = System.nanoTime();

        SuperconvergenceEventFrame frame = new SuperconvergenceEventFrame(
            seq,
            eventId,
            eventType != null ? eventType : "DEFAULT_EVENT",
            contextFrame,
            auditVerdict,
            nowNanos,
            0L
        );
        ringBuffer[slot] = frame;
        return seq;
    }

    /**
     * 读取指定序列号的事件单帧
     */
    public SuperconvergenceEventFrame pollEvent(long sequence) {
        int slot = (int) (sequence & BUFFER_MASK);
        return ringBuffer[slot];
    }

    /**
     * JitterGuard 抖动监控与软着陆触发
     *
     * @param latencyUs        单帧处理耗时（微秒）
     * @param reflectionEngine 自治自愈引擎引用
     */
    public synchronized void recordProcessingLatency(
        long latencyUs,
        AutonomicSelfHealingReflectionEngine reflectionEngine
    ) {
        if (latencyUs > JITTER_THRESHOLD_US) {
            consecutiveJitterCount++;
            if (consecutiveJitterCount >= 3) {
                busOperatingState = AgentOsLifecycleState.DEGRADED_BUFFERED;
                if (reflectionEngine != null && reflectionEngine.canTransition(AgentOsLifecycleState.DEGRADED_BUFFERED)) {
                    reflectionEngine.transitionTo(AgentOsLifecycleState.DEGRADED_BUFFERED);
                }
            }
        } else {
            consecutiveJitterCount = 0;
        }
    }

    /**
     * 获取当前总线运行状态
     */
    public AgentOsLifecycleState getBusOperatingState() {
        return busOperatingState;
    }

    /**
     * 生成并自签名不可变世纪存证凭单
     */
    public CentennialSuperconvergenceReceipt createAndSignReceipt(
        String receiptId,
        String sessionId,
        AgentOsLifecycleState finalState,
        double residualEnergy,
        double metacognitiveDriftRate,
        boolean isBarrierCompliant,
        long busSequence,
        long processingLatencyNanos
    ) {
        long timestamp = System.currentTimeMillis();
        String signature = CentennialSuperconvergenceReceipt.computeSignature(
            receiptId,
            sessionId,
            finalState,
            residualEnergy,
            metacognitiveDriftRate,
            busSequence,
            timestamp
        );

        return new CentennialSuperconvergenceReceipt(
            receiptId,
            sessionId,
            finalState,
            residualEnergy,
            metacognitiveDriftRate,
            isBarrierCompliant,
            busSequence,
            processingLatencyNanos,
            signature,
            timestamp
        );
    }
}
