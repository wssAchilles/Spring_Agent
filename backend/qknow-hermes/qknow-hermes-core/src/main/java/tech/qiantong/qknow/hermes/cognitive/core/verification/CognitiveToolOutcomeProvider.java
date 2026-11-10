package tech.qiantong.qknow.hermes.cognitive.core.verification;

/** Deterministic, local-only interpretation of a tool's structured business result. */
public interface CognitiveToolOutcomeProvider {
    String toolName();

    /** Returns a redacted outcome and digest; implementations must not call models or networks. */
    CognitiveToolOutcomeEvidence evaluate(String argumentsJson, String toolResult);
}
