package tech.qiantong.qknow.hermes.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TraceCollectorTest {

    private TraceCollector collector;

    @BeforeEach
    void setUp() {
        collector = new TraceCollector();
    }

    @Test
    void startAndEndSpanRecordsDuration() {
        TraceContext ctx = collector.startTrace("req-1");
        TraceSpan span = collector.startSpan("rag_retrieval");

        assertNotNull(span.getSpanId());
        assertEquals("rag_retrieval", span.getName());
        assertTrue(span.getStartTime() > 0);

        collector.endSpan(span.getSpanId(), "retrieved docs", 100, 0.05);

        TraceSpan ended = ctx.getSpan(span.getSpanId());
        assertTrue(ended.getEndTime() >= ended.getStartTime());
        assertTrue(ended.getDuration() >= 0);
        assertEquals("success", ended.getStatus());
        assertEquals("retrieved docs", ended.getOutput());
        assertEquals(100, ended.getTokenCount());
        assertEquals(0.05, ended.getCost(), 0.001);

        collector.endTrace();
    }

    @Test
    void nestedSpansParentChild() {
        TraceContext ctx = collector.startTrace("req-2");
        TraceSpan parent = collector.startSpan("agent_execution");

        TraceSpan child1 = collector.startSpan("rag_retrieval", parent);
        TraceSpan child2 = collector.startSpan("llm_generation", parent);

        collector.endSpan(child1.getSpanId(), "docs", "success", 50, 0.02);
        collector.endSpan(child2.getSpanId(), "answer", "success", 200, 0.10);
        collector.endSpan(parent.getSpanId(), "done", "success", 0, 0.0);

        assertEquals(2, parent.getChildren().size());
        assertEquals(child1.getSpanId(), parent.getChildren().get(0).getSpanId());
        assertEquals(child2.getSpanId(), parent.getChildren().get(1).getSpanId());

        collector.endTrace();
    }

    @Test
    void traceTreeStructureCorrect() {
        TraceContext ctx = collector.startTrace("req-3");

        TraceSpan root = collector.startSpan("root");
        TraceSpan childA = collector.startSpan("tool_call", root);
        TraceSpan childB = collector.startSpan("ai_judge", root);
        TraceSpan grandchild = collector.startSpan("sub_tool", childA);

        collector.endSpan(grandchild.getSpanId(), "sub result", "success", 10, 0.01);
        collector.endSpan(childA.getSpanId(), "tool result", "success", 20, 0.02);
        collector.endSpan(childB.getSpanId(), "judge result", "success", 30, 0.03);
        collector.endSpan(root.getSpanId(), "final", "success", 0, 0.0);

        assertEquals(2, root.getChildren().size());
        assertEquals(1, childA.getChildren().size());
        assertEquals(0, childB.getChildren().size());
        assertEquals(grandchild.getSpanId(), childA.getChildren().get(0).getSpanId());

        collector.endTrace();
    }

    @Test
    void concurrentTracesDoNotInterfere() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TraceContext ctx = collector.startTrace("req-" + idx);
                    TraceSpan span = collector.startSpan("op-" + idx);
                    collector.endSpan(span.getSpanId(), "result-" + idx, "success", idx * 10, idx * 0.01);
                    TraceContext result = collector.endTrace();

                    assertNotNull(result);
                    assertEquals("req-" + idx, result.getTraceId());
                    assertEquals(idx * 10, result.getTotalTokens());
                    assertEquals(idx * 0.01, result.getTotalCost(), 0.0001);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertNull(failure.get(), "Concurrent execution had failure: " + failure.get());
    }

    @Test
    void totalTokensAndCostAggregated() {
        TraceContext ctx = collector.startTrace("req-5");

        TraceSpan root = collector.startSpan("root");
        TraceSpan s1 = collector.startSpan("rag", root);
        TraceSpan s2 = collector.startSpan("llm", root);
        TraceSpan s3 = collector.startSpan("judge", root);

        collector.endSpan(s1.getSpanId(), "docs", "success", 100, 0.05);
        collector.endSpan(s2.getSpanId(), "answer", "success", 500, 0.25);
        collector.endSpan(s3.getSpanId(), "score", "success", 50, 0.02);
        collector.endSpan(root.getSpanId(), "done", "success", 0, 0.0);

        TraceContext result = collector.endTrace();

        assertNotNull(result);
        assertEquals(650, result.getTotalTokens());
        assertEquals(0.32, result.getTotalCost(), 0.001);
        assertEquals("success", result.getStatus());
        assertTrue(result.getTotalDuration() >= 0);
    }
}
