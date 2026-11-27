package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * 强制原文落位的双时态结构化情景状态事实（Phase 9.3 BTER-MV，定义 3 & 定理 3）。
 * <p>
 * 借鉴 SodaMem 原文落位不变量（{@code SpansValid(span, turn) == 1}）：
 * 每一条结构化入库的数值/状态/事件事实必须携带来自真实对话回合的 {@code verbatimSpan}，
 * 杜绝 LLM 在结构抽取阶段凭空编造未出现数值或实体。
 * </p>
 */
public record StructuredEpisodicFact(
        String factId,
        String scopeId,
        String entityKey,
        String entityType,
        String attrKey,
        String valueRaw,
        Double numericValue,
        String unit,
        long validFromEpochMs,
        Long validToEpochMs,
        long txFromEpochMs,
        Long txToEpochMs,
        String sessionId,
        PredicateCardinality cardinality,
        String stateStatus,
        String verbatimSpan,
        String sourceTurnText,
        float[] unitEmbedding1024
) {

    public StructuredEpisodicFact {
        if (factId == null || factId.isBlank()) {
            throw new IllegalArgumentException("factId 不能为空");
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId 不能为空");
        }
        if (entityKey == null || entityKey.isBlank()) {
            throw new IllegalArgumentException("entityKey 不能为空");
        }
        if (attrKey == null || attrKey.isBlank()) {
            throw new IllegalArgumentException("attrKey 不能为空");
        }
        entityType = (entityType == null || entityType.isBlank()) ? "ENTITY" : entityType.trim();
        valueRaw = valueRaw != null ? valueRaw.trim() : "";
        unit = unit != null ? unit.trim() : "";
        sessionId = sessionId != null ? sessionId.trim() : "session-0";
        cardinality = Objects.requireNonNullElse(cardinality, PredicateCardinality.MULTI_VALUED);
        stateStatus = (stateStatus == null || stateStatus.isBlank()) ? "ACTIVE" : stateStatus.trim();
        verbatimSpan = verbatimSpan != null ? verbatimSpan.trim() : "";
        sourceTurnText = sourceTurnText != null ? sourceTurnText : "";
        if (!isVerbatimSpanGrounded(verbatimSpan, sourceTurnText)) {
            throw new IllegalArgumentException(
                    "BTER-MV 原文落位不变量校验失败: verbatimSpan [" + verbatimSpan + "] 未真实出现在 sourceTurnText 中");
        }
        if (unitEmbedding1024 != null) {
            unitEmbedding1024 = Arrays.copyOf(unitEmbedding1024, unitEmbedding1024.length);
        }
    }

    @Override
    public float[] unitEmbedding1024() {
        return unitEmbedding1024 != null ? Arrays.copyOf(unitEmbedding1024, unitEmbedding1024.length) : null;
    }

    /**
     * 返回双时态截断后的历史取代副本（严禁物理硬删除）。
     */
    public StructuredEpisodicFact withSuperseded(long newValidToMs, long newTxToMs) {
        return new StructuredEpisodicFact(
                factId, scopeId, entityKey, entityType, attrKey, valueRaw, numericValue, unit,
                validFromEpochMs, newValidToMs, txFromEpochMs, newTxToMs,
                sessionId, cardinality, "SUPERSEDED", verbatimSpan, sourceTurnText, unitEmbedding1024
        );
    }

    public static boolean isVerbatimSpanGrounded(String span, String sourceTurnText) {
        if (span == null || span.isBlank() || sourceTurnText == null || sourceTurnText.isBlank()) {
            return false;
        }
        String normSpan = span.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        String normTurn = sourceTurnText.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return normTurn.contains(normSpan);
    }
}
