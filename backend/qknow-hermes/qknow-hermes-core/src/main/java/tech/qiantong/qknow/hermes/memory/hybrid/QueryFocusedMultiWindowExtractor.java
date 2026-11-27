package tech.qiantong.qknow.hermes.memory.hybrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询聚焦多窗口证据抽取器（Phase 11.3 BPR-LM，根治 {@code clampLongPassage} 中段截断黑洞）。
 * <p>
 * 彻底消除 {@code [2000, len-2000)} 硬截断吞噬中段核心实体的失真问题（例如 15.5k 字符文档偏移 2385 的 {@code 16GB}
 * 与 50k 会话中偏移 11685 的 {@code Thrive Market $150}）：通过检索关键词（去停用词逆窗频权重）+
 * 数字/容量/货币实体密度锚点联合打分，选取互不重叠的 Top-K 高信息密度局部窗口并按原文偏移升序精确拼接。
 * </p>
 */
public final class QueryFocusedMultiWindowExtractor {

    private static final Pattern NUMERIC_ANCHOR_PATTERN = Pattern.compile(
            "(?:\\$\\d+(?:[.,]\\d+)?|\\b\\d+(?:\\.\\d+)?\\s*(?:gb|mb|tb|kb|%|kg|lb|hours?|days?|weeks?|months?|years?)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private QueryFocusedMultiWindowExtractor() {
    }

    /**
     * 使用默认多窗口配额（总长 <= 3800 字符，单窗 1100 字符，最多 3 窗）抽取证据。
     */
    public static String extractMultiWindowEvidence(String title, String fullContent, String focusQuery) {
        return extractMultiWindowEvidence(title, fullContent, focusQuery, 3800, 1100, 3);
    }

