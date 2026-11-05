package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public record ToolActionDfaState(String value) {
    public ToolActionDfaState {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DFA state value is required");
        }
    }
}
