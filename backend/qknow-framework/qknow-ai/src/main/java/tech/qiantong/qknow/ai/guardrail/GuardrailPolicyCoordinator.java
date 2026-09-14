package tech.qiantong.qknow.ai.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.guardrail.core.AdversarialInjectionGate;
import tech.qiantong.qknow.ai.guardrail.core.FaithfulnessVerifier;
import tech.qiantong.qknow.ai.guardrail.core.OutputSafetyFilter;
import tech.qiantong.qknow.ai.guardrail.core.PiiDfaSanitizer;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailViolationType;
import tech.qiantong.qknow.ai.guardrail.model.SanitizeResult;

import java.util.List;
import java.util.Set;

/**
 * 双向安全合规护栏策略协调调度中枢 (GuardrailPolicyCoordinator)
 *
 * 统一调度输入侧（PII 脱敏 + 越狱门禁）与输出侧（合规敏感词 Fail-Close + 幽灵引用清洗 + 事实忠实度预警降级）。
 *
 * @author qknow
 */
@Slf4j
@Component
public class GuardrailPolicyCoordinator {

    private final PiiDfaSanitizer piiSanitizer;
    private final AdversarialInjectionGate injectionGate;
    private final OutputSafetyFilter outputSafetyFilter;
    private final FaithfulnessVerifier faithfulnessVerifier;

    @Autowired
    public GuardrailPolicyCoordinator(
            PiiDfaSanitizer piiSanitizer,
            AdversarialInjectionGate injectionGate,
            OutputSafetyFilter outputSafetyFilter,
            FaithfulnessVerifier faithfulnessVerifier
    ) {
        this.piiSanitizer = piiSanitizer;
        this.injectionGate = injectionGate;
        this.outputSafetyFilter = outputSafetyFilter;
        this.faithfulnessVerifier = faithfulnessVerifier;
    }

    /**
     * 输入侧双阶段安全护栏流水线
     * 1. 纳秒级 PII 脱敏打码
     * 2. 多语言对抗越狱与提示词注入门禁
     */
    public GuardrailDecision coordinateInput(String rawQuery) {
        long startNano = System.nanoTime();

        // 阶段 1: PII 脱敏
        SanitizeResult sanitizeResult = piiSanitizer.sanitize(rawQuery);
        String currentText = sanitizeResult.sanitizedText();

        // 阶段 2: 对抗越狱门禁
        GuardrailDecision injectionDecision = injectionGate.inspect(currentText);
        if (!injectionDecision.permitted()) {
            return injectionDecision;
        }

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;
        if (sanitizeResult.modified()) {
            return GuardrailDecision.redact(
                    currentText,
                    GuardrailViolationType.PII_EXPOSURE,
                    "输入包含敏感个人信息，已完成无感脱敏替换",
                    elapsedMicros
            );
        }

        return GuardrailDecision.permit(currentText, elapsedMicros);
    }

    /**
     * 输出侧双阶段安全护栏流水线
     * 1. 敏感词毫秒级 Fail-Close 阻断
     * 2. 幽灵引用查杀自愈
     * 3. 事实忠实度核验与预警降级
     */
    public GuardrailDecision coordinateOutput(String rawOutput, Set<String> validChunkIds, List<String> referenceContexts) {
        long startNano = System.nanoTime();

        // 阶段 1: 合规红线敏感词 Fail-Close
        GuardrailDecision safetyDecision = outputSafetyFilter.inspectOutput(rawOutput);
        if (!safetyDecision.permitted()) {
            return safetyDecision;
        }

        String currentText = rawOutput;

        // 阶段 2.1: 幽灵引用自愈
        GuardrailDecision citationDecision = faithfulnessVerifier.verifyCitations(currentText, validChunkIds);
        currentText = citationDecision.processedText();

        // 阶段 2.2: 事实忠实度核验 (阈值 0.15)
        GuardrailDecision faithfulnessDecision = faithfulnessVerifier.verifyFaithfulness(currentText, referenceContexts, 0.15);
        currentText = faithfulnessDecision.processedText();

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        if (faithfulnessDecision.action() == tech.qiantong.qknow.ai.guardrail.model.GuardrailPolicyAction.ALERT_DEGRADATION) {
            return faithfulnessDecision;
        }

        if (citationDecision.action() == tech.qiantong.qknow.ai.guardrail.model.GuardrailPolicyAction.REDACTED_REWRITE) {
            return GuardrailDecision.redact(
                    currentText,
                    GuardrailViolationType.PHANTOM_CITATION,
                    citationDecision.reason(),
                    elapsedMicros
            );
        }

        return GuardrailDecision.permit(currentText, elapsedMicros);
    }
}
