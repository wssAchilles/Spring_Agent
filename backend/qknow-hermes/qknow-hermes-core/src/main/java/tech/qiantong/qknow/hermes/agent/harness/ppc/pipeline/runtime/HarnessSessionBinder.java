package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** Bounded, in-memory session pins; the lock makes selection and eviction atomic. */
public final class HarnessSessionBinder {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    public static final int DEFAULT_MAX_BINDINGS = 1024;

    private final Map<HarnessSessionKey, HarnessSessionBinding> bindings = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Clock clock;
    private final Duration ttl;
    private final int maxBindings;

    public HarnessSessionBinder() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_BINDINGS);
    }

    public HarnessSessionBinder(Clock clock, Duration ttl, int maxBindings) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative() || maxBindings < 1) {
            throw new IllegalArgumentException("positive ttl and maxBindings are required");
        }
        this.maxBindings = maxBindings;
    }

    public Lookup lookup(HarnessSessionKey key) {
        Objects.requireNonNull(key, "key");
        lock.lock();
        try {
            HarnessSessionBinding binding = bindings.get(key);
            return new Lookup(Optional.ofNullable(binding), binding != null && binding.expiredAt(clock.instant()));
        } finally {
            lock.unlock();
        }
    }

    public BindResult bind(HarnessSessionKey key, HarnessProfile profile) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(profile, "profile");
        lock.lock();
        try {
            Instant now = clock.instant();
            HarnessSessionBinding current = bindings.get(key);
            if (current != null && !current.expiredAt(now)) {
                return new BindResult(current, false, false);
            }
            HarnessSessionBinding next = put(key, profile, now);
            return new BindResult(next, true, current != null);
        } finally {
            lock.unlock();
        }
    }

    public Resolution resolve(HarnessSessionKey key, HarnessProfile candidate,
                              Function<String, HarnessProfile> currentProfile) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(currentProfile, "currentProfile");
        lock.lock();
        try {
            Instant now = clock.instant();
            HarnessSessionBinding previous = bindings.get(key);
            if (previous != null && !previous.expiredAt(now)) {
                HarnessProfile bound = currentProfile.apply(previous.profileId());
                if (bound != null && matches(previous, bound, key)
                        && bound.approved() && bound.status() != HarnessProfileStatus.RETIRED) {
                    return new Resolution(bound, HarnessBindingAction.REUSED,
                            bound.status() == HarnessProfileStatus.DEPRECATED
                                    ? "DEPRECATED_EXISTING_ONLY" : "SESSION_BINDING_REUSED", previous);
                }
                bindings.remove(key);
                String reason = bound != null && bound.status() == HarnessProfileStatus.RETIRED
                        ? "RETIRED" : bound != null && !bound.approved()
                        ? "UNAPPROVED" : "BINDING_INVALID";
                if (selectable(candidate, key)) {
                    HarnessSessionBinding next = put(key, candidate, now);
                    return new Resolution(candidate, HarnessBindingAction.REBOUND,
                            reason + "_REBOUND", next);
                }
                return new Resolution(null, HarnessBindingAction.FALLBACK, reason + "_FALLBACK", null);
            }
            boolean expired = previous != null;
            if (expired) {
                bindings.remove(key);
            }
            if (selectable(candidate, key)) {
                HarnessSessionBinding next = put(key, candidate, now);
                return new Resolution(candidate,
                        expired ? HarnessBindingAction.EXPIRED : HarnessBindingAction.PINNED,
                        expired ? "BINDING_EXPIRED" : "MATCHED_CAPABILITY", next);
            }
            return new Resolution(null, expired ? HarnessBindingAction.EXPIRED : HarnessBindingAction.FALLBACK,
                    expired ? "BINDING_EXPIRED_FALLBACK" : "NO_SELECTABLE_PROFILE", null);
        } finally {
            lock.unlock();
        }
    }

    public boolean invalidate(HarnessSessionKey key) {
        Objects.requireNonNull(key, "key");
        lock.lock();
        try {
            return bindings.remove(key) != null;
        } finally {
            lock.unlock();
        }
    }

    public boolean invalidate(HarnessSessionBinding binding) {
        Objects.requireNonNull(binding, "binding");
        lock.lock();
        try {
            return bindings.remove(binding.key(), binding);
        } finally {
            lock.unlock();
        }
    }

    public int cleanupExpired() {
        lock.lock();
        try {
            return cleanupExpired(clock.instant());
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return bindings.size();
        } finally {
            lock.unlock();
        }
    }

    private HarnessSessionBinding put(HarnessSessionKey key, HarnessProfile profile, Instant now) {
        cleanupExpired(now);
        if (!bindings.containsKey(key) && bindings.size() >= maxBindings) {
            Iterator<HarnessSessionKey> oldest = bindings.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        HarnessSessionBinding next = new HarnessSessionBinding(key, profile.profileId(), profile.version(),
                profile.fingerprint(), now, now.plus(ttl));
        bindings.put(key, next);
        return next;
    }

    private int cleanupExpired(Instant now) {
        int before = bindings.size();
        bindings.values().removeIf(binding -> binding.expiredAt(now));
        return before - bindings.size();
    }

    private static boolean selectable(HarnessProfile profile, HarnessSessionKey key) {
        return profile.approved() && profile.status() == HarnessProfileStatus.ACTIVE
                && profile.pipeline() != null && key.domain().equals(profile.domainScope())
                && key.capabilityTag() == profile.capabilityTag();
    }

    private static boolean matches(HarnessSessionBinding binding, HarnessProfile profile, HarnessSessionKey key) {
        if (!binding.profileVersion().equals(profile.version())
                || !key.domain().equals(profile.domainScope())
                || key.capabilityTag() != profile.capabilityTag()) {
            return false;
        }
        return binding.profileFingerprint().equals(profile.fingerprint())
                || profile.status() == HarnessProfileStatus.DEPRECATED
                && binding.profileFingerprint().equals(profile.withStatus(HarnessProfileStatus.ACTIVE).fingerprint());
    }

    public record Lookup(Optional<HarnessSessionBinding> binding, boolean expired) {
    }

    public record BindResult(HarnessSessionBinding binding, boolean created, boolean expired) {
    }

    public record Resolution(HarnessProfile profile, HarnessBindingAction action, String reason,
                             HarnessSessionBinding binding) {
    }
}
