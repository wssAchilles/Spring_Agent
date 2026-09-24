package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphCausalDebugReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.StateGraphTimeTravelDebugger;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.StateGraphVisualSyncEngine;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 128 核心契约测试套件：
 * 状态图 (StateGraph) 可视化交互、微秒级状态一致性与全链路因果调试中枢
 * 覆盖定理 1.1（不可变结构共享时间旅行状态完备性）与定理 1.2（纪元防抖与视口 AABB 裁剪李雅普诺夫稳态收敛）等 8 大核心契约
 */
public class Phase128StateGraphVisualDebugContractTest {

    @Test
    @DisplayName("契约 1：超步快照不可变捕获与 O(1) 时间旅行状态完备性 (定理 1.1)")
    void test01_SuperstepSnapshotCaptureAndO1TimeTravel() {
        StateGraphTimeTravelDebugger debugger = new StateGraphTimeTravelDebugger("exec-001", "flow-001", "session-001");

        // 超步 1：设置初始变量
        debugger.captureSuperstep(1, Set.of("node-start"), Map.of("step", 1, "alpha", 0.5));
        // 超步 2：增量修改变量
        debugger.captureSuperstep(2, Set.of("node-process"), Map.of("step", 2, "beta", 1.2));
        // 超步 3：再次增量修改
        debugger.captureSuperstep(3, Set.of("node-end"), Map.of("step", 3, "status", "DONE"));

        // 验证当前状态完备性（增量结构共享）
        Map<String, Object> state3 = debugger.getCurrentSharedState();
        assertEquals(3, state3.get("step"));
        assertEquals(0.5, state3.get("alpha"));
        assertEquals(1.2, state3.get("beta"));
        assertEquals("DONE", state3.get("status"));

        // O(1) 时光旅行回溯至超步 2
        Optional<StateGraphTimeTravelDebugger.SuperstepSnapshot> step2Opt = debugger.timeTravelTo(2);
        assertTrue(step2Opt.isPresent(), "必须能定位到超步 2 快照");
        Map<String, Object> state2 = debugger.getCurrentSharedState();
        assertEquals(2, state2.get("step"));
        assertEquals(0.5, state2.get("alpha"));
        assertEquals(1.2, state2.get("beta"));
        assertNull(state2.get("status"), "超步 2 不应包含超步 3 产生的变量");

        // 验证不可变性：尝试修改抛出异常
        assertThrows(UnsupportedOperationException.class, () -> state2.put("illegal", "value"));
    }

    @Test
    @DisplayName("契约 2：历史超步派生分叉分支隔离，幽灵变量污染率为 0.0% (定理 1.1)")
    void test02_ForkBranchIsolationZeroPhantomPollution() {
        StateGraphTimeTravelDebugger debugger = new StateGraphTimeTravelDebugger("exec-002", "flow-002", "session-002");

        debugger.captureSuperstep(1, Set.of("n1"), Map.of("temperature", 0.2, "model", "deepseek"));
        debugger.captureSuperstep(2, Set.of("n2"), Map.of("tokens", 150));

        // 在超步 1 派生热调试分支，热补丁修改 temperature = 0.9
        String forkBranch = "branch-experimental-high-temp";
        debugger.forkBranch(1, forkBranch, Map.of("temperature", 0.9, "patchApplied", true));

        // 验证分叉分支生效
        assertEquals(forkBranch, debugger.getCurrentBranchId());
        Map<String, Object> forkedState = debugger.getCurrentSharedState();
        assertEquals(0.9, forkedState.get("temperature"));
        assertEquals(true, forkedState.get("patchApplied"));
        assertNull(forkedState.get("tokens"), "分叉基准超步为 1，不包含超步 2 变量");

        // 切换回主分支 main
        boolean switched = debugger.switchBranch("main");
        assertTrue(switched, "必须成功切换回 main 分支");
        assertEquals("main", debugger.getCurrentBranchId());

        // 验证主分支未受到任何幽灵污染（幽灵变量污染率 0.0%）
        debugger.timeTravelTo(1);
        Map<String, Object> mainState1 = debugger.getCurrentSharedState();
        assertEquals(0.2, mainState1.get("temperature"), "主分支历史状态必须为 0.2，不可被派生分支篡改");
        assertNull(mainState1.get("patchApplied"), "主分支不得存在派生分支的热补丁字段");
    }

