package tech.qiantong.qknow.hermes.cognitive.evolution;

import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 睡眠期自合成参数化安全格技能候选凭单（Phase 10.4 P-VPS-Evo，对应定理 4）。
 * <p>
 * 封装由 {@link ProgrammaticSkillSynthesizer} 从已通过双见证的 CodeAct 脚本自动抽象出的：
 * 1. 参数槽位映射（将硬编码列名或过滤键值抽象为 {@code ${slot}} 占位符）；
 * 2. 安全格（无 {@code try:} / 危险系统调用）可复用 Python 模板；
 * 3. 自包含单元测试断言列表（解决历史 {@code unitTestAssertions = List.of()} 死锁）；
 * 4. 作用域反事实锚点测试集（保证仅在同 {@link QuestionRequirementSpec.MetricType} 且高余弦作用域内评估零回归）。
 * </p>
 */
public record SynthesizedSkillCandidate(
        String skillId,
        String name,
        String description,
        QuestionRequirementSpec.MetricType metricType,
        Map<String, String> parameterSlots,
        String codeActPythonTemplate,
        List<String> unitTestAssertions,
        List<CounterfactualSkillRegressionGate.ScopedAnchorTestCase> scopedAnchors,
        String provenanceTrace
) implements Serializable {

    public SynthesizedSkillCandidate {
        Objects.requireNonNull(skillId, "skillId 不能为空");
        Objects.requireNonNull(name, "name 不能为空");
        description = description != null ? description : "";
        metricType = Objects.requireNonNullElse(metricType, QuestionRequirementSpec.MetricType.RAW_VALUE);
        parameterSlots = parameterSlots != null ? Map.copyOf(parameterSlots) : Map.of();
        codeActPythonTemplate = codeActPythonTemplate != null ? codeActPythonTemplate : "";
        unitTestAssertions = unitTestAssertions != null ? List.copyOf(unitTestAssertions) : List.of();
        scopedAnchors = scopedAnchors != null ? List.copyOf(scopedAnchors) : List.of();
        provenanceTrace = provenanceTrace != null ? provenanceTrace : "";
    }
}
