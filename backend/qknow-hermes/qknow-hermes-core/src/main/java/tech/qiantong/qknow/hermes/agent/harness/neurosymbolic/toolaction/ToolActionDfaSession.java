package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ToolActionDfaSession {
    public enum ReservationStatus { RESERVED, COMPLETED, IN_PROGRESS, CONFLICT }

    public record Reservation(ReservationStatus status, ToolCallResponse cachedResponse,
                              ToolActionDfaReceipt cachedReceipt) {
        public Reservation {
            Objects.requireNonNull(status, "status");
        }
    }

    private final String requestId;
    private final String sessionId;
    private final ToolActionDfaSpec spec;
    private final Map<String, Invocation> invocations = new LinkedHashMap<>();
    private final List<ToolActionDfaReceipt> receipts = new ArrayList<>();
    private long ordinal;
    private ToolActionDfaState currentState;
    private boolean closed;

    ToolActionDfaSession(String requestId, ToolActionDfaSpec spec) {
        this.requestId = require(requestId, "requestId");
        this.sessionId = UUID.randomUUID().toString();
        this.spec = Objects.requireNonNull(spec, "spec");
        this.currentState = spec.initialState();
    }

    public String requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    public ToolActionDfaSpec spec() { return spec; }

    public synchronized ToolActionDfaState currentState() {
        return currentState;
    }

    public synchronized ToolCallIdentity identity(String frameworkToolCallId) {
        ensureOpen();
        return frameworkToolCallId == null || frameworkToolCallId.isBlank()
                ? ToolCallIdentity.ordinal(++ordinal)
                : ToolCallIdentity.framework(frameworkToolCallId);
    }

    public synchronized Reservation reserve(ToolCallIdentity identity, String toolName, String rawArguments) {
        ensureOpen();
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(toolName, "toolName");
        String payloadDigest = digest(rawArguments == null ? "" : rawArguments);
        Invocation existing = invocations.get(identity.value());
        if (existing != null) {
            if (!existing.toolName.equals(toolName) || !existing.payloadDigest.equals(payloadDigest)) {
                return new Reservation(ReservationStatus.CONFLICT, null, null);
            }
            return existing.response == null
                    ? new Reservation(ReservationStatus.IN_PROGRESS, null, null)
                    : new Reservation(ReservationStatus.COMPLETED, existing.response, existing.receipt);
        }
        invocations.put(identity.value(), new Invocation(toolName, payloadDigest));
        return new Reservation(ReservationStatus.RESERVED, null, null);
    }

    public synchronized ToolActionDfaDecision check(String toolName) {
        Objects.requireNonNull(toolName, "toolName");
        ToolActionDfaState from = currentState;
        ToolActionDfaState target = spec.target(from, toolName);
        if (target != null) {
            return new ToolActionDfaDecision(true, from, target, toolName, ToolActionDfaReason.DFA_ALLOWED);
        }
        ToolActionDfaReason reason = !spec.knowsTool(toolName)
                ? ToolActionDfaReason.DFA_UNKNOWN_TOOL
                : spec.acceptingStates().contains(from)
                ? ToolActionDfaReason.DFA_TERMINAL_STATE
                : ToolActionDfaReason.DFA_TRANSITION_NOT_FOUND;
        return new ToolActionDfaDecision(false, from, null, toolName, reason);
    }

    public synchronized boolean commitSuccess(ToolCallIdentity identity, ToolActionDfaState expectedFrom,
                                              String toolName) {
        ensureOpen();
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(expectedFrom, "expectedFrom");
        Objects.requireNonNull(toolName, "toolName");
        Invocation invocation = invocations.get(identity.value());
        if (invocation == null || invocation.response != null || !invocation.toolName.equals(toolName)
                || !currentState.equals(expectedFrom)) {
            return false;
        }
        ToolActionDfaState target = spec.target(expectedFrom, toolName);
        if (target == null) {
            return false;
        }
        currentState = target;
        return true;
    }

    public synchronized void commitFailure(ToolCallIdentity identity, String failureCode) {
        ensureOpen();
        Invocation invocation = invocations.get(Objects.requireNonNull(identity, "identity").value());
        Objects.requireNonNull(failureCode, "failureCode");
        if (invocation == null || invocation.response != null) {
            throw new IllegalStateException("Tool call identity is not an active reservation");
        }
    }

    public synchronized void complete(ToolCallIdentity identity, ToolCallResponse response,
                                      ToolActionDfaReceipt receipt) {
        ensureOpen();
        Invocation invocation = invocations.get(Objects.requireNonNull(identity, "identity").value());
        if (invocation == null || invocation.response != null) {
            throw new IllegalStateException("Tool call identity is not reserved or already completed");
        }
        invocation.response = Objects.requireNonNull(response, "response");
        invocation.receipt = Objects.requireNonNull(receipt, "receipt");
        receipts.add(receipt);
    }

    public synchronized void addReceipt(ToolActionDfaReceipt receipt) {
        ensureOpen();
        receipts.add(Objects.requireNonNull(receipt, "receipt"));
    }

    public synchronized List<ToolActionDfaReceipt> receiptsSnapshot() {
        return List.copyOf(receipts);
    }

    synchronized void close() {
        closed = true;
        invocations.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("DFA session is closed");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static final class Invocation {
        private final String toolName;
        private final String payloadDigest;
        private ToolCallResponse response;
        private ToolActionDfaReceipt receipt;
        private Invocation(String toolName, String payloadDigest) {
            this.toolName = toolName;
            this.payloadDigest = payloadDigest;
        }
    }
}
