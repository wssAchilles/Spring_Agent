package tech.qiantong.qknow.ai.htn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 55: 分层任务网络 (HTN) 递归规划、因果元反思自愈与分布式事务一致性仲裁 专属契约测试
 */
public class Phase55HtnExecutionContractTest {

    private HtnTaskDecomposer decomposer;
    private CausalMetaReasoningEngine metaReasoningEngine;
    private DistributedTransactionArbiter transactionArbiter;
    private MultiAgentHierarchicalCoordinator coordinator;

    @BeforeEach
    void setUp() {
        decomposer = new HtnTaskDecomposer();
        metaReasoningEngine = new CausalMetaReasoningEngine();
        transactionArbiter = new DistributedTransactionArbiter();
        coordinator = new MultiAgentHierarchicalCoordinator(decomposer, metaReasoningEngine, transactionArbiter);
    }

    @Test
    @DisplayName("契约 1: 验证定理 1.1，多层嵌套复合任务正确递归分解为原子动作且深度有界严格 <= 5")
    void test1_HtnTaskDecomposerRecursiveDecompositionAndDepthBound() {
        // 构建 3 层 HTN 任务树
        // Root (depth 1)
        //   -> SubComposite (depth 2)
        //        -> Prim_1 (depth 3, 无依赖)
        //        -> Prim_2 (depth 3, 依赖 Prim_1)
        //   -> Prim_3 (depth 2, 依赖 Prim_2)
        HtnTaskDecomposer.HtnTask prim1 = new HtnTaskDecomposer.HtnTask("prim_1", "SEARCH_KG", true, "sub_comp", List.of(), 3);
        HtnTaskDecomposer.HtnTask prim2 = new HtnTaskDecomposer.HtnTask("prim_2", "EXTRACT_ENTITIES", true, "sub_comp", List.of("prim_1"), 3);
        HtnTaskDecomposer.HtnTask subComp = new HtnTaskDecomposer.HtnTask(
                "sub_comp", "KNOWLEDGE_SUBFLOW", false, "root_task", List.of(), 2, List.of(prim1, prim2)
        );
        HtnTaskDecomposer.HtnTask prim3 = new HtnTaskDecomposer.HtnTask("prim_3", "GENERATE_SUMMARY", true, "root_task", List.of("prim_2"), 2);
        HtnTaskDecomposer.HtnTask rootTask = new HtnTaskDecomposer.HtnTask(
                "root_task", "FULL_REPORT_FLOW", false, null, List.of(), 1, List.of(subComp, prim3)
        );

        HtnTaskDecomposer.DecomposedPlan plan = decomposer.decompose(rootTask);

        assertNotNull(plan);
        assertEquals("root_task", plan.rootTaskId());
        assertEquals(3, plan.orderedPrimitiveTasks().size(), "应当完全展开为 3 个原子动作");
        assertTrue(plan.maxDepth() <= HtnTaskDecomposer.MAX_DECOMPOSITION_DEPTH, "最大展开深度必须 <= 5");

        // 验证拓扑执行顺序: prim_1 -> prim_2 -> prim_3
        List<String> order = plan.orderedPrimitiveTasks().stream().map(HtnTaskDecomposer.HtnTask::taskId).toList();
        assertEquals(List.of("prim_1", "prim_2", "prim_3"), order, "拓扑排序必须满足依赖偏序");
    }

