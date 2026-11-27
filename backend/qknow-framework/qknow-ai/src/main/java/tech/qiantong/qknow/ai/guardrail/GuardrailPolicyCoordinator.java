package tech.qiantong.qknow.ai.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.audit.model.GuardrailMetricsVO;
import tech.qiantong.qknow.ai.guardrail.core.AdversarialInjectionGate;
import tech.qiantong.qknow.ai.guardrail.core.FaithfulnessVerifier;
import tech.qiantong.qknow.ai.guardrail.core.OutputSafetyFilter;
import tech.qiantong.qknow.ai.guardrail.core.PiiDfaSanitizer;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailPolicyAction;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailViolationType;
import tech.qiantong.qknow.ai.guardrail.model.SanitizeResult;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 双向安全合规护栏策略协调调度中枢 (GuardrailPolicyCoordinator)
 *
 * 统一调度输入侧（PII 脱敏 + 越狱门禁）与输出侧（合规敏感词 Fail-Close + 幽灵引用清洗 + 事实忠实度预警降级），
 * 并以无锁原子计数实时聚合安全护栏态势指标。
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

    private final AtomicLong totalRequests = new AtomicLong(0L);
    private final AtomicLong sanitizedCount = new AtomicLong(0L);
    private final AtomicLong blockedCount = new AtomicLong(0L);
    private final ConcurrentHashMap<String, AtomicLong> piiTypeCounts = new ConcurrentHashMap<>();
    private final AtomicLongArray hourlyInterceptions = new AtomicLongArray(24);
    private final AtomicLongArray hourlyFaithfulnessSamples = new AtomicLongArray(24);
    private final DoubleAdder[] hourlyFaithfulnessSum = new DoubleAdder[24];
    private final DoubleAdder faithfulnessSum = new DoubleAdder();
    private final AtomicLong faithfulnessSamples = new AtomicLong(0L);

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
        for (String key : List.of("PHONE", "ID_CARD", "BANK_CARD", "EMAIL", "API_TOKEN")) {
            piiTypeCounts.put(key, new AtomicLong(0L));
        }
        for (int h = 0; h < 24; h++) {
            hourlyFaithfulnessSum[h] = new DoubleAdder();
        }
    }

    /**
     * 输入侧双阶段安全护栏流水线
     * 1. 纳秒级 PII 脱敏打码
     * 2. 多语言对抗越狱与提示词注入门禁
     */
    public GuardrailDecision coordinateInput(String rawQuery) {
        long startNano = System.nanoTime();
        totalRequests.incrementAndGet();
        int hour = LocalTime.now().getHour();

        // 阶段 1: PII 脱敏
        SanitizeResult sanitizeResult = piiSanitizer.sanitize(rawQuery);
        String currentText = sanitizeResult.sanitizedText();
        if (sanitizeResult.modified()) {
            sanitizedCount.incrementAndGet();
            hourlyInterceptions.incrementAndGet(hour);
            accumulatePiiDeltas(rawQuery, currentText);
        }

        // 阶段 2: 对抗越狱门禁
        GuardrailDecision injectionDecision = injectionGate.inspect(currentText);
        if (!injectionDecision.permitted()) {
            blockedCount.incrementAndGet();
            hourlyInterceptions.incrementAndGet(hour);
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
        int hour = LocalTime.now().getHour();

        // 阶段 1: 合规红线敏感词 Fail-Close
        GuardrailDecision safetyDecision = outputSafetyFilter.inspectOutput(rawOutput);
        if (!safetyDecision.permitted()) {
            blockedCount.incrementAndGet();
            hourlyInterceptions.incrementAndGet(hour);
            recordFaithfulnessSample(hour, 0.0);
            return safetyDecision;
        }

        String currentText = rawOutput;

        // 阶段 2.1: 幽灵引用自愈
        GuardrailDecision citationDecision = faithfulnessVerifier.verifyCitations(currentText, validChunkIds);
        currentText = citationDecision.processedText();
        if (citationDecision.action() == GuardrailPolicyAction.REDACTED_REWRITE) {
            sanitizedCount.incrementAndGet();
            hourlyInterceptions.incrementAndGet(hour);
        }

        // 阶段 2.2: 事实忠实度核验 (阈值 0.15)
        GuardrailDecision faithfulnessDecision = faithfulnessVerifier.verifyFaithfulness(currentText, referenceContexts, 0.15);
        currentText = faithfulnessDecision.processedText();

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        if (faithfulnessDecision.action() == GuardrailPolicyAction.ALERT_DEGRADATION) {
            hourlyInterceptions.incrementAndGet(hour);
            recordFaithfulnessSample(hour, 0.15);
            return faithfulnessDecision;
        }

        recordFaithfulnessSample(hour, 1.0);

        if (citationDecision.action() == GuardrailPolicyAction.REDACTED_REWRITE) {
            return GuardrailDecision.redact(
                    currentText,
                    GuardrailViolationType.PHANTOM_CITATION,
                    citationDecision.reason(),
                    elapsedMicros
            );
        }

        return GuardrailDecision.permit(currentText, elapsedMicros);
    }

    /**
     * 导出真实运行期安全护栏聚合态势指标（零静态假数据）。
     */
    public GuardrailMetricsVO snapshotMetrics() {
        long total = totalRequests.get();
        long sanitized = sanitizedCount.get();
        long blocked = blockedCount.get();
        double redactionRate = total == 0L ? 0.0 : Math.min(1.0, (double) (sanitized + blocked) / total);

        long faithSamples = faithfulnessSamples.get();
        double avgFaithfulness = faithSamples == 0L
                ? 1.0
                : Math.max(0.0, Math.min(1.0, faithfulnessSum.sum() / faithSamples));

        Map<String, Long> piiDist = new LinkedHashMap<>();
        for (String key : List.of("PHONE", "ID_CARD", "BANK_CARD", "EMAIL", "API_TOKEN")) {
            AtomicLong counter = piiTypeCounts.get(key);
            piiDist.put(key, counter != null ? counter.get() : 0L);
        }

        List<GuardrailMetricsVO.HourlyRiskPoint> hourlyPoints = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            String hourLabel = String.format("%02d:00", h);
            long count = hourlyInterceptions.get(h);
            long hSamples = hourlyFaithfulnessSamples.get(h);
            double hFaith = hSamples == 0L
                    ? avgFaithfulness
                    : Math.max(0.0, Math.min(1.0, hourlyFaithfulnessSum[h].sum() / hSamples));
            hourlyPoints.add(new GuardrailMetricsVO.HourlyRiskPoint(hourLabel, count, hFaith));
        }

        return new GuardrailMetricsVO(
                total,
                sanitized,
                blocked,
                redactionRate,
                avgFaithfulness,
                Collections.unmodifiableMap(piiDist),
                Collections.unmodifiableList(hourlyPoints),
                System.currentTimeMillis()
        );
    }

    private void recordFaithfulnessSample(int hour, double score) {
        double clamped = Math.max(0.0, Math.min(1.0, score));
        faithfulnessSum.add(clamped);
        faithfulnessSamples.incrementAndGet();
        hourlyFaithfulnessSum[hour].add(clamped);
        hourlyFaithfulnessSamples.incrementAndGet(hour);
    }

    private void accumulatePiiDeltas(String raw, String sanitized) {
        addMarkerDelta("PHONE", raw, sanitized, PiiDfaSanitizer.REDACTED_PHONE);
        addMarkerDelta("ID_CARD", raw, sanitized, PiiDfaSanitizer.REDACTED_ID_CARD);
        addMarkerDelta("BANK_CARD", raw, sanitized, PiiDfaSanitizer.REDACTED_BANK_CARD);
        addMarkerDelta("EMAIL", raw, sanitized, PiiDfaSanitizer.REDACTED_EMAIL);
        addMarkerDelta("API_TOKEN", raw, sanitized, PiiDfaSanitizer.REDACTED_TOKEN);
    }

    private void addMarkerDelta(String typeKey, String raw, String sanitized, String marker) {
        int after = countOccurrences(sanitized, marker);
        int before = countOccurrences(raw, marker);
        int delta = Math.max(0, after - before);
        if (delta > 0) {
            piiTypeCounts.computeIfAbsent(typeKey, k -> new AtomicLong(0L)).addAndGet(delta);
        }
    }

    private static int countOccurrences(String text, String sub) {
        if (text == null || text.isEmpty() || sub == null || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
