package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.Locale;

public enum PipelineRuntimeMode {
    OFF, SHADOW, ENFORCE;

    public static PipelineRuntimeMode resolveEffective(boolean enabled, String configuredMode) {
        if (!enabled || configuredMode == null) {
            return OFF;
        }
        return switch (configuredMode.trim().toUpperCase(Locale.ROOT)) {
            case "SHADOW" -> SHADOW;
            case "ENFORCE" -> ENFORCE;
            default -> OFF;
        };
    }
}
