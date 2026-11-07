package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;
import java.util.Objects;

public record StrategyVariant(
        String variantId,
        String parentTemplateId,
        String mutationReason,
        List<String> changedFields,
        String expectedBenefit
) {
    public StrategyVariant {
        if (variantId == null || !variantId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                || parentTemplateId == null || parentTemplateId.isBlank()) {
            throw new IllegalArgumentException("variantId and parentTemplateId are required");
        }
        if (mutationReason == null || mutationReason.isBlank()
                || expectedBenefit == null || expectedBenefit.isBlank()) {
            throw new IllegalArgumentException("mutationReason and expectedBenefit are required");
        }
        changedFields = List.copyOf(Objects.requireNonNull(changedFields, "changedFields"));
        if (changedFields.isEmpty() || changedFields.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("changedFields must be nonempty");
        }
    }
}
