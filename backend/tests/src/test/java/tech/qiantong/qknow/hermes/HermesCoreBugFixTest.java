package tech.qiantong.qknow.hermes;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.guard.ReActStrictWindowCycleGuard;
import tech.qiantong.qknow.hermes.agent.guard.SwarmLoopGuard;
import tech.qiantong.qknow.hermes.flow.checkpoint.LeaseLivenessRecoveryCoordinator;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.impl.JdbcStateCheckpointStorage;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.hermes.infra.prompt.CanonicalPromptCompiler;
import tech.qiantong.qknow.hermes.judge.JudgeConfig;
import tech.qiantong.qknow.hermes.rag.causal.SteinerCausalSubgraphPruner;
import tech.qiantong.qknow.hermes.rag.causal.SteinerCausalSubgraphPruner.GraphEdge;
import tech.qiantong.qknow.hermes.rag.causal.SteinerCausalSubgraphPruner.GraphNode;
import tech.qiantong.qknow.hermes.rag.causal.SteinerCausalSubgraphPruner.SteinerSubgraphResult;
import tech.qiantong.qknow.hermes.tool.hnsw.impl.DefaultHypersphericalHnswToolIndex;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 战役三（3.1 ~ 3.7）：Hermes 状态检查点、Steiner 因果剪枝、HNSW 反向边、循环守卫、比较器传递性、Hoare 网关与租约自愈回归测试
 */
class HermesCoreBugFixTest {

