package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;

import static org.junit.jupiter.api.Assertions.*;

class HarnessProfileLifecycleTest {
    private static final BoundedOperatorPipeline PIPELINE = new BoundedOperatorPipeline(
            List.of(Operator.ADAPTIVE_MEMORY_WINDOW));
    private final HarnessProfileLifecycle lifecycle = new HarnessProfileLifecycle();

    @Test
    void activeIsSelectableAndCanServeExistingBindings() {
        HarnessProfile active = profile("1", true, HarnessProfileStatus.ACTIVE);
        assertEquals(HarnessProfileLifecycle.State.ACTIVE_SELECTABLE, lifecycle.evaluate(active, false));
        assertTrue(lifecycle.selectableForNew(active));
        assertTrue(lifecycle.usableByExisting(active));
    }

    @Test
    void deprecatedIsExistingOnlyAndRetiredIsNeverUsable() {
        HarnessProfile deprecated = profile("1", true, HarnessProfileStatus.DEPRECATED);
        HarnessProfile retired = profile("1", true, HarnessProfileStatus.RETIRED);
        assertEquals(HarnessProfileLifecycle.State.DEPRECATED_EXISTING_ONLY,
                lifecycle.evaluate(deprecated, true));
        assertFalse(lifecycle.selectableForNew(deprecated));
        assertTrue(lifecycle.usableByExisting(deprecated));
        assertEquals(HarnessProfileLifecycle.State.RETIRED, lifecycle.evaluate(retired, true));
        assertFalse(lifecycle.selectableForNew(retired));
        assertFalse(lifecycle.usableByExisting(retired));
    }

    @Test
    void unapprovedIsNeverUsableEvenWhenActive() {
        HarnessProfile unapproved = profile("1", false, HarnessProfileStatus.ACTIVE);
        assertEquals(HarnessProfileLifecycle.State.UNAPPROVED, lifecycle.evaluate(unapproved, true));
        assertFalse(lifecycle.selectableForNew(unapproved));
        assertFalse(lifecycle.usableByExisting(unapproved));
    }

    @Test
    void statusAndVersionChangeIdentityWithoutChangingPipeline() {
        HarnessProfile active = profile("1", true, HarnessProfileStatus.ACTIVE);
        assertEquals(active.fingerprint(), profile("1", true, HarnessProfileStatus.ACTIVE).fingerprint());
        assertNotEquals(active.fingerprint(), profile("2", true, HarnessProfileStatus.ACTIVE).fingerprint());
        assertNotEquals(active.fingerprint(), profile("1", true, HarnessProfileStatus.DEPRECATED).fingerprint());
        assertNotEquals(active.fingerprint(), profile("1", true, HarnessProfileStatus.RETIRED).fingerprint());
        assertEquals(64, active.fingerprint().length());
    }

    @Test
    void baselineMustRemainApprovedActiveAndUnmutated() {
        HarnessProfile baseline = HarnessProfileRegistry.defaults().defaultProfile();
        assertEquals(HarnessProfileStatus.ACTIVE, baseline.status());
        assertTrue(baseline.approved());
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessProfile("baseline", "1", "DEFAULT", null, null, true,
                        "TEST", HarnessProfileStatus.DEPRECATED));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessProfile("baseline", "1", "DEFAULT", null, null, false,
                        "TEST", HarnessProfileStatus.ACTIVE));
    }

    private static HarnessProfile profile(String version, boolean approved, HarnessProfileStatus status) {
        return new HarnessProfile("history-profile", version, "DEFAULT", CapabilityTag.HISTORY,
                PIPELINE, approved, "TEST", status);
    }
}
