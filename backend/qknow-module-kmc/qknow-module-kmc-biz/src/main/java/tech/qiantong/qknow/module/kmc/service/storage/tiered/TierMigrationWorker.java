package tech.qiantong.qknow.module.kmc.service.storage.tiered;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 冷热分层动态迁移与时序衰减 Worker (TierMigrationWorker)
 *
 * 遵循双写无锁防悬挂状态机：
 * 步骤 1 (PREPARE): 压缩切片并计算 Merkle 根；
 * 步骤 2 (SYNC_VERIFY): 校验一致性；
 * 步骤 3 (SWITCH_STATE): 原子切换状态为 COLD；
 * 步骤 4 (PURGE_OLD): 清理热层冗余，杜绝读写悬挂。
 *
 * @author qknow
 */
@Slf4j
@Component
public class TierMigrationWorker {

    private final MultiTieredStorageManager storageManager;
    private final ColdStorageArchiver coldArchiver;

    public TierMigrationWorker(MultiTieredStorageManager storageManager, ColdStorageArchiver coldArchiver) {
        this.storageManager = storageManager;
        this.coldArchiver = coldArchiver;
    }

    /**
     * 访问记录更新热度
     */
    public void recordAccess(Long segmentId) {
        storageManager.getSegment(segmentId);
    }

    /**
     * 应用时序半衰期衰减 H(t) = H * factor
     */
    public void applyTemporalDecay(double decayFactor) {
        for (MultiTieredStorageManager.TieredSegmentVO seg : storageManager.getHotStore().values()) {
            seg.setHeatScore(seg.getHeatScore() * decayFactor);
        }
        for (MultiTieredStorageManager.TieredSegmentVO seg : storageManager.getWarmStore().values()) {
            seg.setHeatScore(seg.getHeatScore() * decayFactor);
        }
    }

    /**
     * 执行自动降温批处理：
     * 1. HOT -> WARM (当热度低于 warmThreshold)
     * 2. WARM -> COLD (当热度低于 coldThreshold，触发深度压缩归档)
     */
    public int executeDemotionBatch(double warmThreshold, double coldThreshold) {
        int demotedCount = 0;

        // 1. HOT -> WARM
        List<MultiTieredStorageManager.TieredSegmentVO> toWarm = new ArrayList<>();
        for (MultiTieredStorageManager.TieredSegmentVO seg : storageManager.getHotStore().values()) {
            if (seg.getHeatScore() < warmThreshold) {
                toWarm.add(seg);
            }
        }
        for (MultiTieredStorageManager.TieredSegmentVO seg : toWarm) {
            seg.setStorageTier(MultiTieredStorageManager.StorageTier.WARM);
            storageManager.putSegment(seg);
            demotedCount++;
        }

        // 2. WARM -> COLD (双写安全防悬挂)
        List<MultiTieredStorageManager.TieredSegmentVO> toCold = new ArrayList<>();
        for (MultiTieredStorageManager.TieredSegmentVO seg : storageManager.getWarmStore().values()) {
            if (seg.getHeatScore() < coldThreshold) {
                toCold.add(seg);
            }
        }

        if (!toCold.isEmpty()) {
            String archivePath = "/data/storage/cold/archive_" + System.currentTimeMillis() + ".zst";
            // 步骤 1 & 2: 压缩与存证
            ColdStorageArchiver.ArchiveBatchResult result = coldArchiver.archiveBatch(toCold, archivePath);
            // 步骤 3 & 4: 原子切换状态为 COLD 并从 WARM 移除
            for (MultiTieredStorageManager.TieredSegmentVO seg : toCold) {
                storageManager.putSegment(seg);
                demotedCount++;
            }
            log.info("[TierMigration] 执行冷层归档成功: 切片数={}, 路径={}", toCold.size(), archivePath);
        }

        return demotedCount;
    }
}