    @Test
    @DisplayName("3.1 JdbcStateCheckpointStorage: 首次 INSERT 与二次 UPDATE 的 14 个占位符下标必须完全对齐")
    void jdbcStateCheckpointStorage_insertsAndUpdatesWithoutIndexMismatch() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:hermes_ckpt_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS hermes_flow_checkpoints (
                        runtime_id VARCHAR(255) PRIMARY KEY,
                        flow_id VARCHAR(255) NOT NULL,
                        group_index INT NOT NULL,
                        completed_results_json TEXT,
                        variables_json TEXT,
                        status VARCHAR(64) NOT NULL,
                        compensation_log TEXT,
                        lease_owner_id VARCHAR(128),
                        lease_expire_at_ms BIGINT NOT NULL,
                        fencing_token BIGINT NOT NULL,
                        version INT NOT NULL,
                        created_at_ms BIGINT NOT NULL,
                        updated_at_ms BIGINT NOT NULL,
                        integrity_hmac BYTEA
                    )
                    """);
        }

        JdbcStateCheckpointStorage storage = new JdbcStateCheckpointStorage(ds);
        byte[] hmac1 = "hmac_v1".getBytes(StandardCharsets.UTF_8);
        StateCheckpointRecord initial = new StateCheckpointRecord(
                "rt-1001", "flow-alpha", 2, "{\"n1\":\"ok\"}", "{\"x\":1}",
                "RUNNING", "", "worker-1", 999999L, 1L, 1, 1000L, 2000L, hmac1
        );

        assertTrue(storage.saveCheckpointWithFencing(initial, 1L), "首次 INSERT 检查点必须成功");

        Optional<StateCheckpointRecord> loaded1 = storage.loadCheckpoint("rt-1001");
        assertTrue(loaded1.isPresent());
        assertEquals("flow-alpha", loaded1.get().flowId());
        assertEquals(2, loaded1.get().groupIndex());
        assertEquals(1000L, loaded1.get().createdAtMs());
        assertEquals(2000L, loaded1.get().updatedAtMs());
        assertArrayEquals(hmac1, loaded1.get().integrityHmac());

        // 二次 UPDATE 并校验 created_at_ms 保持不变且新字段更新成功
        StateCheckpointRecord updated = new StateCheckpointRecord(
                "rt-1001", "flow-alpha", 3, "{\"n1\":\"ok\",\"n2\":\"ok\"}", "{\"x\":2}",
                "SUSPENDED", "comp", "worker-2", 888888L, 2L, 2, 1000L, 3000L, hmac1
        );
        assertTrue(storage.saveCheckpointWithFencing(updated, 2L), "带更高 fencingToken 的 UPDATE 必须成功");
        StateCheckpointRecord loaded2 = storage.loadCheckpoint("rt-1001").orElseThrow();
        assertEquals(3, loaded2.groupIndex());
        assertEquals(1000L, loaded2.createdAtMs());
        assertEquals(3000L, loaded2.updatedAtMs());
    }

    @Test
    @DisplayName("3.2 SteinerCausalSubgraphPruner: 无向化最短路展开后必须恢复原图真实因果有向边方向")
    void steinerCausalSubgraphPruner_preservesOriginalDirectedCausalEdges() {
        SteinerCausalSubgraphPruner pruner = new SteinerCausalSubgraphPruner();
        float[] vA = new float[1024];
        float[] vB = new float[1024];
        float[] vC = new float[1024];
        vA[0] = 1.0f;
        vB[0] = 0.8f; vB[1] = 0.6f;
        vC[1] = 1.0f;

        List<GraphNode> nodes = List.of(
                new GraphNode("A", "EffectA", "concept", vA),
                new GraphNode("B", "RootCauseB", "concept", vB),
                new GraphNode("C", "EffectC", "concept", vC)
        );
        // 真实因果方向为 B -> A 与 B -> C（A 没有指向 B 的边！）
        List<GraphEdge> edges = List.of(
                new GraphEdge("B", "A", "CAUSES", 0.2),
                new GraphEdge("B", "C", "CAUSES", 0.2)
        );

        SteinerSubgraphResult res = pruner.extractSteinerSkeleton(List.of("A", "C"), nodes, edges, 16);
        assertEquals(2, res.steinerEdges().size());
        for (GraphEdge e : res.steinerEdges()) {
            assertEquals("B", e.source(), "输出的 Steiner 骨架边必须保持真实因果源节点 B -> *，严禁反转为 A -> B");
        }
    }

    @Test
    @DisplayName("3.3 DefaultHypersphericalHnswToolIndex: 反向边超 maxM 时必须淘汰最远邻居而非丢弃更近的新邻居")
    @SuppressWarnings("unchecked")
    void hnswToolIndex_prunesFarthestNeighborInsteadOfDroppingCloserIncomingEdge() throws Exception {
        DefaultHypersphericalHnswToolIndex index = new DefaultHypersphericalHnswToolIndex();
        // 先插入中心节点 T0 = e_0
        float[] center = new float[1024];
        center[0] = 1.0f;
        index.insert("tool_center", center, false);

        // 插入 32 个几乎正交的远距节点填满 T0 的第 0 层邻居上限 DEFAULT_M0 = 32
        for (int i = 1; i <= 32; i++) {
            float[] far = new float[1024];
            far[0] = 0.01f;
            far[i] = (float) Math.sqrt(1.0 - 0.01 * 0.01);
            index.insert("tool_far_" + i, far, false);
        }

        // 再插入第 33 个与 T0 夹角极小（cos = 0.9998）的极近邻节点 tool_very_close
        float[] veryClose = new float[1024];
        veryClose[0] = 0.9998f;
        veryClose[100] = (float) Math.sqrt(1.0 - 0.9998 * 0.9998);
        index.insert("tool_very_close", veryClose, false);

        // 检查 T0 (nodeId = 0) 的第 0 层邻接表中必须包含刚插入的极近邻 (nodeId = 33)
        Field nodesField = DefaultHypersphericalHnswToolIndex.class.getDeclaredField("nodesById");
        nodesField.setAccessible(true);
        Map<Integer, Object> nodesById = (Map<Integer, Object>) nodesField.get(index);
        Object centerNode = nodesById.get(0);
        Field neighborsField = centerNode.getClass().getDeclaredField("neighbors");
        neighborsField.setAccessible(true);
        List<List<Integer>> neighbors = (List<List<Integer>>) neighborsField.get(centerNode);
        List<Integer> level0Neighbors = neighbors.get(0);

        assertEquals(32, level0Neighbors.size(), "第 0 层邻居数应受控在 DEFAULT_M0 = 32");
        assertTrue(level0Neighbors.contains(33),
                "反向边修剪应淘汰最远的正交邻居并保留 cos=0.9998 的极近邻节点 33");
    }

    @Test
    @DisplayName("3.4 SwarmLoopGuard & ReActStrictWindowCycleGuard: 修复 W+1 幽灵计数与被拒调用误缩窗漏洞")
    @SuppressWarnings("unchecked")
    void cycleGuards_fixPhantomSeventhElementAndRejectedCallWindowShrink() throws Exception {
        // A. SwarmLoopGuard: 窗口容量 W=6，第 1 次 "dup" 在经过 5 个不同消息后应滑出窗口，第 7 步再出现仅计 2 次 (<3) 不得误熔断
        SwarmLoopGuard swarmGuard = new SwarmLoopGuard();
        swarmGuard.inspectOutput("s1", "dup_msg");
        swarmGuard.inspectOutput("s1", "dup_msg");
        for (int i = 1; i <= 5; i++) {
            swarmGuard.inspectOutput("s1", "unique_" + i);
        }
        assertDoesNotThrow(() -> swarmGuard.inspectOutput("s1", "dup_msg"),
                "最早的 dup_msg 已滑出容量 6 的窗口，当前真实窗口内仅 2 次重复，不得因 W+1=7 幽灵计数误熔断");

        // B. ReActStrictWindowCycleGuard: 被拦截的调用不得提前淘汰最老合法元素导致窗口缩水
        ReActStrictWindowCycleGuard reactGuard = new ReActStrictWindowCycleGuard(20, 2, 4, 3);
        assertFalse(reactGuard.inspectToolCall("t1", "{\"k\":1}").tripped());
        assertFalse(reactGuard.inspectToolCall("t2", "{\"k\":2}").tripped());
        assertFalse(reactGuard.inspectToolCall("t3", "{\"k\":3}").tripped());
        assertFalse(reactGuard.inspectToolCall("t4", "{\"k\":4}").tripped());

        // 此时窗口满 4 个 [t1, t2, t3, t4]；再次调用 t2 触发 maxRepeatedCalls=2 拦截
        assertTrue(reactGuard.inspectToolCall("t2", "{\"k\":2}").tripped());

        Field dequeField = ReActStrictWindowCycleGuard.class.getDeclaredField("callFingerprints");
        dequeField.setAccessible(true);
        java.util.Deque<String> window = (java.util.Deque<String>) dequeField.get(reactGuard);
        assertEquals(4, window.size(), "被拦截的工具调用不得破坏已有滑动窗口，窗口大小必须保持 4 而非缩水为 3");
    }

    @Test
    @DisplayName("3.5 CanonicalPromptCompiler: KnowledgeChunk.compareTo 必须满足严格弱序传递性")
    void canonicalPromptCompiler_knowledgeChunkSatisfiesTransitivity() {
        CanonicalPromptCompiler.KnowledgeChunk a = new CanonicalPromptCompiler.KnowledgeChunk("c", "t", "x", 0.80009);
        CanonicalPromptCompiler.KnowledgeChunk b = new CanonicalPromptCompiler.KnowledgeChunk("b", "t", "x", 0.80004);
        CanonicalPromptCompiler.KnowledgeChunk c = new CanonicalPromptCompiler.KnowledgeChunk("a", "t", "x", 0.79998);

        // 严格弱序传递性验证：不能出现 a > b, b > c 但 a < c 的环！
        int ab = Integer.signum(a.compareTo(b));
        int bc = Integer.signum(b.compareTo(c));
        int ac = Integer.signum(a.compareTo(c));
        assertTrue(ab < 0 && bc < 0 && ac < 0,
                "按相关度降序排列时 a(0.80009) < b(0.80004) < c(0.79998) 必须满足严格传递性");
    }

    @Test
    @DisplayName("3.6 HoareToolContractGateway & JudgeConfig: 无回滚器时不得虚标 compensated=true 且 JudgeConfig 具备开关字段")
    void hoareGatewayAndJudgeConfig_accurateCompensationFlagAndConfigProperties() throws Exception {
        HoareToolContractGateway gateway = new HoareToolContractGateway("test-hoare-secret");
        HoareToolContractGateway.ExecutionOutcome outcomeNoRollback = gateway.execute(
                "risky_tool", "exec-1", Map.of(), Map.of(), Map.of(),
                params -> { throw new RuntimeException("boom"); },
                null,
                Map::of
        );
        assertFalse(outcomeNoRollback.success());
        assertFalse(outcomeNoRollback.receipt().compensated(),
                "未提供 rollbackAction 时，工具抛异常不得虚标 compensated=true");

        // 提供会抛异常的 rollbackAction 时，网关必须捕获回滚异常并返回 compensated=false 而非向外崩溃
        HoareToolContractGateway.ExecutionOutcome outcomeFailingRollback = assertDoesNotThrow(() -> gateway.execute(
                "risky_tool", "exec-2", Map.of(), Map.of(), Map.of(),
                params -> { throw new RuntimeException("boom"); },
                pre -> { throw new RuntimeException("rollback_failed"); },
                Map::of
        ));
        assertFalse(outcomeFailingRollback.receipt().compensated());

        // 验证 JudgeConfig 支持 application.yml 中的 enabled / minScore / maxReflectionRounds 绑定
        Method isEnabled = JudgeConfig.class.getMethod("isEnabled");
        assertNotNull(isEnabled);
    }

    @Test
    @DisplayName("3.7 DagCheckpointManager 冷备环形软删除恢复与 LeaseLivenessRecoveryCoordinator 自愈回调钩子")
    void checkpointColdStandbyAndLeaseRecoveryCallback_workEndToEnd() throws Exception {
        DagCheckpointManager manager = new DagCheckpointManager();
        manager.saveCheckpointWithVariables("rt-cold-1", "flow-1", 1, Map.of(), Map.of("k", "v"));
        assertNotNull(manager.loadCheckpoint("rt-cold-1"));

        // 删除后活跃表不可见，但冷备环形隔离区可恢复
        manager.deleteCheckpoint("rt-cold-1");
        assertNull(manager.loadCheckpoint("rt-cold-1"));
        Method restoreMethod = DagCheckpointManager.class.getMethod("restoreFromColdStandby", String.class);
        Object restored = restoreMethod.invoke(manager, "rt-cold-1");
        assertNotNull(restored, "三级冷备环形缓冲区必须支持找回被淘汰/删除的检查点");

        // 验证 LeaseLivenessRecoveryCoordinator 在抢占超时租约后触发注册的恢复回调
        manager.saveCheckpointWithVariables("rt-expired-1", "flow-2", 2, Map.of(), Map.of());
        DagCheckpointManager.DagCheckpoint cp = manager.loadCheckpoint("rt-expired-1");
        cp.setStatus("RUNNING");
        cp.setLeaseOwnerId("dead-node");
        cp.setLeaseExpireAt(new Timestamp(System.currentTimeMillis() - 5_000L));

        LeaseLivenessRecoveryCoordinator coordinator = new LeaseLivenessRecoveryCoordinator(manager, "rescuer-node", 10_000L);
        AtomicInteger callbackCount = new AtomicInteger(0);
        Method registerListener = LeaseLivenessRecoveryCoordinator.class.getMethod(
                "registerRecoveryListener", java.util.function.Consumer.class);
        registerListener.invoke(coordinator, (java.util.function.Consumer<?>) r -> callbackCount.incrementAndGet());

        var receipts = coordinator.recoverExpiredLeases();
        assertEquals(1, receipts.size());
        assertEquals(1, callbackCount.get(), "自愈接管超时断点后必须通知已注册的恢复执行器回调");
        coordinator.close();
    }
}
