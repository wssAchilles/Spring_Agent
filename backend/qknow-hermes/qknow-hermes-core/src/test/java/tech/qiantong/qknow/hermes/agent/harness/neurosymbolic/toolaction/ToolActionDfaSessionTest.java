package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActionDfaSessionTest {
    @Test
    void checkIsReadOnlyAndOnlySuccessfulCommitAdvances() {
        ToolActionDfaSpec spec = ToolActionDfaRegistry.defaults()
                .findApproved("fixture-read-then-write").orElseThrow();
        ToolActionDfaSession session = new ToolActionDfaSession("request", spec);
        ToolActionDfaState start = session.currentState();
        ToolActionDfaDecision decision = session.check("fixture.read");

        assertTrue(decision.allowed());
        assertEquals(start, session.currentState());
        ToolCallIdentity identity = session.identity("call-1");
        assertEquals(ToolActionDfaSession.ReservationStatus.RESERVED,
                session.reserve(identity, "fixture.read", "{}").status());
        assertTrue(session.commitSuccess(identity, start, "fixture.read"));
        session.complete(identity, ToolCallResponse.of("call-1", "fixture.read", "ok"),
                receipt(spec, identity, "START", "READ", true, false, true, ""));
        assertEquals(new ToolActionDfaState("READ"), session.currentState());

        ToolActionDfaDecision illegal = session.check("fixture.read");
        assertFalse(illegal.allowed());
        assertEquals(new ToolActionDfaState("READ"), session.currentState());
    }

    @Test
    void identitiesAreRequestLocalAndReservationsDetectReplayAndPayloadConflict() {
        ToolActionDfaSpec spec = ToolActionDfaRegistry.defaults()
                .findApproved("fixture-read-then-write").orElseThrow();
        ToolActionDfaSession first = new ToolActionDfaSession("request-1", spec);
        ToolActionDfaSession second = new ToolActionDfaSession("request-2", spec);
        ToolCallIdentity stable = first.identity("model-call");

        assertTrue(stable.frameworkProvided());
        assertEquals(ToolActionDfaSession.ReservationStatus.RESERVED,
                first.reserve(stable, "fixture.read", "{\"x\":1}").status());
        assertEquals(ToolActionDfaSession.ReservationStatus.IN_PROGRESS,
                first.reserve(stable, "fixture.read", "{\"x\":1}").status());
        assertEquals(ToolActionDfaSession.ReservationStatus.CONFLICT,
                first.reserve(stable, "fixture.read", "{\"x\":2}").status());
        assertEquals("ordinal:1", second.identity(null).value());
        assertEquals(new ToolActionDfaState("START"), second.currentState());

        first.complete(stable, ToolCallResponse.of("model-call", "fixture.read", "ok"),
                receipt(spec, stable, "START", "READ", true, false, true, ""));
        ToolActionDfaSession.Reservation replay = first.reserve(stable, "fixture.read", "{\"x\":1}");
        assertEquals(ToolActionDfaSession.ReservationStatus.COMPLETED, replay.status());
        assertEquals("ok", replay.cachedResponse().getResult());
        assertTrue(replay.cachedReceipt().advanced());
    }

    @Test
    void registryRejectsDuplicateActiveRequestAndCleansUp() {
        ToolActionDfaSessionRegistry registry = new ToolActionDfaSessionRegistry(1);
        ToolActionDfaSpec spec = ToolActionDfaRegistry.defaults()
                .findApproved("fixture-read-then-write").orElseThrow();
        ToolActionDfaSessionRegistry.OpenResult opened = registry.open("same-request", spec);
        assertTrue(opened.available());
        assertEquals("REQUEST_ALREADY_ACTIVE", registry.open("same-request", spec).failureCode());
        assertEquals("SESSION_CAPACITY_EXCEEDED", registry.open("other-request", spec).failureCode());
        assertTrue(registry.close(opened.session()));
        assertEquals(0, registry.size());
    }

    private static ToolActionDfaReceipt receipt(ToolActionDfaSpec spec, ToolCallIdentity identity,
                                                 String from, String to, boolean allowed,
                                                 boolean blocked, boolean advanced, String failureCode) {
        return new ToolActionDfaReceipt("request", "session", identity.value(), spec.specId(), spec.version(),
                spec.fingerprint(), from, to, "fixture.read", ToolActionDfaMode.ENFORCE,
                allowed, blocked, advanced, ToolActionDfaReason.DFA_ALLOWED, failureCode);
    }
}
