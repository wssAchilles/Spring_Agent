package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

public final class PpcComposer {
    public static final String ADOPTED = "ADOPTED";
    public static final String REJECTED_AFTER_REAL_PPC = "REJECTED_AFTER_REAL_PPC";
    public static final String DROPPED_UNKNOWN_USAGE_OR_COST = "DROPPED_UNKNOWN_USAGE_OR_COST";

    public record AnonymousOptionView(boolean qualityPass, boolean guardBlocked, boolean cycleDetected,
                                      long totalTokens, double actualCostCny) {}

    public record PositionSwapAudit(String candidateId, String caseId, String pass1Winner,
                                    String pass2Winner, boolean positionSwapConsistent, String outcome) {}

    public record SelectionResult(String selectedCandidateId, String finalVerdict,
                                  List<PositionSwapAudit> audits, Map<String, String> droppedReasons) {
        public SelectionResult {
            audits = List.copyOf(audits);
            droppedReasons = Map.copyOf(droppedReasons);
        }
    }

    public List<PpcCandidate> createCandidates(HermesHarnessVoucher baseline) {
        Objects.requireNonNull(baseline, "baseline");
        InvariantSystemPromptRecord concisePrompt = new AlgebraicHarnessMutator()
                .mutate(baseline, Operator.CONCISE_SYSTEM_PROMPT).invariantSystemPrompt();
        GuardrailsSpecificationRecord guard = baseline.guardrails();
        if (guard.maxCycleSteps() != 3 || concisePrompt.canonical().length()
                >= baseline.invariantSystemPrompt().canonical().length()) {
            throw new IllegalArgumentException("Stage 9 baseline must have three cycle steps and a reducible prompt");
        }
        HermesHarnessVoucher concise = copy(baseline, concisePrompt, guard);
        HermesHarnessVoucher tightened = copy(baseline, baseline.invariantSystemPrompt(),
                new GuardrailsSpecificationRecord(2, guard.loopThreshold(), guard.maxTokenBudget(), guard.maxCostCny()));
        return List.of(PpcCandidate.of("baseline", baseline, List.of()),
                PpcCandidate.of("candidate-a", concise, List.of(Operator.CONCISE_SYSTEM_PROMPT)),
                PpcCandidate.of("candidate-b", tightened, List.of(Operator.GUARDRAIL_TIGHTENING)));
    }

    public PpcCandidate createCombinedCandidate(PpcCandidate concise, PpcCandidate tightened) {
        if (!"candidate-a".equals(concise.candidateId()) || !"candidate-b".equals(tightened.candidateId())) {
            throw new IllegalArgumentException("Stage 9 combination requires candidates A and B");
        }
        HermesHarnessVoucher combined = copy(concise.voucher(),
                concise.voucher().invariantSystemPrompt(), tightened.voucher().guardrails());
        return PpcCandidate.of("candidate-c", combined,
                List.of(Operator.CONCISE_SYSTEM_PROMPT, Operator.GUARDRAIL_TIGHTENING));
    }

