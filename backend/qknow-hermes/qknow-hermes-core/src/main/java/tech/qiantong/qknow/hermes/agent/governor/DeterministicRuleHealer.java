package tech.qiantong.qknow.hermes.agent.governor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内环确定性规则自愈器 (DeterministicRuleHealer)
 * 在毫秒级对文本进行语法完整性、代码块闭合、格式正规化等确定性自愈，
 * 避免无谓的大模型调用浪费。
 */
public class DeterministicRuleHealer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```");
    private static final Pattern UNSPACED_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})([^#\\s\\n].*)$");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?i)<待补充>|\\[请填入\\]|TODO:|FIXME:");

    public record RuleHealingResult(
            String healedContent,
            int fixedCount,
            List<String> appliedRules
    ) {
        public RuleHealingResult {
            Objects.requireNonNull(healedContent, "healedContent 不能为空");
            appliedRules = appliedRules != null ? List.copyOf(appliedRules) : List.of();
        }
    }

    /**
     * 执行全量内环确定性修复流水线
     *
     * @param originalContent 原始文本
     * @return 修复后结果
     */
    public RuleHealingResult heal(String originalContent) {
        if (originalContent == null || originalContent.isBlank()) {
            return new RuleHealingResult("未生成有效内容（已触发默认空值兜底）。", 1, List.of("RULE_EMPTY_FALLBACK"));
        }

        String current = originalContent;
        List<String> appliedRules = new ArrayList<>();
        int fixes = 0;

        // 1. 自动闭合未匹配的代码块 (```)
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(current);
        int fenceCount = 0;
        while (codeBlockMatcher.find()) {
            fenceCount++;
        }
        if (fenceCount % 2 != 0) {
            current = current + "\n```";
            fixes++;
            appliedRules.add("RULE_CLOSE_UNTERMINATED_CODE_FENCE");
        }

        // 2. 规范化 Markdown 标题缺少空格的问题 (#标题 -> # 标题)
        Matcher headingMatcher = UNSPACED_HEADING_PATTERN.matcher(current);
        if (headingMatcher.find()) {
            current = headingMatcher.replaceAll("$1 $2");
            fixes++;
            appliedRules.add("RULE_NORMALIZE_MARKDOWN_HEADINGS");
        }

        // 3. 去除残留的占位符模式
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(current);
        if (placeholderMatcher.find()) {
            current = placeholderMatcher.replaceAll("【已自动消除空占位】");
            fixes++;
            appliedRules.add("RULE_REMOVE_RESIDUAL_PLACEHOLDERS");
        }

        // 4. 去除首尾多余空白行
        String trimmed = current.strip();
        if (!trimmed.equals(current)) {
            current = trimmed;
            fixes++;
            appliedRules.add("RULE_TRIM_BOUNDARY_WHITESPACE");
        }

        return new RuleHealingResult(current, fixes, appliedRules);
    }
}
