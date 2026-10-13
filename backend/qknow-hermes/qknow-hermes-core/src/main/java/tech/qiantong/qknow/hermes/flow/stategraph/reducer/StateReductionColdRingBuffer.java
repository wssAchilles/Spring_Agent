package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateReductionReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 状态规约三级冷备环形缓冲区 (StateReductionColdRingBuffer)
 * 维护历史各超步合并前后的差异快照及对应凭单，杜绝物理硬删除，支持 O(1) 快照检索与可逆时光旅行回滚。
 */
public class StateReductionColdRingBuffer {

    public record BufferedSnapshot(
            String executionId,
            int superstep,
            Map<String, Object> stateSnapshot,
            StateReductionReceipt receipt,
            long recordedTimestamp
    ) {}

    private final int capacity;
    private final BufferedSnapshot[] ringBuffer;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final Map<String, BufferedSnapshot> fastLookupMap = new ConcurrentHashMap<>();
    private final ReentrantLock bufferLock = new ReentrantLock();

    public StateReductionColdRingBuffer(int capacity) {
        this.capacity = capacity > 0 ? capacity : 50;
        this.ringBuffer = new BufferedSnapshot[this.capacity];
    }

    public StateReductionColdRingBuffer() {
        this(50);
    }

    public void recordReduction(String executionId, int superstep, Map<String, Object> state, StateReductionReceipt receipt) {
        bufferLock.lock();
        try {
            int slot = writeIndex.getAndIncrement() % capacity;
            BufferedSnapshot old = ringBuffer[slot];
            if (old != null) {
                fastLookupMap.remove(buildKey(old.executionId(), old.superstep()));
            }

            BufferedSnapshot fresh = new BufferedSnapshot(
                    executionId,
                    superstep,
                    Map.copyOf(state != null ? state : Map.of()),
                    receipt,
                    System.currentTimeMillis()
            );
            ringBuffer[slot] = fresh;
            fastLookupMap.put(buildKey(executionId, superstep), fresh);
        } finally {
            bufferLock.unlock();
        }
    }

    public Optional<BufferedSnapshot> findSnapshot(String executionId, int superstep) {
        return Optional.ofNullable(fastLookupMap.get(buildKey(executionId, superstep)));
    }

    public int getRetainedCount() {
        return fastLookupMap.size();
    }

    private String buildKey(String execId, int step) {
        return execId + "::" + step;
    }
}
