package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public record ToolActionDfaDecision(boolean allowed, ToolActionDfaState fromState,
                                    ToolActionDfaState toState, String toolName,
                                    ToolActionDfaReason reason) {
    public ToolActionDfaDecision {
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(reason, "reason");
        if (allowed != (reason == ToolActionDfaReason.DFA_ALLOWED)) {
            throw new IllegalArgumentException("DFA_ALLOWED must match the decision");
        }
    }
}
