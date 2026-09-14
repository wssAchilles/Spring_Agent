package tech.qiantong.qknow.ai.guardrail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailViolationType;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输出侧合规红线敏感词毫秒级 Fail-Close 阻断器 (OutputSafetyFilter)
 *
 * 毫秒级匹配涉政红线、暴恐违禁、危害公共安全等恶意输出，
 * 严格践行 Fail-Close 铁律，一旦触发立即切断输出并返回标准拒绝话术。
 *
 * @author qknow
 */
@Slf4j
@Component
public class OutputSafetyFilter {

    public static final String SAFE_REDLINE_REPLY =
            "抱歉，该生成内容触碰了系统合规与安全审查红线。根据国家法律法规与系统安全策略，已强制执行 Fail-Close 阻断。";

    // 预编译高危违规输出敏感词模式 (涉暴、涉恐、危害安全等)
    private static final List<Pattern> REDLINE_PATTERNS = List.of(
            Pattern.compile("(?i)(?:制造|合成|配方).*?(?:炸弹|毒药|爆炸物|大规模杀伤武器)"),
            Pattern.compile("(?i)(?:教唆|煽动).*?(?:暴乱|恐怖袭击|武装颠覆)"),
            Pattern.compile("(?i)(?:如何|怎样).*?(?:洗钱|走私毒品|跨国电信诈骗)"),
            Pattern.compile("(?i)(?:提供|购买).*?(?:非法黑客工具|DDOS攻击器|勒索病毒)")
    );

    /**
     * 校验模型输出文本是否触碰合规红线
     */
    public GuardrailDecision inspectOutput(String outputText) {
        if (outputText == null || outputText.isBlank()) {
            return GuardrailDecision.permit(outputText, 0L);
        }

        long startNano = System.nanoTime();

        for (Pattern pattern : REDLINE_PATTERNS) {
            if (pattern.matcher(outputText).find()) {
                long elapsedMicros = (System.nanoTime() - startNano) / 1000;
                log.error("输出文本触碰严重安全合规红线，执行 Fail-Close 阻断: pattern={}", pattern.pattern());
                return GuardrailDecision.refuse(
                        SAFE_REDLINE_REPLY,
                        GuardrailViolationType.REDLINE_SENSITIVE,
                        "输出内容触碰合规安全红线: " + pattern.pattern(),
                        elapsedMicros
                );
            }
        }

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;
        return GuardrailDecision.permit(outputText, elapsedMicros);
    }
}
