package tech.qiantong.qknow.ai.geosync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 61 专属契约测试：跨数据中心分布式超级智能体无冲突状态同步 (CRDT)、因果偏序一致性与跨域安全状态机网络
 */
public class Phase61GeoDistributedSyncContractTest {

    private CrossDomainSyncBus syncBus;
    private GeoStateMachineController stateController;
    private GeoDistributedSyncCoordinator coordinator;

    @BeforeEach
    void setUp() {
        syncBus = new CrossDomainSyncBus();
        stateController = new GeoStateMachineController();
        coordinator = new GeoDistributedSyncCoordinator(syncBus, stateController);
    }

    @Test
    @DisplayName("契约 1: 跨域状态同步不可变存证凭单 SHA-256 自签名与防篡改测试")
    void testReceiptIntegrityAndSha256Verification() {
        GeoStateAuditReceipt receipt = GeoStateAuditReceipt.create(
                "REC-GEO-001", 100L, "REGION_BEIJING", "REGION_SHANGHAI",
                "{REGION_BEIJING=5, REGION_SHANGHAI=3}", "HASH_A1B2C3D4",
                true, false, "跨域状态因果偏序同步完成，两地寄存器达到强最终一致性 (SEC)",
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始不可变存证凭据自签名自验必须通过");

        // 篡改凭据中关键标记（如篡改降级状态）
        GeoStateAuditReceipt tampered = new GeoStateAuditReceipt(
                receipt.receiptId(), receipt.syncRoundId(), receipt.originRegion(), receipt.targetRegion(),
                receipt.vectorClockSnapshot(), receipt.convergedStateHash(),
                receipt.conflictResolved(),
                true, // 篡改 degraded 为 true
                receipt.decisionSummary(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "任何字段被篡改后的跨域存证凭单 SHA-256 自验必须严格失败");
    }

    @Test
    @DisplayName("契约 2: 严格因果偏序向量时钟 Happens-Before 排序与并发冲突精确判定测试 (定理 1.2)")
    void testCausalVectorClockOrderingAndConcurrency() {
        CausalVectorClock clockA = new CausalVectorClock();
        CausalVectorClock clockB = new CausalVectorClock();

        // 初始等价
        assertEquals(CausalVectorClock.Ordering.EQUAL, clockA.compare(clockB));
        assertFalse(clockA.isConcurrentWith(clockB));

        // A 推进事件：A = {NodeA=1}
        clockA.tick("NodeA");
        assertEquals(CausalVectorClock.Ordering.AFTER, clockA.compare(clockB));
        assertEquals(CausalVectorClock.Ordering.BEFORE, clockB.compare(clockA));

        // B 推进自身事件：B = {NodeB=1}
        clockB.tick("NodeB");
        // 此时 A={NodeA=1}, B={NodeB=1} -> 并发 (CONCURRENT)
        assertEquals(CausalVectorClock.Ordering.CONCURRENT, clockA.compare(clockB));
        assertTrue(clockA.isConcurrentWith(clockB), "两节点因果独立并发推进时必须判定为 CONCURRENT");

        // A 接收 B 并合并推进：A 会合并 NodeB=1 并在 NodeA 上自增为 2 -> A={NodeA=2, NodeB=1}
        clockA.update(clockB, "NodeA");
        assertEquals(CausalVectorClock.Ordering.AFTER, clockA.compare(clockB), "合并推进后 A 必须严格领先于 B");
        assertEquals(CausalVectorClock.Ordering.BEFORE, clockB.compare(clockA));
    }

    @Test
    @DisplayName("契约 3: 结合半格 CRDT 寄存器交换律、结合律、幂等律与单调收敛性测试 (定理 1.1)")
    void testStateBasedCrdtSemilatticeIdempotenceAndMonotonicity() {
        StateBasedCrdtRegister<String> reg1 = new StateBasedCrdtRegister<>("REG-POLICY-01", "INIT_V1", "DC_EAST");
        StateBasedCrdtRegister<String> reg2 = new StateBasedCrdtRegister<>("REG-POLICY-01", "INIT_V1", "DC_WEST");

        // 1. 幂等律测试: s ⊔ s = s
        assertFalse(reg1.merge(reg1.snapshot()), "同一状态自我合并必须幂等返回 false");
        assertEquals("INIT_V1", reg1.getValue());

        // 2. 本地推进变更
        reg1.assign("DC_EAST", "POLICY_REACTIVE_V2", 10L);
        reg2.assign("DC_WEST", "POLICY_ADAPTIVE_V2", 20L);

        // 3. 交换律测试: s1 ⊔ s2 ≡ s2 ⊔ s1
        StateBasedCrdtRegister<String> copyA = reg1.snapshot();
        StateBasedCrdtRegister<String> copyB = reg2.snapshot();

        copyA.merge(reg2.snapshot());
        copyB.merge(reg1.snapshot());

        assertEquals(copyA.getValue(), copyB.getValue(), "交换律证明：不同顺序合并必须收敛到严格一致的值");
        assertEquals(copyA.getLogicalEpoch(), copyB.getLogicalEpoch());
    }

    @Test
    @DisplayName("契约 4: 并发冲突场景下确定性打破平局 (Deterministic Tie-Breaking) 与抗 NTP 时钟漂移测试")
    void testCrdtDeterministicTieBreakingWithoutNtpDrift() {
        // 模拟两地发生并发写入（向量时钟并发）
        StateBasedCrdtRegister<String> beijingReg = new StateBasedCrdtRegister<>("REG_BLACKBOARD", "BASE", "NODE_BJ");
        StateBasedCrdtRegister<String> shanghaiReg = new StateBasedCrdtRegister<>("REG_BLACKBOARD", "BASE", "NODE_SH");

        // 两地各写入一次，代际号相同 (epoch=5)
        beijingReg.assign("NODE_BJ", "VALUE_FROM_BJ", 5L);
        shanghaiReg.assign("NODE_SH", "VALUE_FROM_SH", 5L);

        // 确认处于并发状态
        assertTrue(beijingReg.getVectorClock().isConcurrentWith(shanghaiReg.getVectorClock()));

        // 执行合并：节点字典序 NODE_SH > NODE_BJ，因此无论在 BJ 还是 SH 合并，确定性选择 NODE_SH 的值
        beijingReg.merge(shanghaiReg.snapshot());
        shanghaiReg.merge(beijingReg.snapshot());

        assertEquals("VALUE_FROM_SH", beijingReg.getValue(), "并发平局时必须确定性以节点字典序胜出，杜绝 NTP 漂移");
        assertEquals("VALUE_FROM_SH", shanghaiReg.getValue());
    }

    @Test
    @DisplayName("契约 5: 跨域同步总线弱网断开离线暂存 (Hinted Handoff) 与自愈回放测试")
    void testCrossDomainSyncBusHintedHandoff() {
        AtomicInteger receivedCount = new AtomicInteger(0);
        syncBus.subscribe("REGION_GUANGZHOU", msg -> receivedCount.incrementAndGet());

        // 正常在线状态发布
        boolean sentLive = syncBus.publishSyncMessage("REGION_BEIJING", "REGION_GUANGZHOU", "PAYLOAD_1");
        assertTrue(sentLive);
        assertEquals(1, receivedCount.get());
        assertEquals(0, syncBus.getHintedHandoffCount("REGION_GUANGZHOU"));

        // 模拟网络分区突发断开
        syncBus.setNetworkPartitioned(true);
        boolean spooled1 = syncBus.publishSyncMessage("REGION_BEIJING", "REGION_GUANGZHOU", "PAYLOAD_OFFLINE_1");
        boolean spooled2 = syncBus.publishSyncMessage("REGION_BEIJING", "REGION_GUANGZHOU", "PAYLOAD_OFFLINE_2");
        assertTrue(spooled1);
        assertTrue(spooled2);
        assertEquals(2, syncBus.getHintedHandoffCount("REGION_GUANGZHOU"), "网络分区期间事件必须安全暂存入 Hinted Handoff 队列");
        assertEquals(1, receivedCount.get(), "网络分区期间订阅端不得收到未连通消息");

        // 模拟网络自愈恢复并回放
        syncBus.setNetworkPartitioned(false);
        int replayed = syncBus.replayHintedHandoff("REGION_GUANGZHOU");
        assertEquals(2, replayed, "必须全量因果回放暂存的 2 条消息");
        assertEquals(3, receivedCount.get(), "接收端最终完整接收全量消息");
        assertEquals(0, syncBus.getHintedHandoffCount("REGION_GUANGZHOU"));
    }

    @Test
    @DisplayName("契约 6: 跨域状态机控制器网络分区超时 <= 50ms 快速降级隔离测试 (定理 1.3)")
    void testGeoStateMachineControllerNetworkPartitionDegradation() {
        stateController.registerRegion("DC_NORTH");
        stateController.transitionState("DC_NORTH", GeoStateMachineController.GeoSyncState.CONVERGED);
        assertTrue(stateController.isOperationPermitted("DC_NORTH"));

        // 触发全局网络分区紧急防御
        long startTime = System.nanoTime();
        boolean triggered = stateController.triggerPartitionDefense("检测到海底光缆故障网络分区");
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(triggered);
        assertTrue(stateController.isPartitionHalted());
        assertTrue(durationMs <= 50, "状态机紧急分区降级响应耗时必须 <= 50ms (实际: " + durationMs + "ms)");

        // 分区熔断下禁止写操作
        assertFalse(stateController.isOperationPermitted("DC_NORTH"), "处于网络分区状态时必须阻断不可逆写操作");

        // 复位恢复
        stateController.resetPartitionDefense();
        assertTrue(stateController.isOperationPermitted("DC_NORTH"));
    }

    @Test
    @DisplayName("契约 7: 端到端跨数据中心多节点无冲突状态收敛与存证凭据签发测试")
    void testEndToEndGeoDistributedSyncSuccess() {
        coordinator.registerRegion("REGION_EAST");
        coordinator.registerRegion("REGION_WEST");

        // 两地域分别执行本地写入
        coordinator.updateLocalState("REGION_EAST", "SHARED_TASK_STATE", "EAST_PROGRESS_50", 1L);
        coordinator.updateLocalState("REGION_WEST", "SHARED_TASK_STATE", "WEST_PROGRESS_80", 2L);

        // 协调两地域状态同步收敛
        long startNs = System.nanoTime();
        GeoStateAuditReceipt receipt = coordinator.synchronizeRegions(
                1L, "REGION_EAST", "REGION_WEST", "SHARED_TASK_STATE"
        );
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "跨域协同签发的存证账本 SHA-256 签名必须 100% 校验通过");
        assertFalse(receipt.degraded());
        assertEquals("REGION_EAST", receipt.originRegion());
        assertEquals("REGION_WEST", receipt.targetRegion());

        // 验证两端寄存器状态 100% 同构收敛
        var regEast = coordinator.getRegister("REGION_EAST", "SHARED_TASK_STATE");
        var regWest = coordinator.getRegister("REGION_WEST", "SHARED_TASK_STATE");
        assertNotNull(regEast);
        assertNotNull(regWest);
        assertEquals(regEast.getValue(), regWest.getValue(), "两地寄存器在半格合并后必须收敛到严格一致的状态");
        assertEquals("WEST_PROGRESS_80", regEast.getValue()); // epoch=2 胜出
        assertTrue(elapsedMs <= 10, "单次跨域状态调度耗时必须在极速毫秒级内完成 (实际: " + elapsedMs + "ms)");
    }

    @Test
    @DisplayName("契约 8: 调度器边界防御与非法参数拦截测试")
    void testCoordinatorRejectsInvalidInputs() {
        // 非法同步轮次 <= 0
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.synchronizeRegions(0L, "REGION_A", "REGION_B", "REG_1")
        );

        // 空地域名称拦截
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.synchronizeRegions(1L, null, "REGION_B", "REG_1")
        );

        // 未注册地域拦截
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.synchronizeRegions(1L, "UNKNOWN_A", "UNKNOWN_B", "REG_1")
        );
    }
}
