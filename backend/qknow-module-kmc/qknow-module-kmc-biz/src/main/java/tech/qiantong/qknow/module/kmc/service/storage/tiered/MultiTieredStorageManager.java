package tech.qiantong.qknow.module.kmc.service.storage.tiered;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库三级冷热分层存储路由总协调器 (MultiTieredStorageManager)
 *
 * 管理 Hot (内存 HNSW/SIMD)、Warm (本地磁盘/倒排)、Cold (ZSTD 深度压缩归档包) 三级存储。
 *
 * @author qknow
 */
@Slf4j
@Service
public class MultiTieredStorageManager {

    public enum StorageTier {
        HOT,    // 热层：内存驻留，P95 <= 10ms
        WARM,   // 温层：磁盘 IVFFlat / Tantivy，P95 <= 50ms
        COLD    // 冷层：ZSTD 压缩归档包，节约 80%+ 存储
    }

    @Data
    @Builder
    public static class TieredSegmentVO {
        private Long segmentId;
        private Long kbId;
        private String content;
        private float[] vector1536;
        private StorageTier storageTier;
        private double heatScore;
        private long lastAccessTime;
        private String merkleRootHash;
        private String archivePath;
    }

    // 模拟三级物理存储介质容器
    private final Map<Long, TieredSegmentVO> hotMemoryStore = new ConcurrentHashMap<>();
    private final Map<Long, TieredSegmentVO> warmDiskStore = new ConcurrentHashMap<>();
    private final Map<Long, TieredSegmentVO> coldArchiveStore = new ConcurrentHashMap<>();

    private ColdStorageArchiver coldArchiver = new ColdStorageArchiver();

    public void setColdArchiver(ColdStorageArchiver archiver) {
        this.coldArchiver = archiver;
    }

    /**
     * 写入切片，默认写入 HOT 层
     */
    public void putSegment(TieredSegmentVO segment) {
        if (segment == null || segment.getSegmentId() == null) return;
        segment.setLastAccessTime(System.currentTimeMillis());
        if (segment.getStorageTier() == null) {
            segment.setStorageTier(StorageTier.HOT);
        }

        switch (segment.getStorageTier()) {
            case HOT -> {
                hotMemoryStore.put(segment.getSegmentId(), segment);
                warmDiskStore.remove(segment.getSegmentId());
                coldArchiveStore.remove(segment.getSegmentId());
            }
            case WARM -> {
                warmDiskStore.put(segment.getSegmentId(), segment);
                hotMemoryStore.remove(segment.getSegmentId());
                coldArchiveStore.remove(segment.getSegmentId());
            }
            case COLD -> {
                coldArchiveStore.put(segment.getSegmentId(), segment);
                hotMemoryStore.remove(segment.getSegmentId());
                warmDiskStore.remove(segment.getSegmentId());
            }
        }
    }

    /**
     * 透明读取切片：若处于 COLD 层，触发按需秒级解冻
     */
    public Optional<TieredSegmentVO> getSegment(Long segmentId) {
        // 1. 先查 HOT 内存层 (耗时 <= 1ms)
        TieredSegmentVO seg = hotMemoryStore.get(segmentId);
        if (seg != null) {
            seg.setLastAccessTime(System.currentTimeMillis());
            seg.setHeatScore(seg.getHeatScore() + 1.0);
            return Optional.of(seg);
        }

        // 2. 查 WARM 磁盘层 (耗时 <= 10ms)
        seg = warmDiskStore.get(segmentId);
        if (seg != null) {
            seg.setLastAccessTime(System.currentTimeMillis());
            seg.setHeatScore(seg.getHeatScore() + 1.0);
            return Optional.of(seg);
        }

        // 3. 查 COLD 冷归档层 (触发秒级按需解冻 Thaw)
        seg = coldArchiveStore.get(segmentId);
        if (seg != null) {
            log.info("[TieredStorage] 命中 COLD 冷层切片 {}, 触发按需解冻...", segmentId);
            TieredSegmentVO thawed = coldArchiver.thawSingleSegment(seg);
            thawed.setLastAccessTime(System.currentTimeMillis());
            thawed.setHeatScore(thawed.getHeatScore() + 2.0); // 解冻增加热度
            // 自动回填至 WARM 层
            warmDiskStore.put(segmentId, thawed);
            coldArchiveStore.remove(segmentId);
            return Optional.of(thawed);
        }

        return Optional.empty();
    }

    /**
     * 执行向量余弦相似度检索
     */
    public List<TieredSegmentVO> searchTopK(float[] queryVector, int topK) {
        List<TieredSegmentVO> allActive = new ArrayList<>();
        allActive.addAll(hotMemoryStore.values());
        allActive.addAll(warmDiskStore.values());

        // 计算余弦得分并排序
        allActive.sort((a, b) -> {
            double simA = dotProduct(queryVector, a.getVector1536());
            double simB = dotProduct(queryVector, b.getVector1536());
            return Double.compare(simB, simA);
        });

        int limit = Math.min(topK, allActive.size());
        return allActive.subList(0, limit);
    }

    public Map<StorageTier, Integer> getTierStatistics() {
        Map<StorageTier, Integer> stats = new EnumMap<>(StorageTier.class);
        stats.put(StorageTier.HOT, hotMemoryStore.size());
        stats.put(StorageTier.WARM, warmDiskStore.size());
        stats.put(StorageTier.COLD, coldArchiveStore.size());
        return stats;
    }

    public Map<Long, TieredSegmentVO> getHotStore() { return hotMemoryStore; }
    public Map<Long, TieredSegmentVO> getWarmStore() { return warmDiskStore; }
    public Map<Long, TieredSegmentVO> getColdStore() { return coldArchiveStore; }

    private static double dotProduct(float[] v1, float[] v2) {
        if (v1 == null || v2 == null) return 0.0;
        double sum = 0.0;
        int len = Math.min(v1.length, v2.length);
        for (int i = 0; i < len; i++) {
            sum += v1[i] * v2[i];
        }
        return sum;
    }
}
