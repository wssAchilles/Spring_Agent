package tech.qiantong.qknow.hermes.transaction.dto;

import java.util.List;
import java.util.Map;

/**
 * 契约演化自适应校验与映射结果 Record
 */
public record ContractEvolutionResult(
        boolean compatible,
        ContractEvolutionType evolutionType,
        double geodesicDistance,
        double semanticSimilarity,
        Map<String, Object> adaptedPayload,
        List<String> evolutionDetails,
        long executionDurationNanos
) {
    public boolean isCompatible() {
        return compatible;
    }
}
