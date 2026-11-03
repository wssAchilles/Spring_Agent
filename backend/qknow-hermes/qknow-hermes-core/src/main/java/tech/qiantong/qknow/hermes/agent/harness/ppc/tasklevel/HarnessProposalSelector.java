package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.CandidateProbe;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.ProbeStatus;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.Score;

public final class HarnessProposalSelector {
    public Optional<CandidateProbe> select(Score current, List<CandidateProbe> candidates) {
        Objects.requireNonNull(current, "current");
        return List.copyOf(candidates).stream()
                .filter(candidate -> candidate.status() == ProbeStatus.COMPLETED
                        && candidate.score().safe()
                        && candidate.score().quality() > current.quality())
                .sorted(Comparator.comparingDouble((CandidateProbe candidate) -> candidate.score().quality())
                        .reversed().thenComparingInt(candidate -> candidate.proposal().operator().ordinal())
                        .thenComparing(candidate -> candidate.proposal().candidateKey()))
                .findFirst();
    }
}
