package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActionDfaGuardTest {
    @Test
    void failureCommitLeavesStateUnchanged() {
        ToolActionDfaSession session = new ToolActionDfaSession("request",
                ToolActionDfaRegistry.defaults().findApproved("fixture-read-then-write").orElseThrow());
        ToolActionDfaGuard guard = new ToolActionDfaGuard(session);
        ToolCallIdentity identity = session.identity("call-1");
        session.reserve(identity, "fixture.read", "{}");
        ToolActionDfaState before = session.currentState();

        guard.commitFailure(identity, "tool error");
        assertFalse(guard.check("fixture.write").allowed());
        assertTrue(before.equals(session.currentState()));
    }
}