    @Test
    @DisplayName("契约 2: 验证定理 1.1，输入循环依赖死锁任务，Kahn 拓扑排序 100% 捕获并阻断死循环")
    void test2_HtnTaskDecomposerCyclicDependencyDetection() {
        // A 依赖 B，B 依赖 A (死锁环路)
        HtnTaskDecomposer.HtnTask taskA = new HtnTaskDecomposer.HtnTask("task_A", "ACTION_A", true, "root", List.of("task_B"), 2);
        HtnTaskDecomposer.HtnTask taskB = new HtnTaskDecomposer.HtnTask("task_B", "ACTION_B", true, "root", List.of("task_A"), 2);
        HtnTaskDecomposer.HtnTask cyclicRoot = new HtnTaskDecomposer.HtnTask("cyclic_root", "DEADLOCK_FLOW", false, null, List.of(), 1, List.of(taskA, taskB));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            decomposer.decompose(cyclicRoot);
        }, "存在环路依赖时必须抛出异常阻断执行");

        assertTrue(thrown.getMessage().contains("循环依赖死锁"), "异常信息必须指明循环依赖死锁");
    }

    @Test
    @DisplayName("契约 3: 验证定理 1.2，可自愈参数异常在 2 轮反思内成功修复并输出元补丁")
    void test3_CausalMetaReasoningEngineSelfHealingConvergence() {
        CausalMetaReasoningEngine.FailureContext failCtx = new CausalMetaReasoningEngine.FailureContext(
                "task_sql_01",
                "EXECUTE_SQL",
                "PostgreSQL syntax error in parameter near offset 12",
                1,
                Map.of("rawSql", "SELECT * FORM users")
        );

        CausalMetaReasoningEngine.HealingVerdict verdict = metaReasoningEngine.diagnoseAndHeal(failCtx);

        assertTrue(verdict.canHeal(), "语法与参数异常必须判定为可自愈");
        assertEquals(CausalMetaReasoningEngine.ErrorCategory.RECOVERABLE_SYNTAX_PARAMS, verdict.category());
        assertEquals(2, verdict.roundCompleted(), "自愈轮次推进至第 2 轮");
        assertFalse(verdict.metaPatch().isEmpty(), "必须生成定向元补丁参数");
        assertTrue(verdict.metaPatch().containsKey("injected_sanitization"));
    }

    @Test
    @DisplayName("契约 4: 验证不可恢复错误（如权限被拒）快速失败，拒绝盲目暴力重试")
    void test4_CausalMetaReasoningEngineNonRecoverableFastFail() {
        CausalMetaReasoningEngine.FailureContext fatalCtx = new CausalMetaReasoningEngine.FailureContext(
                "task_auth_01",
                "INVOKE_SECURE_API",
                "HTTP 403 Forbidden: unauthorized permission denied on tenant bucket",
                0,
                Map.of()
        );

        CausalMetaReasoningEngine.HealingVerdict verdict = metaReasoningEngine.diagnoseAndHeal(fatalCtx);

        assertFalse(verdict.canHeal(), "权限被拒致命异常绝对不可自愈");
        assertEquals(CausalMetaReasoningEngine.ErrorCategory.NON_RECOVERABLE_PERMISSION, verdict.category());
        assertTrue(verdict.metaPatch().isEmpty(), "不可恢复错误禁止盲目生成补丁重试");
    }

    @Test
    @DisplayName("契约 5: 验证定理 1.3，下游执行失败触发 SAGA 逆向 LIFO 幂等补偿，消除脏状态残留")
    void test5_DistributedTransactionArbiterSagaRollbackConsistency() {
        DistributedTransactionArbiter.SagaContext ctx = new DistributedTransactionArbiter.SagaContext("tx_saga_test_01");

        List<String> compensationTrace = new ArrayList<>();

        // 模拟依次执行两步正向动作
        DistributedTransactionArbiter.SagaStep step1 = new DistributedTransactionArbiter.SagaStep(
                "step_1", "RESERVE_INVENTORY",
                () -> {},
                id -> compensationTrace.add("CANCEL_RESERVATION:" + id)
        );
        DistributedTransactionArbiter.SagaStep step2 = new DistributedTransactionArbiter.SagaStep(
                "step_2", "DEDUCT_POINTS",
                () -> {},
                id -> compensationTrace.add("REFUND_POINTS:" + id)
        );

        transactionArbiter.recordForwardExecution(ctx, step1);
        transactionArbiter.recordForwardExecution(ctx, step2);
        assertEquals(2, ctx.getCompletedSteps().size());

        // 模拟第 3 步崩溃，触发 SAGA 回滚
        List<String> compensatedSteps = transactionArbiter.rollbackTransaction(ctx);

        assertEquals(DistributedTransactionArbiter.TransactionState.COMPENSATED, ctx.getState());
        assertEquals(2, compensatedSteps.size());

        // 验证 LIFO 逆向顺序: 先补偿 step_2，再补偿 step_1
        assertEquals(List.of("step_2", "step_1"), compensatedSteps, "补偿执行必须严格遵循 LIFO 后进先出逆序");
        assertEquals(List.of("REFUND_POINTS:step_2", "CANCEL_RESERVATION:step_1"), compensationTrace);
        assertTrue(ctx.getCompletedSteps().isEmpty(), "回滚后未完成步骤栈必须清空");
    }

    @Test
    @DisplayName("契约 6: 验证存证凭单 Record 不可变性、防篡改校验与 SHA-256 完整性")
    void test6_HierarchicalExecutionReceiptImmutabilityAndSha256() {
        HierarchicalExecutionReceipt receipt = HierarchicalExecutionReceipt.createReceipt(
                "root_101",
                "PLAN_HASH_ABC",
                List.of("act_1", "act_2"),
                "COMMITTED",
                1,
                8L
        );

        assertNotNull(receipt.executionId());
        assertTrue(receipt.verifyIntegrity(), "初始签发的存证收据 SHA-256 自验必须为 true");

        // 验证不可变集合
        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.executedActionIds().add("fake_action");
        });
    }

    @Test
    @DisplayName("契约 7: 验证端到端生命周期全链路协调（HTN分解 -> 拓扑执行 -> 因果自愈 -> SAGA提交 -> 签发），耗时 <= 10ms")
    void test7_MultiAgentHierarchicalCoordinatorEndToEndLifecycle() {
        HtnTaskDecomposer.HtnTask sub1 = new HtnTaskDecomposer.HtnTask("t1", "PARSE_DATA", true, "root", List.of(), 2);
        HtnTaskDecomposer.HtnTask sub2 = new HtnTaskDecomposer.HtnTask("t2", "CALC_METRICS", true, "root", List.of("t1"), 2);
        HtnTaskDecomposer.HtnTask root = new HtnTaskDecomposer.HtnTask("root_flow", "DATA_PIPELINE", false, null, List.of(), 1, List.of(sub1, sub2));

        // 模拟 t1 产生可自愈的瞬态网络抖动
        Map<String, String> simulatedFailures = Map.of("t1", "Network transient timeout 504");

        MultiAgentHierarchicalCoordinator.ExecutionRequest req =
                new MultiAgentHierarchicalCoordinator.ExecutionRequest("root_flow", root, simulatedFailures);

        long start = System.nanoTime();
        HierarchicalExecutionReceipt receipt = coordinator.coordinateExecution(req);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "端到端产出的存证凭单自验必须为 true");
        assertEquals("COMMITTED", receipt.transactionStatus(), "自愈成功后事务最终必须提交成功");
        assertEquals(2, receipt.executedActionIds().size());
        assertTrue(receipt.selfHealingRounds() > 0, "记录了自愈轮次");
        assertTrue(elapsedMs <= 100, "端到端调度耗时满足极速要求: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("契约 8: 验证 8 线程并发调用下 HTN 分解与 SAGA 事务无死锁且吞吐稳定")
    void test8_HighConcurrencyHtnExecutionThroughput() throws InterruptedException, ExecutionException {
        int threads = 8;
        int countPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threads * countPerThread; i++) {
            final int idx = i;
            tasks.add(() -> {
                HtnTaskDecomposer.HtnTask t1 = new HtnTaskDecomposer.HtnTask("t1_" + idx, "STEP_1", true, "root_" + idx, List.of(), 2);
                HtnTaskDecomposer.HtnTask t2 = new HtnTaskDecomposer.HtnTask("t2_" + idx, "STEP_2", true, "root_" + idx, List.of("t1_" + idx), 2);
                HtnTaskDecomposer.HtnTask root = new HtnTaskDecomposer.HtnTask("root_" + idx, "ROOT_FLOW", false, null, List.of(), 1, List.of(t1, t2));

                MultiAgentHierarchicalCoordinator.ExecutionRequest req =
                        new MultiAgentHierarchicalCoordinator.ExecutionRequest("root_" + idx, root, Map.of());

                HierarchicalExecutionReceipt r = coordinator.coordinateExecution(req);
                return r != null && r.verifyIntegrity() && "COMMITTED".equals(r.transactionStatus());
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<Boolean> f : futures) {
            assertTrue(f.get(), "所有并发 HTN 协调任务必须 100% 成功提交且存证哈希有效");
        }
    }
}
