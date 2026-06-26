package tech.qiantong.qknow.hermes.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 工作流状态持久化 — Durable Execution
 * 参考：LangGraph Durable Execution（35.8k⭐）
 *
 * 在每个节点执行前保存快照到 Redis，支持故障恢复和断点续跑。
 */
@Slf4j
public class FlowStateStore {

    private static final String KEY_PREFIX = "flow:state:";
    private static final long SNAPSHOT_TTL_SECONDS = 86400; // 24 hours

    private final IRedisService redisService;

    public FlowStateStore(IRedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 保存执行快照
     */
    public void saveSnapshot(String flowId, String requestId, FlowSnapshot snapshot) {
        if (redisService == null) return;
        try {
            String key = KEY_PREFIX + flowId + ":" + requestId;
            String json = JSON.toJSONString(snapshot);
            redisService.set(key, json, SNAPSHOT_TTL_SECONDS);
            log.debug("Flow snapshot saved: {}", key);
        } catch (Exception e) {
            log.warn("Failed to save flow snapshot", e);
        }
    }

    /**
     * 加载执行快照
     */
    public FlowSnapshot loadSnapshot(String flowId, String requestId) {
        if (redisService == null) return null;
        try {
            String key = KEY_PREFIX + flowId + ":" + requestId;
            String json = (String) redisService.get(key);
            if (json == null) return null;
            return JSON.parseObject(json, FlowSnapshot.class);
        } catch (Exception e) {
            log.warn("Failed to load flow snapshot", e);
            return null;
        }
    }

    /**
     * 清除快照
     */
    public void clearSnapshot(String flowId, String requestId) {
        if (redisService == null) return;
        try {
            String key = KEY_PREFIX + flowId + ":" + requestId;
            redisService.delete(key);
        } catch (Exception e) {
            log.warn("Failed to clear flow snapshot", e);
        }
    }

    /**
     * 工作流快照
     */
    @Data
    public static class FlowSnapshot {
        private String flowId;
        private String requestId;
        private String currentNodeId;
        private List<String> completedNodeIds;
        private List<NodeRunResultBO> results;
        private JSONObject contextVariables;
        private long timestamp;
        private String status; // RUNNING, COMPLETED, FAILED

        public static FlowSnapshot create(String flowId, String requestId) {
            FlowSnapshot snapshot = new FlowSnapshot();
            snapshot.setFlowId(flowId);
            snapshot.setRequestId(requestId);
            snapshot.setCompletedNodeIds(new ArrayList<>());
            snapshot.setResults(new ArrayList<>());
            snapshot.setContextVariables(new JSONObject());
            snapshot.setTimestamp(System.currentTimeMillis());
            snapshot.setStatus("RUNNING");
            return snapshot;
        }
    }
}
