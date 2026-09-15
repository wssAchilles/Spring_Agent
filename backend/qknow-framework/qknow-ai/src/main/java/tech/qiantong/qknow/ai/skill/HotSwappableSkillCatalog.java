package tech.qiantong.qknow.ai.skill;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 62: 写时复制 (COW) 零停机热插拔技能库管理器
 * <p>
 * 基于定理 1.3，基于原子引用实现技能在线发布与热替换，在途请求因果无干扰，报错率为 0。
 */
public class HotSwappableSkillCatalog {

    private final AtomicReference<Map<String, SkillMetadata>> catalogRef = new AtomicReference<>(Map.of());

    /**
     * 注册或热替换升级技能 (Hot-Deploy)
     */
    public synchronized void hotDeploySkill(SkillMetadata newSkill) {
        if (newSkill == null || newSkill.skillId() == null) {
            throw new IllegalArgumentException("待部署技能不能为空");
        }
        Map<String, SkillMetadata> oldMap = catalogRef.get();
        Map<String, SkillMetadata> newMap = new HashMap<>(oldMap);
        newMap.put(newSkill.skillId(), newSkill);
        catalogRef.set(Collections.unmodifiableMap(newMap));
    }

    /**
     * 安全标记废弃技能 (Hot-Deprecate)
     */
    public synchronized void hotDeprecateSkill(String skillId) {
        if (skillId == null) return;
        Map<String, SkillMetadata> oldMap = catalogRef.get();
        SkillMetadata existing = oldMap.get(skillId);
        if (existing != null) {
            SkillMetadata deprecated = new SkillMetadata(
                    existing.skillId(), existing.name(), existing.description(),
                    existing.version(), existing.embeddingVector(), existing.inputSchema(),
                    existing.outputSchema(), existing.dependencies(), existing.successRate(),
                    true // 标记废弃
            );
            Map<String, SkillMetadata> newMap = new HashMap<>(oldMap);
            newMap.put(skillId, deprecated);
            catalogRef.set(Collections.unmodifiableMap(newMap));
        }
    }

    /**
     * 获取当前版本不可变快照 (提供在途请求无锁读取)
     */
    public Map<String, SkillMetadata> getSnapshot() {
        return catalogRef.get();
    }

    public SkillMetadata getSkill(String skillId) {
        return catalogRef.get().get(skillId);
    }

    public int getCatalogSize() {
        return catalogRef.get().size();
    }

    public void clear() {
        catalogRef.set(Map.of());
    }
}
