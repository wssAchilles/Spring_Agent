package tech.qiantong.qknow.hermes.cognitive.core.verification;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Read-only registry of the built-in deterministic task families. */
public final class OutcomeVerifierRegistry {
    private final Map<String, OutcomeTaskFamily> families;

    public OutcomeVerifierRegistry() {
        this(java.util.List.of(OutcomeTaskFamily.structuredOutput(), OutcomeTaskFamily.toolState(),
                OutcomeTaskFamily.deterministicLogic()));
    }

    public OutcomeVerifierRegistry(Collection<OutcomeTaskFamily> families) {
        if (families == null || families.isEmpty()) {
            throw new IllegalArgumentException("at least one task family is required");
        }
        var copy = new LinkedHashMap<String, OutcomeTaskFamily>();
        for (OutcomeTaskFamily family : families) {
            if (family == null || copy.put(family.familyId(), family) != null) {
                throw new IllegalArgumentException("task family ids must be non-null and unique");
            }
        }
        this.families = Map.copyOf(copy);
    }

    public Optional<OutcomeTaskFamily> find(String familyId) {
        return familyId == null ? Optional.empty() : Optional.ofNullable(families.get(familyId));
    }

    public Collection<OutcomeTaskFamily> families() {
        return families.values();
    }
}
