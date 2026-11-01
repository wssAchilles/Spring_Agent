package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer.OperatorProvenance;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer.Suggestion;

public final class OfflineRecursivePpc {
    private static final int MAX_ROUNDS = 2;

    private final ExperienceGuidedProposer proposer;
    private final AlgebraicHarnessMutator mutator;

    public OfflineRecursivePpc(ExperienceGuidedProposer proposer, AlgebraicHarnessMutator mutator) {
        this.proposer = Objects.requireNonNull(proposer, "proposer");
        this.mutator = Objects.requireNonNull(mutator, "mutator");
    }

    public Result run(ExperienceDomain domain, String cue, HermesHarnessVoucher baseline,
                      Map<String, OperatorProvenance> provenance, Probe probe) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(probe, "probe");
        if (cue == null || cue.isBlank()) {
            throw new IllegalArgumentException("cue is required");
        }
        if (!domain.isUnscopedReplay() && !baseline.botId().equals(domain.botId())) {
            throw new IllegalArgumentException("baseline botId does not match domain");
        }

        ProbeResult baselineProbe = Objects.requireNonNull(probe.evaluate(baseline), "baseline probe");
        if (!baselineProbe.allSafe()) {
            throw new IllegalStateException("baseline probe is unsafe");
        }

        List<Suggestion> suggestions = proposer.propose(domain, cue, provenance);
        EnumSet<Operator> tried = EnumSet.noneOf(Operator.class);
        List<Attempt> attempts = new ArrayList<>();
        HermesHarnessVoucher selected = baseline;
        ProbeResult selectedProbe = baselineProbe;
        StopReason stopReason = StopReason.NO_ELIGIBLE_OPERATOR;

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            Proposal proposal = nextProposal(suggestions, tried, selected);
            if (proposal == null) {
                break;
            }

            ProbeResult candidateProbe;
            try {
                candidateProbe = Objects.requireNonNull(probe.evaluate(proposal.voucher()), "candidate probe");
            } catch (RuntimeException failure) {
                attempts.add(new Attempt(round, proposal.suggestion(), proposal.voucher().computeFingerprint(),
                        Optional.empty(), Decision.PROBE_FAILED));
                stopReason = StopReason.PROBE_FAILED;
                break;
            }

            Decision decision = decide(selectedProbe, candidateProbe);
            attempts.add(new Attempt(round, proposal.suggestion(), proposal.voucher().computeFingerprint(),
                    Optional.of(candidateProbe), decision));
            if (decision != Decision.ACCEPTED) {
                stopReason = decision == Decision.INCOMPARABLE
                        ? StopReason.INCOMPARABLE_PROBE : StopReason.NO_IMPROVEMENT;
                break;
            }
            selected = proposal.voucher();
            selectedProbe = candidateProbe;
            if (round == MAX_ROUNDS) {
                stopReason = StopReason.ROUND_LIMIT;
            }
        }
        return new Result(selected, baselineProbe, selectedProbe, attempts, stopReason);
    }

    private Proposal nextProposal(List<Suggestion> suggestions, EnumSet<Operator> tried,
                                  HermesHarnessVoucher selected) {
        for (Suggestion suggestion : suggestions) {
            if (!tried.add(suggestion.operator())) {
                continue;
            }
            try {
                return new Proposal(suggestion, mutator.mutate(selected, suggestion.operator()));
            } catch (IllegalStateException unavailable) {
                // An operator at its bound cannot produce a new candidate.
            }
        }
        return null;
    }

    private Decision decide(ProbeResult previous, ProbeResult candidate) {
        if (!previous.caseScores().keySet().equals(candidate.caseScores().keySet())) {
            return Decision.INCOMPARABLE;
        }
        if (!candidate.allSafe()) {
            return Decision.REJECTED;
        }
        boolean improved = false;
        for (String caseId : previous.caseScores().keySet()) {
            double oldScore = previous.caseScores().get(caseId).quality();
            double newScore = candidate.caseScores().get(caseId).quality();
            if (newScore < oldScore) {
                return Decision.REJECTED;
            }
            improved |= newScore > oldScore;
        }
        return improved ? Decision.ACCEPTED : Decision.REJECTED;
    }

    @FunctionalInterface
    public interface Probe {
        ProbeResult evaluate(HermesHarnessVoucher voucher);
    }

    public record CaseScore(double quality, boolean safe) {
        public CaseScore {
            if (!Double.isFinite(quality) || quality < 0 || quality > 1) {
                throw new IllegalArgumentException("quality must be finite and in [0, 1]");
            }
        }
    }

    public record ProbeResult(Map<String, CaseScore> caseScores) {
        public ProbeResult {
            if (caseScores == null || caseScores.isEmpty()
                    || caseScores.keySet().stream().anyMatch(id -> id == null || id.isBlank())
                    || caseScores.values().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("nonempty case scores with stable IDs are required");
            }
            caseScores = Collections.unmodifiableMap(new TreeMap<>(caseScores));
        }

        public boolean allSafe() {
            return caseScores.values().stream().allMatch(CaseScore::safe);
        }
    }

    public enum Decision {
        ACCEPTED, REJECTED, INCOMPARABLE, PROBE_FAILED
    }

    public enum StopReason {
        NO_ELIGIBLE_OPERATOR, NO_IMPROVEMENT, INCOMPARABLE_PROBE, PROBE_FAILED, ROUND_LIMIT
    }

    public record Attempt(int round, Suggestion suggestion, String candidateFingerprint,
                          Optional<ProbeResult> probe, Decision decision) {
        public Attempt {
            Objects.requireNonNull(suggestion, "suggestion");
            Objects.requireNonNull(candidateFingerprint, "candidateFingerprint");
            Objects.requireNonNull(probe, "probe");
            Objects.requireNonNull(decision, "decision");
        }
    }

    public record Result(HermesHarnessVoucher selected, ProbeResult baselineProbe,
                         ProbeResult selectedProbe, List<Attempt> attempts, StopReason stopReason) {
        public Result {
            Objects.requireNonNull(selected, "selected");
            Objects.requireNonNull(baselineProbe, "baselineProbe");
            Objects.requireNonNull(selectedProbe, "selectedProbe");
            attempts = List.copyOf(attempts);
            Objects.requireNonNull(stopReason, "stopReason");
        }

        public String executionMode() {
            return "OFFLINE_CANDIDATE";
        }
    }

    private record Proposal(Suggestion suggestion, HermesHarnessVoucher voucher) { }
}
