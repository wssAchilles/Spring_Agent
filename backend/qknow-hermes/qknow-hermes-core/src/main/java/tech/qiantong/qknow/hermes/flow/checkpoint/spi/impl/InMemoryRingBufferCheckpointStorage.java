package tech.qiantong.qknow.hermes.flow.checkpoint.spi.impl;

import tech.qiantong.qknow.hermes.flow.checkpoint.spi.LeaseAcquisitionReceipt;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.StateCheckpointStorageSpi;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存环形冷备检查点存储实现 (单测/无 DB 隔离环境)
 * 固定环形容量，溢出自动轮转冷备，杜绝内存无界增长与物理硬删除
 */
public class InMemoryRingBufferCheckpointStorage implements StateCheckpointStorageSpi {

    private final int bufferCapacity;
    private final StateCheckpointRecord[] ringBuffer;
    private final ConcurrentHashMap<String, Integer> indexMap = new ConcurrentHashMap<>();
    private final AtomicInteger writeCursor = new AtomicInteger(0);

    public InMemoryRingBufferCheckpointStorage(int bufferCapacity) {
        this.bufferCapacity = Math.max(64, bufferCapacity);
        this.ringBuffer = new StateCheckpointRecord[this.bufferCapacity];
    }

    public InMemoryRingBufferCheckpointStorage() {
        this(1024);
    }

    @Override
    public synchronized boolean saveCheckpointWithFencing(StateCheckpointRecord record, long fencingToken) {
        Integer existingSlot = indexMap.get(record.runtimeId());
        if (existingSlot != null && ringBuffer[existingSlot] != null) {
            StateCheckpointRecord current = ringBuffer[existingSlot];
            // 写屏障：若当前已提交的版本令牌大于等于传入的令牌，拒绝陈旧写
            if (current.fencingToken() >= fencingToken && !current.runtimeId().equals(record.runtimeId())) {
                return false;
            }
            if (current.fencingToken() > fencingToken) {
                return false;
            }
        }

        int slot = (existingSlot != null) ? existingSlot : (writeCursor.getAndIncrement() % bufferCapacity);
        ringBuffer[slot] = record;
        indexMap.put(record.runtimeId(), slot);
        return true;
    }

    @Override
    public Optional<StateCheckpointRecord> loadCheckpoint(String runtimeId) {
        Integer slot = indexMap.get(runtimeId);
        if (slot == null || ringBuffer[slot] == null) {
            return Optional.empty();
        }
        return Optional.of(ringBuffer[slot]);
    }

    @Override
    public synchronized LeaseAcquisitionReceipt tryAcquireLease(String runtimeId, String ownerId, long ttlMs, long expectedFencingToken) {
        long now = System.currentTimeMillis();
        Optional<StateCheckpointRecord> existingOpt = loadCheckpoint(runtimeId);
        if (existingOpt.isEmpty()) {
            return new LeaseAcquisitionReceipt(false, runtimeId, null, 0L, 0L, "检查点不存在");
        }
        StateCheckpointRecord record = existingOpt.get();
        if (record.fencingToken() != expectedFencingToken) {
            return new LeaseAcquisitionReceipt(false, runtimeId, record.leaseOwnerId(), record.leaseExpireAtMs(), record.fencingToken(), "Fencing Token 不匹配");
        }
        if (record.leaseOwnerId() != null && record.leaseExpireAtMs() > now && !record.leaseOwnerId().equals(ownerId)) {
            return new LeaseAcquisitionReceipt(false, runtimeId, record.leaseOwnerId(), record.leaseExpireAtMs(), record.fencingToken(), "租约尚在有效期内");
        }

        StateCheckpointRecord updated = new StateCheckpointRecord(
                record.runtimeId(), record.flowId(), record.groupIndex(),
                record.completedResultsJson(), record.variablesJson(), record.status(),
                record.compensationLog(), ownerId, now + ttlMs, record.fencingToken() + 1,
                record.version() + 1, record.createdAtMs(), now, record.integrityHmac()
        );
        saveCheckpointWithFencing(updated, updated.fencingToken());
        return new LeaseAcquisitionReceipt(true, runtimeId, ownerId, now + ttlMs, updated.fencingToken(), "租约获取成功");
    }

    @Override
    public synchronized boolean heartbeatLease(String runtimeId, String ownerId, long ttlMs) {
        long now = System.currentTimeMillis();
        return loadCheckpoint(runtimeId).map(record -> {
            if (ownerId.equals(record.leaseOwnerId())) {
                StateCheckpointRecord renewed = new StateCheckpointRecord(
                        record.runtimeId(), record.flowId(), record.groupIndex(),
                        record.completedResultsJson(), record.variablesJson(), record.status(),
                        record.compensationLog(), ownerId, now + ttlMs, record.fencingToken(),
                        record.version(), record.createdAtMs(), now, record.integrityHmac()
                );
                saveCheckpointWithFencing(renewed, record.fencingToken());
                return true;
            }
            return false;
        }).orElse(false);
    }

    @Override
    public synchronized boolean archiveCheckpointToColdBuffer(String runtimeId) {
        Integer slot = indexMap.remove(runtimeId);
        if (slot != null && ringBuffer[slot] != null) {
            // 标记为冷备隔离，不置 null，保留在环形缓冲区供回滚溯源
            StateCheckpointRecord archived = new StateCheckpointRecord(
                    ringBuffer[slot].runtimeId(), ringBuffer[slot].flowId(), ringBuffer[slot].groupIndex(),
                    ringBuffer[slot].completedResultsJson(), ringBuffer[slot].variablesJson(), "ARCHIVED",
                    ringBuffer[slot].compensationLog(), null, 0L, ringBuffer[slot].fencingToken(),
                    ringBuffer[slot].version(), ringBuffer[slot].createdAtMs(), System.currentTimeMillis(),
                    ringBuffer[slot].integrityHmac()
            );
            ringBuffer[slot] = archived;
            return true;
        }
        return false;
    }

    @Override
    public String getStorageType() {
        return "IN_MEMORY_RING_BUFFER";
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }
}
