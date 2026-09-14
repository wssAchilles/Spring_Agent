package tech.qiantong.qknow.hermes.flow.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * DAG 断点续传与长事务检查点管理器
 * 支持工作流执行状态、全量上下文环境变量、SAGA补偿日志的持久化和基于 CAS 乐观锁的安全恢复
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
     * 保存检查点（向下兼容）
     */
    public void saveCheckpoint(String runtimeId, String flowId, int groupIndex,
                               Map<String, NodeRunResultBO> completedResults) {
        saveCheckpointWithVariables(runtimeId, flowId, groupIndex, completedResults, Collections.emptyMap());
    }

    /**
     * 保存检查点（包含全量上下文变量环境）
     */
    public void saveCheckpointWithVariables(String runtimeId, String flowId, int groupIndex,
                                           Map<String, NodeRunResultBO> completedResults,
                                           Map<String, Object> variables) {
        try {
            ensureTableExists();
            if (jdbcTemplate == null) {
                return;
            }
            String resultsJson = JSON.toJSONString(completedResults);
            String varsJson = variables != null ? JSON.toJSONString(variables) : "{}";

            jdbcTemplate.update("""
                    INSERT INTO dag_checkpoints(runtime_id, flow_id, group_index, completed_results,
                                               version, variables_json, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 1, ?, 'SUSPENDED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (runtime_id) DO UPDATE SET
                        group_index = EXCLUDED.group_index,
                        completed_results = EXCLUDED.completed_results,
                        variables_json = EXCLUDED.variables_json,
                        version = dag_checkpoints.version + 1,
                        updated_at = CURRENT_TIMESTAMP
                    """, runtimeId, flowId, groupIndex, resultsJson, varsJson);
            log.debug("Checkpoint saved with variables: runtimeId={}, groupIndex={}", runtimeId, groupIndex);
        } catch (Exception e) {
            log.warn("Failed to save checkpoint with variables: {}", e.getMessage());
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
                    SELECT runtime_id, flow_id, group_index, completed_results,
                           version, variables_json, status, compensation_log
                    FROM dag_checkpoints
                    WHERE runtime_id = ?
                    """, (rs, rowNum) -> {
                DagCheckpoint checkpoint = new DagCheckpoint();
                checkpoint.setRuntimeId(rs.getString("runtime_id"));
                checkpoint.setFlowId(rs.getString("flow_id"));
                checkpoint.setGroupIndex(rs.getInt("group_index"));
                checkpoint.setCompletedResultsJson(rs.getString("completed_results"));
                checkpoint.setVersion(rs.getInt("version"));
                checkpoint.setVariablesJson(rs.getString("variables_json"));
                checkpoint.setStatus(rs.getString("status"));
                checkpoint.setCompensationLog(rs.getString("compensation_log"));
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

    /**
     * 恢复全量上下文变量
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> restoreVariables(DagCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getVariablesJson() == null || checkpoint.getVariablesJson().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.parseObject(checkpoint.getVariablesJson(), Map.class);
        } catch (Exception e) {
            log.warn("Failed to restore checkpoint variables: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public boolean hasSuspendedResult(Map<String, NodeRunResultBO> results) {
        return results != null && results.values().stream()
                .anyMatch(result -> RuntimeStatusEnums.SUSPENDED.getCode().equals(result.getStatus()));
    }

    /**
     * 唤醒挂起的工作流
     */
    public boolean wakeSuspended(String runtimeId, Map<String, Object> humanInput) {
        DagCheckpoint checkpoint = loadCheckpoint(runtimeId);
        if (checkpoint == null) {
            return false;
        }
        Map<String, NodeRunResultBO> results = restoreCompletedResults(checkpoint);
        for (NodeRunResultBO result : results.values()) {
            if (!RuntimeStatusEnums.SUSPENDED.getCode().equals(result.getStatus())) {
                continue;
            }
            Map<String, Object> output = new LinkedHashMap<>();
            if (result.getOutput() != null) {
                output.putAll(result.getOutput());
            }
            output.put("wokenAt", System.currentTimeMillis());
            output.put("humanInput", humanInput != null ? humanInput : Map.of());
            result.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
            result.setOutput(output);

            Map<String, Object> vars = restoreVariables(checkpoint);
            if (humanInput != null) {
                vars.putAll(humanInput);
            }
            saveCheckpointWithVariables(runtimeId, checkpoint.getFlowId(), checkpoint.getGroupIndex(), results, vars);
            log.info("Suspended DAG checkpoint woken: runtimeId={}, node={}", runtimeId, result.getNodeUuid());
            return true;
        }
        return false;
    }

    /**
     * 基于 DB CAS 乐观锁防重的安全唤醒
     */
    public boolean wakeSuspendedWithLock(String runtimeId, Map<String, Object> humanInput) {
        if (jdbcTemplate == null) {
            return wakeSuspended(runtimeId, humanInput);
        }
        try {
            DagCheckpoint checkpoint = loadCheckpoint(runtimeId);
            if (checkpoint == null) {
                return false;
            }
            // 使用 CAS 乐观锁原子更新状态与版本号，防多端并发重复唤醒
            int updated = jdbcTemplate.update("""
                    UPDATE dag_checkpoints
                    SET version = version + 1, status = 'RUNNING', updated_at = CURRENT_TIMESTAMP
                    WHERE runtime_id = ? AND version = ? AND status = 'SUSPENDED'
                    """, runtimeId, checkpoint.getVersion());

            if (updated == 0) {
                log.warn("CAS 乐观锁校验失败，已被其他线程唤醒或状态非挂起: runtimeId={}, version={}",
                        runtimeId, checkpoint.getVersion());
                return false;
            }

            return wakeSuspended(runtimeId, humanInput);
        } catch (Exception e) {
            log.error("CAS 唤醒检查点失败: runtimeId={}, error={}", runtimeId, e.getMessage());
            return false;
        }
    }

    /**
     * 保存 SAGA 逆拓扑补偿审计日志
     */
    public void saveCompensationLog(String runtimeId, String logContent) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE dag_checkpoints
                    SET status = 'COMPENSATED', compensation_log = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE runtime_id = ?
                    """, logContent, runtimeId);
            log.info("Saved SAGA compensation log for runtimeId: {}", runtimeId);
        } catch (Exception e) {
            log.warn("Failed to save compensation log: {}", e.getMessage());
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
                        version INT NOT NULL DEFAULT 1,
                        variables_json TEXT,
                        status VARCHAR(64) NOT NULL DEFAULT 'SUSPENDED',
                        compensation_log TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            // 兼容既有旧表缺失列的场景
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS variables_json TEXT");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS status VARCHAR(64) NOT NULL DEFAULT 'SUSPENDED'");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS compensation_log TEXT");
        } catch (Exception e) {
            log.debug("dag_checkpoints table schema check: {}", e.getMessage());
        }
    }

    @Data
    public static class DagCheckpoint {
        private String runtimeId;
        private String flowId;
        private int groupIndex;
        private String completedResultsJson;
        private int version = 1;
        private String variablesJson;
        private String status = "SUSPENDED";
        private String compensationLog;
    }
}