    /**
     * 对长文本按查询语义与数值实体锚点提取不重叠 Top-K 局部窗口，按原文偏移升序拼接并返回。
     */
    public static String extractMultiWindowEvidence(
            String title,
            String fullContent,
            String focusQuery,
            int maxTotalChars,
            int windowChars,
            int maxWindows
    ) {
        if (fullContent == null || fullContent.isEmpty()) {
            return "";
        }
        int safeMaxTotal = Math.max(600, maxTotalChars);
        if (fullContent.length() <= safeMaxTotal) {
            return fullContent;
        }
        int safeWindow = Math.max(300, Math.min(safeMaxTotal, windowChars));
        int safeMaxWindows = Math.max(1, maxWindows);
        int step = Math.max(200, safeWindow / 2);
        int len = fullContent.length();

        List<String> queryTokens = Bm25HypersphericalHybridIndex.tokenize(focusQuery);
        Set<String> uniqueQueryTerms = new LinkedHashSet<>(queryTokens);

        // 生成覆盖全文（包含末尾边界）的所有候选滑窗
        List<int[]> spans = new ArrayList<>();
        for (int pos = 0; pos < len; pos += step) {
            int end = Math.min(len, pos + safeWindow);
            spans.add(new int[]{pos, end});
            if (end >= len) {
                break;
            }
        }
        if (!spans.isEmpty() && spans.get(spans.size() - 1)[1] < len) {
            spans.add(new int[]{Math.max(0, len - safeWindow), len});
        }

        // 计算查询词在各滑窗间的逆窗频（Rare Query Term Weight），避免高频普通词锁死在 pos=0
        Map<String, Integer> windowDf = new HashMap<>();
        List<String> windowTexts = new ArrayList<>(spans.size());
        for (int[] sp : spans) {
            String wText = fullContent.substring(sp[0], sp[1]);
            windowTexts.add(wText);
            if (!uniqueQueryTerms.isEmpty()) {
                Set<String> wTokens = new LinkedHashSet<>(Bm25HypersphericalHybridIndex.tokenize(wText));
                for (String qt : uniqueQueryTerms) {
                    if (wTokens.contains(qt)) {
                        windowDf.merge(qt, 1, Integer::sum);
                    }
                }
            }
        }

        int numSpans = spans.size();
        String lowerQuery = focusQuery != null ? focusQuery.toLowerCase(Locale.ROOT).trim() : "";

        record ScoredWindow(int start, int end, String text, double score) {}
        List<ScoredWindow> scored = new ArrayList<>(numSpans);
        for (int i = 0; i < numSpans; i++) {
            int[] sp = spans.get(i);
            String wText = windowTexts.get(i);
            String lowerWin = wText.toLowerCase(Locale.ROOT);
            Set<String> wTokens = new LinkedHashSet<>(Bm25HypersphericalHybridIndex.tokenize(wText));

            double score = 0.0;
            int matchedDistinct = 0;
            for (String qt : uniqueQueryTerms) {
                if (wTokens.contains(qt) || lowerWin.contains(qt)) {
                    int df = windowDf.getOrDefault(qt, 1);
                    double idfWeight = Math.log(1.0 + (numSpans - df + 0.5) / (df + 0.5));
                    boolean isNumericTerm = qt.chars().anyMatch(Character::isDigit);
                    score += (1.0 + idfWeight) * (isNumericTerm ? 2.2 : 1.0);
                    matchedDistinct++;
                }
            }
            if (uniqueQueryTerms.size() >= 2 && matchedDistinct >= 2) {
                score += matchedDistinct * 0.65;
            }
            if (!lowerQuery.isEmpty() && lowerQuery.length() >= 4 && lowerWin.contains(lowerQuery)) {
                score += 4.0;
            }

            // 数值/容量/货币锚点加权：确保即使模型未明确拼出实体词（如只搜 "laptop RAM upgrade"），含 "16GB" / "$150" 的中段窗依然脱颖而出
            Matcher numMatcher = NUMERIC_ANCHOR_PATTERN.matcher(wText);
            int numericHits = 0;
            while (numMatcher.find() && numericHits < 8) {
                numericHits++;
            }
            score += numericHits * (matchedDistinct > 0 ? 1.35 : 0.60);

            // 首窗极微弱平分保护，绝不压过中段高价值信息锚点
            if (sp[0] == 0) {
                score += 0.05;
            }

            scored.add(new ScoredWindow(sp[0], sp[1], wText, score));
        }

        // 先选出全篇最高分锚点窗（穿透中段黑洞），若 multi-window >= 2 且首窗未重叠则强制保底纳入 pos=0 首窗（防止主体开场声明被漏）
        List<ScoredWindow> ranked = new ArrayList<>(scored);
        ranked.sort(Comparator.comparingDouble(ScoredWindow::score).reversed()
                .thenComparingInt(ScoredWindow::start));

        List<ScoredWindow> chosen = new ArrayList<>();
        if (!ranked.isEmpty()) {
            chosen.add(ranked.get(0));
        }
        if (safeMaxWindows >= 2 && !scored.isEmpty()) {
            ScoredWindow head = scored.get(0);
            if (!overlapsSignificantly(head.start(), head.end(), chosen)) {
                chosen.add(head);
            }
        }
        for (int i = 1; i < ranked.size() && chosen.size() < safeMaxWindows; i++) {
            ScoredWindow cand = ranked.get(i);
            if (!overlapsSignificantly(cand.start(), cand.end(), chosen)) {
                chosen.add(cand);
            }
        }

        // 按原文偏移升序排列输出，保持因果与时序可读性
        chosen.sort(Comparator.comparingInt(ScoredWindow::start));

        StringBuilder sb = new StringBuilder();
        String safeTitle = (title != null && !title.isBlank()) ? title : "Passage";
        sb.append("[Query-Focused Multi-Window View | title=").append(safeTitle)
                .append(" | totalChars=").append(len)
                .append(" | windows=").append(chosen.size()).append("]\n");
        for (ScoredWindow w : chosen) {
            String header = "\n[Offset: " + w.start() + ".." + w.end() + " / " + len + "]\n";
            int remain = safeMaxTotal - sb.length() - header.length();
            if (remain <= 60) {
                break;
            }
            sb.append(header);
            String body = w.text().length() > remain ? w.text().substring(0, remain) : w.text();
            sb.append(body);
            if (!body.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static boolean overlapsSignificantly(int start, int end, List<? extends Object> chosenWindows) {
        int candLen = Math.max(1, end - start);
        for (Object obj : chosenWindows) {
            // 通过反射/局部接口避免在 public 之外暴露私有记录
            try {
                int s = (int) obj.getClass().getMethod("start").invoke(obj);
                int e = (int) obj.getClass().getMethod("end").invoke(obj);
                int overlap = Math.max(0, Math.min(end, e) - Math.max(start, s));
                if ((double) overlap / candLen >= 0.25) {
                    return true;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }
}
