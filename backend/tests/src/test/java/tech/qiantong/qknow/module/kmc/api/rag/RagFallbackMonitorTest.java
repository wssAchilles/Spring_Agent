package tech.qiantong.qknow.module.kmc.api.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RagFallbackMonitor 测试")
class RagFallbackMonitorTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RagFallbackMonitor.reset();
    }

    @Test
    @DisplayName("record 记录组件降级次数和最近原因")
    void record_updatesSnapshot() {
        RagFallbackMonitor.record("Vector Store", "empty_results", "connection timeout");
        RagFallbackMonitor.record("Vector Store", "empty_results", "read failed");

        Map<String, Object> snapshot = RagFallbackMonitor.snapshot();

        assertTrue(snapshot.containsKey("vector_store"));
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) snapshot.get("vector_store");
        assertEquals(2L, state.get("count"));
        assertEquals("empty_results", state.get("lastFallback"));
        assertEquals("read failed", state.get("lastReason"));
        assertNotNull(state.get("lastAt"));
    }

    @Test
    @DisplayName("diffSince 只返回基线后的新增降级")
    void diffSince_returnsOnlyNewFallbacks() {
        RagFallbackMonitor.record("jni", "old", "before request");
        Map<String, Object> baseline = RagFallbackMonitor.snapshot();

        RagFallbackMonitor.record("jni", "new", "during request");
        RagFallbackMonitor.record("neo4j", "metadata_sql", "during request");

        Map<String, Object> diff = RagFallbackMonitor.diffSince(baseline);

        assertEquals(2, diff.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> jniState = (Map<String, Object>) diff.get("jni");
        assertEquals(1L, jniState.get("count"));
        assertEquals("new", jniState.get("lastFallback"));
        @SuppressWarnings("unchecked")
        Map<String, Object> neo4jState = (Map<String, Object>) diff.get("neo4j");
        assertEquals(1L, neo4jState.get("count"));
    }

    @Test
    @DisplayName("request scope 只收集绑定请求内的降级")
    void scope_collectsOnlyBoundRequestFallbacks() throws Exception {
        RagFallbackMonitor.record("jni", "historical", "outside scope");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (RagFallbackMonitor.Scope scope = RagFallbackMonitor.openScope()) {
            RagFallbackMonitor.record("vector", "empty_results", "inside caller");
            Future<?> task = executor.submit(() -> {
                try (RagFallbackMonitor.ScopeBinding ignored = RagFallbackMonitor.bindScope(scope)) {
                    RagFallbackMonitor.record("neo4j", "metadata_sql", "inside worker");
                }
            });
            task.get(3, TimeUnit.SECONDS);

            Map<String, Object> scoped = RagFallbackMonitor.currentScopeSnapshot();

            assertFalse(scoped.containsKey("jni"));
            assertTrue(scoped.containsKey("vector"));
            assertTrue(scoped.containsKey("neo4j"));
        } finally {
            executor.shutdownNow();
        }
    }
}
