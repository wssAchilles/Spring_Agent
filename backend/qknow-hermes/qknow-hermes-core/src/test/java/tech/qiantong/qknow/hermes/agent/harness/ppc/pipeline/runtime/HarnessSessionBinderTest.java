package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;

import static org.junit.jupiter.api.Assertions.*;

class HarnessSessionBinderTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void keysRequireSessionCapabilityAndDomain() {
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSessionKey("", CapabilityTag.HISTORY, "DEFAULT"));
        assertThrows(NullPointerException.class,
                () -> new HarnessSessionKey("s", null, "DEFAULT"));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessSessionKey("s", CapabilityTag.HISTORY, " "));
        assertNotEquals(key("s", CapabilityTag.HISTORY), key("s", CapabilityTag.TOOL));
        assertNotEquals(key("s", CapabilityTag.HISTORY), key("other", CapabilityTag.HISTORY));
        assertNotEquals(key("s", CapabilityTag.HISTORY),
                new HarnessSessionKey("s", CapabilityTag.HISTORY, "OTHER"));
    }

    @Test
    void bindingKeepsFirstProfileForTheSameKeyAndSeparatesOtherKeys() {
        HarnessSessionBinder binder = new HarnessSessionBinder(Clock.fixed(START, ZoneOffset.UTC),
                Duration.ofMinutes(30), 4);
        HarnessSessionKey history = key("session", CapabilityTag.HISTORY);
        HarnessProfile first = HarnessProfileRegistry.defaults().findApproved("history-profile").orElseThrow();
        HarnessProfile second = HarnessProfileRegistry.defaults().findApproved("tool-profile").orElseThrow();

        assertTrue(binder.bind(history, first).created());
        assertFalse(binder.bind(history, second).created());
        assertEquals(first.fingerprint(), binder.lookup(history).binding().orElseThrow().profileFingerprint());
        assertTrue(binder.bind(key("session", CapabilityTag.TOOL), second).created());
        assertTrue(binder.bind(key("other", CapabilityTag.HISTORY), first).created());
        assertEquals(3, binder.size());
        assertEquals(second.profileId(), binder.lookup(key("session", CapabilityTag.TOOL))
                .binding().orElseThrow().profileId());
    }

    @Test
    void ttlBoundaryIsExpiredAndCleanupRemovesOnlyExpiredBindings() {
        MutableClock clock = new MutableClock(START);
        HarnessSessionBinder binder = new HarnessSessionBinder(clock, Duration.ofMinutes(30), 3);
        HarnessProfile profile = HarnessProfileRegistry.defaults().findApproved("history-profile").orElseThrow();
        HarnessSessionKey old = key("old", CapabilityTag.HISTORY);
        HarnessSessionKey fresh = key("fresh", CapabilityTag.HISTORY);

        HarnessSessionBinding first = binder.bind(old, profile).binding();
        assertEquals(START, first.boundAt());
        assertEquals(START.plus(Duration.ofMinutes(30)), first.expiresAt());
        clock.advance(Duration.ofMinutes(29));
        binder.bind(fresh, profile);
        assertFalse(binder.lookup(old).expired());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(binder.lookup(old).expired());
        assertEquals(1, binder.cleanupExpired());
        assertTrue(binder.lookup(old).binding().isEmpty());
        assertTrue(binder.lookup(fresh).binding().isPresent());
        assertTrue(binder.bind(old, profile).created());
        assertFalse(binder.bind(old, profile).expired());
    }

    @Test
    void capacityEvictsOldestBindingAndNeverExceedsLimit() {
        MutableClock clock = new MutableClock(START);
        HarnessSessionBinder binder = new HarnessSessionBinder(clock, Duration.ofMinutes(30), 2);
        HarnessProfile profile = HarnessProfileRegistry.defaults().findApproved("history-profile").orElseThrow();
        HarnessSessionKey first = key("first", CapabilityTag.HISTORY);
        HarnessSessionKey second = key("second", CapabilityTag.HISTORY);
        HarnessSessionKey third = key("third", CapabilityTag.HISTORY);

        binder.bind(first, profile);
        clock.advance(Duration.ofSeconds(1));
        binder.bind(second, profile);
        clock.advance(Duration.ofSeconds(1));
        binder.bind(third, profile);

        assertEquals(2, binder.size());
        assertTrue(binder.lookup(first).binding().isEmpty());
        assertTrue(binder.lookup(second).binding().isPresent());
        assertTrue(binder.lookup(third).binding().isPresent());
        assertTrue(binder.invalidate(second));
        assertFalse(binder.invalidate(second));
    }

    @Test
    void concurrentBindForOneKeyPinsExactlyOneProfile() throws Exception {
        HarnessSessionBinder binder = new HarnessSessionBinder(Clock.fixed(START, ZoneOffset.UTC),
                Duration.ofMinutes(30), 4);
        HarnessSessionKey key = key("same", CapabilityTag.HISTORY);
        HarnessProfile first = HarnessProfileRegistry.defaults().findApproved("history-profile").orElseThrow();
        HarnessProfile second = HarnessProfileRegistry.defaults().findApproved("tool-profile").orElseThrow();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(16)) {
            List<Future<HarnessSessionBinder.BindResult>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                HarnessProfile candidate = i % 2 == 0 ? first : second;
                futures.add(executor.submit(() -> {
                    start.await();
                    return binder.bind(key, candidate);
                }));
            }
            start.countDown();
            String pinned = futures.getFirst().get(5, TimeUnit.SECONDS).binding().profileFingerprint();
            int created = 0;
            for (Future<HarnessSessionBinder.BindResult> future : futures) {
                HarnessSessionBinder.BindResult result = future.get(5, TimeUnit.SECONDS);
                assertEquals(pinned, result.binding().profileFingerprint());
                if (result.created()) {
                    created++;
                }
            }
            assertEquals(1, created);
            assertEquals(1, binder.size());
        }
    }

    private static HarnessSessionKey key(String session, CapabilityTag capability) {
        return new HarnessSessionKey(session, capability, HarnessProfileRegistry.DEFAULT_DOMAIN);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
