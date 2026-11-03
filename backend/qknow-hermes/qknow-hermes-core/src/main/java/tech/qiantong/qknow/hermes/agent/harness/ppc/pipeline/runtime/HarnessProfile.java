package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;

/** A versioned, immutable entry in the runtime routing whitelist. */
public record HarnessProfile(String profileId, String version, String domainScope,
                             CapabilityTag capabilityTag, BoundedOperatorPipeline pipeline,
                             boolean approved, String source) {
    public HarnessProfile {
        requireText(profileId, "profileId");
        requireText(version, "version");
        requireText(domainScope, "domainScope");
        requireText(source, "source");
        if ("baseline".equals(profileId)) {
            if (capabilityTag != null || pipeline != null || !approved) {
                throw new IllegalArgumentException("baseline must be approved and have no mutation");
            }
        } else if (capabilityTag == null || pipeline == null) {
            throw new IllegalArgumentException("non-baseline profile requires a tag and pipeline");
        }
    }

    public String fingerprint() {
        return hash("harness-profile-v1", profileId, version, domainScope,
                capabilityTag == null ? "<any>" : capabilityTag.name(),
                pipeline == null ? "<none>" : pipeline.fingerprint(),
                Boolean.toString(approved), source);
    }

    static String hash(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) {
            Objects.requireNonNull(value, "fingerprint field");
            canonical.append(value.length()).append(':').append(value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
