package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.time.Instant;
import java.util.Objects;

public record HarnessSessionBinding(HarnessSessionKey key, String profileId, String profileVersion,
                                    String profileFingerprint, Instant boundAt, Instant expiresAt) {
    public HarnessSessionBinding {
        Objects.requireNonNull(key, "key");
        requireText(profileId, "profileId");
        requireText(profileVersion, "profileVersion");
        requireText(profileFingerprint, "profileFingerprint");
        Objects.requireNonNull(boundAt, "boundAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(boundAt)) {
            throw new IllegalArgumentException("expiresAt must be after boundAt");
        }
    }

    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
