package tech.qiantong.qknow.hermes.cognitive.core.model;

public record CognitiveExecutionResult(Status status, String answer, CognitiveTrace trace) {
    public enum Status {
        FALLBACK_BEFORE_EXECUTION,
        COMPLETED,
        FAILED_AFTER_EXECUTION
    }

    public boolean handled() {
        return status != Status.FALLBACK_BEFORE_EXECUTION;
    }
}
