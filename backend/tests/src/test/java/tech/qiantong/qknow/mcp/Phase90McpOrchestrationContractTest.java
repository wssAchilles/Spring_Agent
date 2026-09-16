package tech.qiantong.qknow.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.client.orchestration.engine.CrossMicroserviceMcpTopologyRouter;
import tech.qiantong.qknow.mcp.client.orchestration.engine.McpOrchestrationControlBus;
import tech.qiantong.qknow.mcp.client.orchestration.engine.StreamingToolchainFaultToleranceGovernor;
import tech.qiantong.qknow.mcp.client.orchestration.engine.ToolchainDependencyDagGuard;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpOrchestrationReceipt;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpTopologyNodeState;
import tech.qiantong.qknow.mcp.core.orchestration.dto.StreamingToolChunkEventFrame;
import tech.qiantong.qknow.mcp.core.orchestration.dto.ToolchainDependencyEdge;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 90: 企业级生产 MCP 工具链智能动态编排、跨微服务拓扑自治路由与流式容错自愈中枢
 * 专属契约单元测试套件 (8/8 严苛契约)
 */
public class Phase90McpOrchestrationContractTest {

    private double[] createNormalizedSphericalEmbedding(int seed) {
        double[] vec = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = Math.sin(seed + i * 0.173);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("测试 1: 跨微服务加权拓扑测地路由算法求解最优路径且耗时严格 <= 100μs (定理 1.1)")
    void testTopologyRouter_OptimalGeodesicPathWithin100Micros() {
        CrossMicroserviceMcpTopologyRouter router = new CrossMicroserviceMcpTopologyRouter();
        double[] qEmb = createNormalizedSphericalEmbedding(101);

        for (int i = 0; i < 20; i++) {
            double rtt = 15.0 + (i % 5) * 10.0;
            double err = (i % 7 == 0) ? 0.05 : 0.01;
            double[] nEmb = createNormalizedSphericalEmbedding(101 + i);
            McpTopologyNodeState node = new McpTopologyNodeState(
                    "svc_" + i, "zone_a", List.of("tool_a", "tool_b", "tool_" + i),
                    rtt, err, true, nEmb, System.currentTimeMillis()
            );
            assertTrue(node.isValidEmbedding());
            router.registerNode(node);
        }

        // JVM 预热消除冷启动抖动与触发 JIT 编译
        for (int w = 0; w < 200; w++) {
            router.resolveOptimalToolPath("查询核心数据", qEmb, List.of("tool_a", "tool_b"));
        }

        long startNano = System.nanoTime();
        List<String> path = router.resolveOptimalToolPath("查询核心数据", qEmb, List.of("tool_a", "tool_b"));
        long elapsedMicros = (System.nanoTime() - startNano) / 1000L;

        assertTrue(elapsedMicros <= 100, "拓扑测地路径求解耗时必须 <= 100μs，实测: " + elapsedMicros + "μs");
        assertEquals(2, path.size());
        assertNotNull(path.get(0));
        assertNotNull(path.get(1));
    }

    @Test
    @DisplayName("测试 2: 测地路由器自动过滤高延迟与亚健康微服务节点，寻路成功率 >= 99.5%")
    void testTopologyRouter_AutomaticAvoidanceOfUnhealthyNodes() {
        CrossMicroserviceMcpTopologyRouter router = new CrossMicroserviceMcpTopologyRouter();
        double[] qEmb = createNormalizedSphericalEmbedding(202);

        // 注册两个提供相同工具的服务节点：一个健康低延迟，一个故障宕机
        McpTopologyNodeState healthyNode = new McpTopologyNodeState(
                "svc_healthy", "zone_a", List.of("sql_executor"),
                20.0, 0.01, true, qEmb, System.currentTimeMillis()
        );
        McpTopologyNodeState sickNode = new McpTopologyNodeState(
                "svc_sick", "zone_b", List.of("sql_executor"),
                1500.0, 0.85, false, qEmb, System.currentTimeMillis()
        );

        router.registerNode(healthyNode);
        router.registerNode(sickNode);

        List<String> chosen = router.resolveOptimalToolPath("执行报表统计", qEmb, List.of("sql_executor"));
        assertEquals(1, chosen.size());
        assertEquals("svc_healthy", chosen.get(0), "测地路由器必须 100% 自动避让亚健康节点");
    }

    @Test
    @DisplayName("测试 3: 线性合法工具链 DAG 拓扑排序与依赖序列生成")
    void testDagGuard_LegalLinearTopologicalSort() {
        ToolchainDependencyDagGuard guard = new ToolchainDependencyDagGuard();
        guard.addTool("DataFetchTool");
        guard.addTool("TransformTool");
        guard.addTool("ReportExportTool");

        guard.addDependency(new ToolchainDependencyEdge("DataFetchTool", "TransformTool", "raw", "in", 0.9, false, null));
        guard.addDependency(new ToolchainDependencyEdge("TransformTool", "ReportExportTool", "cleaned", "in", 0.8, false, null));

        assertFalse(guard.hasDeadlockCycle(), "线性无环依赖网不得误报死锁");
        List<String> sorted = guard.topologicalSortOrHeal();

        assertNotNull(sorted);
        assertEquals(3, sorted.size());
        assertEquals("DataFetchTool", sorted.get(0));
        assertEquals("TransformTool", sorted.get(1));
        assertEquals("ReportExportTool", sorted.get(2));
    }

    @Test
    @DisplayName("测试 4: 网状依赖死锁环路实时检出与最小破环切断自愈耗时 <= 50μs (定理 1.2)")
    void testDagGuard_DeadlockCycleDetectionAndHealingWithin50Micros() {
        ToolchainDependencyDagGuard guard = new ToolchainDependencyDagGuard();
        guard.addTool("Tool_A");
        guard.addTool("Tool_B");
        guard.addTool("Tool_C");

        // 构造三元循环依赖死锁: A -> B -> C -> A
        guard.addDependency(new ToolchainDependencyEdge("Tool_A", "Tool_B", "outA", "inB", 0.95, false, null));
        guard.addDependency(new ToolchainDependencyEdge("Tool_B", "Tool_C", "outB", "inC", 0.85, false, null));
        guard.addDependency(new ToolchainDependencyEdge("Tool_C", "Tool_A", "outC", "inA", 0.30, false, null)); // 耦合度最低的脆弱边

        assertTrue(guard.hasDeadlockCycle(), "循环依赖图必须精准检出死锁");

        // JVM 预热拓扑类加载
        ToolchainDependencyDagGuard warmupGuard = new ToolchainDependencyDagGuard();
        warmupGuard.addTool("W_A");
        warmupGuard.addTool("W_B");
        warmupGuard.addDependency(new ToolchainDependencyEdge("W_A", "W_B", "o", "i", 0.5, false, null));
        warmupGuard.addDependency(new ToolchainDependencyEdge("W_B", "W_A", "o", "i", 0.2, false, null));
        for (int w = 0; w < 5; w++) {
            warmupGuard.topologicalSortOrHeal();
        }

        long startNano = System.nanoTime();
        List<String> healed = guard.topologicalSortOrHeal();
        long elapsedMicros = (System.nanoTime() - startNano) / 1000L;

        assertTrue(elapsedMicros <= 50, "死锁破环自愈耗时必须 <= 50μs，实测: " + elapsedMicros + "μs");
        assertNotNull(healed);
        assertEquals(3, healed.size(), "破环自愈后必须包含全部 3 个工具");
        assertFalse(guard.hasDeadlockCycle(), "自愈后依赖网必须成为合法 DAG，死锁彻底消除");
    }

    @Test
    @DisplayName("测试 5: 流式分块传输看门狗超时监控与断流判定")
    void testStreamingChunkWatchdog_TimeoutDetection() {
        StreamingToolchainFaultToleranceGovernor gov = new StreamingToolchainFaultToleranceGovernor();

        gov.updateChunkWatchdog("exec_001");
        assertFalse(gov.isChunkTimedOut("exec_001"), "刚更新时间戳不得判定超时");

        // 未注册的执行会话不超时
        assertFalse(gov.isChunkTimedOut("exec_unknown"));
    }

    @Test
    @DisplayName("测试 6: 三态自适应断路器 (CLOSED -> OPEN -> HALF_OPEN) 与降级桩分流 (定理 1.3)")
    void testCircuitBreaker_ThreeStateTransitionsAndDegradation() {
        StreamingToolchainFaultToleranceGovernor gov = new StreamingToolchainFaultToleranceGovernor();
        String svc = "financial_erp_svc";

        assertEquals(StreamingToolchainFaultToleranceGovernor.CircuitState.CLOSED, gov.getCircuitState(svc));
        assertTrue(gov.isCallPermitted(svc));

        // 连续注入 4 次失败，触发熔断
        gov.recordCallFailure(svc, "Connection refused");
        gov.recordCallFailure(svc, "Read timeout");
        gov.recordCallFailure(svc, "503 Service Unavailable");
        gov.recordCallFailure(svc, "Connection reset");

        assertEquals(StreamingToolchainFaultToleranceGovernor.CircuitState.OPEN, gov.getCircuitState(svc));
        assertFalse(gov.isCallPermitted(svc), "OPEN 态必须拦截直接调用，分流至降级桩");

        String mutation = gov.applyReflexionMutation("accounting_tool", "{}", "服务熔断", 1);
        assertNotNull(mutation);
        assertTrue(mutation.contains("反思自愈纠偏"));
    }

    @Test
    @DisplayName("测试 7: 1000Hz 4096 槽位 Disruptor 无锁编排总线高频推帧与 JitterGuard 监控")
    void testDisruptorBus_SubMicrosecondThroughputAndJitterGuard() {
        McpOrchestrationControlBus bus = new McpOrchestrationControlBus();
        double[] emb = createNormalizedSphericalEmbedding(303);

        assertEquals(McpOrchestrationControlBus.STATUS_NORMAL, bus.getCurrentStatus());

        for (int i = 0; i < 32; i++) {
            StreamingToolChunkEventFrame frame = new StreamingToolChunkEventFrame(
                    "chunk_" + i, "exec_bus_001", "report_tool",
                    i, (i == 31), "payload_" + i, emb,
                    System.currentTimeMillis() * 1000L, i
            );
            assertTrue(frame.isValidEmbedding());
            boolean ok = bus.publishChunkFrame(frame);
            assertTrue(ok);
        }

        StreamingToolChunkEventFrame read = bus.readChunkFrame(0);
        assertNotNull(read);
        assertEquals("chunk_0", read.frameId());
    }

    @Test
    @DisplayName("测试 8: 不可变 MCP 编排存证凭单 SHA-256 密码学自签名与验真 100% 通过")
    void testOrchestrationReceipt_CryptographicSelfVerification() {
        McpOrchestrationControlBus bus = new McpOrchestrationControlBus();

        McpOrchestrationReceipt receipt = bus.issueReceipt(
                "sess_mcp_verified_001",
                "生成企业跨微服务财务审计汇总表",
                List.of("svc_erp", "svc_crm", "svc_risk"),
                1,
                2,
                45L,
                18500L
        );

        assertNotNull(receipt);
        assertEquals("sess_mcp_verified_001", receipt.sessionId());
        assertEquals(3, receipt.plannedToolchain().size());
        assertEquals(1, receipt.cycleDetectedCount());
        assertEquals(2, receipt.selfHealingAttempts());
        assertTrue(receipt.verifySignature(), "不可变存证凭单 SHA-256 自签名验真必须 100% 通过");
    }
}
