package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

/** An ordered, immutable sequence of at most three component mutations. */
public record BoundedOperatorPipeline(List<Operator> operators) {
    public static final int MAX_OPERATORS = 3;
    private static final Set<List<Operator>> REGISTERED_COMBINATIONS = Set.of(
            List.of(Operator.CONCISE_SYSTEM_PROMPT, Operator.ADAPTIVE_MEMORY_WINDOW));

    public BoundedOperatorPipeline {
        operators = List.copyOf(Objects.requireNonNull(operators, "operators"));
        if (operators.isEmpty() || operators.size() > MAX_OPERATORS) {
            throw new IllegalArgumentException("pipeline requires one to three operators");
        }
        new TargetConflictValidator().validate(operators);
        if (operators.size() > 1 && !REGISTERED_COMBINATIONS.contains(operators)) {
            throw new IllegalArgumentException("pipeline combination is not registered");
        }
    }

    public String fingerprint() {
        String canonical = "bounded-operator-pipeline-v1\u001f"
                + String.join("\u001f", operators.stream().map(Enum::name).toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
