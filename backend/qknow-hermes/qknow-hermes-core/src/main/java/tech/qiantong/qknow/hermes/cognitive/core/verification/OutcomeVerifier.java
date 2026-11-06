package tech.qiantong.qknow.hermes.cognitive.core.verification;

@FunctionalInterface
public interface OutcomeVerifier {
    OutcomeSignal verify(OutcomeVerificationRequest request);
}
