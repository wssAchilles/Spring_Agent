package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;
import tech.qiantong.qknow.hermes.agent.bidding.ContractNetDispatcher;
import tech.qiantong.qknow.hermes.agent.bidding.TaskCfp;
import tech.qiantong.qknow.hermes.agent.bidding.WorkerBid;
import tech.qiantong.qknow.hermes.agent.blackboard.BlackboardEntry;
import tech.qiantong.qknow.hermes.agent.blackboard.BlackboardEvent;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.agent.dag.DagTaskNode;
import tech.qiantong.qknow.hermes.agent.dag.HierarchicalTaskPlanner;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;
import tech.qiantong.qknow.hermes.agent.dag.TopologicalPhasedDispatcher;
import tech.qiantong.qknow.hermes.agent.guard.SwarmLoopException;
import tech.qiantong.qknow.hermes.agent.guard.SwarmLoopGuard;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 22 专属契约测试：多智能体动态分工协作、蜂群通信协议与分层任务意图分解
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SwarmOrchestrationContractTest {

    @Mock
    private ChatModel mockChatModel;

    private HierarchicalTaskPlanner planner;
    private SharedBlackboard blackboard;
    private SwarmLoopGuard loopGuard;
    private ContractNetDispatcher contractNetDispatcher;
    private TopologicalPhasedDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        planner = new HierarchicalTaskPlanner(mockChatModel);
        blackboard = new SharedBlackboard();
        loopGuard = new SwarmLoopGuard();
        contractNetDispatcher = new ContractNetDispatcher();
        dispatcher = new TopologicalPhasedDispatcher(contractNetDispatcher, loopGuard);
    }

    private ChatResponse createMockChatResponse(String text) {
        Generation gen = new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text));
        return new ChatResponse(List.of(gen));
    }

    // ==========================================
    // 1. DAG 规划与拓扑排序测试
    // ==========================================

    @Test
    @DisplayName("契约 1: 验证 Kahn 算法对任务 DAG 进行正确拓扑分层与阶段抽取")
    void testHierarchicalTaskPlannerKahnTopologicalSorting() {
        String jsonPlan = """
                [
                  {"taskId": "task-1", "objective": "检索技术白皮书", "requiredCapability": "KNOWLEDGE_RETRIEVAL", "dependencies": []},
                  {"taskId": "task-2", "objective": "抓取线上竞品数据", "requiredCapability": "WEB_SEARCH", "dependencies": []},
                  {"taskId": "task-3", "objective": "对比白皮书与竞品差异", "requiredCapability": "DATA_ANALYSIS", "dependencies": ["task-1", "task-2"]},
                  {"taskId": "task-4", "objective": "生成最终对比总结报告", "requiredCapability": "REPORT_SYNTHESIS", "dependencies": ["task-3"]}
                ]
                """;

        when(mockChatModel.call(any(Prompt.class))).thenReturn(createMockChatResponse(jsonPlan));

        PhasedExecutionPlan plan = planner.plan("请帮我对比技术白皮书与线上竞品数据并输出总结报告");

        assertNotNull(plan);
        assertEquals(4, plan.totalTasks());
        assertEquals(3, plan.phases().size(), "应分为 3 个执行阶段");

        // Phase 0: task-1 与 task-2 (无前置依赖)
        List<String> phase0Ids = plan.phases().get(0).stream().map(DagTaskNode::taskId).sorted().toList();
        assertEquals(List.of("task-1", "task-2"), phase0Ids);

        // Phase 1: task-3 (依赖 task-1 和 task-2)
        List<String> phase1Ids = plan.phases().get(1).stream().map(DagTaskNode::taskId).toList();
        assertEquals(List.of("task-3"), phase1Ids);

        // Phase 2: task-4 (依赖 task-3)
        List<String> phase2Ids = plan.phases().get(2).stream().map(DagTaskNode::taskId).toList();
        assertEquals(List.of("task-4"), phase2Ids);
    }

    @Test
    @DisplayName("契约 2: 验证任务依赖图中出现环路 (A -> B -> A) 时准确检测并抛出异常阻断")
    void testHierarchicalTaskPlannerCycleDetection() {
        String cyclicPlan = """
                [
                  {"taskId": "A", "objective": "任务 A", "requiredCapability": "GENERAL", "dependencies": ["B"]},
                  {"taskId": "B", "objective": "任务 B", "requiredCapability": "GENERAL", "dependencies": ["A"]}
                ]
                """;

        when(mockChatModel.call(any(Prompt.class))).thenReturn(createMockChatResponse(cyclicPlan));

        assertThrows(IllegalStateException.class, () -> {
            planner.plan("循环任务测试");
        }, "存在环路时必须抛出 IllegalStateException，杜绝死锁调度");
    }

    // ==========================================
    // 2. 共享黑板与响应式事件流测试
    // ==========================================

    @Test
    @DisplayName("契约 3: 验证共享黑板基于 CAS 乐观锁的版本递增与并发无丢失")
    void testSharedBlackboardCasVersionAndPartition() throws InterruptedException {
        int threadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + (j % 5);
                        blackboard.commitFact(key, "Worker-" + workerId + "-Val-" + j, "WORKER_" + workerId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "并发提交操作必须在 5 秒内完成");
        executor.shutdown();

        Map<String, BlackboardEntry> snapshot = blackboard.getFactSnapshot();
        assertEquals(5, snapshot.size(), "事实分区应保留 5 个 Key 的最新状态");

        long finalVersion = blackboard.getGlobalVersion();
        assertEquals(threadCount * operationsPerThread, finalVersion, "全局版本号必须严格连续递增无丢失");
    }

    @Test
    @DisplayName("契约 4: 验证共享黑板 Project Reactor 响应式广播事件流")
    void testSharedBlackboardReactiveEventStream() {
        StepVerifier.create(blackboard.eventStream().take(2))
                .then(() -> {
                    blackboard.commitFact("task-1", "检索结果A", "RAG");
                    blackboard.commitFact("task-2", "分析结果B", "ANALYST");
                })
                .assertNext(event -> {
                    assertEquals("FACT_COMMITTED", event.eventType());
                    assertEquals("task-1", event.key());
                    assertEquals(1L, event.version());
                })
                .assertNext(event -> {
                    assertEquals("FACT_COMMITTED", event.eventType());
                    assertEquals("task-2", event.key());
                    assertEquals(2L, event.version());
                })
                .verifyComplete();
    }

    // ==========================================
    // 3. 动态合同网协议 (CNET) 竞标与降级测试
    // ==========================================

    @Test
    @DisplayName("契约 5: 验证合同网综合加权竞标 (能力匹配 + 负载 + 信誉) 优选最优 Worker")
    void testContractNetBiddingWeightedOptimalSelection() {
        EnhancedBaseAgent ragWorker = mock(EnhancedBaseAgent.class);
        when(ragWorker.getName()).thenReturn("RAGSpecialist");
        when(ragWorker.evaluateAndBid(any(TaskCfp.class))).thenReturn(Optional.of(
                new WorkerBid("RAGSpecialist", 0.88, 0.95, 0.20, 0.90)
        ));
        when(ragWorker.executeTask(anyString(), anyString())).thenReturn("RAG专业解答内容");

        EnhancedBaseAgent generalWorker = mock(EnhancedBaseAgent.class);
        when(generalWorker.getName()).thenReturn("GeneralWorker");
        when(generalWorker.evaluateAndBid(any(TaskCfp.class))).thenReturn(Optional.of(
                new WorkerBid("GeneralWorker", 0.65, 0.60, 0.10, 0.80)
        ));

        contractNetDispatcher.registerWorker(ragWorker);
        contractNetDispatcher.registerWorker(generalWorker);

        DagTaskNode task = new DagTaskNode("task-1", "查询知识库文档", "KNOWLEDGE_RETRIEVAL", List.of(), 10, Map.of());
        String result = contractNetDispatcher.bidAndExecute("session-001", task, "上下文输入");

        assertEquals("RAG专业解答内容", result);
        verify(ragWorker, times(1)).executeTask(anyString(), anyString());
        verify(generalWorker, never()).executeTask(anyString(), anyString());
    }

    @Test
    @DisplayName("契约 6: 验证竞标得分均低于阈值 (0.60) 或无 Worker 时自适应软降级至默认兜底 Worker")
    void testContractNetBiddingFallbackOnLowScore() {
        EnhancedBaseAgent weakWorker = mock(EnhancedBaseAgent.class);
        when(weakWorker.getName()).thenReturn("WeakWorker");
        when(weakWorker.evaluateAndBid(any(TaskCfp.class))).thenReturn(Optional.of(
                new WorkerBid("WeakWorker", 0.45, 0.30, 0.80, 0.50) // 得分低于 0.60
        ));

        EnhancedBaseAgent fallbackWorker = mock(EnhancedBaseAgent.class);
        when(fallbackWorker.getName()).thenReturn("DefaultFallbackWorker");
        when(fallbackWorker.executeTask(anyString(), anyString())).thenReturn("兜底降级回答");

        contractNetDispatcher.registerWorker(weakWorker);
        contractNetDispatcher.setDefaultFallbackWorker(fallbackWorker);

        DagTaskNode task = new DagTaskNode("task-2", "冷僻专业计算", "QUANTUM_PHYSICS", List.of(), 10, Map.of());
        String result = contractNetDispatcher.bidAndExecute("session-002", task, "上下文输入");

        assertEquals("兜底降级回答", result);
        verify(fallbackWorker, times(1)).executeTask(anyString(), anyString());
    }

    // ==========================================
    // 4. 阶段调度与上下文流式注入与超时降级
    // ==========================================

    @Test
    @DisplayName("契约 7: 验证阶段调度器按 Phase 推进并自动将上游黑板产出注入下游输入上下文")
    void testTopologicalPhasedDispatcherPhasedContextInjection() {
        AtomicInteger stepCounter = new AtomicInteger(0);

        EnhancedBaseAgent worker = mock(EnhancedBaseAgent.class);
        when(worker.getName()).thenReturn("GeneralWorker");
        when(worker.evaluateAndBid(any(TaskCfp.class))).thenReturn(Optional.of(
                new WorkerBid("GeneralWorker", 0.90, 0.90, 0.0, 0.90)
        ));
        when(worker.executeTask(anyString(), anyString())).thenAnswer(invocation -> {
            String obj = invocation.getArgument(0);
            String ctx = invocation.getArgument(1);
            if (obj.contains("前置")) {
                stepCounter.incrementAndGet();
                return "【前置任务生成的数据产物】";
            } else if (obj.contains("后置")) {
                stepCounter.incrementAndGet();
                assertTrue(ctx.contains("【前置任务生成的数据产物】"), "后置任务必须包含上游任务写入黑板的上下文！");
                return "【后置任务最终成果】";
            }
            return "OK";
        });

        contractNetDispatcher.registerWorker(worker);

        DagTaskNode t1 = new DagTaskNode("t1", "前置任务", "GENERAL", List.of(), 10, Map.of());
        DagTaskNode t2 = new DagTaskNode("t2", "后置任务", "GENERAL", List.of("t1"), 10, Map.of());

        PhasedExecutionPlan plan = new PhasedExecutionPlan(List.of(List.of(t1), List.of(t2)), 2);

        dispatcher.dispatch("session-003", plan, blackboard);

        assertEquals(2, stepCounter.get(), "两阶段任务均应顺利执行");
        Map<String, String> facts = blackboard.getFactsByKeys(List.of("t1", "t2"));
        assertEquals("【前置任务生成的数据产物】", facts.get("t1"));
        assertEquals("【后置任务最终成果】", facts.get("t2"));
    }

    @Test
    @DisplayName("契约 8: 验证 Worker 执行超时强制 30s 硬熔断并写入降级结果 (Fail-Open)，不阻塞后续任务")
    void testTopologicalPhasedDispatcherTimeoutAndFailOpen() {
        EnhancedBaseAgent slowWorker = mock(EnhancedBaseAgent.class);
        when(slowWorker.getName()).thenReturn("SlowWorker");
        when(slowWorker.evaluateAndBid(any(TaskCfp.class))).thenReturn(Optional.of(
                new WorkerBid("SlowWorker", 0.95, 0.95, 0.0, 0.95)
        ));
        when(slowWorker.executeTask(anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(3000); // 模拟耗时 3 秒
            return "慢速答案";
        });

        contractNetDispatcher.registerWorker(slowWorker);

        // 设置超时仅为 1 秒
        DagTaskNode timeoutTask = new DagTaskNode("timeout-task", "超时慢任务", "GENERAL", List.of(), 1, Map.of());
        PhasedExecutionPlan plan = new PhasedExecutionPlan(List.of(List.of(timeoutTask)), 1);

        long start = System.currentTimeMillis();
        dispatcher.dispatch("session-004", plan, blackboard);
        long duration = System.currentTimeMillis() - start;

        assertTrue(duration < 2500, "必须在 2.5 秒内触发硬超时截断，耗时: " + duration + "ms");
        Map<String, String> facts = blackboard.getFactsByKeys(List.of("timeout-task"));
        assertTrue(facts.containsKey("timeout-task"));
        assertTrue(facts.get("timeout-task").contains("降级"), "超时任务必须写入降级结果保障下游不报错");
    }

    // ==========================================
    // 5. 蜂群防死锁与振荡熔断器测试
    // ==========================================

    @Test
    @DisplayName("契约 9: 验证蜂群协同跳转深度达到上限 5 时触发 SWARM_MAX_DEPTH_EXCEEDED 熔断")
    void testSwarmLoopGuardMaxDepthExceededBreaker() {
        String sessionId = "session-deep-01";
        for (int i = 1; i <= 5; i++) {
            loopGuard.preCheck(sessionId, "task-" + i);
        }

        SwarmLoopException ex = assertThrows(SwarmLoopException.class, () -> {
            loopGuard.preCheck(sessionId, "task-6");
        });

        assertEquals("SWARM_MAX_DEPTH_EXCEEDED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("阈值 5"));
    }

    @Test
    @DisplayName("契约 10: 验证同一个会话出现调用拓扑环 (A -> B -> A) 时触发 SWARM_TOPOLOGY_CYCLE_DETECTED 熔断")
    void testSwarmLoopGuardTopologyCycleBreaker() {
        String sessionId = "session-cycle-01";
        loopGuard.preCheck(sessionId, "task-A");
        loopGuard.preCheck(sessionId, "task-B");

        SwarmLoopException ex = assertThrows(SwarmLoopException.class, () -> {
            loopGuard.preCheck(sessionId, "task-A");
        });

        assertEquals("SWARM_TOPOLOGY_CYCLE_DETECTED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("task-A"));
    }

    @Test
    @DisplayName("契约 11: 验证智能体输出相同语义内容达到 3 次时触发 SWARM_SEMANTIC_OSCILLATION_BREAKER 熔断")
    void testSwarmLoopGuardSemanticOscillationBreaker() {
        String sessionId = "session-osc-01";
        String duplicateMsg = "请您提供更详细的信息以便我继续分析。";

        loopGuard.inspectOutput(sessionId, duplicateMsg);
        loopGuard.inspectOutput(sessionId, duplicateMsg);

        SwarmLoopException ex = assertThrows(SwarmLoopException.class, () -> {
            loopGuard.inspectOutput(sessionId, duplicateMsg);
        });

        assertEquals("SWARM_SEMANTIC_OSCILLATION_BREAKER", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("语义无意义循环震荡"));
    }

    // ==========================================
    // 6. SupervisorAgent 端到端全链路协同测试
    // ==========================================

    @Test
    @DisplayName("契约 12: 验证 SupervisorAgent 完整整合意图分解、阶段调度、黑板与结果综合")
    void testSupervisorAgentEndToEndSwarmOrchestration() {
        String planJson = """
                [
                  {"taskId": "t1", "objective": "查询业务数据", "requiredCapability": "KNOWLEDGE_RETRIEVAL", "dependencies": []}
                ]
                """;
        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(createMockChatResponse(planJson)) // 分解
                .thenReturn(createMockChatResponse("基于知识库事实，业务数据已全面就绪。")); // 聚合

        EnhancedBaseAgent worker = mock(EnhancedBaseAgent.class);
        when(worker.getName()).thenReturn("RAGWorker");
        when(worker.evaluateAndBid(any(TaskCfp.class))).thenReturn(Optional.of(
                new WorkerBid("RAGWorker", 0.92, 0.95, 0.1, 0.9)
        ));
        when(worker.executeTask(anyString(), anyString())).thenReturn("业务检索命中结果片段");

        SupervisorAgent supervisor = new SupervisorAgent(
                "MasterSupervisor", "主管智能体", "你是一个协调专家",
                List.of(), mockChatModel, List.of(worker),
                planner, dispatcher, contractNetDispatcher, blackboard, loopGuard
        );

        String finalAnswer = supervisor.chat("请查询业务数据并总结", Map.of("sessionId", "session-e2e-01"));

        assertNotNull(finalAnswer);
        assertTrue(finalAnswer.contains("业务数据已全面就绪"));
        assertEquals("业务检索命中结果片段", blackboard.getFactsByKeys(List.of("t1")).get("t1"));
    }
}
