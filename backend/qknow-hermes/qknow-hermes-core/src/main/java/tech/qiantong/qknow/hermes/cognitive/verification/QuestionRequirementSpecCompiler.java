package tech.qiantong.qknow.hermes.cognitive.verification;

import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec.EntityGranularity;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec.MetricType;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 零外部 LLM 开销的问题六维形式化需求规格编译器（FRSV L1 编译层）。
 * <p>
 * 通过确定性中英文规则从原始问句中解析实体粒度、度量算子、分母提示词、量纲单位、输出格式与时态窗口要求，
 * 并生成对应于 TICK 原子二值核查范式的确定性核查清单。
 * </p>
 */
public class QuestionRequirementSpecCompiler {

    private static final Pattern DENOM_PATTERN_EN = Pattern.compile(
            "(?i)(?:percentage|percent|proportion|share|ratio)\\s+(?:of|relative to|out of|among|over|compared to)\\s+(?:the\\s+)?([a-z0-9_\\-\\s]{2,48}?)(?:\\s+(?:in|on|during|for|when|where|that|which|from|and|or)|[?,.;]|$)"
    );
    private static final Pattern DENOM_OUT_OF_PATTERN_EN = Pattern.compile(
            "(?i)(?:out of|relative to|divided by|as a share of)\\s+(?:the\\s+)?([a-z0-9_\\-\\s]{2,48}?)(?:\\s+(?:in|on|during|for|when|where|that|which)|[?,.;]|$)"
    );
    private static final Pattern DENOM_PATTERN_ZH = Pattern.compile(
            "(?:占|相对于|除以)([\\u4e00-\\u9fa5a-zA-Z0-9_\\-]{2,20})(?:的(?:百分比|比例|占比|比重|比率)|[？?，,。.;；]|$)"
    );

    private static final List<String> DEFAULT_SUB_ENTITY_MARKERS = List.of(
            "radio 1", "radio 2", "radio 3", "radio 4", "radio 5",
            "channel 1", "channel 2", "channel 4", "subsidiary", "division",
            "branch", "sub-channel", "station", "podcast", "imprint", "unit"
    );

    private final TemporalAnchorQueryParser temporalAnchorParser;

    public QuestionRequirementSpecCompiler() {
        this(new TemporalAnchorQueryParser());
    }

    public QuestionRequirementSpecCompiler(TemporalAnchorQueryParser temporalAnchorParser) {
        this.temporalAnchorParser = temporalAnchorParser != null ? temporalAnchorParser : new TemporalAnchorQueryParser();
    }

