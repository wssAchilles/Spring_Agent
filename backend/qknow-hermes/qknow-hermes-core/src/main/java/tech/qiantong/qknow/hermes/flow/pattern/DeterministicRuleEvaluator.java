package tech.qiantong.qknow.hermes.flow.pattern;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 确定性规则与数值校验评估器 (DeterministicRuleEvaluator)
 * <p>
 * 对标 Anthropic Evaluator-Optimizer 中的确定性硬性评估环节：
 * 1. 在复杂长程任务中，杜绝大模型纯文本“自我盲目反思”的数值与格式幻觉；
 * 2. 提供数值平衡、范围断言、正则表达式与 JSON 模式的确定性硬性检验；
 * 3. 校验失败时生成精准具象的反思批评 (Actionable Critique)，指导优化器确定性自愈；
 * 4. 与 EvaluatorOptimizerEngine 深度兼容，保障端到端任务极低失败度。
 * </p>
 */
@Slf4j
@Component
public class DeterministicRuleEvaluator {

    /**
     * 单条确定性断言规则
     */
    public record RuleAssertion(
            String ruleId,
            String ruleDescription,
            int penaltyWeight,
            Predicate<String> validator
    ) {
        public static RuleAssertion of(String id, String desc, int weight, Predicate<String> validator) {
            return new RuleAssertion(id, desc, weight, validator);
        }
    }

    /**
     * 规则套件评估结果 Record
     */
    public record RuleEvaluationReport(
            boolean passed,
            int finalScore,
            List<String> violatedRuleIds,
            List<String> critiqueSuggestions,
            long executionTimeMs
    ) {
        public EvaluatorOptimizerEngine.EvaluationResult toEvaluationResult() {
            if (passed) {
                return EvaluatorOptimizerEngine.EvaluationResult.pass(finalScore, "全量确定性规则断言校验通过");
            } else {
                String critique = "确定性规则断言拦截，未通过规则: " + String.join(", ", violatedRuleIds);
                return EvaluatorOptimizerEngine.EvaluationResult.fail(finalScore, critique, critiqueSuggestions);
            }
        }
    }

    /**
     * 执行规则套件检验
     *
     * @param targetText 待检验的解法或模型输出文本
     * @param rules      断言规则集合
     * @return 规则评估报告
     */
    public RuleEvaluationReport evaluate(String targetText, List<RuleAssertion> rules) {
        long startMs = System.currentTimeMillis();
        if (targetText == null) {
            targetText = "";
        }

        if (rules == null || rules.isEmpty()) {
            return new RuleEvaluationReport(true, 100, Collections.emptyList(), Collections.emptyList(), 0L);
        }

        int score = 100;
        List<String> violatedIds = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        for (RuleAssertion rule : rules) {
            boolean ok = false;
            try {
                ok = rule.validator().test(targetText);
            } catch (Exception e) {
                log.warn("[DeterministicRuleEvaluator] 规则 [{}] 执行异常: {}", rule.ruleId(), e.getMessage());
            }

            if (!ok) {
                score -= rule.penaltyWeight();
                violatedIds.add(rule.ruleId());
                suggestions.add(String.format("【规则未通过】%s (ID: %s)", rule.ruleDescription(), rule.ruleId()));
            }
        }

        int finalScore = Math.max(0, score);
        boolean passed = violatedIds.isEmpty();
        long costMs = System.currentTimeMillis() - startMs;

        log.debug("[DeterministicRuleEvaluator] 规则评估完成: passed={}, score={}, 违规数={}",
                passed, finalScore, violatedIds.size());

        return new RuleEvaluationReport(
                passed,
                finalScore,
                Collections.unmodifiableList(violatedIds),
                Collections.unmodifiableList(suggestions),
                costMs
        );
    }

    // ==================== 常用开箱即用确定性断言构建器 ====================

    /**
     * 校验输出是否为合法 JSON 且包含指定 Key
     */
    public static RuleAssertion jsonRequiredKeys(String ruleId, List<String> requiredKeys, int penaltyWeight) {
        return RuleAssertion.of(ruleId, "输出必须是合法 JSON 格式且包含全部必需键: " + requiredKeys, penaltyWeight, text -> {
            try {
                JSONObject obj = JSON.parseObject(text);
                if (obj == null) return false;
                for (String key : requiredKeys) {
                    if (!obj.containsKey(key) || obj.get(key) == null) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * 正则匹配断言
     */
    public static RuleAssertion regexMatches(String ruleId, String regexPattern, String desc, int penaltyWeight) {
        Pattern pattern = Pattern.compile(regexPattern, Pattern.DOTALL);
        return RuleAssertion.of(ruleId, desc, penaltyWeight, text -> pattern.matcher(text).find());
    }

    /**
     * 数值非负与范围断言 (从 JSON 中提取数值属性)
     */
    public static RuleAssertion jsonNumericRange(String ruleId, String key, double min, double max, int penaltyWeight) {
        return RuleAssertion.of(ruleId, String.format("字段 [%s] 必须为数值且范围位于 [%.2f, %.2f]", key, min, max), penaltyWeight, text -> {
            try {
                JSONObject obj = JSON.parseObject(text);
                if (obj == null || !obj.containsKey(key)) return false;
                Double val = obj.getDouble(key);
                return val != null && val >= min && val <= max;
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * 文本最小字符长度断言
     */
    public static RuleAssertion minLength(String ruleId, int minLength, int penaltyWeight) {
        return RuleAssertion.of(ruleId, "文本输出长度不得少于 " + minLength + " 字符", penaltyWeight, text -> text != null && text.trim().length() >= minLength);
    }
}
