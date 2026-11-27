package tech.qiantong.qknow.hermes.cognitive.verification;

import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复合前提锚定与不可回答主题偷换审计器（Phase 10.3 PG-FRSV，定理 3）。
 * <p>
 * 解决 unigram 词袋与超球面 {@code S^1023} 近邻无法拦截“主题偷换”的缺陷（例如问
 * {@code table tennis} 而上下文仅提 {@code tennis}），并在已充分扫描会话确认前提缺失时保护合法拒答不被推翻。
 * </p>
 */
public class PremiseGroundingAuditor {

    /** 常见英文不可拆分双词/多词复合概念前缀-中心词抽取（动作/属性目标短语） */
    private static final Pattern ACTION_TARGET_PHRASE_PATTERN = Pattern.compile(
            "(?i)\\b(?:play|playing|played|attend|attending|attended|buy|buying|bought|own|owning|owned|use|using|used|eat|eating|ate|drink|drinking|drank|practice|practicing|practiced|visit|visiting|visited|watch|watching|watched|make|making|made|collect|collecting|collected)\\s+(?:a\\s+|an\\s+|the\\s+|my\\s+|your\\s+|some\\s+)?([a-z][a-z\\-]{2,20}\\s+[a-z][a-z\\-]{2,20})(?:\\s+(?:with|at|in|on|during|for|from|to)|[?,.;]|$)"
    );

    private static final Pattern KNOWN_COMPOUND_NOUN_PATTERN = Pattern.compile(
            "(?i)\\b(table tennis|ice hockey|field hockey|rock climbing|mountain bike|electric guitar|acoustic guitar|peanut butter|orange juice|green tea|black coffee|hot chocolate|credit card|debit card|driver license|solar panel|sea turtle|polar bear|high school|middle school|swimming pool|yoga class|piano lesson|violin lesson|tennis court)\\b"
    );

    private static final Set<String> NON_MODIFIER_HEADS = Set.of(
            "this", "that", "these", "those", "every", "each", "many", "much", "more", "most",
            "some", "other", "last", "next", "first", "second", "third", "new", "old", "big",
            "small", "local", "good", "best", "all", "both", "my", "your", "his", "her", "their"
    );

    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final AtomicLong totalPremiseAudits = new AtomicLong(0L);
    private final AtomicLong topicShiftIntercepted = new AtomicLong(0L);
    private final AtomicLong justifiedAbstentionsProtected = new AtomicLong(0L);

    public PremiseGroundingAuditor() {
        this(null);
    }

    public PremiseGroundingAuditor(UnitSphereEmbeddingResolver embeddingResolver) {
        this.embeddingResolver = embeddingResolver;
    }

    /**
     * 不可变第 7 维前提锚定审计报告。
     */
    public record PremiseGroundingReport(
            List<String> compoundAnchors,
            List<String> missingAnchors,
            List<String> partialHeadWordSubstitutions,
            boolean topicShiftRiskDetected,
            boolean answerIsAbstention,
            boolean abstentionJustified,
            String diagnosticHint
    ) {
        public PremiseGroundingReport {
            compoundAnchors = List.copyOf(compoundAnchors != null ? compoundAnchors : List.of());
            missingAnchors = List.copyOf(missingAnchors != null ? missingAnchors : List.of());
            partialHeadWordSubstitutions = List.copyOf(partialHeadWordSubstitutions != null ? partialHeadWordSubstitutions : List.of());
            diagnosticHint = diagnosticHint != null ? diagnosticHint : "";
        }
    }

