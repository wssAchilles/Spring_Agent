package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict W-window frequency automaton for locally dense repeated calls. */
public final class LocallyDenseCycleAutomaton {
    public record Inspection(String fingerprint, int frequency, boolean rejected,
                             List<String> window, Map<String, Integer> counts) {
        public Inspection {
            window = List.copyOf(window);
            counts = Map.copyOf(counts);
        }
    }

    private final int windowSize;
    private final int threshold;
    private final Deque<String> window = new ArrayDeque<>();
    private final Map<String, Integer> counts = new HashMap<>();

    public LocallyDenseCycleAutomaton(int windowSize, int threshold) {
        if (windowSize < 1 || threshold < 2 || threshold > windowSize) {
            throw new IllegalArgumentException("Require W >= K >= 2");
        }
        this.windowSize = windowSize;
        this.threshold = threshold;
    }

    public synchronized Inspection inspect(String toolName, String canonicalArguments) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        String fingerprint = sha256(toolName + "\u001f" + Objects.requireNonNull(canonicalArguments, "canonicalArguments"));
        if (window.size() == windowSize) {
            String expired = window.removeFirst();
            counts.computeIfPresent(expired, (key, count) -> count == 1 ? null : count - 1);
        }
        window.addLast(fingerprint);
        int frequency = counts.merge(fingerprint, 1, Integer::sum);
        return new Inspection(fingerprint, frequency, frequency >= threshold,
                List.copyOf(window), Map.copyOf(counts));
    }

    public synchronized Inspection state() {
        return new Inspection("", 0, false, List.copyOf(window), Map.copyOf(counts));
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