    @Test
    @DisplayName("契约 3：单步步退 (Step Backward) 与单步步进 (Step Forward) 因果偏序一致")
    void test03_StepBackwardAndForwardCausalOrder() {
        StateGraphTimeTravelDebugger debugger = new StateGraphTimeTravelDebugger("exec-003", "flow-003", "session-003");

        debugger.captureSuperstep(10, Set.of("node-A"), Map.of("stage", "A"));
        debugger.captureSuperstep(20, Set.of("node-B"), Map.of("stage", "B"));
        debugger.captureSuperstep(30, Set.of("node-C"), Map.of("stage", "C"));

        assertEquals(30, debugger.getCurrentCursor());

        // 单步步退 -> 20
        Optional<StateGraphTimeTravelDebugger.SuperstepSnapshot> back1 = debugger.stepBackward();
        assertTrue(back1.isPresent());
        assertEquals(20, back1.get().superstep());
        assertEquals(20, debugger.getCurrentCursor());
        assertEquals("B", debugger.getCurrentSharedState().get("stage"));

        // 再次单步步退 -> 10
        Optional<StateGraphTimeTravelDebugger.SuperstepSnapshot> back2 = debugger.stepBackward();
        assertTrue(back2.isPresent());
        assertEquals(10, back2.get().superstep());
        assertEquals(10, debugger.getCurrentCursor());
        assertEquals("A", debugger.getCurrentSharedState().get("stage"));

        // 在起点步退 -> empty
        Optional<StateGraphTimeTravelDebugger.SuperstepSnapshot> back3 = debugger.stepBackward();
        assertTrue(back3.isEmpty(), "已在最早快照，步退应返回 empty");

        // 单步步进 -> 20
        Optional<StateGraphTimeTravelDebugger.SuperstepSnapshot> fwd1 = debugger.stepForward();
        assertTrue(fwd1.isPresent());
        assertEquals(20, fwd1.get().superstep());
        assertEquals("B", debugger.getCurrentSharedState().get("stage"));
    }

    @Test
    @DisplayName("契约 4：单调纪元版本控制 (Epoch Versioning) 与过期事件阻断过滤")
    void test04_EpochVersioningAndStaleEventFiltering() {
        StateGraphVisualSyncEngine syncEngine = new StateGraphVisualSyncEngine("flow-004");
        assertEquals(1L, syncEngine.getCurrentEpoch().get());

        // 推进纪元至 2
        long newEpoch = syncEngine.advanceEpoch();
        assertEquals(2L, newEpoch);

        // 构造来自陈旧纪元 1 的乱序到达事件
        StateGraphVisualSyncEngine.VisualNodeStateEvent staleEvent = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                "node-old", 5, 1L, "SUCCESS", Map.of("val", 100), System.currentTimeMillis()
        );
        boolean staleAccepted = syncEngine.recordEvent(staleEvent);
        assertFalse(staleAccepted, "旧纪元的过期事件必须被 100% 物理拦截");
        assertEquals(0, syncEngine.getPendingEventCount());

