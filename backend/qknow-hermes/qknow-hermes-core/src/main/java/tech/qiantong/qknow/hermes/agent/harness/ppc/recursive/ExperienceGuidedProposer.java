package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcCandidate;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;

public final class ExperienceGuidedProposer {
    private final ExperienceLibrary library;

    public ExperienceGuidedProposer(ExperienceLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    public List<Suggestion> propose(ExperienceDomain domain, String cue,
                                    Map<String, OperatorProvenance> provenance) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(provenance, "provenance");
        if (cue == null || cue.isBlank()) {
            throw new IllegalArgumentException("cue is required");
        }
        if (domain.isUnscopedReplay()) {
            return List.of();
        }

        return library.recallForProposer(domain, cue, Integer.MAX_VALUE).matchedEntries().stream()
                .filter(entry -> entry.domain().equals(domain)
                        && entry.disposition() == ExperienceDisposition.ADOPTED
                        && !entry.failureObserved() && !entry.retired())
                .sorted(Comparator.comparingDouble(ExperienceEntry::confidence).reversed()
                        .thenComparing(ExperienceEntry::entryId))
                .map(entry -> suggestion(entry, provenance.get(entry.entryId())))
                .filter(Objects::nonNull)
                .toList();
    }

    private Suggestion suggestion(ExperienceEntry entry, OperatorProvenance provenance) {
        if (provenance == null || !entry.sourceRunId().equals(provenance.sourceRunId())) {
            return null;
        }
        PpcCandidate candidate = provenance.candidate();
        if (!candidate.voucher().botId().equals(entry.domain().botId())
                || !candidate.voucher().matchesFingerprint(entry.harnessFingerprint())
                || (provenance.pipeline() == null && candidate.operators().size() != 1)
                || (provenance.pipeline() != null
                        && !candidate.operators().equals(provenance.pipeline().operators()))) {
            return null;
        }
        // shortcut: field matching is not source authentication; require a signed run manifest before production use.
        return new Suggestion(entry.entryId(), candidate.operators().getFirst(), provenance.pipeline());
    }

    public record OperatorProvenance(String sourceRunId, PpcCandidate candidate,
                                     BoundedOperatorPipeline pipeline) {
        public OperatorProvenance {
            if (sourceRunId == null || sourceRunId.isBlank()) {
                throw new IllegalArgumentException("sourceRunId is required");
            }
            Objects.requireNonNull(candidate, "candidate");
            if (pipeline != null && !candidate.operators().equals(pipeline.operators())) {
                throw new IllegalArgumentException("pipeline provenance operator mismatch");
            }
        }

        public OperatorProvenance(String sourceRunId, PpcCandidate candidate) {
            this(sourceRunId, candidate, null);
        }
    }

    public record Suggestion(String entryId, Operator operator, BoundedOperatorPipeline pipeline) {
        public Suggestion {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(operator, "operator");
            if (pipeline != null && !pipeline.operators().getFirst().equals(operator)) {
                throw new IllegalArgumentException("pipeline suggestion primary operator mismatch");
            }
        }

        public Suggestion(String entryId, Operator operator) {
            this(entryId, operator, null);
        }
    }
}
