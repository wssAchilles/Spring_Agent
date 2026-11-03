package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;

/** Immutable registry. Approval is declared in code, never written by the router. */
public final class HarnessProfileRegistry {
    public static final String DEFAULT_DOMAIN = "DEFAULT";
    private static final String BUILTIN_SOURCE = "STATIC_WHITELIST";

    private final Map<String, HarnessProfile> profiles;
    private final HarnessProfile baseline;

    public HarnessProfileRegistry(List<HarnessProfile> entries) {
        Map<String, HarnessProfile> byId = new LinkedHashMap<>();
        Set<String> fingerprints = new HashSet<>();
        for (HarnessProfile profile : Objects.requireNonNull(entries, "entries")) {
            Objects.requireNonNull(profile, "profile");
            if (!fingerprints.add(profile.fingerprint())) {
                throw new IllegalArgumentException("duplicate profile fingerprint");
            }
            if (byId.putIfAbsent(profile.profileId(), profile) != null) {
                throw new IllegalArgumentException("duplicate profile id: " + profile.profileId());
            }
        }
        this.baseline = byId.get("baseline");
        if (baseline == null) {
            throw new IllegalArgumentException("baseline profile is required");
        }
        this.profiles = Collections.unmodifiableMap(byId);
    }

    public static HarnessProfileRegistry defaults() {
        return new HarnessProfileRegistry(List.of(
                new HarnessProfile("baseline", "1", DEFAULT_DOMAIN, null, null, true, BUILTIN_SOURCE),
                new HarnessProfile("prompt-profile", "1", DEFAULT_DOMAIN, CapabilityTag.PROMPT_ONLY,
                        new BoundedOperatorPipeline(List.of(Operator.CONCISE_SYSTEM_PROMPT)), true, BUILTIN_SOURCE),
                new HarnessProfile("history-profile", "1", DEFAULT_DOMAIN, CapabilityTag.HISTORY,
                        new BoundedOperatorPipeline(List.of(Operator.ADAPTIVE_MEMORY_WINDOW)), true, BUILTIN_SOURCE),
                new HarnessProfile("tool-profile", "1", DEFAULT_DOMAIN, CapabilityTag.TOOL,
                        new BoundedOperatorPipeline(List.of(Operator.TOOL_GUIDANCE_SYNTHESIS)), true, BUILTIN_SOURCE)));
    }

    public HarnessProfile defaultProfile() {
        return baseline;
    }

    public Optional<HarnessProfile> findApproved(String profileId) {
        HarnessProfile profile = profiles.get(profileId);
        return profile != null && profile.approved() ? Optional.of(profile) : Optional.empty();
    }

    public List<HarnessProfile> listApproved() {
        return profiles.values().stream().filter(HarnessProfile::approved).toList();
    }

    Optional<HarnessProfile> find(String profileId) {
        return Optional.ofNullable(profiles.get(profileId));
    }
}
