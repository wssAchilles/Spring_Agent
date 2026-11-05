package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ToolActionDfaRegistry {
    private static final ToolActionDfaRegistry DEFAULTS = new ToolActionDfaRegistry(java.util.List.of(
            new ToolActionDfaSpec("fixture-read-then-write", "1",
                    new ToolActionDfaState("START"),
                    java.util.Set.of(new ToolActionDfaState("DONE")),
                    java.util.Set.of(new ToolActionDfaState("START"), new ToolActionDfaState("READ"),
                            new ToolActionDfaState("DONE")),
                    java.util.List.of(
                            new ToolActionDfaTransition(new ToolActionDfaState("START"), "fixture.read",
                                    new ToolActionDfaState("READ")),
                            new ToolActionDfaTransition(new ToolActionDfaState("READ"), "fixture.write",
                                    new ToolActionDfaState("DONE"))), true)));

    private final Map<String, ToolActionDfaSpec> specs;

    public ToolActionDfaRegistry(Collection<ToolActionDfaSpec> specs) {
        Objects.requireNonNull(specs, "specs");
        Map<String, ToolActionDfaSpec> copy = new LinkedHashMap<>();
        for (ToolActionDfaSpec spec : specs) {
            Objects.requireNonNull(spec, "spec");
            if (copy.putIfAbsent(spec.specId(), spec) != null) {
                throw new IllegalArgumentException("Duplicate DFA specId: " + spec.specId());
            }
        }
        this.specs = Map.copyOf(copy);
    }

    public static ToolActionDfaRegistry defaults() {
        return DEFAULTS;
    }

    public Optional<ToolActionDfaSpec> findApproved(String specId) {
        ToolActionDfaSpec spec = specs.get(specId);
        return spec != null && spec.approved() ? Optional.of(spec) : Optional.empty();
    }
}
