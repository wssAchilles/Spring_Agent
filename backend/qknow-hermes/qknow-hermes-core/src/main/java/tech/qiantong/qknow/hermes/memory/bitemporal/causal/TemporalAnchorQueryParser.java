package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性双语时态锚点解析器（零外部 LLM 调用开销，Phase 9.3 补齐中英文相对时间窗）。
 * <p>
 * 自动识别中英文绝对日期、年份及相对时间窗口（{@code past N days / last 2 weeks / N days ago / yesterday / 最近7天 / 过去3个月 / 昨天}），
 * 将自然语言查询确定性映射为 {@link TemporalAnchorSpec}。
 * </p>
 */
public class TemporalAnchorQueryParser {

    /** 匹配 YYYY-MM-DD / YYYY/MM/DD / YYYY年M月D日 */
    private static final Pattern YMD_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?(?!\\d)"
    );
    /** 匹配 YYYY-MM / YYYY/MM / YYYY年M月 */
    private static final Pattern YM_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{4})[-/年](\\d{1,2})月?(?![-/\\d])"
    );
    /** 匹配独立年份如 2024年 / in 2024 / before 2023 */
    private static final Pattern YEAR_PATTERN = Pattern.compile(
            "(?<!\\d)(19\\d{2}|20\\d{2})年?(?!\\d)"
    );
    /** 匹配英文相对时间窗: past/last N days|weeks|months|years 或 past/last week|month|year */
    private static final Pattern EN_REL_WINDOW = Pattern.compile(
            "(?i)\\b(?:past|last|over the past|over the last|within the past|within the last)\\s+(?:(\\d+)\\s+)?(day|days|week|weeks|month|months|year|years)\\b"
    );
    /** 匹配英文相对单点: N days|weeks|months|years ago 或 yesterday */
    private static final Pattern EN_REL_AGO = Pattern.compile(
            "(?i)\\b(?:(\\d+)\\s+(day|days|week|weeks|month|months|year|years)\\s+ago|yesterday)\\b"
    );
    /** 匹配中文相对时间窗: 最近/过去/近 + 数量 + 天/日/周/月/年 */
    private static final Pattern ZH_REL_WINDOW = Pattern.compile(
            "(?:最近|过去|近)(\\d+|[一两二三四五六七八九十]+)?(?:个)?(天|日|周|星期|月|年)"
    );
    /** 匹配中文相对单点: N 天前/周前/个月前/年前 或 昨天/上周/上个月/去年 */
    private static final Pattern ZH_REL_AGO = Pattern.compile(
            "(?:(\\d+|[一两二三四五六七八九十]+)(?:个)?(天|日|周|星期|月|年)前|昨天|昨日|上周|上个月|去年)"
    );
    private static final Map<String, Integer> ZH_NUMS = Map.ofEntries(
            Map.entry("一", 1), Map.entry("二", 2), Map.entry("两", 2),
            Map.entry("三", 3), Map.entry("四", 4), Map.entry("五", 5),
            Map.entry("六", 6), Map.entry("七", 7), Map.entry("八", 8),
            Map.entry("九", 9), Map.entry("十", 10)
    );

    private record ParsedTimeHit(int startPos, long epochMs, long endEpochMs, String rawText) {
    }

    /**
     * 解析自然语言查询文本为结构化双时态查询锚点。
     */
    public TemporalAnchorSpec parse(String queryText, long referenceNowMs) {
        if (queryText == null || queryText.isBlank()) {
            return TemporalAnchorSpec.latest(referenceNowMs);
        }
        String text = queryText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        List<ParsedTimeHit> hits = extractAllTimeHits(text);
        List<String> exprs = hits.stream().map(ParsedTimeHit::rawText).toList();

        boolean isRetroactive = containsAny(lower,
                "当时系统认为", "当时以为", "事务回溯", "retroactive", "tx_as_of", "known as of");
        boolean isEvolution = containsAny(lower,
                "演变", "演进", "变化", "变迁", "历程", "对比", "前后", "之间", "直到", "从",
                "evolution", "history", "timeline", "change", "between", "from ", "how did");
        boolean isPoint = containsAny(lower,
                "截至", "之前", "当时", "彼时", "当年", "那年", "时任", "曾任",
                "as of", "before", "until", "prior to", "at that time", "back in", "in ");

        if (isRetroactive && !hits.isEmpty()) {
            long targetMs = hits.get(0).endEpochMs();
            return TemporalAnchorSpec.retroactiveAudit(targetMs, targetMs, exprs);
        }

        if (hits.size() >= 2 && (isEvolution || containsAny(lower, "到", "至", " to ", " and "))) {
            long minStart = Long.MAX_VALUE;
            long maxEnd = Long.MIN_VALUE;
            for (ParsedTimeHit h : hits) {
                minStart = Math.min(minStart, h.epochMs());
                maxEnd = Math.max(maxEnd, h.endEpochMs());
            }
            return TemporalAnchorSpec.evolutionWindow(minStart, maxEnd, referenceNowMs, exprs);
        }

        if (isEvolution) {
            long fromMs = hits.isEmpty() ? 0L : hits.get(0).epochMs();
            long toMs = hits.isEmpty() ? referenceNowMs : Math.max(referenceNowMs, hits.get(0).endEpochMs());
            return TemporalAnchorSpec.evolutionWindow(fromMs, toMs, referenceNowMs, exprs);
        }

        if (!hits.isEmpty() && (isPoint || hits.size() == 1)) {
            long pointMs = hits.get(0).endEpochMs();
            return TemporalAnchorSpec.pointAsOf(pointMs, referenceNowMs, exprs);
        }

        // Phase 9.3: 相对时间窗口与相对单点锚定（基于 referenceNowMs 确定性换算 UTC 起止毫秒）
        TemporalAnchorSpec relativeSpec = parseRelativeTemporalSpec(text, referenceNowMs);
        if (relativeSpec != null) {
            return relativeSpec;
        }

        return TemporalAnchorSpec.latest(referenceNowMs);
    }

    /**
     * 判定锚点是否为显式时间锚定（含相对或绝对时间窗）。
     */
    public static boolean isExplicitlyAnchored(TemporalAnchorSpec spec) {
        return spec != null && (spec.mode() != TemporalAnchorSpec.QueryTemporalMode.LATEST
                || !spec.parsedTimeExpressions().isEmpty());
    }

    private static TemporalAnchorSpec parseRelativeTemporalSpec(String text, long referenceNowMs) {
        LocalDate refDate = Instant.ofEpochMilli(referenceNowMs).atZone(ZoneOffset.UTC).toLocalDate();

        Matcher enWin = EN_REL_WINDOW.matcher(text);
        if (enWin.find()) {
            int count = enWin.group(1) != null ? Math.max(1, Integer.parseInt(enWin.group(1))) : 1;
            LocalDate startDate = subtractUnit(refDate, count, enWin.group(2));
            long startMs = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long endMs = Math.max(startMs, referenceNowMs);
            return TemporalAnchorSpec.evolutionWindow(startMs, endMs, referenceNowMs, List.of(enWin.group().trim()));
        }

        Matcher zhWin = ZH_REL_WINDOW.matcher(text);
        if (zhWin.find()) {
            int count = parseZhOrAsciiInt(zhWin.group(1), 1);
            LocalDate startDate = subtractUnit(refDate, count, zhWin.group(2));
            long startMs = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long endMs = Math.max(startMs, referenceNowMs);
            return TemporalAnchorSpec.evolutionWindow(startMs, endMs, referenceNowMs, List.of(zhWin.group().trim()));
        }

        Matcher enAgo = EN_REL_AGO.matcher(text);
        if (enAgo.find()) {
            String raw = enAgo.group().trim();
            LocalDate targetDate;
            if (raw.toLowerCase(Locale.ROOT).contains("yesterday")) {
                targetDate = refDate.minusDays(1);
            } else {
                int count = Math.max(1, Integer.parseInt(enAgo.group(1)));
                targetDate = subtractUnit(refDate, count, enAgo.group(2));
            }
            long endMs = targetDate.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC).toEpochMilli();
            return TemporalAnchorSpec.pointAsOf(endMs, referenceNowMs, List.of(raw));
        }

        Matcher zhAgo = ZH_REL_AGO.matcher(text);
        if (zhAgo.find()) {
            String raw = zhAgo.group().trim();
            LocalDate targetDate;
            if (raw.contains("昨天") || raw.contains("昨日")) {
                targetDate = refDate.minusDays(1);
            } else if (raw.contains("上周")) {
                targetDate = refDate.minusWeeks(1);
            } else if (raw.contains("上个月")) {
                targetDate = refDate.minusMonths(1);
            } else if (raw.contains("去年")) {
                targetDate = refDate.minusYears(1);
            } else {
                int count = parseZhOrAsciiInt(zhAgo.group(1), 1);
                targetDate = subtractUnit(refDate, count, zhAgo.group(2));
            }
            long endMs = targetDate.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC).toEpochMilli();
            return TemporalAnchorSpec.pointAsOf(endMs, referenceNowMs, List.of(raw));
        }
        return null;
    }

    private static LocalDate subtractUnit(LocalDate refDate, int count, String rawUnit) {
        String u = rawUnit == null ? "day" : rawUnit.toLowerCase(Locale.ROOT);
        if (u.startsWith("day") || "天".equals(u) || "日".equals(u)) {
            return refDate.minusDays(count);
        }
        if (u.startsWith("week") || "周".equals(u) || "星期".equals(u)) {
            return refDate.minusWeeks(count);
        }
        if (u.startsWith("month") || "月".equals(u)) {
            return refDate.minusMonths(count);
        }
        if (u.startsWith("year") || "年".equals(u)) {
            return refDate.minusYears(count);
        }
        return refDate.minusDays(count);
    }

    private static int parseZhOrAsciiInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String t = raw.trim();
        try {
            return Math.max(1, Integer.parseInt(t));
        } catch (NumberFormatException ignored) {
            return ZH_NUMS.getOrDefault(t, fallback);
        }
    }

    private static List<ParsedTimeHit> extractAllTimeHits(String text) {
        List<ParsedTimeHit> hits = new ArrayList<>();
        boolean[] occupied = new boolean[text.length()];

        Matcher ymd = YMD_PATTERN.matcher(text);
        while (ymd.find()) {
            int y = Integer.parseInt(ymd.group(1));
            int m = clamp(Integer.parseInt(ymd.group(2)), 1, 12);
            int d = clamp(Integer.parseInt(ymd.group(3)), 1, 28);
            LocalDate date = safelyBuildDate(y, m, Integer.parseInt(ymd.group(3)));
            if (date == null) {
                date = LocalDate.of(y, m, d);
            }
            long startMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long endMs = date.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC).toEpochMilli();
            hits.add(new ParsedTimeHit(ymd.start(), startMs, endMs, ymd.group()));
            markOccupied(occupied, ymd.start(), ymd.end());
        }

        Matcher ym = YM_PATTERN.matcher(text);
        while (ym.find()) {
            if (isOccupied(occupied, ym.start(), ym.end())) {
                continue;
            }
            int y = Integer.parseInt(ym.group(1));
            int m = clamp(Integer.parseInt(ym.group(2)), 1, 12);
            LocalDate first = LocalDate.of(y, m, 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            long startMs = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long endMs = last.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC).toEpochMilli();
            hits.add(new ParsedTimeHit(ym.start(), startMs, endMs, ym.group()));
            markOccupied(occupied, ym.start(), ym.end());
        }

        Matcher yr = YEAR_PATTERN.matcher(text);
        while (yr.find()) {
            if (isOccupied(occupied, yr.start(), yr.end())) {
                continue;
            }
            int y = Integer.parseInt(yr.group(1));
            long startMs = LocalDate.of(y, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long endMs = LocalDate.of(y, 12, 31).atTime(23, 59, 59, 999_000_000)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
            hits.add(new ParsedTimeHit(yr.start(), startMs, endMs, yr.group()));
            markOccupied(occupied, yr.start(), yr.end());
        }

        hits.sort((a, b) -> Integer.compare(a.startPos(), b.startPos()));
        return hits;
    }

    private static LocalDate safelyBuildDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean containsAny(String lower, String... keywords) {
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static void markOccupied(boolean[] occupied, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(occupied.length, end); i++) {
            occupied[i] = true;
        }
    }

    private static boolean isOccupied(boolean[] occupied, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(occupied.length, end); i++) {
            if (occupied[i]) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * 辅助解析 ISO-8601 时间文本，失败时返回 null。
     */
    public static Long tryParseIsoInstantMs(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso.trim()).toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }
}
