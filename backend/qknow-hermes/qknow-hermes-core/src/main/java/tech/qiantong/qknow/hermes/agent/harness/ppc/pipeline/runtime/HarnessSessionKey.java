package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;

public record HarnessSessionKey(String sessionId, CapabilityTag capabilityTag, String domain) {
    public HarnessSessionKey {
        requireText(sessionId, "sessionId");
        Objects.requireNonNull(capabilityTag, "capabilityTag");
        requireText(domain, "domain");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
