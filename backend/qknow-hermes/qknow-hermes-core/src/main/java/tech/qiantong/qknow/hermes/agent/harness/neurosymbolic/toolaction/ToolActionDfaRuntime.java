package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public final class ToolActionDfaRuntime {
    private final ToolActionDfaRegistry registry;
    private final ToolActionDfaSessionRegistry sessions;

    public ToolActionDfaRuntime(ToolActionDfaRegistry registry, int maxSessions) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.sessions = new ToolActionDfaSessionRegistry(maxSessions);
    }

    public static ToolActionDfaRuntime defaults() {
        return new ToolActionDfaRuntime(ToolActionDfaRegistry.defaults(),
                ToolActionDfaSessionRegistry.DEFAULT_MAX_SESSIONS);
    }

    public ToolCallDispatch openRequest(String requestId, ToolActionDfaMode mode, String specId) {
        Objects.requireNonNull(mode, "mode");
        if (mode == ToolActionDfaMode.OFF) {
            return new ToolCallDispatch(requestId, mode, null, null, null, "");
        }
        ToolActionDfaSpec spec = registry.findApproved(specId).orElse(null);
        if (spec == null) {
            return new ToolCallDispatch(requestId, mode, null, null, null, "DFA_SPEC_NOT_FOUND");
        }
        ToolActionDfaSessionRegistry.OpenResult opened = sessions.open(requestId, spec);
        return new ToolCallDispatch(requestId, mode, spec, opened.session(), sessions,
                opened.available() ? "" : opened.failureCode());
    }

    public int activeSessions() {
        return sessions.size();
    }
}
