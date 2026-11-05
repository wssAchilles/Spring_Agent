package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public record ToolActionDfaReceipt(String requestId, String sessionId, String toolCallId,
                                   String specId, String specVersion, String specFingerprint,
                                   String fromState, String toState, String toolName,
                                   ToolActionDfaMode mode, boolean allowed, boolean blocked,
                                   boolean advanced, ToolActionDfaReason reason, String failureCode) {
    public ToolActionDfaReceipt {
        requestId = required(requestId, "requestId");
        sessionId = sessionId == null ? "" : sessionId;
        toolCallId = toolCallId == null ? "" : toolCallId;
        specId = specId == null ? "" : specId;
        specVersion = specVersion == null ? "" : specVersion;
        specFingerprint = specFingerprint == null ? "" : specFingerprint;
        fromState = fromState == null ? "" : fromState;
        toState = toState == null ? "" : toState;
        toolName = toolName == null ? "" : toolName;
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(reason, "reason");
        failureCode = failureCode == null ? "" : failureCode;
        if (advanced && (!allowed || blocked || toState.isBlank())) {
            throw new IllegalArgumentException("Advanced receipt requires an allowed non-blocked transition");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
