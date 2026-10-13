package tech.qiantong.qknow.hermes.flow.reducer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateReductionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发 Reducer 状态图基础设施契约测试套件 (ConcurrentReducerStateGraphContractTest)
 * 验证半群代数状态强汇聚、排他写屏障防冲突、动态 Send 路由及 SHA-256 密码学防篡改凭单。
 */
public class ConcurrentReducerStateGraphContractTest {

    @Test
    @DisplayName("EXP-REDUCER-01: 验证虚拟线程并发执行下的累加器与列表追加器半群汇聚无丢失更新")
    void testConcurrentVirtualThreadsAdditiveAndAppendReducers() {
        StateCheckpointRepository repo = new StateCheckpointRepository.DefaultStateCheckpointRepository();
        DurableStateGraphEngine.DefaultDurableStateGraphEngine engine =
                new DurableStateGraphEngine.DefaultDurableStateGraphEngine(repo);

        // 构建状态 Schema: counter 为数值累加器，logs 为列表追加器
        StateGraphSchema schema = StateGraphSchema.builder()
                .channel(StateChannelDefinition.of("counter", StandardStateReducers.sumDouble()))
                .channel(StateChannelDefinition.of("logs", StandardStateReducers.appendList()))
                .build();
        engine.setStateGraphSchema(schema);

        // 注册 4 个并行并发节点，各向 counter 累加 25.0，向 logs 追加一条日志
        for (int i = 1; i <= 4; i++) {
            final String nodeName = "workerNode-" + i;
            engine.registerGuardedNode(
                    nodeName,
                    state -> true,
                    state -> Map.of(
                            "counter", 25.0,
                            "logs", List.of("log-from-" + nodeName)
                    ),
                    List.of()
            );
        }

        String executionId = "exec-test-reducer-01";
        engine.initializeExecution(
                executionId,
                "main",
                Map.of("counter", 0.0, "logs", List.of("initial-log")),
                List.of("workerNode-1", "workerNode-2", "workerNode-3", "workerNode-4")
        );

        // 步进执行一个超步
        DurableStateGraphEngine.SuperstepExecutionResult result = engine.advanceSuperstep(executionId, "main");

        assertTrue(result.completed(), "所有无后续节点的分支应正常达成终态");
        assertEquals(1, result.executedSuperstep());

        Map<String, Object> finalState = result.mutatedState();
        assertNotNull(finalState);

        // 验证半群结合律累加：0.0 + 25.0 * 4 = 100.0
        Object counterObj = finalState.get("counter");
        assertNotNull(counterObj);
        double counterVal = ((Number) counterObj).doubleValue();
        assertEquals(100.0, counterVal, 0.0001, "4 个并发节点累加值必须严格精确为 100.0，杜绝丢失更新");

        // 验证列表规约：初始 1 项 + 4 个并发节点各 1 项 = 5 项
        Object logsObj = finalState.get("logs");
        assertTrue(logsObj instanceof List<?>);
        List<?> logsList = (List<?>) logsObj;
        assertEquals(5, logsList.size(), "并发列表追加必须全部保留，长度为 5");
        assertTrue(logsList.contains("initial-log"));

        // 验证产生的凭单合法且签名有效
        Optional<StateReductionReceipt> receiptOpt = engine.getLastReductionReceipt(executionId, "main");
        assertTrue(receiptOpt.isPresent());
        StateReductionReceipt receipt = receiptOpt.get();
        assertEquals(0, receipt.conflictCount(), "合法并发规约冲突计数必须为 0");
        assertTrue(receipt.verifySignature(), "规约凭单必须通过 MessageDigest.isEqual 常量时间验签");
    }

