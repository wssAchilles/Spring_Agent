package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;
import java.util.Locale;

public final class ExperienceLeakageGuard {
    private static final List<String> GRADER_MARKERS = List.of(
            "deterministic_eval_summary", "deterministic_eval_result",
            "judge_scope_and_deterministic_status", "qualitypass",
            "\"passed_count\"", "\"pass_rate\"", "\"test_pass_rate\"",
            "scoring_rubric", "gold_rubric", "qrels.tsv",
            "ground_truth_answer", "expectedkeywords");
    private static final List<String> QREL_MARKERS = List.of(
            "tx-20261029-8891", "25:99:99", "acc-9901", "5800.00",
            "chunk-pay-idem-01", "chunk-arch-ebpf-01");

    private ExperienceLeakageGuard() {}

    public static void assertNoQrelOrGraderLeakage(String contextName, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        for (String marker : GRADER_MARKERS) {
            if (lower.contains(marker)) {
                throw new ExperienceLeakageException(
                        "GRADER_VERDICT_LEAKAGE: " + contextName + " contains " + marker);
            }
        }
        for (String marker : QREL_MARKERS) {
            if (lower.contains(marker)) {
                throw new ExperienceLeakageException(
                        "QREL_LEAKAGE: " + contextName + " contains " + marker);
            }
        }
    }

    public static void assertSafeEntry(ExperienceEntry entry) {
        assertNoQrelOrGraderLeakage("entryId", entry.entryId());
        assertNoQrelOrGraderLeakage("taskCategory", entry.domain().taskCategory());
        assertNoQrelOrGraderLeakage("cue", entry.cue());
        assertNoQrelOrGraderLeakage("lesson", entry.lesson());
        assertNoQrelOrGraderLeakage("evidenceSummary", entry.evidenceSummary());
        assertNoQrelOrGraderLeakage("evidenceId", entry.evidenceRef().evidenceId());
        assertNoQrelOrGraderLeakage("requestId", entry.evidenceRef().requestId());
        assertNoQrelOrGraderLeakage("executionMode", entry.evidenceRef().executionMode());
        assertNoQrelOrGraderLeakage("sourceRunId", entry.sourceRunId());
        assertNoQrelOrGraderLeakage("retirementReason", entry.retirementReason());
    }
}

class ExperienceLeakageException extends IllegalArgumentException {
    ExperienceLeakageException(String message) {
        super(message);
    }
}
