package tech.qiantong.qknow.hermes.agent.harness;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 不可变系统提示词契约。
 *
 * <p>该 Record 只承载静态内容：角色定义、核心原则、安全红线。
 * 任何 {{...}}、${...} 或 {identifier} 形式的动态占位符都会在构造时 fail-close，
 * 防止动态变量进入 DeepSeek 静态缓存前缀。</p>
 */
public record InvariantSystemPromptRecord(
        String roleDefinition,
        String corePrinciples,
        String safetyInstructions
) {
    private static final Pattern DYNAMIC_PLACEHOLDER = Pattern.compile(
            "\\{\\{[^{}]*}}|\\$\\{[^{}]*}|\\{[A-Za-z_][A-Za-z0-9_.-]*}");

    public InvariantSystemPromptRecord {
        roleDefinition = normalize(roleDefinition);
        corePrinciples = normalize(corePrinciples);
        safetyInstructions = normalize(safetyInstructions);
        assertNoDynamicPlaceholder(roleDefinition);
        assertNoDynamicPlaceholder(corePrinciples);
        assertNoDynamicPlaceholder(safetyInstructions);
    }

    public String canonical() {
        return String.join("\u001f", roleDefinition, corePrinciples, safetyInstructions);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void assertNoDynamicPlaceholder(String value) {
        Matcher matcher = DYNAMIC_PLACEHOLDER.matcher(value);
        if (matcher.find()) {
            throw new IllegalArgumentException(
                    "InvariantSystemPrompt 检测到动态占位符，拒绝进入静态缓存前缀: " + matcher.group());
        }
    }

    public static InvariantSystemPromptRecord empty() {
        return new InvariantSystemPromptRecord("", "", "");
    }

    public InvariantSystemPromptRecord withRoleDefinition(String role) {
        return new InvariantSystemPromptRecord(role, corePrinciples, safetyInstructions);
    }
}