    @Test
    @DisplayName("EXP-REDUCER-02: 验证排他写屏障检测到并发写冲突并生成阻断存证")
    void testExclusiveWriteBarrierConflictDetection() {
        StateCheckpointRepository repo = new StateCheckpointRepository.DefaultStateCheckpointRepository();
        DurableStateGraphEngine.DefaultDurableStateGraphEngine engine =
                new DurableStateGraphEngine.DefaultDurableStateGraphEngine(repo);

        // 配置 config_channel 为排他写屏障
        StateGraphSchema schema = StateGraphSchema.builder()
                .channel(StateChannelDefinition.of("config_channel", StandardStateReducers.exclusiveWriteBarrier()))
                .build();
        engine.setStateGraphSchema(schema);

        // 注册两个冲突节点试图同时写入 config_channel
        engine.registerGuardedNode("conflictingNodeA", state -> true, state -> Map.of("config_channel", "VALUE_A"), List.of());
        engine.registerGuardedNode("conflictingNodeB", state -> true, state -> Map.of("config_channel", "VALUE_B"), List.of());

        String executionId = "exec-test-conflict-02";
        engine.initializeExecution(executionId, "main", Map.of("config_channel", "INIT"), List.of("conflictingNodeA", "conflictingNodeB"));

        DurableStateGraphEngine.SuperstepExecutionResult result = engine.advanceSuperstep(executionId, "main");
        assertNotNull(result);

        Optional<StateReductionReceipt> receiptOpt = engine.getLastReductionReceipt(executionId, "main");
        assertTrue(receiptOpt.isPresent());
        StateReductionReceipt receipt = receiptOpt.get();

        // 必须检测到至少 1 次排他写写冲突
        assertTrue(receipt.conflictCount() >= 1, "排他写屏障必须拦截并发竞争写入并记录冲突计数");
        assertTrue(receipt.verifySignature());
    }

    @Test
    @DisplayName("EXP-REDUCER-03: 验证动态 Send 包 (DynamicSendPacket) 正确路由并驱动下一超步活跃节点")
    void testDynamicSendPacketRouting() {
        StateCheckpointRepository repo = new StateCheckpointRepository.DefaultStateCheckpointRepository();
        DurableStateGraphEngine.DefaultDurableStateGraphEngine engine =
                new DurableStateGraphEngine.DefaultDurableStateGraphEngine(repo);

        // 注册入口动态派发节点
        engine.registerEnvelopeNode(
                "dispatcherNode",
                state -> true,
                state -> new NodeExecutionEnvelope(
                        "dispatcherNode",
                        Map.of("processed", true),
                        List.of(
                                DynamicSendPacket.of("dynamicWorker-1", Map.of("subtask", "task1")),
                                DynamicSendPacket.of("dynamicWorker-2", Map.of("subtask", "task2"))
                        ),
                        List.of(),
                        System.currentTimeMillis()
                ),
                List.of() // 静态后继为空，全靠动态派发
        );

        // 注册下游动态节点
        engine.registerGuardedNode("dynamicWorker-1", state -> true, state -> Map.of("worker1_done", true), List.of());
        engine.registerGuardedNode("dynamicWorker-2", state -> true, state -> Map.of("worker2_done", true), List.of());

        String executionId = "exec-test-dynamic-send-03";
        engine.initializeExecution(executionId, "main", Map.of(), List.of("dispatcherNode"));

        // 执行超步 1
        DurableStateGraphEngine.SuperstepExecutionResult step1Result = engine.advanceSuperstep(executionId, "main");
        assertFalse(step1Result.completed(), "存在动态派发后继，不应进入终态");
        assertEquals(2, step1Result.nextActiveNodes().size());
        assertTrue(step1Result.nextActiveNodes().contains("dynamicWorker-1"));
        assertTrue(step1Result.nextActiveNodes().contains("dynamicWorker-2"));

        // 执行超步 2
        DurableStateGraphEngine.SuperstepExecutionResult step2Result = engine.advanceSuperstep(executionId, "main");
        assertTrue(step2Result.completed(), "下游全部执行完毕，进入终态");
        assertEquals(2, step2Result.executedSuperstep());
        assertTrue(Boolean.TRUE.equals(step2Result.mutatedState().get("worker1_done")));
        assertTrue(Boolean.TRUE.equals(step2Result.mutatedState().get("worker2_done")));
    }

    @Test
    @DisplayName("EXP-REDUCER-04: 验证 StateReductionReceipt 签名防篡改与常量时间比对")
    void testStateReductionReceiptTamperProof() {
        StateReductionReceipt receipt = StateReductionReceipt.createSigned(
                "rcpt-1001",
                "exec-1001",
                1,
                "before-hash-abc",
                "after-hash-xyz",
                3,
                0,
                2,
                10001L,
                System.currentTimeMillis()
        );

        assertTrue(receipt.verifySignature(), "原始凭单签名校验必须通过");

        // 构造被篡改的凭单（修改冲突计数为 999）
        StateReductionReceipt tamperedReceipt = new StateReductionReceipt(
                receipt.reductionReceiptId(),
                receipt.executionId(),
                receipt.superstep(),
                receipt.beforeStateHash(),
                receipt.afterStateHash(),
                receipt.reducedChannelCount(),
                999, // 恶意篡改
                receipt.branchDeltaCount(),
                receipt.stateVersionToken(),
                receipt.sha256Signature(),
                receipt.timestamp()
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改后的凭单签名校验必须返回 false");
    }
}