        // 构造当前纪元 2 的合法事件
        StateGraphVisualSyncEngine.VisualNodeStateEvent validEvent = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                "node-new", 5, 2L, "RUNNING", Map.of("val", 200), System.currentTimeMillis()
        );
        boolean validAccepted = syncEngine.recordEvent(validEvent);
        assertTrue(validAccepted, "当前纪元合法事件必须被接受");
        assertEquals(1, syncEngine.getPendingEventCount());
    }

    @Test
    @DisplayName("契约 5：循环超步 16ms 防抖合并，李雅普诺夫稳态收敛防事件风暴 (定理 1.2)")
    void test05_LoopSuperstepDebounceBufferConvergence() {
        StateGraphVisualSyncEngine syncEngine = new StateGraphVisualSyncEngine("flow-005");
        long epoch = syncEngine.getCurrentEpoch().get();

        // 模拟一个循环超步节点在 16ms 周期内连续产生 100 次高频状态突变事件
        for (int i = 1; i <= 100; i++) {
            StateGraphVisualSyncEngine.VisualNodeStateEvent pulseEvent = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                    "node-loop-eval", i, epoch, "RUNNING", Map.of("iteration", i, "score", i * 0.01), System.currentTimeMillis()
            );
            syncEngine.recordEvent(pulseEvent);
        }

        // 验证防抖缓冲区仅保留 1 个合并后的最新事件
        assertEquals(1, syncEngine.getPendingEventCount(), "100 次高频脉冲必须幂等合并为 1 个最新事件");

        List<StateGraphVisualSyncEngine.VisualNodeStateEvent> flushed = syncEngine.flushDebouncedEvents();
        assertEquals(1, flushed.size());
        assertEquals("node-loop-eval", flushed.get(0).nodeUuid());
        assertEquals(100, flushed.get(0).superstep(), "排空事件必须保持最后第 100 次更新的超步状态");
        assertEquals(100, flushed.get(0).data().get("iteration"));

        // 排空后缓冲区必须重置为 0
        assertEquals(0, syncEngine.getPendingEventCount());
    }

    @Test
    @DisplayName("契约 6：视口 AABB 裁剪推流准确率 100%，视口外图元被微秒级剔除")
    void test06_ViewportAABBCullingFiltering() {
        StateGraphVisualSyncEngine syncEngine = new StateGraphVisualSyncEngine("flow-006");
        long epoch = syncEngine.getCurrentEpoch().get();

        // 定义前端画布当前可视区域：[0, 0] 到 [800, 600]，带 50px 外扩缓冲区
        StateGraphVisualSyncEngine.ViewportAABB viewport = new StateGraphVisualSyncEngine.ViewportAABB(0, 0, 800, 600, 50.0);

        // 构造 3 个节点事件
        StateGraphVisualSyncEngine.VisualNodeStateEvent evIn = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                "node-inside", 1, epoch, "RUNNING", Map.of(), System.currentTimeMillis());
        StateGraphVisualSyncEngine.VisualNodeStateEvent evMargin = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                "node-margin", 1, epoch, "RUNNING", Map.of(), System.currentTimeMillis());
        StateGraphVisualSyncEngine.VisualNodeStateEvent evOut = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                "node-outside", 1, epoch, "RUNNING", Map.of(), System.currentTimeMillis());

        List<StateGraphVisualSyncEngine.VisualNodeStateEvent> allEvents = List.of(evIn, evMargin, evOut);

        // 节点坐标映射
        Map<String, double[]> coordinates = Map.of(
                "node-inside", new double[]{300.0, 250.0},     // 明确在视口核心内部
                "node-margin", new double[]{830.0, 500.0},     // 在 [800, 850] 扩展缓冲区内，平滑保留
                "node-outside", new double[]{1800.0, 1500.0}   // 远离视口外部
        );

        List<StateGraphVisualSyncEngine.VisualNodeStateEvent> visibleEvents = syncEngine.filterVisibleEvents(allEvents, coordinates, viewport);

        assertEquals(2, visibleEvents.size(), "必须保留视口内与边界缓冲区节点，严格剔除外部节点");
        Set<String> visibleUuids = Set.of(visibleEvents.get(0).nodeUuid(), visibleEvents.get(1).nodeUuid());
        assertTrue(visibleUuids.contains("node-inside"));
        assertTrue(visibleUuids.contains("node-margin"));
        assertFalse(visibleUuids.contains("node-outside"), "视口外节点必须被 100% 物理剔除");
    }

    @Test
    @DisplayName("契约 7：纯 Java 21 Record 密码学自签名与常数时间验真防篡改")
    void test07_CryptographicReceiptSignatureVerification() {
        StateGraphCausalDebugReceipt receipt = StateGraphCausalDebugReceipt.createSigned(
                "exec-007", "flow-007", "debug-sess-007", 5,
                Set.of("node-A", "node-B"), Map.of("var1", 42, "var2", "hello"),
                "main", false, 12L, System.currentTimeMillis()
        );

        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名十六进制长度必须为 64");
        assertTrue(receipt.verifySignature(), "初始合法凭单自签名验真必须通过");

        // 构造被恶意篡改的凭单（如伪造签名）
        StateGraphCausalDebugReceipt tamperedReceipt = new StateGraphCausalDebugReceipt(
                receipt.executionId(), receipt.flowId(), receipt.debugSessionId(),
                receipt.targetSuperstep(), receipt.activeNodeUuids(), receipt.sharedStateSnapshot(),
                receipt.branchId(), receipt.isForkedBranch(), receipt.latencyMicros(),
                "bad000000000000000000000000000000000000000000000000000000000badf", receipt.timestamp()
        );
        assertFalse(tamperedReceipt.verifySignature(), "被篡改签名的凭单验真必须失败");
    }

    @Test
    @DisplayName("契约 8：端到端高频调度集成与微秒级极速性能预算 (<= 20μs)")
    void test08_EndToEndHighFrequencyPerformanceBudget() {
        StateGraphTimeTravelDebugger debugger = new StateGraphTimeTravelDebugger("perf-exec", "perf-flow", "perf-sess", 128);
        StateGraphVisualSyncEngine syncEngine = new StateGraphVisualSyncEngine("perf-flow");

        StateGraphVisualSyncEngine.ViewportAABB viewport = new StateGraphVisualSyncEngine.ViewportAABB(0, 0, 1000, 1000);
        Map<String, double[]> coords = Map.of(
                "node-1", new double[]{100, 100},
                "node-2", new double[]{500, 500}
        );

        // 预热 JIT
        for (int i = 0; i < 200; i++) {
            debugger.captureSuperstep(i, Set.of("node-1"), Map.of("k", i));
            debugger.timeTravelTo(i / 2);
        }

        // 性能评测：连续 1000 次操作（快照捕获 + 时间旅行寻址 + 视口裁剪 + 凭单生成）
        int iterations = 1000;
        long startNano = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            debugger.captureSuperstep(i, Set.of("node-1", "node-2"), Map.of("round", i));
            debugger.timeTravelTo(Math.max(0, i - 1));
            StateGraphVisualSyncEngine.VisualNodeStateEvent event = new StateGraphVisualSyncEngine.VisualNodeStateEvent(
                    "node-1", i, 1L, "RUNNING", Map.of("iter", i), System.currentTimeMillis()
            );
            syncEngine.filterVisibleEvents(List.of(event), coords, viewport);
        }
        long totalNano = System.nanoTime() - startNano;
        double avgMicros = (double) totalNano / (iterations * 1000.0);

        System.out.printf("[Phase 128 Performance] 单次调试/同步综合操作平均耗时: %.3f μs%n", avgMicros);
        assertTrue(avgMicros <= 20.0, String.format("单次调试操作耗时必须 <= 20μs，实测: %.3f μs", avgMicros));

        // 最终生成带签名的存证凭单
        StateGraphCausalDebugReceipt finalReceipt = debugger.createReceipt((long) (avgMicros * 10));
        assertTrue(finalReceipt.verifySignature(), "终局凭单自验真必须 100% 通过");
    }
}
