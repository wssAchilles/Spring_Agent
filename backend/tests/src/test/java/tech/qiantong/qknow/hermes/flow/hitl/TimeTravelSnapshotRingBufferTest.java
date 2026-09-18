package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.dto.WorkflowNodeStateSnapshot;
import tech.qiantong.qknow.hermes.flow.hitl.engine.TimeTravelSnapshotRingBuffer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeTravelSnapshotRingBuffer 环形快照池与时空回溯测试")
class TimeTravelSnapshotRingBufferTest {

    private TimeTravelSnapshotRingBuffer ringBuffer;

    @BeforeEach
    void setUp() {
        // 使用定长容量 M = 5 进行边界测试
        ringBuffer = new TimeTravelSnapshotRingBuffer(5);
    }

    private WorkflowNodeStateSnapshot createMockSnapshot(String id, String parentId, String nodeName, Map<String, Object> delta) {
        return new WorkflowNodeStateSnapshot(
                id,
                "wf-main",
                "main-branch",
                "node-" + id,
                nodeName,
                parentId,
                delta,
                "in-hash",
                "out-hash",
                null,
                System.currentTimeMillis() * 1000L
        );
    }

    @Test
    @DisplayName("测试1: 环形快照池定长容量驱逐与内存一致有界性")
    void testRingBufferBoundedEviction() {
        assertEquals(5, ringBuffer.getCapacity());

        // 连续写入 8 个快照
        for (int i = 1; i <= 8; i++) {
            ringBuffer.recordSnapshot(createMockSnapshot("S" + i, i > 1 ? "S" + (i - 1) : null, "Node" + i, Map.of("step", i)));
        }

        // 大小必须被限制在 5
        assertEquals(5, ringBuffer.size(), "环形缓冲区大小必须严格不超过容量上限 5");

        // 最早的 S1, S2, S3 应已被驱逐淘汰
        assertNull(ringBuffer.findSnapshotById("S1"), "S1 应被驱逐以释放内存");
        assertNull(ringBuffer.findSnapshotById("S2"), "S2 应被驱逐以释放内存");
        assertNull(ringBuffer.findSnapshotById("S3"), "S3 应被驱逐以释放内存");

        // 活跃的应为 S4 ~ S8
        assertNotNull(ringBuffer.findSnapshotById("S4"));
        assertNotNull(ringBuffer.findSnapshotById("S8"));
        assertEquals("S8", ringBuffer.getLatestSnapshot().snapshotId());
    }

    @Test
    @DisplayName("测试2: 时光旅行重构算子 100% 偏序一致且只读防御")
    void testReconstructStateAtSnapshotConsistency() {
        // 构建 S1 -> S2 -> S3 依赖链
        ringBuffer.recordSnapshot(createMockSnapshot("S1", null, "InitNode", Map.of("varA", 100, "user", "alice")));
        ringBuffer.recordSnapshot(createMockSnapshot("S2", "S1", "CalcNode", Map.of("varB", 200, "varA", 150))); // varA 被覆盖为 150
        ringBuffer.recordSnapshot(createMockSnapshot("S3", "S2", "FinishNode", Map.of("varC", 300)));

        // 回溯重构 S2 时刻的状态
        Map<String, Object> stateAtS2 = ringBuffer.reconstructStateAt("S2");
        assertNotNull(stateAtS2);
        assertEquals(150, stateAtS2.get("varA"), "S2 时刻 varA 应为覆盖后的 150");
        assertEquals(200, stateAtS2.get("varB"), "S2 时刻 varB 应为 200");
        assertEquals("alice", stateAtS2.get("user"), "S2 时刻 user 应继承自 S1");
        assertFalse(stateAtS2.containsKey("varC"), "S2 时刻不得包含未来时刻 S3 的变量 varC（零时间污染）");

        // 验证只读防御（不可变约束）
        assertThrows(UnsupportedOperationException.class, () -> stateAtS2.put("maliciousKey", "evilValue"),
                "重构的历史状态必须为不可变视图，防止被外部原地篡改");
    }

    @Test
    @DisplayName("测试3: 分叉派生 (Fork Branching) 隔离历史时间线")
    void testForkBranchingIsolation() {
        ringBuffer.recordSnapshot(createMockSnapshot("S1", null, "Node1", Map.of("k1", "v1")));
        ringBuffer.recordSnapshot(createMockSnapshot("S2", "S1", "Node2", Map.of("k2", "v2")));

        // 从 S2 派生新分支 branch-dev-1
        WorkflowNodeStateSnapshot forkedSnap = ringBuffer.forkBranch(
                "S2",
                "branch-dev-1",
                "node-fork-01",
                "ForkNode",
                Map.of("k2", "v2_modified", "forkFlag", true)
        );

        assertNotNull(forkedSnap);
        assertEquals("branch-dev-1", forkedSnap.branchId());
        assertEquals("S2", forkedSnap.parentSnapshotId());
        assertEquals("v2_modified", forkedSnap.deltaVariables().get("k2"));
        assertEquals(true, forkedSnap.deltaVariables().get("forkFlag"));

        // 验证原 S2 快照变量完全未被改变
        WorkflowNodeStateSnapshot originalS2 = ringBuffer.findSnapshotById("S2");
        assertNotNull(originalS2);
        assertEquals("v2", originalS2.deltaVariables().get("k2"), "原快照属性不得被分叉修改所污染");
        assertFalse(originalS2.deltaVariables().containsKey("forkFlag"));
    }
}
