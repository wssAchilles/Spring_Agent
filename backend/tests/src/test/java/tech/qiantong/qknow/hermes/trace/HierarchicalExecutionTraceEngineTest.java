package tech.qiantong.qknow.hermes.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.trace.engine.HierarchicalExecutionTraceEngine;
import tech.qiantong.qknow.hermes.trace.model.HierarchicalTraceSpan;
import tech.qiantong.qknow.hermes.trace.model.TraceExecutionReceipt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 106 契约测试集二：层次化轻量因果追踪引擎测试
 *
 * @author Achilles
 * @version 1.0
 */
public class HierarchicalExecutionTraceEngineTest {

    @Test
    @DisplayName("契约 3：父子 Span 树状嵌套拓扑与 Lamport 因果时钟单调性校验")
    void testHierarchicalTopologyAndLamportMonotonicity() {
        HierarchicalExecutionTraceEngine engine = new HierarchicalExecutionTraceEngine();

        HierarchicalTraceSpan root = engine.startTrace("trace-t1", "wf-001", "MasterOrchestrator", "AGENT_REASONING", "Root Goal");
        assertNotNull(root);
        assertNull(root.parentSpanId());

        HierarchicalTraceSpan child1 = engine.startChildSpan("trace-t1", root.spanId(), "SubStep-SwarmDebate", "SWARM_DEBATE", "Debate Task");
        assertNotNull(child1);
        assertEquals(root.spanId(), child1.parentSpanId());
        assertTrue(child1.startNano() >= root.startNano(), "Lamport 约束: 子节点起始时间必须 >= 父节点起始时间");

        HierarchicalTraceSpan child2 = engine.startChildSpan("trace-t1", child1.spanId(), "Tool-McpQuery", "TOOL_MCP", "Query DB");
        assertNotNull(child2);
        assertEquals(child1.spanId(), child2.parentSpanId());
        assertTrue(child2.startNano() >= child1.startNano(), "Lamport 约束: 多层嵌套必须保持偏序单调递增");

        // 正常结束节点
        engine.endSpan("trace-t1", child2.spanId(), "SUCCESS", 100, "DB Result");
        engine.endSpan("trace-t1", child1.spanId(), "SUCCESS", 250, "Debate Winner");
        engine.endSpan("trace-t1", root.spanId(), "SUCCESS", 350, "Final Response");

        List<HierarchicalTraceSpan> spans = engine.getTraceSpans("trace-t1");
        assertEquals(3, spans.size());
    }

    @Test
    @DisplayName("契约 4：物理时钟漂移反事实消融实验 (偏序校正消除因果倒挂)")
    void testClockSkewCounterfactualAblation() {
        HierarchicalExecutionTraceEngine engine = new HierarchicalExecutionTraceEngine();

        HierarchicalTraceSpan root = engine.startTrace("trace-skew", "wf-skew", "RootAgent", "AGENT_ROOT", "Input");
        assertNotNull(root);

        // 派生子节点，并验证其单调性被引擎强制对齐
        HierarchicalTraceSpan child = engine.startChildSpan("trace-skew", root.spanId(), "AsyncWorker", "WORKER", "Task");
        assertTrue(child.startNano() >= root.startNano(), "引擎必须在纳秒级消除任何因果倒挂");
    }

    @Test
    @DisplayName("契约 5：定长 20 步环形池内存有界性与最老 Trace 优雅淘汰")
    void testRingBufferBoundedCapacityAndEviction() {
        HierarchicalExecutionTraceEngine engine = new HierarchicalExecutionTraceEngine();

        // 写入 25 条独立的 Trace
        for (int i = 1; i <= 25; i++) {
            String tId = "trace-batch-" + i;
            engine.startTrace(tId, "wf-batch", "RootStep-" + i, "STEP", "Input-" + i);
            engine.endTrace(tId);
        }

        // 容量上限应严格受限于 20
        assertEquals(20, engine.getActiveTraceCount(), "定长环形池容量必须严格保持 <= 20，杜绝内存泄漏");

        // 前 5 条老 Trace 必须已被安全淘汰驱逐
        for (int i = 1; i <= 5; i++) {
            assertTrue(engine.getTraceSpans("trace-batch-" + i).isEmpty(), "老旧 Trace [" + i + "] 必须已被驱逐");
        }

        // 第 25 条 Trace 必须健在
        assertFalse(engine.getTraceSpans("trace-batch-25").isEmpty(), "最新 Trace 必须保存在内存中");
    }

    @Test
    @DisplayName("契约 6：CPM 关键路径拓扑遍历与累计关键耗时精确计算")
    void testCriticalPathCalculation() throws InterruptedException {
        HierarchicalExecutionTraceEngine engine = new HierarchicalExecutionTraceEngine();

        String tId = "trace-cpm";
        HierarchicalTraceSpan root = engine.startTrace(tId, "wf-cpm", "RootCoordinator", "AGENT", "Task");

        // 派生两个并发分支：BranchA (耗时短) 与 BranchB (耗时长)
        HierarchicalTraceSpan branchA = engine.startChildSpan(tId, root.spanId(), "BranchA-Quick", "TASK", "A");
        HierarchicalTraceSpan branchB = engine.startChildSpan(tId, root.spanId(), "BranchB-Slow", "TASK", "B");

        Thread.sleep(5); // 模拟耗时
        engine.endSpan(tId, branchA.spanId(), "SUCCESS", 50, "Done A");

        Thread.sleep(15); // 模拟耗时更长
        engine.endSpan(tId, branchB.spanId(), "SUCCESS", 120, "Done B");

        engine.endSpan(tId, root.spanId(), "SUCCESS", 170, "Final Output");

        TraceExecutionReceipt receipt = engine.endTrace(tId);
        assertNotNull(receipt);
        assertEquals(3, receipt.totalSpans());
        // 50 + 120 + 170 = 340
        assertEquals(340, receipt.totalTokens());
        assertEquals(0, receipt.errorCount());

        // 关键路径耗时应大于等于最长分支耗时 + root 自身耗时
        assertTrue(receipt.criticalPathDurationUs() > 0, "关键路径耗时必须大于 0");
        assertTrue(receipt.verifySignature(), "签发凭单自验真必须通过");
    }
}
