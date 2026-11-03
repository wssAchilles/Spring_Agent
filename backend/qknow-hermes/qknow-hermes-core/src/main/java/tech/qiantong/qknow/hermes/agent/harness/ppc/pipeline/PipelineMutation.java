package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

/** Executes validated pipelines and generates only registered bounded candidates. */
public final class PipelineMutation {
    public static final int MAX_CANDIDATES = 4;
    private final AlgebraicHarnessMutator mutator = new AlgebraicHarnessMutator();

    public HermesHarnessVoucher apply(HermesHarnessVoucher source, BoundedOperatorPipeline pipeline) {
        HermesHarnessVoucher current = Objects.requireNonNull(source, "source");
        for (Operator operator : Objects.requireNonNull(pipeline, "pipeline").operators()) {
            current = mutator.mutate(current, operator);
        }
        return current;
    }

    public List<BoundedOperatorPipeline> generate(
            List<Operator> singles, List<List<Operator>> registeredCombinations) {
        Objects.requireNonNull(singles, "singles");
        Objects.requireNonNull(registeredCombinations, "registeredCombinations");
        List<BoundedOperatorPipeline> candidates = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        for (Operator single : singles) {
            add(candidates, fingerprints, new BoundedOperatorPipeline(List.of(single)));
        }
        for (List<Operator> combination : registeredCombinations) {
            if (combination == null || combination.size() < 2) {
                throw new IllegalArgumentException("registered combination requires at least two operators");
            }
            add(candidates, fingerprints, new BoundedOperatorPipeline(combination));
        }
        return List.copyOf(candidates);
    }

    private static void add(List<BoundedOperatorPipeline> candidates, Set<String> fingerprints,
                            BoundedOperatorPipeline pipeline) {
        if (candidates.size() == MAX_CANDIDATES) {
            throw new IllegalArgumentException("pipeline candidate limit exceeded");
        }
        if (!fingerprints.add(pipeline.fingerprint())) {
            throw new IllegalArgumentException("duplicate pipeline candidate");
        }
        candidates.add(pipeline);
    }
}
