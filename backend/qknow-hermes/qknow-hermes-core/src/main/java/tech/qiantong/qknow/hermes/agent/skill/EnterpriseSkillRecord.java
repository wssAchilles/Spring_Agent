package tech.qiantong.qknow.hermes.agent.skill;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 企业级受控自进化技能记录不可变凭单 (EnterpriseSkillRecord · Phase 9.4 升级可验证程序技能)
 * <p>
 * 遵循 Java 21 Record 规约、Sutton Option 宏动作形式化定义与阿里千问 1024 维超球面几何基线：
 * 1. 支持可执行 {@link SkillKind#CODEACT_PYTHON_TEMPLATE} 参数化 Python 程序技能与单元测试断言桩；
 * 2. 携带基于 ADK {@code SkillToolset} 范式的长度前缀 SHA-256 内容指纹，支持 {@link MessageDigest#isEqual} 常量时间验签；
 * 3. 引入三态生命周期（{@link SkillLifecycleState}）与延迟渐进披露模式（{@link SkillDisclosureMode}）。
 * </p>
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
        float[] skillVector,
        SkillKind skillKind,
        SkillLifecycleState lifecycleState,
        SkillDisclosureMode disclosureMode,
        String codeActPythonTemplate,
        String contentSha256,
        List<String> unitTestAssertions
) implements Serializable {

    private static final int DIMENSION = 1024;

    public enum SkillKind {
        MACRO_ACTION_CHAIN,
        CODEACT_PYTHON_TEMPLATE
    }

    public enum SkillLifecycleState {
        PROBATION,
        VERIFIED,
        QUARANTINED
    }

    public enum SkillDisclosureMode {
        LAZY_EPHEMERAL,
        BOUNDED_LRU,
        PERSISTENT
    }

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
     * Phase 6/8 向后兼容 13 参数构造器。
     */
    public EnterpriseSkillRecord(
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
    ) {
        this(
                skillId, skillName, description, signatureDsl,
                parameterSlots, actionSequence, preconditionRule, postconditionRule,
                usageCount, successCount, healthScore, lastUsedTimestamp, skillVector,
                SkillKind.MACRO_ACTION_CHAIN,
                SkillLifecycleState.VERIFIED,
                SkillDisclosureMode.LAZY_EPHEMERAL,
                "",
                computeLengthPrefixedSha256(skillId, skillName, description, signatureDsl, "", List.of()),
                List.of()
        );
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
        skillKind = Objects.requireNonNullElse(skillKind, SkillKind.MACRO_ACTION_CHAIN);
        lifecycleState = Objects.requireNonNullElse(lifecycleState, SkillLifecycleState.VERIFIED);
        disclosureMode = Objects.requireNonNullElse(disclosureMode, SkillDisclosureMode.LAZY_EPHEMERAL);
        codeActPythonTemplate = codeActPythonTemplate != null ? codeActPythonTemplate : "";
        unitTestAssertions = unitTestAssertions != null ? List.copyOf(unitTestAssertions) : List.of();
        if (contentSha256 == null || contentSha256.isBlank()) {
            contentSha256 = computeLengthPrefixedSha256(
                    skillId, skillName, description, signatureDsl, codeActPythonTemplate, unitTestAssertions
            );
        }

        if (skillVector != null) {
            if (skillVector.length != DIMENSION) {
                throw new IllegalArgumentException("千问向量维度必须严格为 1024 维，实际传入: " + skillVector.length);
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
     * 工厂辅助方法：创建全新未调用的初始宏动作技能记录
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
     * Phase 9.4 新增工厂：创建携带密码学长度前缀 SHA-256 指纹与测试桩的可执行 CodeAct Python 程序技能。
     */
    public static EnterpriseSkillRecord ofCodeActTemplate(
            String skillId,
            String skillName,
            String description,
            Map<String, String> parameterSlots,
            String pythonTemplate,
            List<String> unitTestAssertions,
            SkillLifecycleState initialState,
            float[] skillVector
    ) {
        long now = System.currentTimeMillis();
        String safeCode = pythonTemplate != null ? pythonTemplate : "";
        List<String> safeTests = unitTestAssertions != null ? List.copyOf(unitTestAssertions) : List.of();
        String sha = computeLengthPrefixedSha256(skillId, skillName, description, "CODEACT_PYTHON_V4", safeCode, safeTests);
        return new EnterpriseSkillRecord(
                skillId, skillName, description, "CODEACT_PYTHON_V4",
                parameterSlots, List.of(), "AST_SELF_CONTAINED_PASS", "CODEACT_SPEC_ALIGNED",
                0, 0, 0.85, now, skillVector,
                SkillKind.CODEACT_PYTHON_TEMPLATE,
                initialState != null ? initialState : SkillLifecycleState.PROBATION,
                SkillDisclosureMode.LAZY_EPHEMERAL,
                safeCode,
                sha,
                safeTests
        );
    }

    /**
     * 返回更新生命周期状态后的不可变新副本。
     */
    public EnterpriseSkillRecord withLifecycleState(SkillLifecycleState newState) {
        return new EnterpriseSkillRecord(
                skillId, skillName, description, signatureDsl,
                parameterSlots, actionSequence, preconditionRule, postconditionRule,
                usageCount, successCount, healthScore, lastUsedTimestamp, skillVector,
                skillKind, newState, disclosureMode, codeActPythonTemplate, contentSha256, unitTestAssertions
        );
    }

    /**
     * 合并追加单元测试断言并重算长度前缀 SHA-256。
     */
    public EnterpriseSkillRecord withMergedUnitTests(List<String> extraTests) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(unitTestAssertions);
        if (extraTests != null) {
            merged.addAll(extraTests);
        }
        List<String> list = List.copyOf(merged);
        String sha = computeLengthPrefixedSha256(skillId, skillName, description, signatureDsl, codeActPythonTemplate, list);
        return new EnterpriseSkillRecord(
                skillId, skillName, description, signatureDsl,
                parameterSlots, actionSequence, preconditionRule, postconditionRule,
                usageCount, successCount, healthScore, lastUsedTimestamp, skillVector,
                skillKind, lifecycleState, disclosureMode, codeActPythonTemplate, sha, list
        );
    }

    /**
     * 基于生物复制子动力学（Replicator Dynamics）与指数半衰期更新技能适应度与健康度
     */
    public EnterpriseSkillRecord withHealthUpdate(boolean isSuccess, double speedup, long nowEpochMs) {
        int newUsage = this.usageCount + 1;
        int newSuccess = this.successCount + (isSuccess ? 1 : 0);
        double successRate = (double) newSuccess / (double) newUsage;

        double halfLifeMs = 7.0 * 24.0 * 3600.0 * 1000.0;
        double lambda = Math.log(2.0) / halfLifeMs;
        long deltaT = Math.max(0L, nowEpochMs - this.lastUsedTimestamp);
        double timeDecay = Math.exp(-lambda * (double) deltaT);

        double speedupReward = Math.min(Math.max(speedup / 10.0, 0.0), 1.0);
        double rawFitness = 0.5 * successRate + 0.3 * speedupReward + 0.2;
        double newHealth = rawFitness * timeDecay;

        return new EnterpriseSkillRecord(
                this.skillId, this.skillName, this.description, this.signatureDsl,
                this.parameterSlots, this.actionSequence, this.preconditionRule, this.postconditionRule,
                newUsage, newSuccess, newHealth, nowEpochMs, this.skillVector,
                this.skillKind, this.lifecycleState, this.disclosureMode,
                this.codeActPythonTemplate, this.contentSha256, this.unitTestAssertions
        );
    }

    /**
     * 采用 8 字节大端长度前缀防边界拼接碰撞计算 SHA-256 内容指纹（借鉴 ADK SkillToolset）。
     */
    public static String computeLengthPrefixedSha256(
            String skillId,
            String skillName,
            String description,
            String signatureDsl,
            String pythonTemplate,
            List<String> unitTests
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            feedSegment(md, skillId);
            feedSegment(md, skillName);
            feedSegment(md, description);
            feedSegment(md, signatureDsl);
            feedSegment(md, pythonTemplate);
            if (unitTests != null) {
                for (String t : unitTests) {
                    feedSegment(md, t);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("计算技能长度前缀 SHA-256 失败", e);
        }
    }

    /**
     * 常量时间验签，防止时序侧信道风险并拦截内容篡改。
     */
    public boolean verifyContentSha256ConstantTime() {
        if (contentSha256 == null || contentSha256.isBlank()) {
            return false;
        }
        String recomputed = computeLengthPrefixedSha256(
                skillId, skillName, description, signatureDsl, codeActPythonTemplate, unitTestAssertions
        );
        return MessageDigest.isEqual(
                recomputed.getBytes(StandardCharsets.UTF_8),
                contentSha256.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void feedSegment(MessageDigest md, String segment) {
        byte[] bytes = (segment != null ? segment : "").getBytes(StandardCharsets.UTF_8);
        md.update(ByteBuffer.allocate(8).putLong(bytes.length).array());
        md.update(bytes);
    }
}
