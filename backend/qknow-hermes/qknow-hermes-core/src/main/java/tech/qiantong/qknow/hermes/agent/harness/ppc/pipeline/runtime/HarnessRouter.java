package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;

/** Deterministic whitelist router; it never mutates profiles or request state. */
public final class HarnessRouter {
    private final HarnessProfileRegistry registry;
    private final Map<CapabilityTag, String> routes;

    public HarnessRouter(HarnessProfileRegistry registry, Map<CapabilityTag, String> routes) {
        this.registry = Objects.requireNonNull(registry, "registry");
        EnumMap<CapabilityTag, String> copy = new EnumMap<>(CapabilityTag.class);
        copy.putAll(Objects.requireNonNull(routes, "routes"));
        this.routes = Map.copyOf(copy);
    }

    public static HarnessRouter defaults() {
        return new HarnessRouter(HarnessProfileRegistry.defaults(), Map.of(
                CapabilityTag.PROMPT_ONLY, "prompt-profile",
                CapabilityTag.HISTORY, "history-profile",
                CapabilityTag.TOOL, "tool-profile"));
    }

    public HarnessRouteDecision route(HarnessRoutingContext context) {
        Objects.requireNonNull(context, "context");
        if (!HarnessProfileRegistry.DEFAULT_DOMAIN.equals(context.domain())) {
            return fallback(context, "DOMAIN_MISMATCH");
        }
        if (context.capabilityTag() == null) {
            return fallback(context, "UNKNOWN_CAPABILITY");
        }
        String profileId = routes.get(context.capabilityTag());
        if (profileId == null) {
            return fallback(context, "NO_APPROVED_ROUTE");
        }
        HarnessProfile profile = registry.find(profileId).orElse(null);
        if (profile == null) {
            return fallback(context, "UNKNOWN_PROFILE");
        }
        if (!profile.approved()) {
            return fallback(context, "PROFILE_NOT_APPROVED");
        }
        if (profile.status() == HarnessProfileStatus.DEPRECATED) {
            return fallback(context, "PROFILE_DEPRECATED");
        }
        if (profile.status() == HarnessProfileStatus.RETIRED) {
            return fallback(context, "PROFILE_RETIRED");
        }
        if (!context.domain().equals(profile.domainScope())
                || context.capabilityTag() != profile.capabilityTag()) {
            return fallback(context, "PROFILE_MISMATCH");
        }
        return decision(context, profile, "MATCHED_CAPABILITY", false);
    }

    private HarnessRouteDecision fallback(HarnessRoutingContext context, String reason) {
        return decision(context, registry.defaultProfile(), reason, true);
    }

    HarnessProfile routedProfile(HarnessRoutingContext context) {
        String profileId = routes.get(context.capabilityTag());
        return profileId == null ? null : registry.find(profileId).orElse(null);
    }

    HarnessProfile profile(String profileId) {
        return registry.find(profileId).orElse(null);
    }

    HarnessProfile baseline() {
        return registry.defaultProfile();
    }

    HarnessRouteDecision decisionFor(HarnessRoutingContext context, HarnessProfile profile,
                                     String reason, boolean fallback) {
        return decision(context, profile, reason, fallback);
    }

    private static HarnessRouteDecision decision(HarnessRoutingContext context, HarnessProfile profile,
                                                 String reason, boolean fallback) {
        String routeFingerprint = HarnessProfile.hash("harness-route-v1",
                context.domain() == null ? "<none>" : context.domain(),
                context.capabilityTag() == null ? "<unknown>" : context.capabilityTag().name(),
                profile.fingerprint(), reason, Boolean.toString(fallback));
        return new HarnessRouteDecision(profile, reason, fallback, routeFingerprint);
    }
}
