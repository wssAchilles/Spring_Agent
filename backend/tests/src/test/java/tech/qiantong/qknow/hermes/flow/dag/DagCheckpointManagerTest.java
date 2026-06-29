package tech.qiantong.qknow.hermes.flow.dag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DagCheckpointManagerTest {

    @Test
    @DisplayName("恢复空检查点返回空Map")
    void restoreCompletedResults_withNull_returnsEmpty() {
        DagCheckpointManager manager = new DagCheckpointManager();
        DagCheckpointManager.DagCheckpoint checkpoint = new DagCheckpointManager.DagCheckpoint();
        checkpoint.setCompletedResultsJson(null);

        Map<String, NodeRunResultBO> restored = manager.restoreCompletedResults(checkpoint);
        assertTrue(restored.isEmpty());
    }

    @Test
    @DisplayName("DagCheckpoint数据类getter/setter正确")
    void dagCheckpoint_gettersSetters_work() {
        DagCheckpointManager.DagCheckpoint checkpoint = new DagCheckpointManager.DagCheckpoint();
        checkpoint.setRuntimeId("rt-1");
        checkpoint.setFlowId("flow-1");
        checkpoint.setGroupIndex(2);
        checkpoint.setCompletedResultsJson("{\"key\":\"value\"}");

        assertEquals("rt-1", checkpoint.getRuntimeId());
        assertEquals("flow-1", checkpoint.getFlowId());
        assertEquals(2, checkpoint.getGroupIndex());
        assertEquals("{\"key\":\"value\"}", checkpoint.getCompletedResultsJson());
    }
}
