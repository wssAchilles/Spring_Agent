package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public record ToolActionDfaTransition(ToolActionDfaState fromState, String toolName,
                                     ToolActionDfaState toState) {
    public ToolActionDfaTransition {
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(toolName, "toolName");
        if (toolName.isBlank()) {
            throw new IllegalArgumentException("DFA tool name is required; epsilon transitions are unsupported");
        }
    }
}
