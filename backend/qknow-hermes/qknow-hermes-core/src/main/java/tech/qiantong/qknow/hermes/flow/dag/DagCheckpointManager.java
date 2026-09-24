package tech.qiantong.qknow.hermes.flow.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.checkpoint.LeaseLivenessRecoveryReceipt;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG 断点续传与长事务检查点管理器
 * 支持工作流执行状态、全量上下文环境变量、SAGA补偿日志持久化
 * 并提供基于单调递增 Fencing Token 写屏障与双轨原子 CAS 租约超时自愈能力 (Lease/Heartbeat TTL)
 */
@Slf4j
@Component
public class DagCheckpointManager {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * 内存降级缓存（用于纯内存单测或无真实 DB 环境）
     */
    private final Map<String, DagCheckpoint> inMemoryStore = new ConcurrentHashMap<>();

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
            String resultsJson = JSON.toJSONString(completedResults);
            String varsJson = variables != null ? JSON.toJSONString(variables) : "{}";

            if (jdbcTemplate == null) {
                DagCheckpoint cp = inMemoryStore.computeIfAbsent(runtimeId, k -> {
                    DagCheckpoint c = new DagCheckpoint();
                    c.setRuntimeId(runtimeId);
                    c.setFlowId(flowId);
                    c.setStatus("SUSPENDED");
                    c.setFencingToken(1L);
                    return c;
                });
                cp.setFlowId(flowId);
                cp.setGroupIndex(groupIndex);
                cp.setCompletedResultsJson(resultsJson);
                cp.setVariablesJson(varsJson);
                cp.setVersion(cp.getVersion() + 1);
                return;
            }

