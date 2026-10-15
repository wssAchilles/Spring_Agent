package tech.qiantong.qknow.hermes.agent.skill;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 企业级受控自进化技能记录不可变凭单 (EnterpriseSkillRecord)
 * <p>
 * 遵循 Java 21 Record 规约、Sutton Option 宏动作形式化定义与阿里千问 1536 维超球面几何基线。
 * 记录封装了经过验证的原子工具有序执行链、强类型参数槽位表、Hoare 形式化前后置规约、
 * 以及由生物复制子动力学（Replicator Dynamics）驱动的自适应健康度与时序衰减状态。
 * </p>
 *
 * @author Achilles
 * @since 2026-10-15
 */
public record EnterpriseSkillRecord(
        String skillId,
        String skillName,
        String description,
        String signatureDsl,
        Map<String, String> parameterSlots,
        List<MacroActionStep> actionSequence,
        String preconditionRule,
        String postconditionRule,
        int usageCount,
        int successCount,
        double healthScore,
        long lastUsedTimestamp,
        float[] skillVector
) implements Serializable {

    private static final int DIMENSION = 1536;

    /**
     * 单个受控宏动作步骤原子定义 (MacroActionStep)
     */
    public record MacroActionStep(
            int stepOrder,
            String toolName,
            Map<String, String> argumentTemplates,
            String expectedSchema
    ) implements Serializable {
        public MacroActionStep {
            Objects.requireNonNull(toolName, "toolName 不能为 null");
            argumentTemplates = argumentTemplates != null ? Map.copyOf(argumentTemplates) : Collections.emptyMap();
            expectedSchema = expectedSchema != null ? expectedSchema : "{}";
        }
    }

    /**
     * 紧凑构造函数，实施严格防御性拷贝与超球面几何校验
     */
    public EnterpriseSkillRecord {
        Objects.requireNonNull(skillId, "skillId 不能为 null");
        Objects.requireNonNull(skillName, "skillName 不能为 null");
        Objects.requireNonNull(description, "description 不能为 null");
        parameterSlots = parameterSlots != null ? Map.copyOf(parameterSlots) : Collections.emptyMap();
        actionSequence = actionSequence != null ? List.copyOf(actionSequence) : Collections.emptyList();

        if (skillVector != null) {
            if (skillVector.length != DIMENSION) {
                throw new IllegalArgumentException("千问向量维度必须严格为 1536 维，实际传入: " + skillVector.length);
            }
            double sumSq = 0.0;
            for (float v : skillVector) {
                sumSq += (double) v * v;
            }
            double norm = Math.sqrt(sumSq);
            if (Math.abs(norm - 1.0) > 1e-3) {
                throw new IllegalArgumentException("千问超球面向量必须严格满足 L2 范数单位归一化，实际范数: " + norm);
            }
            skillVector = skillVector.clone();
        }
    }

    @Override
    public float[] skillVector() {
        return skillVector != null ? skillVector.clone() : null;
    }

    /**
     * 工厂辅助方法：创建全新未调用的初始技能记录
     */
    public static EnterpriseSkillRecord ofInitial(
            String skillId,
            String skillName,
            String description,
            String signatureDsl,
            Map<String, String> parameterSlots,
            List<MacroActionStep> actionSequence,
            String preconditionRule,
            String postconditionRule,
            float[] skillVector
    ) {
        long now = System.currentTimeMillis();
        return new EnterpriseSkillRecord(
                skillId, skillName, description, signatureDsl,
                parameterSlots, actionSequence, preconditionRule, postconditionRule,
                0, 0, 1.0, now, skillVector
        );
    }

    /**
     * 基于生物复制子动力学（Replicator Dynamics）与指数半衰期更新技能适应度与健康度
     *
     * @param isSuccess   本次回放是否完全成功
     * @param speedup     本次回放相较于大模型基线的加速比（如 10.5x）
     * @param nowEpochMs  当前时钟毫秒数
     * @return 演化后的全新不可变技能记录实例
     */
    public EnterpriseSkillRecord withHealthUpdate(boolean isSuccess, double speedup, long nowEpochMs) {
        int newUsage = this.usageCount + 1;
        int newSuccess = this.successCount + (isSuccess ? 1 : 0);
        double successRate = (double) newSuccess / (double) newUsage;

        // 7 天时间半衰期常数 (7 * 24 * 3600 * 1000 ms)
        double halfLifeMs = 7.0 * 24.0 * 3600.0 * 1000.0;
        double lambda = Math.log(2.0) / halfLifeMs;
        long deltaT = Math.max(0L, nowEpochMs - this.lastUsedTimestamp);
        double timeDecay = Math.exp(-lambda * (double) deltaT);

        // 复制子动力学适应度方程：0.5 * 成功率 + 0.3 * 加速比贡献 + 0.2 固有先验
        double speedupReward = Math.min(Math.max(speedup / 10.0, 0.0), 1.0);
        double rawFitness = 0.5 * successRate + 0.3 * speedupReward + 0.2;
        double newHealth = rawFitness * timeDecay;

        return new EnterpriseSkillRecord(
                this.skillId, this.skillName, this.description, this.signatureDsl,
                this.parameterSlots, this.actionSequence, this.preconditionRule, this.postconditionRule,
                newUsage, newSuccess, newHealth, nowEpochMs, this.skillVector
        );
    }
}
