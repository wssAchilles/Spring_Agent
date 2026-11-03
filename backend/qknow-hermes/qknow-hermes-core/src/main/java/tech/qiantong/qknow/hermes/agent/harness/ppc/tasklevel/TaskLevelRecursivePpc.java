package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcCandidate;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.PipelineMutation;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer.OperatorProvenance;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.CandidateProbe;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.ProbeStatus;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.Score;

/** Bounded, deterministic task-level PPC for an isolated algorithm benchmark. */
public final class TaskLevelRecursivePpc {
    public static final String MODE = "SYNTHETIC_ALGORITHM_BENCHMARK";
    public static final String CONTROLLED_MODE = "CONTROLLED_FIXTURE";
    private static final String CATEGORY = "synthetic_algorithm_benchmark";
    private static final String CONTROLLED_CATEGORY = "controlled_harness11b2a";

    private final ExperienceLibrary library;
    private final ExperienceGuidedProposer proposer;
    private final AlgebraicHarnessMutator mutator;
    private final PipelineMutation pipelineMutation = new PipelineMutation();
    private final HarnessProposalSelector selector = new HarnessProposalSelector();
    private final PpcStopPolicy stopPolicy;
    private final Map<String, OperatorProvenance> adoptedProvenance = new LinkedHashMap<>();

    public TaskLevelRecursivePpc(ExperienceLibrary library) {
        this(library, PpcStopPolicy.twoRounds());
    }

    public TaskLevelRecursivePpc(ExperienceLibrary library, PpcStopPolicy stopPolicy) {
        this.library = Objects.requireNonNull(library, "library");
        this.proposer = new ExperienceGuidedProposer(library);
        this.mutator = new AlgebraicHarnessMutator();
        this.stopPolicy = Objects.requireNonNull(stopPolicy, "stopPolicy");
    }

    public Result runBootstrap(Task task, HermesHarnessVoucher baseline,
                               List<Operator> operators, Probe probe) {
        validate(task, baseline);
        if (task.domain().isUnscopedReplay()) {
            throw new IllegalArgumentException("UNSCOPED_REPLAY cannot adopt benchmark experience");
        }
        List<ExperienceGuidedProposal> proposals = List.copyOf(operators).stream()
                .distinct().map(ExperienceGuidedProposal::bootstrap).toList();
        return run(task, baseline, proposals, probe, true, false);
    }

    public Result runBootstrapPipeline(Task task, HermesHarnessVoucher baseline,
                                        List<Operator> singles, List<List<Operator>> registeredCombinations,
                                        Probe probe) {
        validate(task, baseline);
        if (task.domain().isUnscopedReplay()) {
            throw new IllegalArgumentException("UNSCOPED_REPLAY cannot adopt benchmark experience");
        }
        List<BoundedOperatorPipeline> pipelines = pipelineMutation.generate(singles, registeredCombinations);
        if (pipelines.isEmpty()) {
            throw new IllegalArgumentException("at least one candidate is required");
        }
        return run(task, baseline, pipelines.stream().map(ExperienceGuidedProposal::pipeline).toList(),
                probe, true, false);
    }

    public Result runTransfer(Task task, HermesHarnessVoucher baseline, Probe probe) {
        validate(task, baseline);
        List<ExperienceGuidedProposal> proposals = proposer.propose(task.domain(), task.cue(),
                        Map.copyOf(adoptedProvenance)).stream()
                .map(ExperienceGuidedProposal::fromExperience).toList();
        return run(task, baseline, proposals, probe, false, false);
    }

