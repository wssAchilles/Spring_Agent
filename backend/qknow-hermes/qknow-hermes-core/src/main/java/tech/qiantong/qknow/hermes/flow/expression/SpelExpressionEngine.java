package tech.qiantong.qknow.hermes.flow.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Spring Expression Language (SpEL) 的表达式引擎
 * <p>
 * 支持比较运算: ==, !=, >=, <=, >, <
 * 支持逻辑运算: &&, ||, !
 * 支持变量占位符: {{ variableName }}
 * <p>
 * 安全限制:
 * - 拒绝包含 T(Runtime), T(ProcessBuilder), T(System), T(Class) 等危险类型引用
 * - 使用 SimpleEvaluationContext 阻止类型引用
 * - 缺失变量返回 false，不抛异常
 */
@Slf4j
public class SpelExpressionEngine implements ExpressionEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*}}");

    private static final Pattern CONTAINS_PATTERN = Pattern.compile(
            "'([^']*)'\\s+contains\\s+'([^']*)'");

    private static final Pattern STARTS_WITH_PATTERN = Pattern.compile(
            "'([^']*)'\\s+startsWith\\s+'([^']*)'");

    /**
     * 危险表达式关键字列表
     */
    private static final String[] DANGEROUS_KEYWORDS = {
            "T(Runtime)", "T(ProcessBuilder)", "T(System)", "T(Class)",
            "T(java.lang.Runtime)", "T(java.lang.ProcessBuilder)",
            "T(java.lang.System)", "T(java.lang.Class)",
            "T(java.lang.reflect)", "new ProcessBuilder",
            "Runtime.getRuntime", "exec("
    };

    private final ExpressionParser parser;

    public SpelExpressionEngine() {
        this.parser = new SpelExpressionParser();
    }

    /**
     * 评估表达式
     *
     * @param expression 表达式字符串，支持 {{ variableName }} 占位符
     * @param context    上下文变量
     * @return 评估结果
     */
    @Override
    public boolean evaluate(String expression, Map<String, Object> context) {
        // 1. null/空检查
        if (expression == null || expression.isBlank()) {
            return false;
        }

        // 2. null context 检查
        if (context == null) {
            return false;
        }

        // 3. 安全检查：拒绝危险表达式
        if (containsDangerousKeyword(expression)) {
            log.warn("表达式包含危险关键字，拒绝执行: {}", expression);
            return false;
        }

        try {
            // 4. 将 {{ variableName }} 占位符替换为实际变量引用
            String resolved = resolveVariables(expression, context);

            // 4.5. 将 contains/startsWith 操作符转换为 matches 表达式
            resolved = preprocessStringOperators(resolved);

            // 5. 使用 SimpleEvaluationContext 构建安全的评估上下文
            EvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withRootObject(context)
                    .build();

            // 6. 解析并评估表达式
            Expression spelExpression = parser.parseExpression(resolved);
            Object result = spelExpression.getValue(evalContext, Boolean.class);

            if (result instanceof Boolean boolResult) {
                return boolResult;
            }

            return false;

        } catch (Exception e) {
            log.debug("表达式评估失败: {}", expression, e);
            return false;
        }
    }

    /**
     * 安全检查：是否包含危险关键字
     */
    private boolean containsDangerousKeyword(String expression) {
        String upper = expression.toUpperCase();
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upper.contains(keyword.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 {{ variableName }} 占位符替换为 SpEL 变量引用
     * 如果变量不存在于上下文中，替换为 'null'（不会抛异常）
     */
    private String resolveVariables(String expression, Map<String, Object> context) {
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            Object value = context.get(variableName);
            if (value == null) {
                // 缺失变量替换为 null
                matcher.appendReplacement(sb, "null");
            } else if (value instanceof String strValue) {
                // 字符串值用单引号包裹
                matcher.appendReplacement(sb, "'" + escapeSpelString(strValue) + "'");
            } else {
                matcher.appendReplacement(sb, value.toString());
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 转义字符串中的单引号
     */
    private String escapeSpelString(String value) {
        return value.replace("'", "\\'");
    }

    /**
     * 将 contains/startsWith 操作符转换为 matches 正则表达式
     * SpEL 不支持对字符串使用 contains/startsWith 操作符，需要转换为 matches
     */
    private String preprocessStringOperators(String expression) {
        Matcher containsMatcher = CONTAINS_PATTERN.matcher(expression);
        if (containsMatcher.find()) {
            String escaped = escapeRegexMeta(containsMatcher.group(2));
            expression = containsMatcher.replaceAll("'" + containsMatcher.group(1) + "' matches '.*" + escaped + ".*'");
        }
        Matcher startsWithMatcher = STARTS_WITH_PATTERN.matcher(expression);
        if (startsWithMatcher.find()) {
            String escaped = escapeRegexMeta(startsWithMatcher.group(2));
            expression = startsWithMatcher.replaceAll("'" + startsWithMatcher.group(1) + "' matches '" + escaped + ".*'");
        }
        return expression;
    }

    /**
     * 转义正则表达式中的特殊字符
     */
    private String escapeRegexMeta(String value) {
        return value.replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("|", "\\|");
    }
}
