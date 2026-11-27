package tech.qiantong.qknow.hermes.cognitive.verification;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 面向问题语义的六维形式化需求规格契约（FRSV，Phase 9.1 定理 4 核心输入）。
 * <p>
 * 在 L1 阶段由 {@link QuestionRequirementSpecCompiler} 零 LLM 开销确定性编译生成，
 * 用于在 R3 验收阶段彻底阻断“仅凭答案与自身推理自洽便获得 0.88–0.94 高分漏放”的结构性规格错配。
 * </p>
 */
public record QuestionRequirementSpec(
        String questionText,
        EntityGranularity entityGranularity,
        MetricType metricType,
        String requiredDenominatorHint,
        String requiredUnit,
        String outputFormatPattern,
        boolean requiresTemporalWindow,
        boolean requiresCodeActWitness,
        List<String> atomicChecklist,
        List<String> expectedGranularityKeywords,
        List<String> forbiddenSubEntityHints
) implements Serializable {

    /**
     * 目标实体粒度约束枚举。
     */
    public enum EntityGranularity {
        UNSPECIFIED,
        OVERARCHING_ORG_OR_NETWORK,
        EXACT_SUB_ENTITY,
        DECADE_OR_PERIOD,
        EXACT_DATE_OR_YEAR
    }

    /**
     * 问题目标度量/算子口径枚举。
     */
    public enum MetricType {
        RAW_VALUE,
        ABSOLUTE_DIFF,
        SIGNED_DIFF,
        PERCENTAGE_CHANGE,
        RATIO_SHARE,
        COUNT,
        ENTITY_FACT,
        BOOLEAN_YES_NO,
        SERIES_LIST
    }

    public QuestionRequirementSpec {
        questionText = questionText != null ? questionText.trim() : "";
        entityGranularity = Objects.requireNonNullElse(entityGranularity, EntityGranularity.UNSPECIFIED);
        metricType = Objects.requireNonNullElse(metricType, MetricType.ENTITY_FACT);
        requiredDenominatorHint = requiredDenominatorHint != null ? requiredDenominatorHint.trim() : "";
        requiredUnit = requiredUnit != null ? requiredUnit.trim() : "";
        outputFormatPattern = outputFormatPattern != null ? outputFormatPattern.trim() : "";
        atomicChecklist = List.copyOf(atomicChecklist != null ? atomicChecklist : List.of());
        expectedGranularityKeywords = List.copyOf(expectedGranularityKeywords != null ? expectedGranularityKeywords : List.of());
        forbiddenSubEntityHints = List.copyOf(forbiddenSubEntityHints != null ? forbiddenSubEntityHints : List.of());
    }
}
