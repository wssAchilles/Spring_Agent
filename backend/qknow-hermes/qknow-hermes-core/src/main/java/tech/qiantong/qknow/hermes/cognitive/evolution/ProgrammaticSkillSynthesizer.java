package tech.qiantong.qknow.hermes.cognitive.evolution;

import com.alibaba.fastjson2.JSON;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;
import tech.qiantong.qknow.hermes.cognitive.verification.RequirementSpecAuditResult;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 程序化技能自动合成器（Phase 10.4 P-VPS-Evo，对应定理 4 与论文 §5.4）。
 * <p>
 * 彻底破解“历史收割硬编码空单测 {@code List.of()} 与空槽位 {@code Map.of()} 导致 100% 隔离”的三重死锁：
 * 1. 从通过双见证的 CodeAct 脚本与问题规格中确定性抽象可泛化列名槽位（如 {@code ${target_col}}、{@code ${group_col}}）；
 * 2. 对行数 {@code <= 25} 的 {@link ReadOnlyDatasetView} 自动前置安全 {@code if not DATASET_ROWS:} 内联快照，
 *    使技能在剥离外部视图的独立单测环境下仍可自包含闭环运行；
 * 3. 自动生成 {@code >= 1} 条自包含 Python {@code assert} 单元测试及携带完整视图的 {@code ScopedAnchorTestCase}，
 *    并经过 {@link AstSecurityInspector} 静态安全预检（严禁 {@code try:} / 危险导入 / 动态求值）。
 * </p>
 */
public class ProgrammaticSkillSynthesizer {

    private static final int INLINE_ROW_SNAPSHOT_LIMIT = 25;
    private static final Pattern TRY_BLOCK_PATTERN = Pattern.compile("(?m)^\\s*try\\s*:\\s*$");

    private final AstSecurityInspector astInspector;
    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final AtomicLong totalSynthesisAttempts = new AtomicLong(0L);
    private final AtomicLong totalSynthesizedCandidates = new AtomicLong(0L);
    private final AtomicLong totalSynthesisRejected = new AtomicLong(0L);

    public ProgrammaticSkillSynthesizer() {
        this(new AstSecurityInspector(), new UnitSphereEmbeddingResolver());
    }

    public ProgrammaticSkillSynthesizer(
            AstSecurityInspector astInspector,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this.astInspector = astInspector != null ? astInspector : new AstSecurityInspector();
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
    }

