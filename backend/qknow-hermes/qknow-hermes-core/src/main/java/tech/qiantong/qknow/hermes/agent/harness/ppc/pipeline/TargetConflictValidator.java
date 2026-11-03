package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

/** Rejects implicit overwrite of the same Harness component. */
public final class TargetConflictValidator {
    public void validate(List<Operator> operators) {
        Objects.requireNonNull(operators, "operators");
        EnumSet<Operator> seenOperators = EnumSet.noneOf(Operator.class);
        for (Operator operator : operators) {
            Objects.requireNonNull(operator, "operator");
            if (!seenOperators.add(operator)) {
                throw new IllegalArgumentException("duplicate pipeline operator: " + operator);
            }
        }
        validateTargets(operators.stream().map(OperatorTarget::of).toList());
    }

    public void validateTargets(List<OperatorTarget> targets) {
        Objects.requireNonNull(targets, "targets");
        EnumSet<OperatorTarget> seenTargets = EnumSet.noneOf(OperatorTarget.class);
        for (OperatorTarget target : targets) {
            Objects.requireNonNull(target, "target");
            if (!seenTargets.add(target)) {
                throw new IllegalArgumentException("pipeline target conflict: " + target);
            }
        }
    }
}