    public void assertNoQrelLeakage(String text, List<String> expectedKeywords) {
        String lower = Objects.requireNonNull(text, "text").toLowerCase(Locale.ROOT);
        for (String marker : expectedKeywords) {
            if (marker != null && !marker.isBlank() && lower.contains(marker.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("QREL_LEAKAGE_DETECTED");
            }
        }
        for (String marker : List.of("expectedkeywords", "ground_truth_answer", "gold_rubric",
                "qrels.tsv", "qualitypass", "scoring_rubric")) {
            if (lower.contains(marker)) {
                throw new IllegalStateException("QREL_LEAKAGE_DETECTED");
            }
        }
    }

    public SelectionResult select(String runId, List<PpcCandidate> candidates, List<PpcProbeResult> probes) {
        return select(runId, candidates, probes, List.of());
    }

    public SelectionResult select(String runId, List<PpcCandidate> candidates, List<PpcProbeResult> probes,
                                  List<String> expectedKeywords) {
        Objects.requireNonNull(runId, "runId");
        List<PpcCandidate> candidateCopy = List.copyOf(candidates);
        List<PpcProbeResult> probeCopy = List.copyOf(probes);
        Map<String, String> dropped = new HashMap<>();
        Map<String, List<PpcProbeResult>> byCandidate = new HashMap<>();
        for (PpcCandidate candidate : candidateCopy) {
            assertNoQrelLeakage(candidate.voucher().invariantSystemPrompt().canonical(), expectedKeywords);
            byCandidate.put(candidate.candidateId(), probeCopy.stream()
                    .filter(probe -> candidate.candidateId().equals(probe.candidateId())).toList());
        }
        List<PpcProbeResult> baseline = byCandidate.get("baseline");
        if (baseline == null || baseline.size() != 5 || baseline.stream().anyMatch(this::unknownCost)) {
            return new SelectionResult("baseline", REJECTED_AFTER_REAL_PPC, List.of(),
                    Map.of("baseline", DROPPED_UNKNOWN_USAGE_OR_COST));
        }
        Set<String> cases = new HashSet<>();
        for (PpcProbeResult probe : baseline) {
            if (!cases.add(probe.caseId())) {
                throw new IllegalArgumentException("Duplicate baseline caseId");
            }
        }
        double baseCost = baseline.stream().mapToDouble(PpcProbeResult::actualCostCny).sum();
        long baseTokens = baseline.stream().mapToLong(PpcProbeResult::totalTokens).sum();
        List<PositionSwapAudit> audits = new ArrayList<>();
        List<PpcCandidate> eligible = new ArrayList<>();
        for (PpcCandidate candidate : candidateCopy) {
            if ("baseline".equals(candidate.candidateId())) continue;
            List<PpcProbeResult> own = byCandidate.get(candidate.candidateId());
            if (own.size() != 5 || own.stream().anyMatch(this::unknownCost)) {
                dropped.put(candidate.candidateId(), DROPPED_UNKNOWN_USAGE_OR_COST);
                continue;
            }
            Map<String, PpcProbeResult> keyed = new HashMap<>();
            for (PpcProbeResult probe : own) {
                if (keyed.put(probe.caseId(), probe) != null) {
                    throw new IllegalArgumentException("Duplicate candidate caseId");
                }
            }
            if (!keyed.keySet().equals(cases)) {
                dropped.put(candidate.candidateId(), "INCOMPLETE_CASE_SET");
                continue;
            }
            int improvements = 0;
            boolean valid = true;
            for (PpcProbeResult base : baseline) {
                PpcProbeResult probe = keyed.get(base.caseId());
                PositionSwapAudit audit = compare(runId, candidate.candidateId(), base, probe);
                audits.add(audit);
                if (!audit.positionSwapConsistent() || !probe.qualityPass() || probe.guardBlocked()
                        || probe.cycleDetected() || !base.qualityPass()) valid = false;
                if (probe.actualCostCny() < base.actualCostCny() || probe.totalTokens() < base.totalTokens()) {
                    improvements++;
                }
            }
            double cost = own.stream().mapToDouble(PpcProbeResult::actualCostCny).sum();
            long tokens = own.stream().mapToLong(PpcProbeResult::totalTokens).sum();
            if (valid && improvements >= 3 && (cost < baseCost || (cost <= baseCost && tokens < baseTokens))) {
                eligible.add(candidate.withResults(own));
            }
        }
        PpcCandidate winner = eligible.stream().min(Comparator.comparingDouble(PpcCandidate::totalCostCny)
                .thenComparingLong(PpcCandidate::totalTokens).thenComparing(PpcCandidate::candidateId)).orElse(null);
        return new SelectionResult(winner == null ? "baseline" : winner.candidateId(),
                winner == null ? REJECTED_AFTER_REAL_PPC : ADOPTED, audits, dropped);
    }

    private boolean unknownCost(PpcProbeResult probe) {
        return probe.usageStatus() != UsageStatus.KNOWN || !probe.costKnown()
                || !Double.isFinite(probe.actualCostCny()) || probe.actualCostCny() <= 0.0D;
    }

    private PositionSwapAudit compare(String runId, String candidateId, PpcProbeResult base, PpcProbeResult probe) {
        AnonymousOptionView baseView = view(base);
        AnonymousOptionView candidateView = view(probe);
        boolean candidateFirst = (sha256(runId + ":" + candidateId + ":" + base.caseId())[0] & 1) != 0;
        AnonymousOptionView first = candidateFirst ? candidateView : baseView;
        AnonymousOptionView second = candidateFirst ? baseView : candidateView;
        String pass1 = winner(first, second);
        String pass2 = winner(second, first);
        String physical1 = physicalWinner(pass1, candidateFirst);
        String physical2 = physicalWinner(pass2, !candidateFirst);
        boolean consistent = !"TIE".equals(physical1) && physical1.equals(physical2);
        return new PositionSwapAudit(candidateId, base.caseId(), physical1, physical2, consistent,
                consistent ? physical1 : "INCONSISTENT_TIE");
    }

    private AnonymousOptionView view(PpcProbeResult probe) {
        return new AnonymousOptionView(probe.qualityPass(), probe.guardBlocked(), probe.cycleDetected(),
                probe.totalTokens(), probe.actualCostCny());
    }

    private String winner(AnonymousOptionView a, AnonymousOptionView b) {
        int quality = Boolean.compare(a.qualityPass() && !a.guardBlocked() && !a.cycleDetected(),
                b.qualityPass() && !b.guardBlocked() && !b.cycleDetected());
        if (quality != 0) return quality > 0 ? "Option_A" : "Option_B";
        int cost = Double.compare(a.actualCostCny(), b.actualCostCny());
        if (cost != 0) return cost < 0 ? "Option_A" : "Option_B";
        int tokens = Long.compare(a.totalTokens(), b.totalTokens());
        return tokens == 0 ? "TIE" : tokens < 0 ? "Option_A" : "Option_B";
    }

    private String physicalWinner(String option, boolean candidateFirst) {
        if ("TIE".equals(option)) return "TIE";
        return ("Option_A".equals(option) == candidateFirst) ? "CANDIDATE" : "BASELINE";
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private HermesHarnessVoucher copy(HermesHarnessVoucher source,
                                      InvariantSystemPromptRecord prompt, GuardrailsSpecificationRecord guardrails) {
        return new HermesHarnessVoucher(source.voucherId(), source.botId(),
                source.version(), source.createdAt(), prompt, guardrails, source.memoryContext(), source.toolGuidance(),
                source.subagents(), source.skillBank(), source.sandboxedExecutionPlan(), source.mcpMiddlewarePlugins());
    }
}