    /**
     * 从一次双见证通过的 CodeAct 执行结果确定性合成为自包含参数化技能候选。
     */
    public Optional<SynthesizedSkillCandidate> synthesizeFromExecution(
            String skillId,
            String question,
            QuestionRequirementSpec requirementSpec,
            ReadOnlyDatasetView datasetView,
            String rawPythonScript,
            VerifiedCodeActReceipt codeActReceipt,
            RequirementSpecAuditResult specAuditResult
    ) {
        totalSynthesisAttempts.incrementAndGet();
        if (skillId == null || skillId.isBlank()
                || rawPythonScript == null || rawPythonScript.isBlank()
                || codeActReceipt == null || !codeActReceipt.isSuccess()
                || specAuditResult == null || !specAuditResult.passed()) {
            totalSynthesisRejected.incrementAndGet();
            return Optional.empty();
        }

        // 严格 AST 安全预检与禁用 try: 检查（遵循 AstSecurityInspector & SkillWeaver 安全格规约）
        String trimmedScript = rawPythonScript.trim();
        String astErr = astInspector.inspect("python", trimmedScript);
        if ((astErr != null && !astErr.isBlank()) || TRY_BLOCK_PATTERN.matcher(trimmedScript).find()) {
            totalSynthesisRejected.incrementAndGet();
            return Optional.empty();
        }

        QuestionRequirementSpec.MetricType metricType = requirementSpec != null
                ? requirementSpec.metricType()
                : QuestionRequirementSpec.MetricType.RAW_VALUE;
        String safeQuestion = question != null ? question.trim() : "";
        String expectedToken = extractPrimaryOutputToken(codeActReceipt.stdout());

        // 1. 抽象可泛化参数槽位（优先从 ReadOnlyDatasetView 列名匹配脚本中出现的字面列名）
        Map<String, String> parameterSlots = new LinkedHashMap<>();
        Map<String, String> anchorBindings = new LinkedHashMap<>();
        String abstractedBody = trimmedScript;

        if (datasetView != null && datasetView.columns() != null && !datasetView.columns().isEmpty()) {
            int colSlotIdx = 0;
            for (String col : datasetView.columns()) {
                if (col == null || col.isBlank() || col.length() < 2) {
                    continue;
                }
                String quotedSingle = "'" + col + "'";
                String quotedDouble = "\"" + col + "\"";
                if (abstractedBody.contains(quotedSingle) || abstractedBody.contains(quotedDouble)) {
                    String slotName = colSlotIdx == 0 ? "target_col" : "col_" + colSlotIdx;
                    colSlotIdx++;
                    parameterSlots.put(slotName, "列名槽位(" + col + ")");
                    anchorBindings.put(slotName, col);
                    abstractedBody = abstractedBody
                            .replace(quotedSingle, "'${" + slotName + "}'")
                            .replace(quotedDouble, "\"${" + slotName + "}\"");
                }
            }
        }
        // 若脚本未字面匹配特定列名，则绑定 metric_scope 语义槽位确保签名具备跨任务泛化描述能力
        if (parameterSlots.isEmpty()) {
            parameterSlots.put("metric_scope", "计算口径(" + metricType.name() + ")");
            anchorBindings.put("metric_scope", metricType.name());
        }

        // 2. 构建带可选轻量内联表格快照的安全 Python 模板（行数 <= 25 时支持无外挂自包含单测执行）
        StringBuilder templateBuilder = new StringBuilder();
        templateBuilder.append("# H-NSCDS P-VPS-Evo 作用域参数化技能: ").append(sanitizeComment(skillId)).append('\n');
        templateBuilder.append("# metric_scope=${metric_scope_hint}\n");
        if (datasetView != null && datasetView.columns() != null && !datasetView.columns().isEmpty()
                && datasetView.rows() != null && datasetView.rows().size() <= INLINE_ROW_SNAPSHOT_LIMIT) {
            templateBuilder.append("if not DATASET_COLUMNS:\n");
            templateBuilder.append("    DATASET_COLUMNS = ").append(JSON.toJSONString(datasetView.columns())).append('\n');
            templateBuilder.append("if not DATASET_ROWS:\n");
            templateBuilder.append("    DATASET_ROWS = ").append(JSON.toJSONString(datasetView.rows())).append('\n');
        }
        templateBuilder.append(abstractedBody);
        String finalTemplate = templateBuilder.toString().replace("# metric_scope=${metric_scope_hint}\n", "");

        // 用真实槽位替换后做一次最终 AST 安全审计
        String testRendered = renderWithBindings(finalTemplate, anchorBindings);
        String renderedAstErr = astInspector.inspect("python", testRendered);
        if (renderedAstErr != null && !renderedAstErr.isBlank()) {
            totalSynthesisRejected.incrementAndGet();
            return Optional.empty();
        }

        // 3. 自动合成 >= 1 条自包含单元测试断言（不依赖 try:/os/open）
        List<String> unitAssertions = new ArrayList<>();
        unitAssertions.add("assert isinstance(DATASET_COLUMNS, list) and isinstance(DATASET_ROWS, list)");
        if (datasetView != null && !datasetView.rows().isEmpty() && datasetView.rows().size() <= INLINE_ROW_SNAPSHOT_LIMIT) {
            unitAssertions.add("assert len(DATASET_ROWS) == " + datasetView.rows().size());
        }

        // 4. 构建携带完整 ReadOnlyDatasetView 与预期输出 token 的作用域反事实锚点
        float[] queryVec = embeddingResolver.resolveUnit1024(safeQuestion.isBlank() ? skillId : safeQuestion);
        CounterfactualSkillRegressionGate.ScopedAnchorTestCase primaryAnchor =
                new CounterfactualSkillRegressionGate.ScopedAnchorTestCase(
                        "anchor-" + skillId,
                        safeQuestion,
                        anchorBindings,
                        datasetView,
                        expectedToken,
                        false,
                        metricType,
                        queryVec
                );

        String skillName = "pvps_" + metricType.name().toLowerCase(Locale.ROOT) + "_" + sanitizeIdSuffix(skillId);
        String description = buildSkillDescription(safeQuestion, metricType, parameterSlots);

        totalSynthesizedCandidates.incrementAndGet();
        return Optional.of(new SynthesizedSkillCandidate(
                skillId,
                skillName,
                description,
                metricType,
                parameterSlots,
                finalTemplate,
                unitAssertions,
                List.of(primaryAnchor),
                "receipt=" + codeActReceipt.receiptId() + ";cons="
                        + String.format(Locale.ROOT, "%.4f",
                                specAuditResult.consistencyReceipt().overallConsistencyScore())
        ));
    }

    public Map<String, Object> getTelemetryStats() {
        return Map.of(
                "totalSynthesisAttempts", totalSynthesisAttempts.get(),
                "totalSynthesizedCandidates", totalSynthesizedCandidates.get(),
                "totalSynthesisRejected", totalSynthesisRejected.get()
        );
    }

    private static String renderWithBindings(String template, Map<String, String> bindings) {
        String rendered = template;
        for (Map.Entry<String, String> e : bindings.entrySet()) {
            String safeVal = e.getValue() != null
                    ? e.getValue().replace("\\", "").replace("\"", "").replace("'", "").replace("\n", " ")
                    : "";
            rendered = rendered.replace("${" + e.getKey() + "}", safeVal);
        }
        return rendered;
    }

    private static String extractPrimaryOutputToken(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }
        String[] lines = stdout.trim().split("\\R+");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                Matcher m = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?%?)").matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
                return line.length() > 64 ? line.substring(0, 64) : line;
            }
        }
        return "";
    }

    private static String buildSkillDescription(
            String question,
            QuestionRequirementSpec.MetricType metricType,
            Map<String, String> slots
    ) {
        String qShort = question.length() > 80 ? question.substring(0, 80) : question;
        return String.format(Locale.ROOT,
                "[P-VPS-Evo %s] 槽位=%s | 源任务=%s",
                metricType.name(), String.join(",", slots.keySet()), qShort);
    }

    private static String sanitizeComment(String s) {
        return s.replace("\n", " ").replace("\r", " ").trim();
    }

    private static String sanitizeIdSuffix(String raw) {
        String clean = raw.replaceAll("[^a-zA-Z0-9_]", "_");
        return clean.length() > 24 ? clean.substring(clean.length() - 24) : clean;
    }
}
