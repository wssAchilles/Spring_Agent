package tech.qiantong.qknow.hermes.flow.checkpoint.spi.impl;

import tech.qiantong.qknow.hermes.flow.checkpoint.spi.LeaseAcquisitionReceipt;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.StateCheckpointStorageSpi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 JDBC 关系型数据库的云原生状态检查点持久化存储 SPI 实现
 * 采用原生 SQL CAS fencing_token 乐观锁与单调递增写屏障
 */
public class JdbcStateCheckpointStorage implements StateCheckpointStorageSpi {

    private static final Logger log = LoggerFactory.getLogger(JdbcStateCheckpointStorage.class);

    private final DataSource dataSource;

    public JdbcStateCheckpointStorage(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean saveCheckpointWithFencing(StateCheckpointRecord record, long fencingToken) {
        if (dataSource == null) {
            log.warn("[JdbcCheckpoint] DataSource 未配置，跳过物理持久化");
            return false;
        }

        String checkSql = "SELECT fencing_token FROM hermes_flow_checkpoints WHERE runtime_id = ?";
        String updateSql = "UPDATE hermes_flow_checkpoints SET "
                + "flow_id = ?, group_index = ?, completed_results_json = ?, variables_json = ?, status = ?, "
                + "compensation_log = ?, lease_owner_id = ?, lease_expire_at_ms = ?, fencing_token = ?, "
                + "version = ?, updated_at_ms = ?, integrity_hmac = ? "
                + "WHERE runtime_id = ? AND fencing_token <= ?";
        String insertSql = "INSERT INTO hermes_flow_checkpoints ("
                + "runtime_id, flow_id, group_index, completed_results_json, variables_json, status, "
                + "compensation_log, lease_owner_id, lease_expire_at_ms, fencing_token, version, created_at_ms, updated_at_ms, integrity_hmac"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, record.runtimeId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        long currentToken = rs.getLong("fencing_token");
                        if (currentToken > fencingToken) {
                            conn.rollback();
                            log.warn("[JdbcCheckpoint] 拦截陈旧写: 当前库内 token={}, 提交 token={}", currentToken, fencingToken);
                            return false;
                        }

                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            bindRecordToStatement(updateStmt, record);
                            updateStmt.setString(13, record.runtimeId());
                            updateStmt.setLong(14, fencingToken);
                            int rows = updateStmt.executeUpdate();
                            conn.commit();
                            return rows > 0;
                        }
                    } else {
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, record.runtimeId());
                            bindRecordToStatement(insertStmt, record);
                            insertStmt.setLong(12, record.createdAtMs());
                            int rows = insertStmt.executeUpdate();
                            conn.commit();
                            return rows > 0;
                        }
                    }
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("[JdbcCheckpoint] 保存检查点失败: runtimeId={}", record.runtimeId(), e);
            return false;
        }
    }

    private void bindRecordToStatement(PreparedStatement stmt, StateCheckpointRecord record) throws SQLException {
        stmt.setString(1, record.flowId());
        stmt.setInt(2, record.groupIndex());
        stmt.setString(3, record.completedResultsJson());
        stmt.setString(4, record.variablesJson());
        stmt.setString(5, record.status());
        stmt.setString(6, record.compensationLog());
        stmt.setString(7, record.leaseOwnerId());
        stmt.setLong(8, record.leaseExpireAtMs());
        stmt.setLong(9, record.fencingToken());
        stmt.setInt(10, record.version());
        stmt.setLong(11, record.updatedAtMs());
        stmt.setBytes(12, record.integrityHmac());
    }

    @Override
    public Optional<StateCheckpointRecord> loadCheckpoint(String runtimeId) {
        if (dataSource == null) {
            return Optional.empty();
        }

        String sql = "SELECT runtime_id, flow_id, group_index, completed_results_json, variables_json, status, "
                + "compensation_log, lease_owner_id, lease_expire_at_ms, fencing_token, version, created_at_ms, updated_at_ms, integrity_hmac "
                + "FROM hermes_flow_checkpoints WHERE runtime_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, runtimeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new StateCheckpointRecord(
                            rs.getString("runtime_id"),
                            rs.getString("flow_id"),
                            rs.getInt("group_index"),
                            rs.getString("completed_results_json"),
                            rs.getString("variables_json"),
                            rs.getString("status"),
                            rs.getString("compensation_log"),
                            rs.getString("lease_owner_id"),
                            rs.getLong("lease_expire_at_ms"),
                            rs.getLong("fencing_token"),
                            rs.getInt("version"),
                            rs.getLong("created_at_ms"),
                            rs.getLong("updated_at_ms"),
                            rs.getBytes("integrity_hmac")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("[JdbcCheckpoint] 读取检查点失败: runtimeId={}", runtimeId, e);
        }
        return Optional.empty();
    }

    @Override
    public LeaseAcquisitionReceipt tryAcquireLease(String runtimeId, String ownerId, long ttlMs, long expectedFencingToken) {
        Optional<StateCheckpointRecord> existingOpt = loadCheckpoint(runtimeId);
        if (existingOpt.isEmpty()) {
            return new LeaseAcquisitionReceipt(false, runtimeId, null, 0L, 0L, "检查点不存在");
        }
        StateCheckpointRecord record = existingOpt.get();
        long now = System.currentTimeMillis();
        if (record.fencingToken() != expectedFencingToken) {
            return new LeaseAcquisitionReceipt(false, runtimeId, record.leaseOwnerId(), record.leaseExpireAtMs(), record.fencingToken(), "令牌已过期");
        }
        if (record.leaseOwnerId() != null && record.leaseExpireAtMs() > now && !record.leaseOwnerId().equals(ownerId)) {
            return new LeaseAcquisitionReceipt(false, runtimeId, record.leaseOwnerId(), record.leaseExpireAtMs(), record.fencingToken(), "租约尚在有效期");
        }

        StateCheckpointRecord updated = new StateCheckpointRecord(
                record.runtimeId(), record.flowId(), record.groupIndex(),
                record.completedResultsJson(), record.variablesJson(), record.status(),
                record.compensationLog(), ownerId, now + ttlMs, record.fencingToken() + 1,
                record.version() + 1, record.createdAtMs(), now, record.integrityHmac()
        );
        boolean saved = saveCheckpointWithFencing(updated, record.fencingToken());
        return new LeaseAcquisitionReceipt(saved, runtimeId, ownerId, now + ttlMs, updated.fencingToken(), saved ? "租约获取成功" : "抢占并发冲突");
    }

    @Override
    public boolean heartbeatLease(String runtimeId, String ownerId, long ttlMs) {
        return loadCheckpoint(runtimeId).map(record -> {
            if (ownerId.equals(record.leaseOwnerId())) {
                long now = System.currentTimeMillis();
                StateCheckpointRecord renewed = new StateCheckpointRecord(
                        record.runtimeId(), record.flowId(), record.groupIndex(),
                        record.completedResultsJson(), record.variablesJson(), record.status(),
                        record.compensationLog(), ownerId, now + ttlMs, record.fencingToken(),
                        record.version(), record.createdAtMs(), now, record.integrityHmac()
                );
                return saveCheckpointWithFencing(renewed, record.fencingToken());
            }
            return false;
        }).orElse(false);
    }

    @Override
    public boolean archiveCheckpointToColdBuffer(String runtimeId) {
        return loadCheckpoint(runtimeId).map(record -> {
            StateCheckpointRecord archived = new StateCheckpointRecord(
                    record.runtimeId(), record.flowId(), record.groupIndex(),
                    record.completedResultsJson(), record.variablesJson(), "ARCHIVED",
                    record.compensationLog(), null, 0L, record.fencingToken(),
                    record.version(), record.createdAtMs(), System.currentTimeMillis(), record.integrityHmac()
            );
            return saveCheckpointWithFencing(archived, record.fencingToken());
        }).orElse(false);
    }

    @Override
    public String getStorageType() {
        return "JDBC_RELATIONAL";
    }
}
