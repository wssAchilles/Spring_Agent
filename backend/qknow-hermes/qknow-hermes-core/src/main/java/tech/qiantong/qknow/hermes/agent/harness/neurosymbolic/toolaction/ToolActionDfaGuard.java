package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public final class ToolActionDfaGuard {
    private final ToolActionDfaSession session;

    public ToolActionDfaGuard(ToolActionDfaSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public ToolActionDfaDecision check(String toolName) {
        return session.check(toolName);
    }

    public boolean commitSuccess(ToolCallIdentity identity, ToolActionDfaState expectedFrom, String toolName) {
        return session.commitSuccess(identity, expectedFrom, toolName);
    }

    public void commitFailure(ToolCallIdentity identity, String failureCode) {
        session.commitFailure(identity, failureCode);
    }
}
