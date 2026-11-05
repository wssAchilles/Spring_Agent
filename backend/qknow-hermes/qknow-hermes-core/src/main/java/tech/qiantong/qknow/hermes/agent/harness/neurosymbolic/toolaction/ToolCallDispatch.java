package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class ToolCallDispatch extends ToolInterceptor implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String requestId;
    private final ToolActionDfaMode mode;
    private final ToolActionDfaSpec spec;
    private final ToolActionDfaSession session;
    private final ToolActionDfaSessionRegistry sessions;
    private final String failureCode;
    private final CopyOnWriteArrayList<ToolActionDfaReceipt> localReceipts = new CopyOnWriteArrayList<>();
    private final AtomicLong unavailableOrdinal = new AtomicLong();
    private final Object lifecycleMonitor = new Object();
    private Lifecycle lifecycle = Lifecycle.OPEN;
    private int inFlightDispatches;

    ToolCallDispatch(String requestId, ToolActionDfaMode mode, ToolActionDfaSpec spec,
                     ToolActionDfaSession session, ToolActionDfaSessionRegistry sessions, String failureCode) {
        this.requestId = requestId == null || requestId.isBlank() ? "unidentified" : requestId;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.spec = spec;
        this.session = session;
        this.sessions = sessions;
        this.failureCode = failureCode == null ? "" : failureCode;
        if (mode == ToolActionDfaMode.ENFORCE && session == null) {
            String code = this.failureCode.isBlank() ? "DFA_SESSION_UNAVAILABLE" : this.failureCode;
            localReceipts.add(receipt("", "", "", "", false, true, false,
                    ToolActionDfaReason.DFA_SESSION_UNAVAILABLE, code));
        }
    }

    public ToolActionDfaMode mode() { return mode; }
    @Override
    public String getName() { return "tool-action-dfa-dispatch"; }
    public boolean isEnforce() { return mode == ToolActionDfaMode.ENFORCE; }
    /** Whether this dispatcher should be attached; SHADOW must stay attached to record unavailable decisions. */
    public boolean isAvailable() { return mode != ToolActionDfaMode.ENFORCE || session != null; }
    public String failureCode() { return failureCode; }
    public ToolActionDfaState sessionSnapshot() { return session == null ? null : session.currentState(); }

    public List<ToolActionDfaReceipt> receiptsSnapshot() {
        if (session == null) {
            return List.copyOf(localReceipts);
        }
        List<ToolActionDfaReceipt> receipts = new java.util.ArrayList<>(session.receiptsSnapshot());
        receipts.addAll(localReceipts);
        return List.copyOf(receipts);
    }

    public void recordUncovered(String path) {
        Objects.requireNonNull(path, "path");
        synchronized (lifecycleMonitor) {
            if (mode == ToolActionDfaMode.OFF || lifecycle != Lifecycle.OPEN) {
                return;
            }
            ToolActionDfaState state = sessionSnapshot();
            ToolActionDfaReceipt receipt = receipt(
                    "", "UNCOVERED:" + path, state == null ? "" : state.value(), "",
                    false, mode == ToolActionDfaMode.ENFORCE, false,
                    ToolActionDfaReason.DFA_PATH_UNCOVERED, "DFA_PATH_UNCOVERED");
            addReceipt(receipt);
        }
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        if (!beginDispatch()) {
            return failure(request, "DFA_DISPATCH_CLOSED");
        }
        try {
            if (mode == ToolActionDfaMode.OFF) {
                return handler.call(request);
            }
            if (session == null) {
                return unavailable(request, handler);
            }

            String toolName = request.getToolName() == null ? "" : request.getToolName();
            ToolCallIdentity identity = session.identity(request.getToolCallId());
            ToolActionDfaSession.Reservation reservation = session.reserve(identity, toolName, request.getArguments());
            if (reservation.status() != ToolActionDfaSession.ReservationStatus.RESERVED) {
                return duplicate(request, identity, toolName, reservation);
            }

            ToolActionDfaGuard guard = new ToolActionDfaGuard(session);
            ToolActionDfaDecision decision = guard.check(toolName);
            if (!decision.allowed() && mode == ToolActionDfaMode.ENFORCE) {
                ToolCallResponse rejected = failure(request, decision.reason().name());
                ToolActionDfaReceipt rejectionReceipt = receipt(identity.value(), toolName,
                        decision.fromState().value(), "", false, true, false,
                        decision.reason(), decision.reason().name());
                session.complete(identity, rejected, rejectionReceipt);
                return rejected;
            }

            ToolCallResponse response;
            try {
                response = handler.call(request);
                if (response == null) {
                    response = failure(request, "TOOL_RESPONSE_NULL");
                }
            } catch (RuntimeException exception) {
                response = failure(request, "TOOL_EXECUTION_EXCEPTION");
            }

            boolean explicitRejection = hasGuardRejection(response.getResult());
            boolean successful = !response.isError() && !explicitRejection;
            boolean advanced = false;
            ToolActionDfaReason reason = decision.allowed() ? ToolActionDfaReason.DFA_ALLOWED : decision.reason();
            String failure = "";
            if (!successful) {
                reason = explicitRejection ? ToolActionDfaReason.DFA_GUARD_REJECTED
                        : ToolActionDfaReason.DFA_TOOL_FAILURE;
                failure = explicitRejection ? "EXISTING_GUARD_REJECTED" : "TOOL_CALL_FAILED";
                guard.commitFailure(identity, failure);
            } else if (decision.allowed()) {
                advanced = guard.commitSuccess(identity, decision.fromState(), toolName);
                if (!advanced) {
                    reason = ToolActionDfaReason.DFA_STALE_COMPLETION;
                    failure = "SHADOW_STALE_COMPLETION";
                }
            }
            ToolActionDfaState next = advanced ? decision.toState() : null;
            ToolActionDfaReceipt callReceipt = receipt(identity.value(), toolName, decision.fromState().value(),
                    next == null ? "" : next.value(), decision.allowed(), explicitRejection, advanced, reason, failure);
            session.complete(identity, response, callReceipt);
            return response;
        } finally {
            endDispatch();
        }
    }

    private ToolCallResponse unavailable(ToolCallRequest request, ToolCallHandler handler) {
        if (mode == ToolActionDfaMode.ENFORCE) {
            ToolCallResponse rejected = failure(request, failureCode.isBlank() ? "DFA_SESSION_UNAVAILABLE" : failureCode);
            localReceipts.add(receipt(identityFor(request), request.getToolName(), "", "", false, true, false,
                    ToolActionDfaReason.DFA_SESSION_UNAVAILABLE, failureCode));
            return rejected;
        }
        return passThroughUnavailable(request, handler, ToolActionDfaReason.DFA_SESSION_UNAVAILABLE,
                failureCode.isBlank() ? "DFA_SESSION_UNAVAILABLE" : failureCode);
    }

    private ToolCallResponse passThroughUnavailable(ToolCallRequest request, ToolCallHandler handler,
                                                   ToolActionDfaReason reason, String code) {
        try {
            ToolCallResponse response = handler.call(request);
            String receiptCode = code;
            if (response == null) {
                receiptCode = "TOOL_RESPONSE_NULL";
            }
            localReceipts.add(receipt(identityFor(request), request.getToolName(), "", "", false, false, false,
                    reason, receiptCode));
            return response;
        } catch (RuntimeException exception) {
            localReceipts.add(receipt(identityFor(request), request.getToolName(), "", "", false, false, false,
                    reason, "TOOL_EXECUTION_EXCEPTION"));
            throw exception;
        }
    }

    private ToolCallResponse duplicate(ToolCallRequest request, ToolCallIdentity identity, String toolName,
                                       ToolActionDfaSession.Reservation reservation) {
        if (reservation.status() == ToolActionDfaSession.ReservationStatus.COMPLETED) {
            ToolActionDfaReceipt original = reservation.cachedReceipt();
            session.addReceipt(receipt(identity.value(), toolName,
                    original == null ? currentState() : original.fromState(), "",
                    original != null && original.allowed(), original != null && original.blocked(), false,
                    ToolActionDfaReason.DFA_DUPLICATE_REPLAY,
                    original == null ? "DFA_DUPLICATE_REPLAY" : original.failureCode()));
            return reservation.cachedResponse();
        }
        String failure = switch (reservation.status()) {
            case IN_PROGRESS -> "DFA_DUPLICATE_IN_PROGRESS";
            case CONFLICT -> "DFA_IDENTITY_CONFLICT";
            default -> "DFA_IDENTITY_UNAVAILABLE";
        };
        ToolCallResponse rejected = failure(request, failure);
        session.addReceipt(receipt(identity.value(), toolName, currentState(), "", false,
                mode == ToolActionDfaMode.ENFORCE, false,
                reservation.status() == ToolActionDfaSession.ReservationStatus.CONFLICT
                        ? ToolActionDfaReason.DFA_IDENTITY_CONFLICT
                        : ToolActionDfaReason.DFA_DUPLICATE_IN_PROGRESS,
                failure));
        return rejected;
    }

    private ToolCallResponse failure(ToolCallRequest request, String code) {
        String id = request.getToolCallId() == null ? "" : request.getToolCallId();
        String tool = request.getToolName() == null ? "unknown" : request.getToolName();
        return ToolCallResponse.error(id, tool, code);
    }

    private String identityFor(ToolCallRequest request) {
        String id = request.getToolCallId();
        return id == null || id.isBlank() ? "ordinal:" + unavailableOrdinal.incrementAndGet() : "tool:" + id;
    }

    private String currentState() {
        ToolActionDfaState state = sessionSnapshot();
        return state == null ? "" : state.value();
    }

    private ToolActionDfaReceipt receipt(String identity, String toolName, String from, String to,
                                         boolean allowed, boolean blocked, boolean advanced,
                                         ToolActionDfaReason reason, String failure) {
        return new ToolActionDfaReceipt(requestId, session == null ? "" : session.sessionId(), identity,
                spec == null ? "" : spec.specId(), spec == null ? "" : spec.version(),
                spec == null ? "" : spec.fingerprint(), from, to, toolName, mode,
                allowed, blocked, advanced, reason, failure);
    }

    private void addReceipt(ToolActionDfaReceipt receipt) {
        if (session != null) {
            session.addReceipt(receipt);
        } else {
            localReceipts.add(receipt);
        }
    }

    private boolean beginDispatch() {
        synchronized (lifecycleMonitor) {
            if (lifecycle != Lifecycle.OPEN) {
                return false;
            }
            inFlightDispatches++;
            return true;
        }
    }

    private void endDispatch() {
        synchronized (lifecycleMonitor) {
            inFlightDispatches--;
            if (inFlightDispatches == 0) {
                lifecycleMonitor.notifyAll();
            }
        }
    }

    /** Stops admission, drains admitted handlers, and returns the stable final evidence snapshot. */
    public List<ToolActionDfaReceipt> closeAndSnapshot() {
        close();
        return receiptsSnapshot();
    }

    private static boolean hasGuardRejection(String result) {
        if (result == null) {
            return false;
        }
        return result.startsWith("[HARNESS_GUARD_REJECTED:")
                || result.startsWith("CIRCUIT_BREAKER_")
                || result.startsWith("【系统熔断提示】")
                || result.startsWith("【系统纠偏提示】")
                || isPermissionDenied(result);
    }

    private static boolean isPermissionDenied(String result) {
        if (!result.contains("permission_denied")) {
            return false;
        }
        try {
            JsonNode body = JSON.readTree(result);
            return body != null && body.isObject()
                    && "permission_denied".equals(body.path("error").asText());
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    @Override
    public void close() {
        boolean interrupted = false;
        synchronized (lifecycleMonitor) {
            if (lifecycle == Lifecycle.CLOSED) {
                return;
            }
            if (lifecycle == Lifecycle.CLOSING) {
                while (lifecycle != Lifecycle.CLOSED) {
                    try {
                        lifecycleMonitor.wait();
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            lifecycle = Lifecycle.CLOSING;
            while (inFlightDispatches != 0) {
                try {
                    lifecycleMonitor.wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }
        try {
            if (session != null && sessions != null) {
                sessions.close(session);
            }
        } finally {
            synchronized (lifecycleMonitor) {
                lifecycle = Lifecycle.CLOSED;
                lifecycleMonitor.notifyAll();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private enum Lifecycle { OPEN, CLOSING, CLOSED }
}
