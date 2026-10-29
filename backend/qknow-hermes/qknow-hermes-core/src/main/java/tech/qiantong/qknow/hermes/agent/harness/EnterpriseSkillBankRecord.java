package tech.qiantong.qknow.hermes.agent.harness;

import java.util.List;

/**
 * 企业技能库契约。技能 ID 与超球面余弦准入门禁均来自静态配置。
 */
public record EnterpriseSkillBankRecord(
        List<String> activeSkillIds,
        double cosineGateThreshold
) {
    public EnterpriseSkillBankRecord {
        activeSkillIds = activeSkillIds == null ? List.of() : List.copyOf(activeSkillIds);
        for (String skillId : activeSkillIds) {
            if (skillId == null || skillId.isBlank()) {
                throw new IllegalArgumentException("activeSkillIds 不能包含空技能 ID");
            }
        }
        if (!Double.isFinite(cosineGateThreshold) || cosineGateThreshold < 0.0D || cosineGateThreshold > 1.0D) {
            throw new IllegalArgumentException("cosineGateThreshold 必须在 [0, 1] 区间");
        }
    }

    public String canonical() {
        return String.join(",", activeSkillIds) + "\u001f" + cosineGateThreshold;
    }

    public static EnterpriseSkillBankRecord empty() {
        return new EnterpriseSkillBankRecord(List.of(), 0.88D);
    }
}