            jdbcTemplate.update("""
                    INSERT INTO dag_checkpoints(runtime_id, flow_id, group_index, completed_results,
                                               version, variables_json, status, fencing_token, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 1, ?, 'SUSPENDED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
     * 带 Fencing Token 写屏障的检查点保存
     * 若持有令牌陈旧（已被新节点抢占递增），则原子拒绝写入，从存储层彻底杜绝脑裂与陈旧覆写
     */
    public boolean saveCheckpointWithFencingToken(String runtimeId, String flowId, int groupIndex,
                                                 Map<String, NodeRunResultBO> completedResults,
                                                 Map<String, Object> variables,
                                                 long fencingToken) {
        ensureTableExists();
        String resultsJson = JSON.toJSONString(completedResults);
        String varsJson = variables != null ? JSON.toJSONString(variables) : "{}";

        if (jdbcTemplate == null) {
            DagCheckpoint cp = inMemoryStore.get(runtimeId);
            if (cp == null) {
                cp = new DagCheckpoint();
                cp.setRuntimeId(runtimeId);
                cp.setFlowId(flowId);
                cp.setGroupIndex(groupIndex);
                cp.setCompletedResultsJson(resultsJson);
                cp.setVariablesJson(varsJson);
                cp.setFencingToken(fencingToken > 0 ? fencingToken : 1L);
                cp.setStatus("SUSPENDED");
                inMemoryStore.put(runtimeId, cp);
                return true;
            }
            if (fencingToken > 0 && cp.getFencingToken() != fencingToken) {
                log.warn("内存写屏障拦截陈旧写: runtimeId={}, expectedToken={}, currentToken={}",
                        runtimeId, fencingToken, cp.getFencingToken());
                return false;
            }
            cp.setGroupIndex(groupIndex);
            cp.setCompletedResultsJson(resultsJson);
            cp.setVariablesJson(varsJson);
            cp.setVersion(cp.getVersion() + 1);
            return true;
        }

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dag_checkpoints
                    SET group_index = ?, completed_results = ?, variables_json = ?,
                        version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE runtime_id = ? AND fencing_token = ?
                    """, groupIndex, resultsJson, varsJson, runtimeId, fencingToken);
            if (updated == 0) {
                log.warn("Fencing Token 写屏障拦截陈旧写: runtimeId={}, token={}", runtimeId, fencingToken);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("保存带写屏障检查点失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 加载检查点
     */
    public DagCheckpoint loadCheckpoint(String runtimeId) {
        try {
            ensureTableExists();
            if (jdbcTemplate == null) {
                return inMemoryStore.get(runtimeId);
            }
            List<DagCheckpoint> results = jdbcTemplate.query("""
                    SELECT runtime_id, flow_id, group_index, completed_results,
                           version, variables_json, status, compensation_log,
                           lease_owner_id, lease_expire_at, fencing_token
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
                checkpoint.setLeaseOwnerId(rs.getString("lease_owner_id"));
                checkpoint.setLeaseExpireAt(rs.getTimestamp("lease_expire_at"));
                checkpoint.setFencingToken(rs.getLong("fencing_token"));
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
                inMemoryStore.remove(runtimeId);
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
     * 唤醒挂起的工作流（基础方法）
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
     * 双轨原子 CAS 抢占与租约超时自愈中枢方法
     * 具备双重抢占能力：
     * 1. 正常唤醒挂起中的断点 (status = 'SUSPENDED')；
     * 2. 探活抢占崩溃超时的断点 (status = 'RUNNING' AND lease_expire_at < NOW)
     * 抢占成功即刻续租、单调递增 Fencing Token 并签发不可变自愈凭单
     */
    public Optional<LeaseLivenessRecoveryReceipt> wakeSuspendedOrRecoverLease(
            String runtimeId,
            String newOwnerId,
            long leaseDurationMs,
            Map<String, Object> humanInput
    ) {
        ensureTableExists();
        long now = System.currentTimeMillis();
        Timestamp newExpireAt = new Timestamp(now + leaseDurationMs);

        if (jdbcTemplate == null) {
            DagCheckpoint cp = inMemoryStore.get(runtimeId);
            if (cp == null) {
                return Optional.empty();
            }
            synchronized (cp) {
                long currentNow = System.currentTimeMillis();
                boolean isSuspended = "SUSPENDED".equals(cp.getStatus());
                boolean isExpired = "RUNNING".equals(cp.getStatus()) && cp.getLeaseExpireAt() != null
                        && cp.getLeaseExpireAt().getTime() < currentNow;
                if (!isSuspended && !isExpired) {
                    return Optional.empty();
                }
                String prevOwner = cp.getLeaseOwnerId();
                long prevToken = cp.getFencingToken();
                long newToken = prevToken + 1;
                cp.setStatus("RUNNING");
                cp.setLeaseOwnerId(newOwnerId);
                cp.setLeaseExpireAt(newExpireAt);
                cp.setFencingToken(newToken);
                cp.setVersion(cp.getVersion() + 1);

                String reason = isSuspended ? "SUSPENDED_NORMAL_WAKEUP" : "LEASE_TIMEOUT_TAKEOVER";
                if (isSuspended) {
                    wakeSuspended(runtimeId, humanInput);
                }
                LeaseLivenessRecoveryReceipt receipt = LeaseLivenessRecoveryReceipt.create(
                        runtimeId, cp.getFlowId(), prevOwner, newOwnerId, prevToken, newToken, reason, leaseDurationMs);
                return Optional.of(receipt);
            }
        }

        try {
            DagCheckpoint cp = loadCheckpoint(runtimeId);
            if (cp == null) {
                return Optional.empty();
            }
            String prevOwner = cp.getLeaseOwnerId();
            long prevToken = cp.getFencingToken();
            long newToken = prevToken + 1;

            int updated = jdbcTemplate.update("""
                    UPDATE dag_checkpoints
                    SET version = version + 1,
                        status = 'RUNNING',
                        lease_owner_id = ?,
                        lease_expire_at = ?,
                        fencing_token = fencing_token + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE runtime_id = ? AND (
                        status = 'SUSPENDED' OR (status = 'RUNNING' AND lease_expire_at < CURRENT_TIMESTAMP)
                    )
                    """, newOwnerId, newExpireAt, runtimeId);

            if (updated == 0) {
                return Optional.empty();
            }
            String reason = "SUSPENDED".equals(cp.getStatus()) ? "SUSPENDED_NORMAL_WAKEUP" : "LEASE_TIMEOUT_TAKEOVER";
            if ("SUSPENDED".equals(cp.getStatus())) {
                wakeSuspended(runtimeId, humanInput);
            }
            LeaseLivenessRecoveryReceipt receipt = LeaseLivenessRecoveryReceipt.create(
                    runtimeId, cp.getFlowId(), prevOwner, newOwnerId, prevToken, newToken, reason, leaseDurationMs);
            return Optional.of(receipt);
        } catch (Exception e) {
            log.error("自愈抢占检查点异常: runtimeId={}, error={}", runtimeId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 持有节点心跳续租
     * 必须匹配当前 ownerId 与 fencingToken，若令牌已过时则续约失败
     */
    public boolean refreshLease(String runtimeId, String ownerId, long fencingToken, long leaseDurationMs) {
        ensureTableExists();
        long now = System.currentTimeMillis();
        Timestamp newExpireAt = new Timestamp(now + leaseDurationMs);

        if (jdbcTemplate == null) {
            DagCheckpoint cp = inMemoryStore.get(runtimeId);
            if (cp == null) {
                return false;
            }
            if (ownerId != null && ownerId.equals(cp.getLeaseOwnerId()) && cp.getFencingToken() == fencingToken) {
                cp.setLeaseExpireAt(newExpireAt);
                return true;
            }
            return false;
        }

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE dag_checkpoints
                    SET lease_expire_at = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE runtime_id = ? AND lease_owner_id = ? AND fencing_token = ?
                    """, newExpireAt, runtimeId, ownerId, fencingToken);
            return updated > 0;
        } catch (Exception e) {
            log.warn("心跳续租失败: runtimeId={}, error={}", runtimeId, e.getMessage());
            return false;
        }
    }

    /**
     * 扫描待自愈租约超时断点列表
     */
    public List<DagCheckpoint> findExpiredRunningCheckpoints() {
        ensureTableExists();
        if (jdbcTemplate == null) {
            long now = System.currentTimeMillis();
            return inMemoryStore.values().stream()
                    .filter(cp -> "RUNNING".equals(cp.getStatus()) && cp.getLeaseExpireAt() != null
                            && cp.getLeaseExpireAt().getTime() < now)
                    .toList();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT runtime_id, flow_id, group_index, completed_results,
                           version, variables_json, status, compensation_log,
                           lease_owner_id, lease_expire_at, fencing_token
                    FROM dag_checkpoints
                    WHERE status = 'RUNNING' AND lease_expire_at < CURRENT_TIMESTAMP
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
                checkpoint.setLeaseOwnerId(rs.getString("lease_owner_id"));
                checkpoint.setLeaseExpireAt(rs.getTimestamp("lease_expire_at"));
                checkpoint.setFencingToken(rs.getLong("fencing_token"));
                return checkpoint;
            });
        } catch (Exception e) {
            log.warn("扫描超时检查点失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 保存 SAGA 逆拓扑补偿审计日志
     */
    public void saveCompensationLog(String runtimeId, String logContent) {
        ensureTableExists();
        if (jdbcTemplate == null) {
            DagCheckpoint cp = inMemoryStore.get(runtimeId);
            if (cp != null) {
                cp.setStatus("COMPENSATED");
                cp.setCompensationLog(logContent);
            }
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
                        lease_owner_id VARCHAR(128),
                        lease_expire_at TIMESTAMP,
                        fencing_token BIGINT NOT NULL DEFAULT 1,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            // 兼容既有旧表缺失列的场景
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS variables_json TEXT");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS status VARCHAR(64) NOT NULL DEFAULT 'SUSPENDED'");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS compensation_log TEXT");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS lease_owner_id VARCHAR(128)");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS lease_expire_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE dag_checkpoints ADD COLUMN IF NOT EXISTS fencing_token BIGINT NOT NULL DEFAULT 1");
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
        private String leaseOwnerId;
        private Timestamp leaseExpireAt;
        private long fencingToken = 1L;
    }
}
