package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;

public record PlanVerificationResult(boolean valid, List<String> reasonCodes) {
    public PlanVerificationResult {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
