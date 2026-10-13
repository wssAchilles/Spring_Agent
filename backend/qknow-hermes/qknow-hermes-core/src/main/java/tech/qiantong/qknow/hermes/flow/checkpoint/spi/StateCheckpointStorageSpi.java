package tech.qiantong.qknow.hermes.flow.checkpoint.spi;

import java.util.Optional;

/**
 * 可插拔云原生状态检查点存储 SPI 接口
 * 保证状态更新符合 Fencing Token 单调递增写屏障与因果一致性割集
 */
public interface StateCheckpointStorageSpi {

    /**
     * 保存检查点（严格 Fencing Token 单调写屏障）
     * 若现存 fencingToken >= proposedToken，原子拒绝写入，防止脑裂覆写
     */
    boolean saveCheckpointWithFencing(StateCheckpointRecord record, long fencingToken);

    /**
     * 加载指定运行时的状态检查点
     */
    Optional<StateCheckpointRecord> loadCheckpoint(String runtimeId);

    /**
     * 双轨原子 CAS 抢占租约 (Lease / Heartbeat TTL)
     */
    LeaseAcquisitionReceipt tryAcquireLease(String runtimeId, String ownerId, long ttlMs, long expectedFencingToken);

    /**
     * 租约心跳续期
     */
    boolean heartbeatLease(String runtimeId, String ownerId, long ttlMs);

    /**
     * 软删除检查点（转入三级冷备环形缓冲区，防物理硬删除）
     */
    boolean archiveCheckpointToColdBuffer(String runtimeId);

    /**
     * SPI 实现类型标识
     */
    String getStorageType();
}
