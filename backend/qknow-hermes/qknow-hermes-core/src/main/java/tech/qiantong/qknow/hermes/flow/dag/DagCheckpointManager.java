package tech.qiantong.qknow.hermes.flow.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * DAG 断点续传管理器
 * 支持工作流执行状态的持久化和恢复
 */
@Slf4j
@Component
public class DagCheckpointManager {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        ensureTableExists();
    }

    /**
     * 保存检查点
     */
    public void saveCheckpoint(String runtimeId, String flowId, int groupIndex,
                               Map<String, NodeRunResultBO> completedResults) {
        try {
            ensureTableExists();
            if (jdbcTemplate == null) {
                return;
            }
            String resultsJson = JSON.toJSONString(completedResults);
            jdbcTemplate.update("""
                    INSERT INTO dag_checkpoints(runtime_id, flow_id, group_index, completed_results, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (runtime_id) DO UPDATE SET
                        group_index = EXCLUDED.group_index,
                        completed_results = EXCLUDED.completed_results,
                        updated_at = CURRENT_TIMESTAMP
                    """, runtimeId, flowId, groupIndex, resultsJson, System.currentTimeMillis());
            log.debug("Checkpoint saved: runtimeId={}, groupIndex={}", runtimeId, groupIndex);
        } catch (Exception e) {
            log.warn("Failed to save checkpoint: {}", e.getMessage());
        }
    }

    /**
     * 加载检查点
     */
    public DagCheckpoint loadCheckpoint(String runtimeId) {
        try {
            ensureTableExists();
            if (jdbcTemplate == null) {
                return null;
            }
            List<DagCheckpoint> results = jdbcTemplate.query("""
                    SELECT runtime_id, flow_id, group_index, completed_results
                    FROM dag_checkpoints
                    WHERE runtime_id = ?
                    """, (rs, rowNum) -> {
                DagCheckpoint checkpoint = new DagCheckpoint();
                checkpoint.setRuntimeId(rs.getString("runtime_id"));
                checkpoint.setFlowId(rs.getString("flow_id"));
                checkpoint.setGroupIndex(rs.getInt("group_index"));
                checkpoint.setCompletedResultsJson(rs.getString("completed_results"));
                return checkpoint;
            }, runtimeId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.warn("Failed to load checkpoint: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 删除检查点
     */
    public void deleteCheckpoint(String runtimeId) {
        try {
            ensureTableExists();
            if (jdbcTemplate == null) {
                return;
            }
            jdbcTemplate.update("DELETE FROM dag_checkpoints WHERE runtime_id = ?", runtimeId);
        } catch (Exception e) {
            log.warn("Failed to delete checkpoint: {}", e.getMessage());
        }
    }

    /**
     * 恢复已完成的结果
     */
    public Map<String, NodeRunResultBO> restoreCompletedResults(DagCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getCompletedResultsJson() == null) {
            return new LinkedHashMap<>();
        }
        try {
            JSONObject json = JSONObject.parseObject(checkpoint.getCompletedResultsJson());
            Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
            for (String key : json.keySet()) {
                results.put(key, json.getObject(key, NodeRunResultBO.class));
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to restore completed results: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void ensureTableExists() {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS dag_checkpoints (
                        runtime_id VARCHAR(255) PRIMARY KEY,
                        flow_id VARCHAR(255) NOT NULL,
                        group_index INT NOT NULL,
                        completed_results TEXT,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT
                    )
                    """);
        } catch (Exception e) {
            log.debug("dag_checkpoints table creation: {}", e.getMessage());
        }
    }

    @Data
    public static class DagCheckpoint {
        private String runtimeId;
        private String flowId;
        private int groupIndex;
        private String completedResultsJson;
    }
}
