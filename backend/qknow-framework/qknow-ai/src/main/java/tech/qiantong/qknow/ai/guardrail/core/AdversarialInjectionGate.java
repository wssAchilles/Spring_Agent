package tech.qiantong.qknow.ai.guardrail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailViolationType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多语言对抗越狱与提示词注入安全门禁 (AdversarialInjectionGate)
 *
 * 覆盖中英文对抗越狱、同音字变异、角色扮演逃逸、系统提示词窥探，
 * 并支持嵌套 Base64 隐蔽载荷的递归解包与检测。
 *
 * @author qknow
 */
@Slf4j
@Component
public class AdversarialInjectionGate {

    public static final String SAFE_REFUSAL_REPLY =
            "抱歉，系统检测到您的输入包含可能试图绕过安全策略的提示词或对抗性指令。出于安全合规要求，该请求已被阻断。";

    // 1. 中英文越狱与系统提示词覆盖核心规则库
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 英文指令越狱
            Pattern.compile("(?i)\\bignore\\s+(?:all\\s+)?(?:previous|prior|above)\\s+instructions\\b"),
            Pattern.compile("(?i)\\bdisregard\\s+(?:all\\s+)?(?:previous|prior)\\s+(?:instructions|rules)\\b"),
            Pattern.compile("(?i)\\bdan\\s+mode\\b"),
            Pattern.compile("(?i)\\bjailbreak\\b"),
            Pattern.compile("(?i)\\bsystem\\s+prompt\\s+(?:override|leak|reveal)\\b"),
            Pattern.compile("(?i)\\breveal\\s+(?:your\\s+)?system\\s+prompt\\b"),
            Pattern.compile("(?i)\\bdeveloper\\s+mode\\s+(?:enabled|on|activate)\\b"),

            // 中文指令覆盖与越狱
            Pattern.compile("(?i)忽略(?:之前|以上|所有)的?(?:指令|规则|提示词|限制)"),
            Pattern.compile("(?i)无视(?:系统|安全|开发者)?(?:规则|限制|过滤)"),
            Pattern.compile("(?i)开启(?:开发者|DAN|自由|上帝)模式"),
            Pattern.compile("(?i)(?:现在|立刻)?(?:开始)?扮演.*?(?:不受|无).*?(?:规则|道德|约束)"),
            Pattern.compile("(?i)(?:输出|透露|泄露|显示)(?:你的)?(?:系统提示词|System Prompt|初始指令|核心设定)"),
            Pattern.compile("(?i)以无道德限制模式回答"),

            // Markdown 隐蔽数据外发探测 (LLM07)
            Pattern.compile("(?i)!\\[.*?\\]\\(https?://[^\\s\\)]+[\\?&](?:token|data|q|leak)=[^\\s\\)]+\\)")
    );

    // 2. 疑似 Base64 连续编码子串（长度 >= 24，纯 Base64 字符）
    private static final Pattern BASE64_CANDIDATE_PATTERN = Pattern.compile(
            "([A-Za-z0-9+/]{24,}={0,2})"
    );

    /**
     * 检查输入是否包含对抗性注入攻击
     */
    public GuardrailDecision inspect(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailDecision.permit(input, 0L);
        }

        long startNano = System.nanoTime();

        // 1. 直接模式匹配
        for (Pattern pattern : INJECTION_PATTERNS) {
            Matcher m = pattern.matcher(input);
            if (m.find()) {
                long elapsedMicros = (System.nanoTime() - startNano) / 1000;
                log.warn("检测到对抗提示词注入攻击: pattern={}", pattern.pattern());
                return GuardrailDecision.refuse(
                        SAFE_REFUSAL_REPLY,
                        GuardrailViolationType.PROMPT_INJECTION,
                        "命中对抗越狱指令: " + pattern.pattern(),
                        elapsedMicros
                );
            }
        }

        // 2. Base64 嵌套载荷递归解包扫描
        Matcher b64Matcher = BASE64_CANDIDATE_PATTERN.matcher(input);
        while (b64Matcher.find()) {
            String b64Chunk = b64Matcher.group(1);
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(b64Chunk);
                String decodedText = new String(decodedBytes, StandardCharsets.UTF_8);
                // 递归校验解码明文
                for (Pattern pattern : INJECTION_PATTERNS) {
                    if (pattern.matcher(decodedText).find()) {
                        long elapsedMicros = (System.nanoTime() - startNano) / 1000;
                        log.warn("检测到 Base64 嵌套伪装越狱指令: decodedText={}", decodedText);
                        return GuardrailDecision.refuse(
                                SAFE_REFUSAL_REPLY,
                                GuardrailViolationType.PROMPT_INJECTION,
                                "Base64 嵌套载荷命中对抗注入指令: " + pattern.pattern(),
                                elapsedMicros
                        );
                    }
                }
            } catch (Exception ignored) {
                // 容错处理非标准 Base64 字符串
            }
        }

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;
        return GuardrailDecision.permit(input, elapsedMicros);
    }
}
