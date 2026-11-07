package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StrategyLineage(
        String rootTemplateId,
        String parentTemplateId,
        List<String> children,
        List<LifecycleEvent> adoptedHistory,
        List<LifecycleEvent> retiredHistory,
        String rollbackPointer
) {
    public StrategyLineage {
        if (rootTemplateId == null || rootTemplateId.isBlank()) {
            throw new IllegalArgumentException("rootTemplateId is required");
        }
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        adoptedHistory = List.copyOf(Objects.requireNonNull(adoptedHistory, "adoptedHistory"));
        retiredHistory = List.copyOf(Objects.requireNonNull(retiredHistory, "retiredHistory"));
    }

    public record LifecycleEvent(String templateId, int experienceVersion,
                                 StrategyLifecycleStatus status, Instant occurredAt) {
        public LifecycleEvent {
            if (templateId == null || templateId.isBlank() || experienceVersion < 1) {
                throw new IllegalArgumentException("templateId and positive experienceVersion are required");
            }
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
