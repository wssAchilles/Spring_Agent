package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-14 HITL 恢复回归：挂起节点被唤醒后必须从“已完成”集合移除并重新执行，
 * 绝不能把挂起工具直接标记 SUCCESS 后跳过真正的工具调用。
 */
class HitlCheckpointReplayTest {

    @Test
    @DisplayName("挂起节点唤醒后被移出完成集，恢复时重新执行该节点")
    void suspendedNodeRemovedOnWakeForReExecution() {
        DagCheckpointManager manager = new DagCheckpointManager();

        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        NodeRunResultBO start = NodeRunResultBO.success("start_1", "开始", Map.of("query", "q"));
        NodeRunResultBO tool = NodeRunResultBO.suspended("tool_1", "工具调用",
                Map.of("status", "SUSPENDED", "tool", "mcp.github.search_repositories", "replayOnWake", true));
        results.put("start_1", start);
        results.put("tool_1", tool);

        manager.saveCheckpointWithVariables("r-1", "bot-9", 5, results, Map.of("query", "q"));
        assertTrue(manager.wakeSuspended("r-1", Map.of("approvalKey", "9:r-1:tool_1")));

        DagCheckpointManager.DagCheckpoint checkpoint = manager.loadCheckpoint("r-1");
        assertNotNull(checkpoint);
        assertEquals(5, checkpoint.getGroupIndex());

        Map<String, NodeRunResultBO> restored = manager.restoreCompletedResults(checkpoint);
        assertTrue(restored.containsKey("start_1"));
        assertFalse(restored.containsKey("tool_1"),
                "挂起工具节点必须重新执行，不能以 SUCCESS 身份留在完成集中");
        assertFalse(manager.hasSuspendedResult(restored));

        Map<String, Object> vars = manager.restoreVariables(checkpoint);
        assertEquals("9:r-1:tool_1", vars.get("approvalKey"));
    }

    @Test
    @DisplayName("已无挂起节点时重复唤醒返回 false")
    void secondWakeReturnsFalse() {
        DagCheckpointManager manager = new DagCheckpointManager();
        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        results.put("tool_1", NodeRunResultBO.suspended("tool_1", "工具调用", Map.of("status", "SUSPENDED")));
        manager.saveCheckpointWithVariables("r-2", "bot-9", 3, results, Map.of());

        assertTrue(manager.wakeSuspended("r-2", Map.of()));
        assertFalse(manager.wakeSuspended("r-2", Map.of()));
    }
}
