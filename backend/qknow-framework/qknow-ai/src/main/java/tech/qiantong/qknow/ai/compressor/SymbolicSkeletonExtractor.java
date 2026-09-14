package tech.qiantong.qknow.ai.compressor;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 强类型符号骨架与关键不可变锚点提取器 (定理 1.1: 符号 100% 保持约束)
 */
@Component
public class SymbolicSkeletonExtractor {

    public enum AnchorType {
        SLICE_ID,        // 知识切片哈希 ID (如 slice_policy_001)
        METRIC_NUMBER,   // 关键数值与百分比 (如 3.25%, 1500ms, 2026)
        SQL_OPERATOR,    // SQL 与逻辑谓词 (如 SELECT, WHERE, >=, !=)
        ENTITY_UUID,     // 实体 UUID
        KEY_PREDICATE    // 核心因果与判定谓词 (如 因果关系, 矛盾, 前提, 结论, 必须, 严禁)
    }

    public record SymbolicAnchor(
            AnchorType type,
            String value,
            int startIndex,
            int endIndex
    ) {}

    public record SymbolicSkeleton(
            List<SymbolicAnchor> anchors,
            String originalText
    ) {
        public boolean containsAnchorValue(String val) {
            if (val == null || anchors == null) return false;
            return anchors.stream().anyMatch(a -> a.value().equalsIgnoreCase(val.trim()));
        }
    }

    // 预编译核心正则
    private static final Pattern PATTERN_SLICE_ID = Pattern.compile("slice_[a-zA-Z0-9_]+");
    private static final Pattern PATTERN_METRIC = Pattern.compile("(?<![a-zA-Z0-9_])[0-9]+(?:\\.[0-9]+)?(?:%|ms|s|KB|MB|GB|元|次)?(?![a-zA-Z0-9_])");
    private static final Pattern PATTERN_SQL_OP = Pattern.compile("(?i)\\b(SELECT|WHERE|JOIN|GROUP BY|ORDER BY|LIMIT|INSERT|UPDATE|DELETE|COUNT|AVG)\\b|>=|<=|!=|=");
    private static final Pattern PATTERN_UUID = Pattern.compile("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");
    private static final Pattern PATTERN_PREDICATE = Pattern.compile("因果关系|因果链|关键矛盾|前提条件|核心结论|必须|禁止|严禁|不可逆|定理[0-9]+(?:\\.[0-9]+)?");

    /**
     * 抽取文本中的所有不可变符号锚点并保序
     */
    public SymbolicSkeleton extract(String text) {
        if (text == null || text.isBlank()) {
            return new SymbolicSkeleton(Collections.emptyList(), text);
        }

        List<SymbolicAnchor> anchorList = new ArrayList<>();

        matchPattern(PATTERN_SLICE_ID, AnchorType.SLICE_ID, text, anchorList);
        matchPattern(PATTERN_UUID, AnchorType.ENTITY_UUID, text, anchorList);
        matchPattern(PATTERN_METRIC, AnchorType.METRIC_NUMBER, text, anchorList);
        matchPattern(PATTERN_SQL_OP, AnchorType.SQL_OPERATOR, text, anchorList);
        matchPattern(PATTERN_PREDICATE, AnchorType.KEY_PREDICATE, text, anchorList);

        // 按文本起始位置排序
        anchorList.sort(Comparator.comparingInt(SymbolicAnchor::startIndex));

        // 消除重叠区间 (保留较长匹配)
        List<SymbolicAnchor> deduplicated = new ArrayList<>();
        int lastEnd = -1;
        for (SymbolicAnchor a : anchorList) {
            if (a.startIndex() >= lastEnd) {
                deduplicated.add(a);
                lastEnd = a.endIndex();
            }
        }

        return new SymbolicSkeleton(Collections.unmodifiableList(deduplicated), text);
    }

    private void matchPattern(Pattern pattern, AnchorType type, String text, List<SymbolicAnchor> targetList) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            targetList.add(new SymbolicAnchor(type, matcher.group(), matcher.start(), matcher.end()));
        }
    }
}
