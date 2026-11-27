package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicViewCompletenessCertificate.CompletenessGrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 双时态情景-关系物化视图完备性审计器（Phase 10.3 CC-E2R，定理 3）。
 */
public class EpisodicViewCompletenessAuditor {

    private final AtomicLong totalCertifiedViews = new AtomicLong(0L);
    private final AtomicLong completeViews = new AtomicLong(0L);
    private final AtomicLong partialWarningViews = new AtomicLong(0L);
    private final AtomicLong suppressedUnreliableViews = new AtomicLong(0L);

    /**
     * 基于原始对话轮次与物化情景事实行评估视图覆盖完备性并签发证书。
     */
    public EpisodicViewCompletenessCertificate certify(
            List<String> rawTurnTexts,
            List<StructuredEpisodicFact> projectedFacts,
            int maxDisplayRows
    ) {
        totalCertifiedViews.incrementAndGet();
        int totalCurrencyMentions = 0;
        boolean hasRelativeTemp = false;
        if (rawTurnTexts != null) {
            for (String turn : rawTurnTexts) {
                if (turn != null && !turn.isBlank()) {
                    totalCurrencyMentions += EpisodicRelationalStateExtractor.countCurrencyMentions(turn);
                    if (!hasRelativeTemp && EpisodicRelationalStateExtractor.hasRelativeTemporalPhrase(turn)) {
                        hasRelativeTemp = true;
                    }
                }
            }
        }

        List<StructuredEpisodicFact> safeFacts = projectedFacts != null ? projectedFacts : List.of();
        int boundMerchantSpend = 0;
        int anonUserSpend = 0;
        int totalNumericFacts = 0;
        for (StructuredEpisodicFact f : safeFacts) {
            if (f.numericValue() != null) {
                totalNumericFacts++;
            }
            if ("spending".equalsIgnoreCase(f.attrKey())) {
                if (f.entityKey() != null && !"user".equalsIgnoreCase(f.entityKey().trim())) {
                    boundMerchantSpend++;
                } else {
                    anonUserSpend++;
                }
            }
        }

        int totalSpend = boundMerchantSpend + anonUserSpend;
        double entityBindingCoverage = totalSpend == 0
                ? (totalCurrencyMentions == 0 ? 1.0 : 0.0)
                : (double) boundMerchantSpend / totalSpend;
        double numericMentionRecall = totalCurrencyMentions == 0
                ? 1.0
                : Math.min(1.0, (double) Math.max(totalSpend, totalNumericFacts) / totalCurrencyMentions);
        int totalRows = safeFacts.size();
        int shownRows = maxDisplayRows > 0 ? Math.min(totalRows, maxDisplayRows) : totalRows;
        double displayCoverageRatio = totalRows == 0 ? 1.0 : (double) shownRows / totalRows;

        CompletenessGrade grade;
        String banner;
        double minCov = Math.min(entityBindingCoverage, numericMentionRecall);
        if (totalCurrencyMentions > 0 && (boundMerchantSpend == 0 || minCov < 0.40)) {
            grade = CompletenessGrade.UNRELIABLE_SUPPRESS;
            suppressedUnreliableViews.incrementAndGet();
            banner = String.format(Locale.ROOT,
                    "[UNRELIABLE_VIEW_SUPPRESSED] entity_binding=%.2f (%d/%d), currency_recall=%.2f (%d/%d). View suppressed; verify directly against raw conversation sessions.",
                    entityBindingCoverage, boundMerchantSpend, totalSpend, numericMentionRecall, totalSpend, totalCurrencyMentions);
        } else if (minCov < 0.85 || displayCoverageRatio < 1.0 || hasRelativeTemp) {
            grade = CompletenessGrade.PARTIAL_SUPPLEMENTARY;
            partialWarningViews.incrementAndGet();
            banner = String.format(Locale.ROOT,
                    "[PARTIAL_VIEW_WARNING] completeness=PARTIAL (entity_binding=%.2f, recall=%.2f, shown=%d/%d, rel_time=%s). Do NOT assume unshown entities/amounts are zero; verify all dated sessions before concluding.",
                    entityBindingCoverage, numericMentionRecall, shownRows, totalRows, hasRelativeTemp);
        } else {
            grade = CompletenessGrade.COMPLETE_AUTHORITATIVE;
            completeViews.incrementAndGet();
            banner = "COMPLETE_AUTHORITATIVE";
        }

        return new EpisodicViewCompletenessCertificate(
                grade,
                entityBindingCoverage,
                numericMentionRecall,
                displayCoverageRatio,
                totalCurrencyMentions,
                boundMerchantSpend,
                anonUserSpend,
                hasRelativeTemp,
                banner
        );
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCertifiedViews", totalCertifiedViews.get());
        map.put("completeViews", completeViews.get());
        map.put("partialWarningViews", partialWarningViews.get());
        map.put("suppressedUnreliableViews", suppressedUnreliableViews.get());
        return Collections.unmodifiableMap(map);
    }
}
