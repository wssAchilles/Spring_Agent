package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ToolActionDfaSpec {
    public static final int MAX_STATES = 64;
    public static final int MAX_TRANSITIONS = 256;

    private final String specId;
    private final String version;
    private final ToolActionDfaState initialState;
    private final Set<ToolActionDfaState> acceptingStates;
    private final Set<ToolActionDfaState> states;
    private final List<ToolActionDfaTransition> transitions;
    private final boolean approved;
    private final String fingerprint;

    public ToolActionDfaSpec(String specId, String version, ToolActionDfaState initialState,
                             Set<ToolActionDfaState> acceptingStates,
                             Set<ToolActionDfaState> states,
                             List<ToolActionDfaTransition> transitions,
                             boolean approved) {
        this.specId = required(specId, "specId");
        this.version = required(version, "version");
        this.initialState = Objects.requireNonNull(initialState, "initialState");
        this.acceptingStates = Set.copyOf(Objects.requireNonNull(acceptingStates, "acceptingStates"));
        this.states = Set.copyOf(Objects.requireNonNull(states, "states"));
        this.transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
        this.approved = approved;
        validate();
        this.fingerprint = computeFingerprint();
    }

    public String specId() { return specId; }
    public String version() { return version; }
    public ToolActionDfaState initialState() { return initialState; }
    public Set<ToolActionDfaState> acceptingStates() { return acceptingStates; }
    public Set<ToolActionDfaState> states() { return states; }
    public List<ToolActionDfaTransition> transitions() { return transitions; }
    public boolean approved() { return approved; }
    public String fingerprint() { return fingerprint; }

    ToolActionDfaState target(ToolActionDfaState from, String toolName) {
        return transitions.stream()
                .filter(t -> t.fromState().equals(from) && t.toolName().equals(toolName))
                .map(ToolActionDfaTransition::toState)
                .findFirst().orElse(null);
    }

    boolean knowsTool(String toolName) {
        return transitions.stream().anyMatch(t -> t.toolName().equals(toolName));
    }

    private void validate() {
        if (states.isEmpty() || states.size() > MAX_STATES || transitions.size() > MAX_TRANSITIONS) {
            throw new IllegalArgumentException("DFA requires 1..64 states and at most 256 transitions");
        }
        if (!states.contains(initialState) || !states.containsAll(acceptingStates)) {
            throw new IllegalArgumentException("Initial and accepting states must belong to states");
        }
        Set<TransitionKey> edges = new HashSet<>();
        for (ToolActionDfaTransition transition : transitions) {
            if (!states.contains(transition.fromState()) || !states.contains(transition.toState())) {
                throw new IllegalArgumentException("Transition endpoints must belong to states");
            }
            if (!edges.add(new TransitionKey(transition.fromState(), transition.toolName()))) {
                throw new IllegalArgumentException("DFA transition is nondeterministic");
            }
        }
    }

    private String computeFingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, specId);
                write(output, version);
                write(output, initialState.value());
                output.writeBoolean(approved);
                sortedStates(states).forEach(state -> writeUnchecked(output, state.value()));
                output.writeInt(-1);
                sortedStates(acceptingStates).forEach(state -> writeUnchecked(output, state.value()));
                output.writeInt(-1);
                transitions.stream().sorted(Comparator
                                .comparing((ToolActionDfaTransition t) -> t.fromState().value())
                                .thenComparing(ToolActionDfaTransition::toolName)
                                .thenComparing(t -> t.toState().value()))
                        .forEach(t -> {
                            writeUnchecked(output, t.fromState().value());
                            writeUnchecked(output, t.toolName());
                            writeUnchecked(output, t.toState().value());
                        });
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to fingerprint DFA spec", e);
        }
    }

    private static List<ToolActionDfaState> sortedStates(Set<ToolActionDfaState> values) {
        return values.stream().sorted(Comparator.comparing(ToolActionDfaState::value)).toList();
    }

    private static void writeUnchecked(DataOutputStream output, String value) {
        try {
            write(output, value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(data.length);
        output.write(data);
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private record TransitionKey(ToolActionDfaState fromState, String toolName) { }
}
