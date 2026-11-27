package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

/**
 * 谓词基数语义分类枚举（对应 Phase 8.1 定理 8.1 冲突消解门控）。
 * <p>
 * 彻底根治 Graphiti 等无基数约束图谱在高并发写入时全图误杀多值并存事实的缺陷：
 * 仅对 {@link #FUNCTIONAL} 单值函数依赖谓词在同主语活跃槽位内执行确定性覆写截断。
 * </p>
 */
public enum PredicateCardinality {
    /** 单值函数依赖属性（如现居地、当前职位、当前首选平台）：新值覆盖并截断旧活跃事实 */
    FUNCTIONAL,
    /** 多值集合属性（如掌握技能、合作品牌、历史经历）：新事实与既有活跃事实并存，严禁跨目标误杀 */
    MULTI_VALUED,
    /** 可累计事件流（如就医记录、出行次数、阅读进度）：保留唯一标识与原始轮次指针以支持精确去重计数 */
    COUNTABLE_EVENT
}
