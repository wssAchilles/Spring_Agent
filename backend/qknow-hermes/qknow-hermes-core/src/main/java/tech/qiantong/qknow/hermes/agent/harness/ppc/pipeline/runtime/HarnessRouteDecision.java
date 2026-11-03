package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.Objects;

public record HarnessRouteDecision(HarnessProfile profile, String reason, boolean fallback,
                                   String routeFingerprint) {
    public HarnessRouteDecision {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(routeFingerprint, "routeFingerprint");
    }
}
