package tech.qiantong.qknow.hermes.coalition.engine;

import tech.qiantong.qknow.hermes.coalition.dto.CoalitionEventFrame;
import tech.qiantong.qknow.hermes.coalition.dto.CoalitionSettlementReceipt;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 1000Hz 4096 槽位 Disruptor 无锁跨域协同总线
 */
public class CrossDomainCoalitionControlBus {

    private static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    private final CoalitionEventFrame[] ringBuffer = new CoalitionEventFrame[BUFFER_SIZE];
    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicInteger consecutiveJitterCount = new AtomicInteger(0);
    private final AtomicReference<String> busStatus = new AtomicReference<>("STATUS_NORMAL");

    public CrossDomainCoalitionControlBus() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            ringBuffer[i] = new CoalitionEventFrame(i, "INIT", "00", false, System.nanoTime());
        }
    }

    /**
     * 纳秒级非阻塞发布事件单帧
     */
    public long publishEvent(String eventType, String payloadHash, boolean isJitterDetected) {
        long currentSeq = sequence.getAndIncrement();
        int slot = (int) (currentSeq & BUFFER_MASK);

        if (isJitterDetected) {
            int jc = consecutiveJitterCount.incrementAndGet();
            if (jc >= 3) {
                busStatus.set("STATUS_DEGRADED_BUFFERED");
            }
        } else {
            consecutiveJitterCount.set(0);
            if (!"STATUS_NORMAL".equals(busStatus.get())) {
                busStatus.set("STATUS_NORMAL");
            }
        }

        CoalitionEventFrame frame = new CoalitionEventFrame(
            currentSeq,
            eventType,
            payloadHash,
            isJitterDetected,
            System.nanoTime()
        );
        ringBuffer[slot] = frame;
        return currentSeq;
    }

    /**
     * 签发不可变密码学执行凭单
     */
    public CoalitionSettlementReceipt signReceipt(
        String coalitionId,
        String settlementId,
        int memberCount,
        double totalValue,
        double paretoUtility,
        double maxCredit,
        boolean isSovereigntyCompliant,
        long latencyUs
    ) {
        String receiptId = "RCPT-COALITION-" + System.nanoTime();
        long now = System.currentTimeMillis();
        String signature = CoalitionSettlementReceipt.computeSignature(
            receiptId,
            coalitionId,
            settlementId,
            totalValue,
            paretoUtility,
            now
        );

        return new CoalitionSettlementReceipt(
            receiptId,
            coalitionId,
            settlementId,
            memberCount,
            totalValue,
            paretoUtility,
            maxCredit,
            isSovereigntyCompliant,
            latencyUs,
            signature,
            now
        );
    }

    public String getBusStatus() {
        return busStatus.get();
    }

    public long getPublishedSequence() {
        return sequence.get();
    }
}
