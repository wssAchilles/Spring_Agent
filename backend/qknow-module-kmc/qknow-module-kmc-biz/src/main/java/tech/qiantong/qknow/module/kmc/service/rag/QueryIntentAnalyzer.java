package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class QueryIntentAnalyzer {

    private static final Pattern DAY_PATTERN = Pattern.compile(
            "(?i)day\\s*0?(\\d{1,2})|第\\s*0?(\\d{1,2})\\s*[天日]");

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
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                return Integer.parseInt(number);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String extractDocName(String query) {
        Matcher matcher = DAY_PATTERN.matcher(query);
        if (matcher.find()) {
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                int day = Integer.parseInt(number);
                return String.format("Day%02d", day);
            } catch (NumberFormatException ignored) {
            }
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
