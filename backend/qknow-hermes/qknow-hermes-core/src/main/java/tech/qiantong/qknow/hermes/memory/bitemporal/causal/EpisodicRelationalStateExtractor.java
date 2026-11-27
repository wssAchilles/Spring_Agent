package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性情景对话→双时态关系状态抽取器（Phase 9.3 BTER-MV，零额外 LLM 延迟）。
 * <p>
 * 从长对话文本中按确定性中英文规则抽取单值数值槽更新（{@link PredicateCardinality#FUNCTIONAL}）
 * 与可计数事件流（{@link PredicateCardinality#COUNTABLE_EVENT}），自动绑定真实 {@code verbatimSpan} 与
 * 千问 {@code S^1023} 单位向量。
 * </p>
 */
public class EpisodicRelationalStateExtractor {

    private static final int MAX_FACTS_PER_TURN = 25;

    /** 匹配数值状态更新: "my weight is 72 kg" / "budget to $350" / "followers increased to 350" */
    private static final Pattern FUNCTIONAL_NUMERIC_PATTERN = Pattern.compile(
            "(?i)(weight|salary|budget|balance|price|rent|cost|spending|spent|score|followers|subscribers|ram|memory|storage|mileage|steps|calories|hours|weeks|days|months|issues|books|bikes|devices|items|balls|baseballs|体重|薪资|预算|余额|花费|支出|价格)\\s*(?:is|was|are|were|now|changed to|updated to|increased to|upgraded to|to|at|of|for|为|是|降至|涨至|调整为)?\\s*(?:\\$|USD|RMB|￥)?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(kg|lbs|dollars?|usd|rmb|yuan|元|公里|km|miles|gb|tb|mb|weeks?|days?|months?|hours?|%)?"
    );

    /** 匹配通用消费/金额与商户实体（优先匹配显式 at/from/with 商户短语）: "spent $120 at Trader Joe's" / "paid $85 for groceries at Walmart" */
    private static final Pattern SPENDING_AT_ENTITY_PATTERN = Pattern.compile(
            "(?i)(?:i|we|user|he|she)?\\s*(?:spent|paid|ordered|charged|cost(?:\\s+me)?|bought)\\s*(?:(?:[^$.,;!?\\n]{1,36}?\\s+(?:for|at)\\s+)|(?:(?:about|around|approximately|nearly)\\s+))?(?:\\$|USD\\s*|RMB\\s*|￥)(\\d+(?:,\\d{3})*(?:\\.\\d+)?)(?:(?:(?:\\s+(?:for|on)\\s+[^.,;!?\\n]{1,36}?)?\\s+(?:at|from|with)\\s+((?-i:[A-Z0-9])[A-Za-z0-9'&.\\-]*(?:\\s+(?-i:[A-Z0-9])[A-Za-z0-9'&.\\-]*){0,4}))|(?:\\s+(?:for|on)\\s+[^.,;!?\\n]{1,36})?)"
    );

    /** Phase 10.3 CC-E2R 新增：当商户在金额左侧同句前置时回溯绑定（如 "went to Trader Joe's ... and spent $80" / "order with Thrive Market ... spent $150"） */
    private static final Pattern PRECEDING_MERCHANT_PATTERN = Pattern.compile(
            "(?i)(?:went\\s+to|ordered\\s+from|order\\s+with|shopped\\s+at|bought\\s+at|visit(?:ed)?|from|at|with)\\s+((?-i:[A-Z0-9])[A-Za-z0-9'&.\\-]*(?:\\s+(?-i:[A-Z0-9])[A-Za-z0-9'&.\\-]*){0,4})"
    );

    private static final Pattern CURRENCY_MENTION_PATTERN = Pattern.compile(
            "(?i)(?:\\$|USD\\s*|RMB\\s*|￥)\\s*\\d+(?:,\\d{3})*(?:\\.\\d+)?"
    );

    private static final Pattern RELATIVE_TEMPORAL_PHRASE_PATTERN = Pattern.compile(
            "(?i)\\b(?:last\\s+(?:month|week|year)|week\\s+before\\s+last|days?\\s+ago|weeks?\\s+ago|months?\\s+ago|yesterday|two\\s+weeks\\s+ago)\\b"
    );

    /** 匹配可计数事件行为: "bought groceries at Walmart" / "visited Dr. Smith" / "attended yoga class" */
    private static final Pattern COUNTABLE_EVENT_PATTERN = Pattern.compile(
            "(?i)(?:i|we|user|he|she|我|用户)\\s+(bought|visited|attended|completed|ordered|went to|ran|finished|read|flew|use|using|own|added|upgraded|joined|played|watched|took|购买了?|去了?|参加|就诊|打卡)\\s+([^.,;!?\\n]{2,48})"
    );

    private final UnitSphereEmbeddingResolver embeddingResolver;

    public EpisodicRelationalStateExtractor() {
        this(new UnitSphereEmbeddingResolver());
    }

    public EpisodicRelationalStateExtractor(UnitSphereEmbeddingResolver embeddingResolver) {
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
    }

    /**
     * 从单轮情景对话文本中抽取全部有原文跨度支撑的 {@link StructuredEpisodicFact}。
     */
    public List<StructuredEpisodicFact> extractFromTurn(
            String scopeId,
            String sessionId,
            String defaultEntityKey,
            String turnText,
            long turnEpochMs
    ) {
        if (turnText == null || turnText.isBlank()) {
            return List.of();
        }
        String safeScope = (scopeId == null || scopeId.isBlank()) ? "default-scope" : scopeId;
        String safeEntity = (defaultEntityKey == null || defaultEntityKey.isBlank()) ? "user" : defaultEntityKey;
        List<StructuredEpisodicFact> out = new ArrayList<>();
        Set<String> seenSpans = new LinkedHashSet<>();
        List<int[]> consumedRanges = new ArrayList<>();

        Matcher mSpend = SPENDING_AT_ENTITY_PATTERN.matcher(turnText);
        while (mSpend.find() && out.size() < MAX_FACTS_PER_TURN) {
            String span = mSpend.group().trim();
            if (!seenSpans.add(span.toLowerCase(Locale.ROOT))) {
                continue;
            }
            consumedRanges.add(new int[]{mSpend.start(), mSpend.end()});
            String numStr = mSpend.group(1).replace(",", "");
            Double numVal = tryParseDouble(numStr);
            String merchantRaw = mSpend.group(2);
            if (merchantRaw == null || merchantRaw.isBlank()) {
                int[] backMatch = findPrecedingMerchantWindow(turnText, mSpend.start());
                if (backMatch != null) {
                    merchantRaw = turnText.substring(backMatch[0], backMatch[1]);
                    int expandedStart = Math.max(0, backMatch[2]);
                    span = turnText.substring(expandedStart, mSpend.end()).trim();
                }
            }
            String targetEntity = (merchantRaw != null && !merchantRaw.isBlank())
                    ? merchantRaw.trim().replaceAll("\\s+$", "")
                    : safeEntity;
            String rawVal = "$" + numStr;
            float[] vec = embeddingResolver.resolveUnit1024(targetEntity + " spending " + rawVal);
            out.add(new StructuredEpisodicFact(
                    "es_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    safeScope, targetEntity, "SPEND_EVENT", "spending",
                    rawVal, numVal, "USD",
                    turnEpochMs, null, turnEpochMs, null,
                    sessionId, PredicateCardinality.COUNTABLE_EVENT, "ACTIVE",
                    span, turnText, vec
            ));
        }

        Matcher mFunc = FUNCTIONAL_NUMERIC_PATTERN.matcher(turnText);
        while (mFunc.find() && out.size() < MAX_FACTS_PER_TURN) {
            if (isCoveredByRanges(mFunc.start(), mFunc.end(), consumedRanges)) {
                continue;
            }
            String span = mFunc.group().trim();
            if (!seenSpans.add(span.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String attr = normalizeAttrKey(mFunc.group(1));
            String numStr = mFunc.group(2).replace(",", "");
            String unit = mFunc.group(3) != null ? mFunc.group(3).trim() : "";
            Double numVal = tryParseDouble(numStr);
            String rawVal = unit.isEmpty() ? numStr : (numStr + " " + unit);
            float[] vec = embeddingResolver.resolveUnit1024(safeEntity + " " + attr + " " + rawVal);
            out.add(new StructuredEpisodicFact(
                    "ef_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    safeScope, safeEntity, "USER_OR_ENTITY", attr,
                    rawVal, numVal, unit,
                    turnEpochMs, null, turnEpochMs, null,
                    sessionId, PredicateCardinality.FUNCTIONAL, "ACTIVE",
                    span, turnText, vec
            ));
        }

        Matcher mEvt = COUNTABLE_EVENT_PATTERN.matcher(turnText);
        while (mEvt.find() && out.size() < MAX_FACTS_PER_TURN) {
            String span = mEvt.group().trim();
            if (!seenSpans.add(span.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String verb = normalizeAttrKey(mEvt.group(1));
            String obj = mEvt.group(2).trim();
            float[] vec = embeddingResolver.resolveUnit1024(safeEntity + " " + verb + " " + obj);
            out.add(new StructuredEpisodicFact(
                    "ev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    safeScope, safeEntity, "EVENT_ACTOR", "event:" + verb,
                    obj, 1.0, "count",
                    turnEpochMs, null, turnEpochMs, null,
                    sessionId, PredicateCardinality.COUNTABLE_EVENT, "ACTIVE",
                    span, turnText, vec
            ));
        }
        return List.copyOf(out);
    }

    /**
     * 在同句左侧最多 140 字符窗口内回溯前置商户专名，返回 {@code [merchantStart, merchantEnd, phraseStart]}。
     */
    private static int[] findPrecedingMerchantWindow(String text, int amountMatchStart) {
        if (text == null || amountMatchStart <= 0) {
            return null;
        }
        int windowStart = Math.max(0, amountMatchStart - 140);
        for (int i = amountMatchStart - 1; i >= windowStart; i--) {
            char ch = text.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == ';') {
                windowStart = i + 1;
                break;
            }
        }
        if (windowStart >= amountMatchStart) {
            return null;
        }
        String slice = text.substring(windowStart, amountMatchStart);
        Matcher backMatcher = PRECEDING_MERCHANT_PATTERN.matcher(slice);
        int[] best = null;
        while (backMatcher.find()) {
            String candidate = backMatcher.group(1);
            if (candidate != null && !candidate.isBlank() && !"I".equals(candidate.trim())) {
                best = new int[]{
                        windowStart + backMatcher.start(1),
                        windowStart + backMatcher.end(1),
                        windowStart + backMatcher.start()
                };
            }
        }
        return best;
    }

    public static int countCurrencyMentions(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        Matcher m = CURRENCY_MENTION_PATTERN.matcher(text);
        while (m.find()) {
            count++;
        }
        return count;
    }

    public static boolean hasRelativeTemporalPhrase(String text) {
        return text != null && !text.isBlank() && RELATIVE_TEMPORAL_PHRASE_PATTERN.matcher(text).find();
    }

    private static boolean isCoveredByRanges(int start, int end, List<int[]> ranges) {
        for (int[] r : ranges) {
            if (start >= r[0] && end <= r[1]) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeAttrKey(String raw) {
        if (raw == null) {
            return "attr";
        }
        String low = raw.trim().toLowerCase(Locale.ROOT);
        return switch (low) {
            case "spent", "spending", "cost", "花费", "支出" -> "spending";
            case "预算" -> "budget";
            case "体重" -> "weight";
            case "购买了", "购买" -> "bought";
            case "去了", "去" -> "went to";
            default -> low;
        };
    }

    private static Double tryParseDouble(String raw) {
        try {
            return Double.parseDouble(Objects.requireNonNull(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
