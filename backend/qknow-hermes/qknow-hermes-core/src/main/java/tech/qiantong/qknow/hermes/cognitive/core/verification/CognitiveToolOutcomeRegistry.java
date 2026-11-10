package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class CognitiveToolOutcomeRegistry {
    private final Map<String, CognitiveToolOutcomeProvider> providers;

    public CognitiveToolOutcomeRegistry(List<CognitiveToolOutcomeProvider> providers) {
        this.providers = providers == null ? Map.of() : providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> requireToolName(provider.toolName()), Function.identity()));
    }

    public static CognitiveToolOutcomeRegistry empty() {
        return new CognitiveToolOutcomeRegistry(List.of());
    }

    public CognitiveToolOutcomeEvidence evaluate(String toolName, String argumentsJson, String toolResult) {
        CognitiveToolOutcomeProvider provider = providers.get(toolName);
        if (provider == null) {
            return CognitiveToolOutcomeEvidence.unknown("OUTCOME_PROVIDER_UNAVAILABLE");
        }
        try {
            CognitiveToolOutcomeEvidence evidence = provider.evaluate(argumentsJson, toolResult);
            return evidence == null
                    ? CognitiveToolOutcomeEvidence.unknown("OUTCOME_PROVIDER_NO_SIGNAL") : evidence;
        } catch (RuntimeException ignored) {
            return CognitiveToolOutcomeEvidence.unknown("OUTCOME_PROVIDER_FAILED");
        }
    }

    private static String requireToolName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("toolName is required");
        }
        return name;
    }
}
