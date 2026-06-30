package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class QueryIntentAnalyzer {

    private static final Pattern DAY_PATTERN = Pattern.compile(
            "(?i)day\\s*0?(\\d{1,2})|第\\s*0?(\\d{1,2})\\s*[天日]|第([一二三四五六七八九十]+)\\s*[天日]");

    private static final Map<String, Integer> CHINESE_NUMBERS = new java.util.HashMap<>();
    static {
        String[] digits = {"零","一","二","三","四","五","六","七","八","九","十",
                "十一","十二","十三","十四","十五","十六","十七","十八","十九",
                "二十","二十一","二十二","二十三","二十四","二十五","二十六","二十七","二十八","二十九","三十"};
        for (int i = 0; i < digits.length; i++) CHINESE_NUMBERS.put(digits[i], i);
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "请", "请告诉我", "告诉我", "在", "时候", "的时候", "我", "主要",
            "哪些", "什么", "关于", "一下", "信息", "了解", "的", "了", "是",
            "有", "和", "与", "或", "及", "等", "中", "对", "把", "被");

    public QueryIntent analyze(String query) {
        if (StrUtil.isBlank(query)) {
            return QueryIntent.builder().build();
        }

        Integer dayNo = extractDayNo(query);
        String docName = extractDocName(query);
        List<String> keywords = extractKeywords(query);

        return QueryIntent.builder()
                .dayNo(dayNo)
                .docName(docName)
                .keywords(keywords)
                .build();
    }

    private Integer extractDayNo(String query) {
        Matcher matcher = DAY_PATTERN.matcher(query);
        if (matcher.find()) {
            // Group 1: dayXX pattern, Group 2: 第N天 pattern, Group 3: 第X天 pattern (Chinese)
            if (matcher.group(1) != null) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            } else if (matcher.group(2) != null) {
                try {
                    return Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException ignored) {
                }
            } else if (matcher.group(3) != null) {
                String chineseNum = matcher.group(3);
                Integer dayNo = CHINESE_NUMBERS.get(chineseNum);
                if (dayNo != null) {
                    return dayNo;
                }
            }
        }
        return null;
    }

    private String extractDocName(String query) {
        Integer dayNo = extractDayNo(query);
        if (dayNo != null) {
            return String.format("Day%02d", dayNo);
        }
        return null;
    }

    private List<String> extractKeywords(String query) {
        Set<String> terms = new LinkedHashSet<>();
        String normalized = query.replaceAll("[^\\p{IsHan}A-Za-z0-9]+", " ");
        for (String token : normalized.split("\\s+")) {
            if (StrUtil.isBlank(token) || token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
        }
        return List.copyOf(terms);
    }
}
