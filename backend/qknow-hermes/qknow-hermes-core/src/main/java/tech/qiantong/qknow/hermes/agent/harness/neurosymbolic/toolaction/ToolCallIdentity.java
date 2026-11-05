package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.util.Objects;

public record ToolCallIdentity(String value, boolean frameworkProvided) {
    public ToolCallIdentity {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Tool call identity is required");
        }
    }

    public static ToolCallIdentity framework(String id) {
        return new ToolCallIdentity("tool:" + id, true);
    }

    public static ToolCallIdentity ordinal(long value) {
        return new ToolCallIdentity("ordinal:" + value, false);
    }
}
