package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActionDfaSpecTest {
    @Test
    void fingerprintIgnoresInputCollectionOrderAndChangesWithSemantics() {
        ToolActionDfaSpec first = spec(List.of(edge("START", "read", "READ"), edge("READ", "write", "DONE")),
                new LinkedHashSet<>(List.of(state("DONE"), state("READ"), state("START"))));
        ToolActionDfaSpec reordered = spec(List.of(edge("READ", "write", "DONE"), edge("START", "read", "READ")),
                new LinkedHashSet<>(List.of(state("START"), state("READ"), state("DONE"))));
        ToolActionDfaSpec changed = spec(List.of(edge("START", "read", "DONE")),
                new LinkedHashSet<>(List.of(state("START"), state("READ"), state("DONE"))));

        assertEquals(first.fingerprint(), reordered.fingerprint());
        assertNotEquals(first.fingerprint(), changed.fingerprint());
        assertEquals(64, first.fingerprint().length());
    }

    @Test
    void rejectsInvalidEndpointsAndNondeterministicTransitions() {
        assertThrows(IllegalArgumentException.class, () -> new ToolActionDfaSpec("invalid", "1",
                state("MISSING"), Set.of(state("DONE")), Set.of(state("START"), state("DONE")),
                List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> spec(
                List.of(edge("START", "read", "READ"), edge("START", "read", "DONE")),
                new LinkedHashSet<>(List.of(state("START"), state("READ"), state("DONE")))));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolActionDfaTransition(state("START"), " ", state("DONE")));
    }

    @Test
    void transitionIdentityUsesStructuredStateAndToolPair() {
        ToolActionDfaSpec spec = new ToolActionDfaSpec("nul-pairs", "1", state("A"),
                Set.of(state("DONE"), state("OTHER")),
                Set.of(state("A"), state("A\u0000B"), state("DONE"), state("OTHER")),
                List.of(edge("A", "B\u0000C", "OTHER"), edge("A\u0000B", "C", "DONE")), true);

        assertEquals(state("OTHER"), spec.target(state("A"), "B\u0000C"));
        assertEquals(state("DONE"), spec.target(state("A\u0000B"), "C"));
    }

    @Test
    void enforcesStateAndTransitionLimitsAndApprovedLookup() {
        Set<ToolActionDfaState> tooMany = new LinkedHashSet<>();
        for (int i = 0; i <= ToolActionDfaSpec.MAX_STATES; i++) {
            tooMany.add(state("S" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> new ToolActionDfaSpec("wide", "1", state("S0"),
                Set.of(state("S0")), tooMany, List.of(), true));

        ToolActionDfaSpec unapproved = new ToolActionDfaSpec("unapproved", "1", state("START"),
                Set.of(state("DONE")), Set.of(state("START"), state("DONE")), List.of(), false);
        ToolActionDfaRegistry registry = new ToolActionDfaRegistry(List.of(unapproved));
        assertTrue(registry.findApproved("unapproved").isEmpty());
        assertTrue(ToolActionDfaRegistry.defaults().findApproved("fixture-read-then-write").isPresent());
    }

    private static ToolActionDfaSpec spec(List<ToolActionDfaTransition> transitions,
                                          Set<ToolActionDfaState> states) {
        return new ToolActionDfaSpec("spec", "1", state("START"), Set.of(state("DONE")), states,
                transitions, true);
    }

    private static ToolActionDfaTransition edge(String from, String tool, String to) {
        return new ToolActionDfaTransition(state(from), tool, state(to));
    }

    private static ToolActionDfaState state(String value) {
        return new ToolActionDfaState(value);
    }
}
