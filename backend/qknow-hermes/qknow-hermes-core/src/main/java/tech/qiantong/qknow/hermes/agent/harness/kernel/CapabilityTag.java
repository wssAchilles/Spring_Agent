package tech.qiantong.qknow.hermes.agent.harness.kernel;

import java.util.Set;

public enum CapabilityTag {
    PROMPT_ONLY(Set.of()),
    RAG(Set.of(ContextKind.RAG)),
    TOOL(Set.of(ContextKind.TOOL)),
    HISTORY(Set.of(ContextKind.HISTORY)),
    COMPOSITE(Set.of(ContextKind.RAG, ContextKind.TOOL));

    public enum ContextKind { RAG, TOOL, HISTORY }

    private final Set<ContextKind> requiredContexts;

    CapabilityTag(Set<ContextKind> requiredContexts) {
        this.requiredContexts = requiredContexts;
    }

    public Set<ContextKind> requiredContexts() {
        return requiredContexts;
    }
}