    /**
     * 将自然语言问题确定性编译为六维不可变 {@link QuestionRequirementSpec}。
     */
    public QuestionRequirementSpec compile(String question) {
        String safeQ = question == null ? "" : question.trim();
        String lower = safeQ.toLowerCase(Locale.ROOT);
        List<String> checklist = new ArrayList<>();

        // 1. 解析目标度量算子与单位
        MetricType metricType = MetricType.ENTITY_FACT;
        String requiredUnit = "";
        String requiredDenominator = "";
        String outputFormatPattern = "";
        boolean requiresCodeAct = false;

        boolean hasPercentChange = containsAny(lower,
                "percentage change", "percent change", "percentage increase", "percentage decrease",
                "growth rate", "by what percentage", "增长率", "变化率", "百分比变化");
        boolean hasPercentOrRatio = containsAny(lower,
                "percentage", "percent", "%", "proportion", "share of", "ratio of", "百分比", "占比", "比重", "比例");
        boolean hasDiff = containsAny(lower,
                "how much more", "how much less", "how much higher", "how much lower",
                "difference between", "increase between", "decrease between", "change from",
                "净增", "净减", "差值", "相差", "多了多少", "少了多少", "增加了多少", "减少了多少");
        boolean hasSignedDiff = containsAny(lower, "net change", "change from", "净增", "净减", "变化量");
        boolean hasCount = containsAny(lower,
                "how many", "total number of", "number of ", "count of", "count how many",
                "多少次", "几个", "有多少", "共计多少", "次数");
        boolean hasSeries = containsAny(lower,
                "y_references", "predict the values for the missing",
                "data series", "time series", "value series",
                "draw a pie chart", "draw a line chart", "draw a bar chart",
                "draw a chart", "plot a ", "chart showing");
        boolean asksExplicitPercentage = containsAny(lower,
                "what is the percentage", "what percentage", "by what percentage",
                "how many percent", "and what is the percentage", "what is the percent",
                "percentage change of", "percent change of", "百分比是多少", "占比多少");
        boolean asksForEntityOnly = !asksExplicitPercentage && containsAny(lower,
                "which ", "what city", "what airport", "what team", "what country",
                "what state", "who ", "name of");
        boolean isBoolean = isBooleanQuestion(lower);

        if (hasSeries) {
            metricType = MetricType.SERIES_LIST;
            outputFormatPattern = "y_references";
            requiresCodeAct = true;
            checklist.add("CHECK_FORMAT_Y_REFERENCES");
            checklist.add("CHECK_CODEACT_NUMERIC_ALIGNMENT");
        } else if (hasPercentChange) {
            metricType = MetricType.PERCENTAGE_CHANGE;
            requiredUnit = asksForEntityOnly ? "" : "%";
            requiredDenominator = asksForEntityOnly ? "" : extractDenominatorHint(safeQ);
            requiresCodeAct = true;
            if (!asksForEntityOnly) {
                checklist.add("CHECK_UNIT_PERCENTAGE");
                checklist.add("CHECK_DENOMINATOR_SCOPE");
            }
            checklist.add("CHECK_CODEACT_NUMERIC_ALIGNMENT");
        } else if (hasPercentOrRatio) {
            metricType = MetricType.RATIO_SHARE;
            requiredUnit = (asksForEntityOnly || (containsAny(lower, "ratio", "比率")
                    && !containsAny(lower, "%", "percent", "百分比", "占比")))
                    ? ""
                    : "%";
            requiredDenominator = asksForEntityOnly ? "" : extractDenominatorHint(safeQ);
            requiresCodeAct = true;
            if ("%".equals(requiredUnit)) {
                checklist.add("CHECK_UNIT_PERCENTAGE");
            }
            if (!asksForEntityOnly) {
                checklist.add("CHECK_DENOMINATOR_SCOPE");
            }
            checklist.add("CHECK_CODEACT_NUMERIC_ALIGNMENT");
        } else if (hasSignedDiff || hasDiff) {
            metricType = hasSignedDiff ? MetricType.SIGNED_DIFF : MetricType.ABSOLUTE_DIFF;
            requiresCodeAct = true;
            checklist.add("CHECK_DELTA_COMPUTATION_NOT_RAW_ENDPOINT");
            checklist.add("CHECK_CODEACT_NUMERIC_ALIGNMENT");
        } else if (hasCount) {
            metricType = MetricType.COUNT;
            requiresCodeAct = true;
            checklist.add("CHECK_EXACT_COUNT_INTEGER");
            checklist.add("CHECK_CODEACT_NUMERIC_ALIGNMENT");
        } else if (isBoolean) {
            metricType = MetricType.BOOLEAN_YES_NO;
            outputFormatPattern = "yes|no";
            checklist.add("CHECK_BOOLEAN_YES_NO_FORMAT");
        } else if (containsAny(lower, "average", "mean", "sum", "total", "median", "correlation", "std", "平均", "总计", "相关系数")) {
            metricType = MetricType.RAW_VALUE;
            requiresCodeAct = true;
            checklist.add("CHECK_CODEACT_NUMERIC_ALIGNMENT");
        }

        // 2. 解析实体粒度约束
        EntityGranularity granularity = EntityGranularity.UNSPECIFIED;
        List<String> expectedGranularityKeywords = new ArrayList<>();
        List<String> forbiddenSubHints = new ArrayList<>();

        if (containsAny(lower,
                "network", "parent company", "broadcaster", "organization", "company",
                "corporation", "media group", "institution", "所属网络", "母公司", "集团", "总台", "机构")) {
            granularity = EntityGranularity.OVERARCHING_ORG_OR_NETWORK;
            expectedGranularityKeywords.add("parent_or_network");
            forbiddenSubHints.addAll(DEFAULT_SUB_ENTITY_MARKERS);
            checklist.add("CHECK_ENTITY_GRANULARITY_OVERARCHING");
        } else if (containsAny(lower, "decade", "century", "era", "年代", "世纪")) {
            granularity = EntityGranularity.DECADE_OR_PERIOD;
            checklist.add("CHECK_ENTITY_GRANULARITY_DECADE");
        } else if (containsAny(lower, "what year", "which year", "what date", "when did", "哪一年", "哪一天", "何时")) {
            granularity = EntityGranularity.EXACT_DATE_OR_YEAR;
            checklist.add("CHECK_ENTITY_GRANULARITY_DATE");
        }

        // 3. 解析时态窗口约束
        TemporalAnchorSpec anchorSpec = temporalAnchorParser.parse(safeQ, System.currentTimeMillis());
        boolean requiresTemporal = anchorSpec.mode() != TemporalAnchorSpec.QueryTemporalMode.LATEST
                || !anchorSpec.parsedTimeExpressions().isEmpty();
        if (requiresTemporal) {
            checklist.add("CHECK_TEMPORAL_WINDOW_COVERAGE");
        }

        return new QuestionRequirementSpec(
                safeQ,
                granularity,
                metricType,
                requiredDenominator,
                requiredUnit,
                outputFormatPattern,
                requiresTemporal,
                requiresCodeAct,
                checklist,
                expectedGranularityKeywords,
                forbiddenSubHints
        );
    }

    private static String extractDenominatorHint(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        Matcher m1 = DENOM_OUT_OF_PATTERN_EN.matcher(question);
        if (m1.find()) {
            return cleanHint(m1.group(1));
        }
        Matcher m2 = DENOM_PATTERN_EN.matcher(question);
        if (m2.find()) {
            return cleanHint(m2.group(1));
        }
        Matcher m3 = DENOM_PATTERN_ZH.matcher(question);
        if (m3.find()) {
            return cleanHint(m3.group(1));
        }
        return "";
    }

    private static String cleanHint(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64).trim();
        }
        return trimmed;
    }

    private static boolean isBooleanQuestion(String lower) {
        if (lower.isEmpty()) {
            return false;
        }
        boolean startsWithBoolAux = lower.startsWith("is ") || lower.startsWith("are ")
                || lower.startsWith("was ") || lower.startsWith("were ")
                || lower.startsWith("did ") || lower.startsWith("does ")
                || lower.startsWith("do ") || lower.startsWith("has ")
                || lower.startsWith("have ") || lower.startsWith("can ")
                || lower.startsWith("是否");
        if (!startsWithBoolAux) {
            return false;
        }
        // 排除礼貌祈使句与开放描述指令（如 "Can you describe the structure..."）
        if (lower.startsWith("can you ") && containsAny(lower,
                "can you describe", "can you explain", "can you list", "can you summarize",
                "can you provide", "can you tell", "can you help", "can you draw", "can you show")) {
            return false;
        }
        // 排除 "is X or Y" 选择问句
        return !lower.contains(" or ") || containsAny(lower, "yes or no", "true or false", "是否");
    }

    private static boolean containsAny(String lower, String... keywords) {
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
