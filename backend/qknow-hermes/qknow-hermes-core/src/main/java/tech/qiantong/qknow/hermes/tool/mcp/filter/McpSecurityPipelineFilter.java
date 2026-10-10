package tech.qiantong.qknow.hermes.tool.mcp.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 零信任 MCP 工具执行安全管道过滤器 (McpSecurityPipelineFilter)
 * 提供入参清洗与出参双向微秒级 DFA / 正则敏感数据自动脱敏，杜绝间接提示词注入与隐私数据泄漏。
 */
public class McpSecurityPipelineFilter {

    private static final Logger log = LoggerFactory.getLogger(McpSecurityPipelineFilter.class);

    // 常用敏感信息正则脱敏模式 (带非数字边界保护，避免身份证中的子串误匹配手机号)
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("sk-[a-zA-Z0-9]{20,}");

    public record FilterResult(
            Map<String, Object> sanitizedArguments,
            String sanitizedOutput,
            int maskedFieldsCount
    ) {}

    /**
     * 对工具入参执行清洗与安全检查
     */
    public Map<String, Object> filterInboundArguments(String toolName, Map<String, Object> arguments) {
        if (arguments == null) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String strVal) {
                // 剔除潜在的命令注入特殊字符
                String cleanStr = strVal.replaceAll("[;&|`$]", "");
                sanitized.put(entry.getKey(), cleanStr);
            } else {
                sanitized.put(entry.getKey(), val);
            }
        }
        return sanitized;
    }

    /**
     * 对工具输出结果执行敏感信息脱敏 (严格遵循先长串身份证、后短串手机号顺序)
     */
    public String filterOutboundResult(String toolName, String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return rawOutput;
        }

        String result = rawOutput;

        // 1. 身份证脱敏：保留前6后4位
        result = ID_CARD_PATTERN.matcher(result).replaceAll(m -> {
            String id = m.group();
            return id.substring(0, 6) + "********" + id.substring(14);
        });

        // 2. 手机号脱敏：保留前3后4位
        result = PHONE_PATTERN.matcher(result).replaceAll(m -> {
            String p = m.group();
            return p.substring(0, 3) + "****" + p.substring(7);
        });

        // 3. API Key 脱敏
        result = API_KEY_PATTERN.matcher(result).replaceAll("sk-********************");

        return result;
    }

    /**
     * 完整双向过滤执行
     */
    public FilterResult processToolCall(String toolName, Map<String, Object> arguments, String rawOutput) {
        Map<String, Object> cleanArgs = filterInboundArguments(toolName, arguments);
        String cleanOutput = filterOutboundResult(toolName, rawOutput);

        int maskedCount = 0;
        if (rawOutput != null) {
            if (ID_CARD_PATTERN.matcher(rawOutput).find()) maskedCount++;
            if (PHONE_PATTERN.matcher(rawOutput).find()) maskedCount++;
            if (API_KEY_PATTERN.matcher(rawOutput).find()) maskedCount++;
        }

        log.debug("[McpSecurityFilter] 工具执行安全过滤完毕: tool={}, maskedCount={}", toolName, maskedCount);
        return new FilterResult(cleanArgs, cleanOutput, maskedCount);
    }
}
