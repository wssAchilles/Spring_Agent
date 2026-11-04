package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.Objects;

public final class HarnessProfileLifecycle {
    public enum State {
        ACTIVE_SELECTABLE,
        DEPRECATED_EXISTING_ONLY,
        RETIRED,
        UNAPPROVED
    }

    public State evaluate(HarnessProfile profile, boolean existingBinding) {
        Objects.requireNonNull(profile, "profile");
        if (!profile.approved()) {
            return State.UNAPPROVED;
        }
        return switch (profile.status()) {
            case ACTIVE -> State.ACTIVE_SELECTABLE;
            case DEPRECATED -> State.DEPRECATED_EXISTING_ONLY;
            case RETIRED -> State.RETIRED;
        };
    }

    public boolean selectableForNew(HarnessProfile profile) {
        return evaluate(profile, false) == State.ACTIVE_SELECTABLE;
    }

    public boolean usableByExisting(HarnessProfile profile) {
        State state = evaluate(profile, true);
        return state == State.ACTIVE_SELECTABLE || state == State.DEPRECATED_EXISTING_ONLY;
    }
}
