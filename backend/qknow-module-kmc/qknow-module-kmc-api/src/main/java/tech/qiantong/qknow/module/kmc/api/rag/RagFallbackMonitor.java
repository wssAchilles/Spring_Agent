package tech.qiantong.qknow.module.kmc.api.rag;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-process RAG fallback telemetry.
 */
public final class RagFallbackMonitor {

    private static final ConcurrentHashMap<String, FallbackState> STATES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Scope> CURRENT_SCOPE = new ThreadLocal<>();

    private RagFallbackMonitor() {
    }

    public static void record(String component, String fallback, String reason) {
        String key = normalize(component);
        String safeFallback = fallback != null ? fallback : "";
        String safeReason = sanitize(reason);
        STATES.computeIfAbsent(key, ignored -> new FallbackState())
                .record(safeFallback, safeReason);
        Scope scope = CURRENT_SCOPE.get();
        if (scope != null) {
            scope.record(key, safeFallback, safeReason);
        }
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        STATES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue().snapshot()));
        return result;
    }

    public static Map<String, Object> diffSince(Map<String, Object> baseline) {
        Map<String, Object> result = new LinkedHashMap<>();
        STATES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    long before = countOf(baseline != null ? baseline.get(entry.getKey()) : null);
                    long delta = entry.getValue().count.get() - before;
                    if (delta > 0) {
                        Map<String, Object> state = entry.getValue().snapshot();
                        state.put("count", delta);
                        result.put(entry.getKey(), state);
                    }
                });
        return result;
    }

    public static Scope openScope() {
        Scope scope = new Scope(CURRENT_SCOPE.get());
        CURRENT_SCOPE.set(scope);
        return scope;
    }

    public static Scope currentScope() {
        return CURRENT_SCOPE.get();
    }

    public static ScopeBinding bindScope(Scope scope) {
        Scope previous = CURRENT_SCOPE.get();
        CURRENT_SCOPE.set(scope);
        return new ScopeBinding(previous);
    }

    public static Map<String, Object> currentScopeSnapshot() {
        Scope scope = CURRENT_SCOPE.get();
        return scope != null ? scope.snapshot() : Map.of();
    }

    public static void reset() {
        STATES.clear();
        CURRENT_SCOPE.remove();
    }

    private static String normalize(String component) {
        if (component == null || component.isBlank()) {
            return "unknown";
        }
        return component.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String sanitize(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String normalized = reason.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private static long countOf(Object state) {
        if (state instanceof Map<?, ?> data) {
            Object count = data.get("count");
            if (count instanceof Number number) {
                return number.longValue();
            }
            if (count instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final ConcurrentHashMap<String, FallbackState> states = new ConcurrentHashMap<>();
        private boolean closed;

        private Scope(Scope previous) {
            this.previous = previous;
        }

        private void record(String component, String fallback, String reason) {
            states.computeIfAbsent(component, ignored -> new FallbackState()).record(fallback, reason);
        }

        private Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            states.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue().snapshot()));
            return result;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous != null) {
                CURRENT_SCOPE.set(previous);
            } else {
                CURRENT_SCOPE.remove();
            }
        }
    }

    public static final class ScopeBinding implements AutoCloseable {
        private final Scope previous;
        private boolean closed;

        private ScopeBinding(Scope previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous != null) {
                CURRENT_SCOPE.set(previous);
            } else {
                CURRENT_SCOPE.remove();
            }
        }
    }

    private static final class FallbackState {
        private final AtomicLong count = new AtomicLong();
        private volatile String lastFallback = "";
        private volatile String lastReason = "";
        private volatile Instant lastAt;

        private void record(String fallback, String reason) {
            count.incrementAndGet();
            lastFallback = fallback;
            lastReason = reason;
            lastAt = Instant.now();
        }

        private Map<String, Object> snapshot() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", count.get());
            data.put("lastFallback", lastFallback);
            data.put("lastReason", lastReason);
            data.put("lastAt", lastAt != null ? lastAt.toString() : null);
            return data;
        }
    }
}
