package tech.qiantong.qknow.hermes.memory.bitemporal;

/**
 * 三级冷备可逆存储隔离层级枚举（严格遵循 AGENTS.md §7.5 零物理硬删除铁律）。
 */
public enum ColdStandbyTier {
    /** 当前活跃在线认知热图 */
    ACTIVE_HOT,
    /** 矛盾截断或引用归零后的温备可逆隔离层 */
    WARM_QUARANTINE,
    /** 定长环形冷备长期法医归档层 */
    COLD_RING_ARCHIVE
}
