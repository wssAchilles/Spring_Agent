package tech.qiantong.qknow.mcp.client.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 间接提示词注入 (Indirect Prompt Injection) 四道纵深防御与主动免疫流水线
 */
public class McpSecurityFilterPipeline {

    private static final Logger log = LoggerFactory.getLogger(McpSecurityFilterPipeline.class);

    // 匹配潜伏在外部返回数据中的典型越狱与伪造系统提示词指令
    private static final Pattern INDIRECT_INJECTION_PATTERN = Pattern.compile(
        "(?i)\\b(ignore\\s+(all\\s+)?previous\\s+instructions|system\\s+update:|disregard\\s+all\\s+prior|reveal\\s+(the\\s+)?api\\s+key|output\\s+your\\s+system\\s+prompt|new\\s+system\\s+directive)\\b"
    );

    // 手机号与身份证基础正则脱敏
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?!\\d)");

    // 免疫记忆账本（模拟 Phase 45 亲和度成熟沉淀，二次免疫耗时 <= 1ms 毫秒级拦截）
    private final Set<String> immuneAntigenLedger = ConcurrentHashMap.newKeySet();

    /**
     * 前置调用检查
     */
    public void preCheckCall(String serverId, String toolName, String inputJson) {
        if (inputJson != null && INDIRECT_INJECTION_PATTERN.matcher(inputJson).find()) {
            throw new SecurityException("入参中潜伏对抗性提示词覆盖指令");
        }
    }

    /**
     * 后置出参清洗与主动免疫防御
     */
    public String postSanitizeResult(String serverId, String toolName, String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return rawOutput;
        }

        String lowerOutput = rawOutput.toLowerCase(Locale.ROOT);

        // 1. 检查是否命中已沉淀的免疫抗原库 (二次免疫 100% 极速拦截)
        for (String antigenSignature : immuneAntigenLedger) {
            if (lowerOutput.contains(antigenSignature)) {
                log.warn("[MCP Immune] 触发既有抗原二次免疫拦截! tool={}:{}, antigen={}", serverId, toolName, antigenSignature);
                return "[安全警告：第三方 MCP 工具返回的数据被 Phase 45 免疫记忆账本直接拦截 (抗原命中)]";
            }
        }

        // 2. 深度穿透检测间接提示词注入 (Indirect Prompt Injection)
        if (INDIRECT_INJECTION_PATTERN.matcher(rawOutput).find()) {
            log.warn("[MCP Security] 拦截到第三方工具 [{}:{}] 返回的数据中潜伏恶意指令覆盖!", serverId, toolName);

            // 自动提取抗原特征，存入免疫账本
            String antigen = extractAntigenSignature(rawOutput);
            immuneAntigenLedger.add(antigen);
            log.info("[MCP Security] 新抗原已沉淀至免疫账本: {}", antigen);

            return "[安全警告：第三方 MCP 工具返回的数据因潜伏恶意提示词覆盖指令 (Indirect Prompt Injection) 已被安全网关彻底阻断]";
        }

        // 3. 敏感数据 DFA/正则脱敏 (Phase 32 原则)
        String sanitized = PHONE_PATTERN.matcher(rawOutput).replaceAll("[REDACTED_PHONE]");
        sanitized = ID_CARD_PATTERN.matcher(sanitized).replaceAll("[REDACTED_ID_CARD]");

        // 4. Markdown 隐蔽外发链接阻断 (OWASP LLM07)
        if (sanitized.contains("![") && (sanitized.contains("?leak=") || sanitized.contains("&token="))) {
            sanitized = sanitized.replaceAll("!\\[.*?\\]\\(https?://[^\\s\\)]+\\)", "[已清除可疑 Markdown 外发载荷]");
        }

        // 5. 16KB 有界截断保护
        if (sanitized.length() > 16000) {
            sanitized = sanitized.substring(0, 16000) + "... [已执行 16KB 安全截断]";
        }

        return sanitized;
    }

    private String extractAntigenSignature(String text) {
        var matcher = INDIRECT_INJECTION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().toLowerCase(Locale.ROOT);
        }
        return "generic-injection-antigen";
    }

    public Set<String> getImmuneAntigenLedger() {
        return Collections.unmodifiableSet(immuneAntigenLedger);
    }
}