    public Result runControlled(Task task, HermesHarnessVoucher baseline,
                                List<Operator> operators, Probe probe) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(operators, "operators");
        if (task.domain().isUnscopedReplay()
                || !CONTROLLED_CATEGORY.equals(task.domain().taskCategory())
                || !baseline.botId().equals(task.domain().botId())) {
            throw new IllegalArgumentException("a matching controlled fixture Domain is required");
        }
        if (operators.isEmpty() || operators.size() > 2 || operators.stream().distinct().count() != operators.size()
                || operators.stream().anyMatch(operator -> operator != Operator.CONCISE_SYSTEM_PROMPT
                        && operator != Operator.ADAPTIVE_MEMORY_WINDOW)) {
            throw new IllegalArgumentException("controlled PPC accepts only unique A/B operators");
        }
        List<ExperienceGuidedProposal> proposals = List.copyOf(operators).stream()
                .map(ExperienceGuidedProposal::bootstrap).toList();
        return run(task, baseline, proposals, probe, false, true);
    }

    private Result run(Task task, HermesHarnessVoucher baseline,
                       List<ExperienceGuidedProposal> proposed, Probe probe,
                       boolean bootstrap, boolean controlled) {
        Objects.requireNonNull(probe, "probe");
        Score baselineScore = Objects.requireNonNull(probe.evaluate(task, baseline), "baseline score");
        if (!baselineScore.safe()) {
            throw new IllegalStateException("unsafe baseline cannot start PPC");
        }
        int probeCount = 1;
        HermesHarnessVoucher current = baseline;
        Score currentScore = baselineScore;
        List<PpcRoundEvidence> rounds = new ArrayList<>();
        List<ExperienceEntry> adopted = new ArrayList<>();
        List<String> appliedEntryIds = new ArrayList<>();
        Map<String, ExperienceGuidedProposal> remaining = new LinkedHashMap<>();
        for (ExperienceGuidedProposal proposal : proposed) {
            remaining.putIfAbsent(proposal.candidateKey(), proposal);
        }
        List<String> recalledEntryIds = bootstrap ? List.of() : remaining.values().stream()
                .filter(ExperienceGuidedProposal::hasExperienceSource)
                .map(ExperienceGuidedProposal::sourceEntryId).toList();
        StopReason stop = StopReason.NO_MORE_PROPOSALS;

        for (int round = 1; round <= stopPolicy.maxRounds(); round++) {
            Map<ExperienceGuidedProposal, HermesHarnessVoucher> candidates = new LinkedHashMap<>();
            for (ExperienceGuidedProposal proposal : remaining.values()) {
                try {
                    candidates.put(proposal, proposal.pipeline() == null
                            ? mutator.mutate(current, proposal.operator())
                            : pipelineMutation.apply(current, proposal.pipeline()));
                } catch (IllegalStateException unavailable) {
                    // A bounded operator with no state change is not a probe.
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            if (!stopPolicy.canProbe(probeCount, candidates.size())) {
                stop = StopReason.BUDGET_EXHAUSTED;
                break;
            }

            List<CandidateProbe> measured = new ArrayList<>();
            for (var candidate : candidates.entrySet()) {
                probeCount++;
                Score score;
                try {
                    score = Objects.requireNonNull(probe.evaluate(task, candidate.getValue()), "candidate score");
                } catch (RuntimeException failure) {
                    measured.add(new CandidateProbe(candidate.getKey(),
                            candidate.getValue().computeFingerprint(), null, ProbeStatus.FAILED));
                    continue;
                }
                measured.add(new CandidateProbe(candidate.getKey(),
                        candidate.getValue().computeFingerprint(), score, ProbeStatus.COMPLETED));
            }

            CandidateProbe winner = selector.select(currentScore, measured).orElse(null);
            HermesHarnessVoucher next = winner == null ? current : candidates.get(winner.proposal());
            PpcRoundEvidence evidence = new PpcRoundEvidence(task.id(), round,
                    current.computeFingerprint(), currentScore, measured,
                    winner == null ? null : winner.proposal(), next.computeFingerprint());
            rounds.add(evidence);
            if (winner == null) {
                stop = measured.stream().anyMatch(candidate -> candidate.status() == ProbeStatus.FAILED)
                        ? StopReason.PROBE_FAILED : StopReason.NO_IMPROVEMENT;
                break;
            }

            current = next;
            currentScore = winner.score();
            remaining.remove(winner.proposal().candidateKey());
            if (winner.proposal().hasExperienceSource()) {
                appliedEntryIds.add(winner.proposal().sourceEntryId());
            }
            if (bootstrap) {
                adopted.add(adopt(task, round, winner.proposal(), current,
                        currentScore, evidence));
            }
            if (round == stopPolicy.maxRounds()) {
                stop = StopReason.ROUND_LIMIT;
            }
        }

        Outcome outcome = controlled ? Outcome.EVALUATED : bootstrap
                ? adopted.isEmpty() ? Outcome.NO_ADOPTION : Outcome.ADOPTED
                : appliedEntryIds.isEmpty() ? Outcome.NO_TRANSFER : Outcome.TRANSFERRED;
        return new Result(task.id(), controlled ? CONTROLLED_MODE : MODE, outcome, stop, baseline, current, baselineScore,
                currentScore, rounds, adopted, recalledEntryIds, appliedEntryIds, probeCount);
    }

    private ExperienceEntry adopt(Task task, int round, ExperienceGuidedProposal proposal,
                                  HermesHarnessVoucher selected, Score score, PpcRoundEvidence evidence) {
        String entryId = "hac1-" + task.id() + "-r" + round;
        String runId = "hac1-run-" + task.id();
        ExperienceEntry entry = new ExperienceEntry(entryId, task.domain(), task.cue(),
                "Accepted synthetic operator " + proposal.candidateKey() + " for bounded benchmark reuse",
                "Deterministic task probe improved in an isolated benchmark",
                new EvidenceReference("hac1-evidence-" + task.id() + "-r" + round,
                        "hac1-request-" + task.id() + "-r" + round, evidence.computeDigest(), MODE),
                score.quality(), runId, selected.computeFingerprint(), ExperienceDisposition.ADOPTED,
                false, ExperienceEntry.AttributionStatus.NOT_APPLICABLE,
                1, false, null, Instant.EPOCH);
        ExperienceEntry saved = library.recordExperience(entry);
        adoptedProvenance.put(saved.entryId(), new OperatorProvenance(runId,
                PpcCandidate.of(entryId, selected, proposal.pipeline() == null
                        ? List.of(proposal.operator()) : proposal.pipeline().operators()), proposal.pipeline()));
        return saved;
    }

    private static void validate(Task task, HermesHarnessVoucher baseline) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(baseline, "baseline");
        if (!task.domain().isUnscopedReplay() && !CATEGORY.equals(task.domain().taskCategory())) {
            throw new IllegalArgumentException("only the synthetic algorithm Domain is allowed");
        }
        if (!task.domain().isUnscopedReplay() && !baseline.botId().equals(task.domain().botId())) {
            throw new IllegalArgumentException("baseline botId does not match Domain");
        }
    }

    @FunctionalInterface
    public interface Probe {
        Score evaluate(Task task, HermesHarnessVoucher voucher);
    }

    public record Task(String id, ExperienceDomain domain, String cue) {
        public Task {
            if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("safe task ID is required");
            }
            Objects.requireNonNull(domain, "domain");
            if (cue == null || cue.length() < 5) {
                throw new IllegalArgumentException("cue is required");
            }
        }
    }

    public enum Outcome { ADOPTED, NO_ADOPTION, TRANSFERRED, NO_TRANSFER, EVALUATED }

    public enum StopReason { NO_MORE_PROPOSALS, NO_IMPROVEMENT, PROBE_FAILED, BUDGET_EXHAUSTED, ROUND_LIMIT }

    public record Result(String taskId, String mode, Outcome outcome, StopReason stopReason,
                         HermesHarnessVoucher baseline, HermesHarnessVoucher selected,
                         Score baselineScore, Score selectedScore, List<PpcRoundEvidence> rounds,
                         List<ExperienceEntry> adoptedEntries, List<String> recalledEntryIds,
                         List<String> appliedEntryIds, int probeCount) {
        public Result {
            rounds = List.copyOf(rounds);
            adoptedEntries = List.copyOf(adoptedEntries);
            recalledEntryIds = List.copyOf(recalledEntryIds);
            appliedEntryIds = List.copyOf(appliedEntryIds);
        }
    }
}
