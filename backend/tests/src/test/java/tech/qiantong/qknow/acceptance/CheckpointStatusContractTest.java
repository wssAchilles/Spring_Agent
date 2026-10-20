package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N3 回归防护：检查点保存不得覆盖生命周期状态，挂起/唤醒的 CAS 语义必须可重复成立。
 */
class CheckpointStatusContractTest {

    @Test
    @DisplayName("唤醒后再次保存检查点不得把状态重置回 SUSPENDED，二次挂起可再次唤醒")
    void saveMustPreserveLifecycleStatus() {
        DagCheckpointManager manager = new DagCheckpointManager();
        manager.init();

        String runtimeId = "rt_status_001";
        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        results.put("n1", NodeRunResultBO.suspended("n1", "审批节点", Map.of("reason", "等待审批")));

        manager.saveCheckpointWithVariables(runtimeId, "flow_1", 0, results, Map.of("q", "x"));
        assertEquals("SUSPENDED", manager.loadCheckpoint(runtimeId).getStatus());

        // 第一次唤醒
        assertTrue(manager.wakeSuspendedWithLock(runtimeId, Map.of("approvalApproved", true)));
        assertEquals("RUNNING", manager.loadCheckpoint(runtimeId).getStatus());

        // 唤醒后常规保存检查点：必须保留 RUNNING，否则 CAS 屏障失效
        Map<String, NodeRunResultBO> completed = new LinkedHashMap<>();
        completed.put("n1", NodeRunResultBO.success("n1", "审批节点", Map.of("ok", true)));
        manager.saveCheckpointWithVariables(runtimeId, "flow_1", 1, completed, Map.of("q", "x"));
        assertEquals("RUNNING", manager.loadCheckpoint(runtimeId).getStatus(),
                "保存检查点不得把 RUNNING 重置为 SUSPENDED");

        // 第二次挂起：显式 markSuspended 后必须能再次 CAS 唤醒
        assertTrue(manager.markSuspended(runtimeId));
        assertEquals("SUSPENDED", manager.loadCheckpoint(runtimeId).getStatus());

        Map<String, NodeRunResultBO> secondSuspend = new LinkedHashMap<>();
        secondSuspend.put("n1", NodeRunResultBO.suspended("n1", "审批节点", Map.of("reason", "再次审批")));
        manager.saveCheckpointWithVariables(runtimeId, "flow_1", 2, secondSuspend, Map.of("q", "x"));
        assertTrue(manager.wakeSuspendedWithLock(runtimeId, Map.of()), "二次挂起必须可以再次唤醒");
        assertEquals("RUNNING", manager.loadCheckpoint(runtimeId).getStatus());
    }
}