    /**
     * 执行双通道（复合短语精确落位 + 中心词替身检测）前提审计与合法拒答裁决。
     */
    public PremiseGroundingReport auditPremiseGrounding(
            String question,
            String allRetrievedEvidenceText,
            String candidateAnswer,
            int scannedSessionCount,
            int totalSessionCount
    ) {
        totalPremiseAudits.incrementAndGet();
        String safeQ = question == null ? "" : question;
        String evLower = allRetrievedEvidenceText == null ? "" : allRetrievedEvidenceText.toLowerCase(Locale.ROOT);
        boolean isAbstain = isExplicitAbstention(candidateAnswer);

        Set<String> anchors = extractCompoundAnchors(safeQ);
        List<String> missing = new ArrayList<>();
        List<String> substitutions = new ArrayList<>();

        for (String phrase : anchors) {
            if (containsWholePhrase(evLower, phrase)) {
                continue;
            }
            missing.add(phrase);
            String[] parts = phrase.split("\\s+");
            String headWord = parts[parts.length - 1];
            if (!evLower.isEmpty() && containsWholeWord(evLower, headWord)) {
                substitutions.add(phrase + " -> " + headWord);
            }
        }

        boolean topicShiftRisk = !substitutions.isEmpty();
        boolean sufficientScan = totalSessionCount <= 0
                || scannedSessionCount >= Math.min(totalSessionCount, 3);
        boolean abstentionJustified = isAbstain && (topicShiftRisk || (!missing.isEmpty() && sufficientScan));

        String hint = "";
        if (topicShiftRisk && !isAbstain) {
            topicShiftIntercepted.incrementAndGet();
            hint = "PREMISE_TOPIC_SHIFT: Question specifies compound concept [" + String.join(", ", missing)
                    + "] which NEVER appears verbatim in retrieved evidence; evidence only mentions broader head word in ["
                    + String.join("; ", substitutions) + "]. Refuse with explicit insufficient-information abstention instead of substituting concepts.";
        } else if (abstentionJustified) {
            justifiedAbstentionsProtected.incrementAndGet();
        }

        return new PremiseGroundingReport(
                List.copyOf(anchors),
                missing,
                substitutions,
                topicShiftRisk,
                isAbstain,
                abstentionJustified,
                hint
        );
    }

    public static boolean isExplicitAbstention(String candidateAnswer) {
        if (candidateAnswer == null || candidateAnswer.isBlank()) {
            return false;
        }
        String low = candidateAnswer.toLowerCase(Locale.ROOT);
        return low.contains("not mentioned")
                || low.contains("does not mention")
                || low.contains("never mentioned")
                || low.contains("not provided")
                || low.contains("information provided is not enough")
                || low.contains("not enough information")
                || low.contains("insufficient information")
                || low.contains("cannot determine")
                || low.contains("do not have")
                || low.contains("don't have")
                || low.contains("no record")
                || low.contains("not specify")
                || low.contains("does not specify")
                || low.contains("未提及")
                || low.contains("信息不足");
    }

    private static Set<String> extractCompoundAnchors(String question) {
        Set<String> found = new LinkedHashSet<>();
        Matcher known = KNOWN_COMPOUND_NOUN_PATTERN.matcher(question);
        while (known.find()) {
            found.add(known.group(1).toLowerCase(Locale.ROOT).trim());
        }
        Matcher action = ACTION_TARGET_PHRASE_PATTERN.matcher(question);
        while (action.find()) {
            String rawPhrase = action.group(1).toLowerCase(Locale.ROOT).trim();
            String[] parts = rawPhrase.split("\\s+");
            if (parts.length == 2 && !NON_MODIFIER_HEADS.contains(parts[0]) && !NON_MODIFIER_HEADS.contains(parts[1])) {
                found.add(rawPhrase);
            }
        }
        return found;
    }

    private static boolean containsWholePhrase(String textLower, String phraseLower) {
        if (textLower.isEmpty() || phraseLower.isEmpty()) {
            return false;
        }
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phraseLower) + "(?![a-z0-9])");
        return p.matcher(textLower).find();
    }

    private static boolean containsWholeWord(String textLower, String wordLower) {
        if (textLower.isEmpty() || wordLower.isEmpty()) {
            return false;
        }
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(wordLower) + "(?![a-z0-9])");
        return p.matcher(textLower).find();
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalPremiseAudits", totalPremiseAudits.get());
        map.put("topicShiftIntercepted", topicShiftIntercepted.get());
        map.put("justifiedAbstentionsProtected", justifiedAbstentionsProtected.get());
        return Collections.unmodifiableMap(map);
    }
}
