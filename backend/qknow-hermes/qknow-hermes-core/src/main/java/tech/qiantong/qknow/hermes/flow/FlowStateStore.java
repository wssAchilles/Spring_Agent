package tech.qiantong.qknow.hermes.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 工作流状态持久化 — Durable Execution
 * 参考：LangGraph Durable Execution（35.8k⭐）
 *
 * 在每个节点执行前保存快照，支持故障恢复和断点续跑。
 * 优先使用 Redis 持久化，无 Redis 时降级为内存存储。
 */
@Slf4j
@Component
public class FlowStateStore {

    private static final String KEY_PREFIX = "flow:state:";
    private static final long SNAPSHOT_TTL_SECONDS = 86400; // 24 hours

    @Autowired(required = false)
    private tech.qiantong.qknow.redis.service.IRedisService redisService;

    // 内存降级存储
    private final Map<String, FlowSnapshot> memoryStore = new ConcurrentHashMap<>();

    /**
     * 保存执行快照
     */
    public void saveSnapshot(String flowId, String requestId, FlowSnapshot snapshot) {
        String key = flowId + ":" + requestId;
        try {
            if (redisService != null) {
                String json = JSON.toJSONString(snapshot);
                redisService.set(KEY_PREFIX + key, json, SNAPSHOT_TTL_SECONDS);
            } else {
                memoryStore.put(key, snapshot);
            }
            log.debug("Flow snapshot saved: {}", key);
        } catch (Exception e) {
            log.warn("Failed to save flow snapshot", e);
        }
    }

    /**
     * 加载执行快照
     */
    public FlowSnapshot loadSnapshot(String flowId, String requestId) {
        String key = flowId + ":" + requestId;
        try {
            if (redisService != null) {
                String json = (String) redisService.get(KEY_PREFIX + key);
                if (json == null) return null;
                return JSON.parseObject(json, FlowSnapshot.class);
            } else {
                return memoryStore.get(key);
            }
        } catch (Exception e) {
            log.warn("Failed to load flow snapshot", e);
            return null;
        }
    }

    /**
     * 清除快照
     */
    public void clearSnapshot(String flowId, String requestId) {
        String key = flowId + ":" + requestId;
        try {
            if (redisService != null) {
                redisService.delete(KEY_PREFIX + key);
            } else {
                memoryStore.remove(key);
            }
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
