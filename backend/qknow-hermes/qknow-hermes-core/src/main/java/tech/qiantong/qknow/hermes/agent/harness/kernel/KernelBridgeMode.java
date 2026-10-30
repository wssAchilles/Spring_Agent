package tech.qiantong.qknow.hermes.agent.harness.kernel;

import java.util.Locale;

/** Production bridge mode; disabled configurations never enter the kernel. */
public enum KernelBridgeMode {
    OFF, SHADOW, ENFORCE;

    public static KernelBridgeMode resolveEffective(boolean enabled, String configuredMode) {
        if (!enabled || configuredMode == null || configuredMode.isBlank()) {
            return OFF;
        }
        return switch (configuredMode.trim().toUpperCase(Locale.ROOT)) {
            case "SHADOW" -> SHADOW;
            case "ENFORCE" -> ENFORCE;
            default -> OFF;
        };
    }
}
