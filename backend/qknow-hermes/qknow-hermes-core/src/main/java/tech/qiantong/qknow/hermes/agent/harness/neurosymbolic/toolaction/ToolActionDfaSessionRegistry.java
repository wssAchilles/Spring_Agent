package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ToolActionDfaSessionRegistry {
    public static final int DEFAULT_MAX_SESSIONS = 1024;

    public record OpenResult(ToolActionDfaSession session, String failureCode) {
        public boolean available() { return session != null; }
    }

    private final int maxSessions;
    private final Map<String, ToolActionDfaSession> active = new LinkedHashMap<>();

    public ToolActionDfaSessionRegistry() {
        this(DEFAULT_MAX_SESSIONS);
    }

    public ToolActionDfaSessionRegistry(int maxSessions) {
        if (maxSessions < 1) {
            throw new IllegalArgumentException("maxSessions must be positive");
        }
        this.maxSessions = maxSessions;
    }

    public synchronized OpenResult open(String requestId, ToolActionDfaSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (requestId == null || requestId.isBlank()) {
            return new OpenResult(null, "REQUEST_ID_REQUIRED");
        }
        if (active.containsKey(requestId)) {
            return new OpenResult(null, "REQUEST_ALREADY_ACTIVE");
        }
        if (active.size() >= maxSessions) {
            return new OpenResult(null, "SESSION_CAPACITY_EXCEEDED");
        }
        ToolActionDfaSession session = new ToolActionDfaSession(requestId, spec);
        active.put(requestId, session);
        return new OpenResult(session, "");
    }

    public synchronized boolean close(ToolActionDfaSession session) {
        Objects.requireNonNull(session, "session");
        if (!active.remove(session.requestId(), session)) {
            return false;
        }
        session.close();
        return true;
    }

    public synchronized int size() {
        return active.size();
    }
}
