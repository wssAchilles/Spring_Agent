package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 冻结 Phase 8.1 与 Phase 8.2 跨层通信的只读关系快照视图（Java 21 不可变 Record）。
 * <p>
 * 将双时态因果演化检索产出的活跃事实链规范化投影为不可变二维只读表，
 * 供安全格 CodeAct 沙盒直接执行精确去重计数、日期差运算与分组聚合，彻底消除纯文本计数漂移。
 * </p>
 */
public record ReadOnlyDatasetView(
        String viewId,
        TemporalAnchorSpec anchorSpec,
        List<String> columns,
        List<List<String>> rows,
        int activeFactCount,
        int supersededFactCount,
        String contentSha256
) {
    public static final List<String> CANONICAL_COLUMNS = List.of(
            "step_rank", "edge_id", "subject", "predicate", "object",
            "valid_from_ms", "valid_to_ms", "tx_from_ms", "cardinality", "score"
    );

    public static final List<String> EPISODIC_COLUMNS = List.of(
            "fact_id", "entity_key", "attr_key", "value_raw", "numeric_value",
            "unit", "valid_from_ms", "valid_to_ms", "session_id", "cardinality", "state_status", "verbatim_span"
    );

    public ReadOnlyDatasetView {
        Objects.requireNonNull(viewId, "viewId 不能为空");
        Objects.requireNonNull(anchorSpec, "anchorSpec 不能为空");
        columns = List.copyOf(columns != null ? columns : List.of());
        List<List<String>> immutableRows = new ArrayList<>();
        if (rows != null) {
            for (List<String> r : rows) {
                immutableRows.add(List.copyOf(r != null ? r : List.of()));
            }
        }
        rows = List.copyOf(immutableRows);
        Objects.requireNonNull(contentSha256, "contentSha256 不能为空");
    }

    /**
     * 将有序因果演化链投影为不可变二维只读快照视图，并附带内容 SHA-256 完整性指纹。
     */
    public static ReadOnlyDatasetView fromCausalSteps(
            String viewId,
            TemporalAnchorSpec anchorSpec,
            List<CausalEvolutionStep> steps,
            int supersededFactCount
    ) {
        List<CausalEvolutionStep> safeSteps = steps != null ? steps : List.of();
        List<List<String>> projectedRows = new ArrayList<>(safeSteps.size());
        StringBuilder canonicalPayload = new StringBuilder();
        canonicalPayload.append(viewId).append('|').append(anchorSpec.mode().name()).append('\n');

        for (CausalEvolutionStep st : safeSteps) {
            List<String> row = List.of(
                    String.valueOf(st.stepRank()),
                    st.edgeId(),
                    st.sourceNodeId(),
                    st.relationPredicate(),
                    st.targetNodeId(),
                    String.valueOf(st.validFromEpochMs()),
                    st.validToEpochMs() != null ? String.valueOf(st.validToEpochMs()) : "ACTIVE",
                    String.valueOf(st.txFromEpochMs()),
                    st.cardinality().name(),
                    String.format(Locale.ROOT, "%.4f", st.compositeScore())
            );
            projectedRows.add(row);
            canonicalPayload.append(String.join("\t", row)).append('\n');
        }

        String sha256 = sha256Hex(canonicalPayload.toString());
        return new ReadOnlyDatasetView(
                viewId,
                anchorSpec,
                CANONICAL_COLUMNS,
                projectedRows,
                safeSteps.size(),
                Math.max(0, supersededFactCount),
                sha256
        );
    }

    /**
     * Phase 9.3 BTER-MV 新增：将结构化双时态情景事实台账直投为 CodeAct 可执行的强类型二维只读关系视图。
     */
    public static ReadOnlyDatasetView fromEpisodicFacts(
            String viewId,
            TemporalAnchorSpec anchorSpec,
            List<StructuredEpisodicFact> facts,
            int supersededFactCount
    ) {
        List<StructuredEpisodicFact> safeFacts = facts != null ? facts : List.of();
        List<List<String>> projectedRows = new ArrayList<>(safeFacts.size());
        StringBuilder canonicalPayload = new StringBuilder();
        canonicalPayload.append(viewId).append('|').append(anchorSpec.mode().name()).append("|EPISODIC\n");

        int activeCount = 0;
        for (StructuredEpisodicFact f : safeFacts) {
            if ("ACTIVE".equalsIgnoreCase(f.stateStatus())) {
                activeCount++;
            }
            List<String> row = List.of(
                    f.factId(),
                    f.entityKey(),
                    f.attrKey(),
                    f.valueRaw(),
                    f.numericValue() != null ? String.valueOf(f.numericValue()) : "",
                    f.unit(),
                    String.valueOf(f.validFromEpochMs()),
                    f.validToEpochMs() != null ? String.valueOf(f.validToEpochMs()) : "ACTIVE",
                    f.sessionId(),
                    f.cardinality().name(),
                    f.stateStatus(),
                    f.verbatimSpan()
            );
            projectedRows.add(row);
            canonicalPayload.append(String.join("\t", row)).append('\n');
        }

        String sha256 = sha256Hex(canonicalPayload.toString());
        return new ReadOnlyDatasetView(
                viewId,
                anchorSpec,
                EPISODIC_COLUMNS,
                projectedRows,
                activeCount,
                Math.max(0, supersededFactCount),
                sha256
        );
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算 ReadOnlyDatasetView SHA-256 失败", e);
        }
    }
}
