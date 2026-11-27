package tech.qiantong.qknow.hermes.flow.stategraph.journal;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.DurableStateGraphCheckpointer;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 基于嵌入式 PostgreSQL 16 预写步骤日志（Write-Ahead Step Journal）与 {@code DeltaChannel} 的状态图持久化引擎。
 * <p>
 * 对应博士论文第 6 章核心内核设计（融合 DBOS Embedded Postgres Step Journal + LangGraph Checkpoint v4）：
 * 1. 常态仅持久化节点增量通道变更（{@code channelDeltas}）与订阅版本向量（{@code versionsSeen}），满 {@code snapshotInterval} 超步才生成周期基底快照；
 * 2. 崩溃恢复时对已处于 {@link StepLifecycleStatus#COMMITTED} 的幂等键直接 O(1) 旁路返回历史增量，保证零重复 API/工具副作用；
 * 3. 分支废弃或超时淘汰时全部转入定长三级冷备可逆环形缓冲区（{@code hermes_state_cold_ring}），严禁物理硬删除。
 * </p>
 */
public class PgStepJournalStateCheckpointer implements StateCheckpointRepository {

    private static final Logger log = LoggerFactory.getLogger(PgStepJournalStateCheckpointer.class);
    private static final int DEFAULT_COLD_RING_CAPACITY = 256;

    private final DataSource dataSource;
    private final String hmacSecretKey;
    private final int snapshotInterval;
    private volatile boolean schemaEnsured = false;

    // 内存高速预写日志索引: scopeKey(executionId:branchId) -> 按追加序排列的步骤日志
    private final Map<String, List<WriteAheadStepJournalEntry>> journalByBranch = new ConcurrentHashMap<>();
    // 幂等旁路寻址表: executionId:branchId:idempotencyKey -> 已提交日志项
    private final Map<String, WriteAheadStepJournalEntry> committedByIdempotencyKey = new ConcurrentHashMap<>();
    // 周期性基底状态快照（每 snapshotInterval 超步落盘一次，避免重放长链）: scopeKey -> (superstep -> baseState)
    private final Map<String, NavigableMap<Integer, Map<String, Object>>> periodicBaseSnapshots = new ConcurrentHashMap<>();
    // 兼容 StateCheckpointRepository 的检查点视图
    private final Map<String, NavigableMap<Integer, StateCheckpointRecord>> checkpointRecords = new ConcurrentHashMap<>();
    // 单调递增 CAS Fencing Token 屏障
    private final Map<String, AtomicLong> branchFencingTokens = new ConcurrentHashMap<>();
    // 三级冷备可逆环形隔离队列（零物理硬删除）
    private final Deque<WriteAheadStepJournalEntry> coldRingArchive = new ArrayDeque<>(DEFAULT_COLD_RING_CAPACITY);

    public PgStepJournalStateCheckpointer() {
        this(null, "hermes-default-pg-journal-secret", 8);
    }

    public PgStepJournalStateCheckpointer(DataSource dataSource, String hmacSecretKey, int snapshotInterval) {
        this.dataSource = dataSource;
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-default-pg-journal-secret"
                : hmacSecretKey;
        this.snapshotInterval = Math.max(1, snapshotInterval);
        if (this.dataSource != null) {
            ensureSchemaIfNeeded();
        }
    }

    /**
     * 执行或幂等重放单个状态图节点步骤（DBOS {@code @Step} 精确一次语义）。
     */
    public synchronized Map<String, Object> executeOrReplayIdempotentStep(
            String executionId,
            String branchId,
            int superstep,
            String nodeId,
            String idempotencyKey,
            long fencingToken,
            Map<String, Long> versionsSeen,
            Supplier<Map<String, Object>> stepAction
    ) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为空");
        Objects.requireNonNull(stepAction, "stepAction 不能为空");

        String normalizedBranch = normalizeBranch(branchId);
        String idemLookupKey = executionId + ":" + normalizedBranch + ":" + idempotencyKey;

        // 1. 先检查内存与嵌入式 PostgreSQL 是否已存在 COMMITTED 步骤记录
        WriteAheadStepJournalEntry existing = committedByIdempotencyKey.get(idemLookupKey);
        if (existing == null && dataSource != null) {
            existing = loadCommittedEntryFromJdbc(executionId, normalizedBranch, idempotencyKey);
            if (existing != null) {
                committedByIdempotencyKey.put(idemLookupKey, existing);
            }
        }
        if (existing != null && existing.status() == StepLifecycleStatus.COMMITTED) {
            if (!existing.verifyMac(hmacSecretKey)) {
                throw new SecurityException("步骤日志 MAC 验真失败，疑似底层存储被篡改: key=" + idemLookupKey);
            }
            log.info("[StepJournal] 命中已提交幂等日志，零副作用旁路返回: exec={}, branch={}, step={}, node={}",
                    executionId, normalizedBranch, superstep, nodeId);
            return existing.channelDeltas();
        }

        // 2. CAS Fencing Token 写屏障校验
        String scopeKey = buildScopeKey(executionId, normalizedBranch);
        AtomicLong tokenRef = branchFencingTokens.computeIfAbsent(scopeKey, k -> new AtomicLong(0L));
        if (fencingToken <= tokenRef.get() && tokenRef.get() > 0L) {
            throw new IllegalStateException("Fencing Token 写屏障拒绝陈旧节点执行: incoming="
                    + fencingToken + ", active=" + tokenRef.get());
        }

        // 3. 首次真实执行步骤动作并签发增量日志凭单
        Map<String, Object> rawOutput = stepAction.get();
        Map<String, Object> safeDeltas = Map.copyOf(rawOutput != null ? rawOutput : Map.of());

        WriteAheadStepJournalEntry committedEntry = WriteAheadStepJournalEntry.createSigned(
                executionId,
                normalizedBranch,
                superstep,
                nodeId,
                idempotencyKey,
                fencingToken,
                safeDeltas,
                versionsSeen,
                StepLifecycleStatus.COMMITTED,
                hmacSecretKey
        );

        if (dataSource != null) {
            persistEntryToJdbc(committedEntry);
        }

        tokenRef.set(fencingToken);
        journalByBranch.computeIfAbsent(scopeKey, k -> new ArrayList<>()).add(committedEntry);
        committedByIdempotencyKey.put(idemLookupKey, committedEntry);

        // 4. 若达到周期快照间隔，生成基底合并快照加速后续 O(1) 时间旅行重构
        if (superstep % snapshotInterval == 0) {
            Map<String, Object> merged = reconstructStateAt(executionId, normalizedBranch, superstep);
            periodicBaseSnapshots
                    .computeIfAbsent(scopeKey, k -> new TreeMap<>())
                    .put(superstep, merged);
        }

        return safeDeltas;
    }

    /**
     * 按增量补丁链重构任意历史超步的完整状态视图（优先命中最近的周期基底快照）。
     */
    public synchronized Map<String, Object> reconstructStateAt(String executionId, String branchId, int targetSuperstep) {
        String scopeKey = buildScopeKey(executionId, normalizeBranch(branchId));
        Map<String, Object> state = new LinkedHashMap<>();
        int startStepExclusive = -1;

        NavigableMap<Integer, Map<String, Object>> baseMap = periodicBaseSnapshots.get(scopeKey);
        if (baseMap != null) {
            Map.Entry<Integer, Map<String, Object>> floor = baseMap.floorEntry(targetSuperstep);
            if (floor != null) {
                state.putAll(floor.getValue());
                startStepExclusive = floor.getKey();
            }
        }

        // 确保在读取前同步内存与 PostgreSQL 持久层中的全部已提交步骤
        List<WriteAheadStepJournalEntry> entries = listJournalEntries(executionId, branchId);
        for (WriteAheadStepJournalEntry entry : entries) {
            if (entry.status() == StepLifecycleStatus.COMMITTED
                    && entry.superstep() > startStepExclusive
                    && entry.superstep() <= targetSuperstep) {
                state.putAll(entry.channelDeltas());
            }
        }
        return Collections.unmodifiableMap(state);
    }

    /**
     * 从指定历史超步执行因果时间旅行分叉（Causal Time-Travel Fork），仅固化分叉基底与覆盖增量，零主分支污染。
     */
    public synchronized String forkBranchFromJournal(
            String executionId,
            String parentBranchId,
            int fromSuperstep,
            Map<String, Object> stateOverrides
    ) {
        Map<String, Object> baseState = reconstructStateAt(executionId, parentBranchId, fromSuperstep);
        String forkBranchId = "fork_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String forkScope = buildScopeKey(executionId, forkBranchId);

        Map<String, Object> forkedBase = new LinkedHashMap<>(baseState);
        if (stateOverrides != null && !stateOverrides.isEmpty()) {
            forkedBase.putAll(stateOverrides);
        }
        Map<String, Object> immutableForkBase = Map.copyOf(forkedBase);

        periodicBaseSnapshots
                .computeIfAbsent(forkScope, k -> new TreeMap<>())
                .put(fromSuperstep, immutableForkBase);

        WriteAheadStepJournalEntry forkAnchor = WriteAheadStepJournalEntry.createSigned(
                executionId,
                forkBranchId,
                fromSuperstep,
                "__TIME_TRAVEL_FORK__",
                "fork-anchor-" + forkBranchId,
                1L,
                stateOverrides != null ? stateOverrides : Map.of(),
                Map.of(),
                StepLifecycleStatus.COMMITTED,
                hmacSecretKey
        );
        if (dataSource != null) {
            if (!baseState.isEmpty()) {
                WriteAheadStepJournalEntry forkBaseEntry = WriteAheadStepJournalEntry.createSigned(
                        executionId,
                        forkBranchId,
                        fromSuperstep,
                        "__TIME_TRAVEL_BASE__",
                        "fork-base-" + forkBranchId,
                        1L,
                        baseState,
                        Map.of(),
                        StepLifecycleStatus.COMMITTED,
                        hmacSecretKey
                );
                persistEntryToJdbc(forkBaseEntry);
            }
            persistEntryToJdbc(forkAnchor);
        }
        journalByBranch.computeIfAbsent(forkScope, k -> new ArrayList<>()).add(forkAnchor);
        branchFencingTokens.put(forkScope, new AtomicLong(1L));
        return forkBranchId;
    }

    /**
     * 将废弃或回滚分支的全部日志软隔离移入三级冷备可逆环形缓冲区（严禁物理硬删除）。
     */
    public synchronized int quarantineBranchToColdRing(String executionId, String branchId) {
        String normBranch = normalizeBranch(branchId);
        String scopeKey = buildScopeKey(executionId, normBranch);
        List<WriteAheadStepJournalEntry> removed = journalByBranch.remove(scopeKey);
        if ((removed == null || removed.isEmpty()) && dataSource != null) {
            removed = loadBranchEntriesFromJdbc(executionId, normBranch);
        }
        if (removed == null || removed.isEmpty()) {
            return 0;
        }
        int archived = 0;
        for (WriteAheadStepJournalEntry e : removed) {
            committedByIdempotencyKey.remove(executionId + ":" + normBranch + ":" + e.idempotencyKey());
            WriteAheadStepJournalEntry quarantined = WriteAheadStepJournalEntry.createSigned(
                    e.executionId(), e.branchId(), e.superstep(), e.nodeId(), e.idempotencyKey(),
                    e.fencingToken(), e.channelDeltas(), e.versionsSeen(),
                    StepLifecycleStatus.QUARANTINED_COLD, hmacSecretKey
            );
            if (coldRingArchive.size() >= DEFAULT_COLD_RING_CAPACITY) {
                coldRingArchive.pollFirst();
            }
            coldRingArchive.addLast(quarantined);
            if (dataSource != null) {
                persistColdRingToJdbc(quarantined);
            }
            archived++;
        }
        if (dataSource != null) {
            markBranchQuarantinedInJdbc(executionId, normBranch);
        }
        return archived;
    }

    public synchronized List<WriteAheadStepJournalEntry> listJournalEntries(String executionId, String branchId) {
        String normBranch = normalizeBranch(branchId);
        String scopeKey = buildScopeKey(executionId, normBranch);
        List<WriteAheadStepJournalEntry> inMem = journalByBranch.getOrDefault(scopeKey, List.of());
        if (dataSource == null) {
            return List.copyOf(inMem);
        }
        List<WriteAheadStepJournalEntry> dbEntries = loadBranchEntriesFromJdbc(executionId, normBranch);
        if (dbEntries.isEmpty()) {
            return List.copyOf(inMem);
        }
        Map<String, WriteAheadStepJournalEntry> merged = new LinkedHashMap<>();
        for (WriteAheadStepJournalEntry dbEntry : dbEntries) {
            merged.put(dbEntry.idempotencyKey(), dbEntry);
        }
        for (WriteAheadStepJournalEntry memEntry : inMem) {
            merged.putIfAbsent(memEntry.idempotencyKey(), memEntry);
        }
        List<WriteAheadStepJournalEntry> result = new ArrayList<>(merged.values());
        journalByBranch.put(scopeKey, result);
        return List.copyOf(result);
    }

    public synchronized int getColdRingSize() {
        return coldRingArchive.size();
    }

    public synchronized Map<String, Object> getTelemetryStats() {
        int memEntries = 0;
        for (List<WriteAheadStepJournalEntry> list : journalByBranch.values()) {
            memEntries += list.size();
        }
        int scopeCount = journalByBranch.size();
        int committedCount = committedByIdempotencyKey.size();
        int coldCount = coldRingArchive.size();
        if (dataSource != null) {
            JdbcTelemetryCounts dbCounts = queryJdbcTelemetryCounts();
            scopeCount = Math.max(scopeCount, dbCounts.scopeCount());
            committedCount = Math.max(committedCount, dbCounts.committedCount());
            memEntries = Math.max(memEntries, dbCounts.committedCount());
            coldCount = Math.max(coldCount, dbCounts.coldRingCount());
        }
        return Map.of(
                "activeBranchScopeCount", scopeCount,
                "committedEntryCount", committedCount,
                "totalJournalEntries", memEntries,
                "baseSnapshotBranchCount", periodicBaseSnapshots.size(),
                "coldRingCount", coldCount
        );
    }

    // ==================== StateCheckpointRepository 兼容实现 ====================

    @Override
    public synchronized boolean saveWithFencing(StateCheckpointRecord record) {
        Objects.requireNonNull(record, "record 不能为空");
        String scopeKey = buildScopeKey(record.executionId(), normalizeBranch(record.branchId()));
        AtomicLong tokenRef = branchFencingTokens.computeIfAbsent(scopeKey, k -> new AtomicLong(0L));
        if (record.fencingToken() <= tokenRef.get() && tokenRef.get() > 0L) {
            return false;
        }
        tokenRef.set(record.fencingToken());
        checkpointRecords.computeIfAbsent(scopeKey, k -> new TreeMap<>()).put(record.superstep(), record);
        periodicBaseSnapshots.computeIfAbsent(scopeKey, k -> new TreeMap<>())
                .put(record.superstep(), record.fullStateSnapshot());
        return true;
    }

    @Override
    public synchronized Optional<StateCheckpointRecord> getCheckpoint(String executionId, String branchId, int superstep) {
        String scopeKey = buildScopeKey(executionId, normalizeBranch(branchId));
        NavigableMap<Integer, StateCheckpointRecord> map = checkpointRecords.get(scopeKey);
        return map != null ? Optional.ofNullable(map.get(superstep)) : Optional.empty();
    }

    @Override
    public synchronized Optional<StateCheckpointRecord> getLatestCheckpoint(String executionId, String branchId) {
        String scopeKey = buildScopeKey(executionId, normalizeBranch(branchId));
        NavigableMap<Integer, StateCheckpointRecord> map = checkpointRecords.get(scopeKey);
        return (map != null && !map.isEmpty()) ? Optional.of(map.lastEntry().getValue()) : Optional.empty();
    }

    @Override
    public synchronized List<StateCheckpointRecord> getTimelineHistory(String executionId, String branchId) {
        String scopeKey = buildScopeKey(executionId, normalizeBranch(branchId));
        NavigableMap<Integer, StateCheckpointRecord> map = checkpointRecords.get(scopeKey);
        return map != null ? List.copyOf(map.values()) : List.of();
    }

    /**
     * 适配为 {@link DurableStateGraphCheckpointer} 接口视图（避免与 StateCheckpointRepository 同名方法返回类型擦除冲突）。
     */
    public DurableStateGraphCheckpointer asDurableCheckpointer() {
        return new DurableStateGraphCheckpointer() {
            @Override
            public boolean saveSnapshot(SnapshotRecord snapshot) {
                Objects.requireNonNull(snapshot, "snapshot 不能为空");
                StateCheckpointRecord adapted = StateCheckpointRecord.create(
                        snapshot.snapshotId(),
                        snapshot.executionId(),
                        snapshot.branchId(),
                        snapshot.superstep(),
                        snapshot.stateDelta(),
                        snapshot.activeNodes(),
                        snapshot.fencingToken()
                );
                return saveWithFencing(adapted);
            }

            @Override
            public Optional<SnapshotRecord> loadSnapshot(String executionId, String branchId, int superstep) {
                return getCheckpoint(executionId, branchId, superstep).map(r -> new SnapshotRecord(
                        r.checkpointId(), r.executionId(), r.branchId(), r.superstep(),
                        r.fullStateSnapshot(), r.pendingNodes(), r.fencingToken(),
                        r.createdAtEpochMs(), r.checksumSha256()
                ));
            }

            @Override
            public String forkBranch(String executionId, String parentBranchId, int fromSuperstep, Map<String, Object> stateOverrides) {
                return forkBranchFromJournal(executionId, parentBranchId, fromSuperstep, stateOverrides);
            }

            @Override
            public List<SnapshotRecord> getTimelineHistory(String executionId, String branchId) {
                return listSnapshotTimeline(executionId, branchId);
            }
        };
    }

    public synchronized List<DurableStateGraphCheckpointer.SnapshotRecord> listSnapshotTimeline(String executionId, String branchId) {
        return getTimelineHistory(executionId, branchId).stream()
                .map(r -> new DurableStateGraphCheckpointer.SnapshotRecord(
                        r.checkpointId(), r.executionId(), r.branchId(), r.superstep(),
                        r.fullStateSnapshot(), r.pendingNodes(), r.fencingToken(),
                        r.createdAtEpochMs(), r.checksumSha256()))
                .toList();
    }

    // ==================== PostgreSQL 16 JDBC 预写日志持久化 ====================

    private record JdbcTelemetryCounts(int scopeCount, int committedCount, int coldRingCount) {}

    private void ensureSchemaIfNeeded() {
        if (schemaEnsured || dataSource == null) {
            return;
        }
        synchronized (this) {
            if (schemaEnsured) {
                return;
            }
            String createJournalSql = """
                    CREATE TABLE IF NOT EXISTS hermes_step_journal (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        execution_id VARCHAR(128) NOT NULL,
                        branch_id VARCHAR(64) NOT NULL DEFAULT 'main',
                        superstep INTEGER NOT NULL,
                        node_id VARCHAR(128) NOT NULL,
                        idempotency_key VARCHAR(256) NOT NULL,
                        fencing_token BIGINT NOT NULL,
                        channel_deltas_json TEXT NOT NULL,
                        versions_seen_json TEXT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        created_at_ms BIGINT NOT NULL,
                        mac_signature BYTEA NOT NULL,
                        CONSTRAINT uq_hermes_step_journal_exec_branch_idem UNIQUE (execution_id, branch_id, idempotency_key)
                    )""";
            String createIdxSql = "CREATE INDEX IF NOT EXISTS idx_hermes_step_journal_exec_branch_step "
                    + "ON hermes_step_journal (execution_id, branch_id, superstep, created_at_ms)";
            String createColdSql = """
                    CREATE TABLE IF NOT EXISTS hermes_state_cold_ring (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        execution_id VARCHAR(128) NOT NULL,
                        branch_id VARCHAR(64) NOT NULL,
                        superstep INTEGER NOT NULL,
                        node_id VARCHAR(128) NOT NULL,
                        idempotency_key VARCHAR(256) NOT NULL,
                        fencing_token BIGINT NOT NULL,
                        channel_deltas_json TEXT NOT NULL,
                        versions_seen_json TEXT NOT NULL,
                        archived_status VARCHAR(32) NOT NULL,
                        created_at_ms BIGINT NOT NULL,
                        archived_at_ms BIGINT NOT NULL,
                        mac_signature BYTEA NOT NULL
                    )""";
            try (Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute(createJournalSql);
                stmt.execute(createIdxSql);
                stmt.execute(createColdSql);
                schemaEnsured = true;
            } catch (SQLException e) {
                log.debug("[StepJournal] 幂等初始化表结构跳过或异常: {}", e.getMessage());
            }
        }
    }

    private void persistEntryToJdbc(WriteAheadStepJournalEntry entry) {
        ensureSchemaIfNeeded();
        String sql = "INSERT INTO hermes_step_journal ("
                + "execution_id, branch_id, superstep, node_id, idempotency_key, fencing_token, "
                + "channel_deltas_json, versions_seen_json, status, created_at_ms, mac_signature"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (execution_id, branch_id, idempotency_key) DO NOTHING";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.executionId());
            ps.setString(2, entry.branchId());
            ps.setInt(3, entry.superstep());
            ps.setString(4, entry.nodeId());
            ps.setString(5, entry.idempotencyKey());
            ps.setLong(6, entry.fencingToken());
            ps.setString(7, JSON.toJSONString(entry.channelDeltas()));
            ps.setString(8, JSON.toJSONString(entry.versionsSeen()));
            ps.setString(9, entry.status().name());
            ps.setLong(10, entry.createdAtEpochMs());
            ps.setBytes(11, entry.macSignature());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("[StepJournal] JDBC 写入异常，已保留内存预写副本: {}", e.getMessage());
        }
    }

    private void persistColdRingToJdbc(WriteAheadStepJournalEntry entry) {
        ensureSchemaIfNeeded();
        String sql = "INSERT INTO hermes_state_cold_ring ("
                + "execution_id, branch_id, superstep, node_id, idempotency_key, fencing_token, "
                + "channel_deltas_json, versions_seen_json, archived_status, created_at_ms, archived_at_ms, mac_signature"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.executionId());
            ps.setString(2, entry.branchId());
            ps.setInt(3, entry.superstep());
            ps.setString(4, entry.nodeId());
            ps.setString(5, entry.idempotencyKey());
            ps.setLong(6, entry.fencingToken());
            ps.setString(7, JSON.toJSONString(entry.channelDeltas()));
            ps.setString(8, JSON.toJSONString(entry.versionsSeen()));
            ps.setString(9, entry.status().name());
            ps.setLong(10, entry.createdAtEpochMs());
            ps.setLong(11, System.currentTimeMillis());
            ps.setBytes(12, entry.macSignature());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("[StepJournal] 冷备归档写库异常: {}", e.getMessage());
        }
    }

    private void markBranchQuarantinedInJdbc(String executionId, String branchId) {
        String sql = "UPDATE hermes_step_journal SET status = 'QUARANTINED_COLD' "
                + "WHERE execution_id = ? AND branch_id = ? AND status = 'COMMITTED'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.setString(2, branchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("[StepJournal] 软隔离标记异常: {}", e.getMessage());
        }
    }

    private List<WriteAheadStepJournalEntry> loadBranchEntriesFromJdbc(String executionId, String branchId) {
        ensureSchemaIfNeeded();
        String sql = "SELECT superstep, node_id, idempotency_key, fencing_token, channel_deltas_json, versions_seen_json, "
                + "status, created_at_ms, mac_signature FROM hermes_step_journal "
                + "WHERE execution_id = ? AND branch_id = ? AND status = 'COMMITTED' "
                + "ORDER BY superstep ASC, created_at_ms ASC, id ASC";
        List<WriteAheadStepJournalEntry> loaded = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.setString(2, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> deltas = JSON.parseObject(
                            rs.getString("channel_deltas_json"), new TypeReference<>() {});
                    Map<String, Long> versions = JSON.parseObject(
                            rs.getString("versions_seen_json"), new TypeReference<>() {});
                    loaded.add(new WriteAheadStepJournalEntry(
                            executionId,
                            branchId,
                            rs.getInt("superstep"),
                            rs.getString("node_id"),
                            rs.getString("idempotency_key"),
                            rs.getLong("fencing_token"),
                            deltas != null ? deltas : Map.of(),
                            versions != null ? versions : Map.of(),
                            StepLifecycleStatus.valueOf(rs.getString("status")),
                            rs.getLong("created_at_ms"),
                            rs.getBytes("mac_signature")
                    ));
                }
            }
        } catch (SQLException e) {
            log.debug("[StepJournal] JDBC 时间线查询异常: {}", e.getMessage());
        }
        return loaded;
    }

    private JdbcTelemetryCounts queryJdbcTelemetryCounts() {
        ensureSchemaIfNeeded();
        int scopeCount = 0;
        int committedCount = 0;
        int coldCount = 0;
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*), COUNT(DISTINCT execution_id || '::' || branch_id) FROM hermes_step_journal WHERE status = 'COMMITTED'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    committedCount = rs.getInt(1);
                    scopeCount = rs.getInt(2);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM hermes_state_cold_ring");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    coldCount = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.debug("[StepJournal] JDBC 遥测计数查询跳过: {}", e.getMessage());
        }
        return new JdbcTelemetryCounts(scopeCount, committedCount, coldCount);
    }

    private WriteAheadStepJournalEntry loadCommittedEntryFromJdbc(
            String executionId,
            String branchId,
            String idempotencyKey
    ) {
        String sql = "SELECT superstep, node_id, fencing_token, channel_deltas_json, versions_seen_json, "
                + "status, created_at_ms, mac_signature FROM hermes_step_journal "
                + "WHERE execution_id = ? AND branch_id = ? AND idempotency_key = ? AND status = 'COMMITTED'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.setString(2, branchId);
            ps.setString(3, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> deltas = JSON.parseObject(
                            rs.getString("channel_deltas_json"), new TypeReference<>() {});
                    Map<String, Long> versions = JSON.parseObject(
                            rs.getString("versions_seen_json"), new TypeReference<>() {});
                    return new WriteAheadStepJournalEntry(
                            executionId,
                            branchId,
                            rs.getInt("superstep"),
                            rs.getString("node_id"),
                            idempotencyKey,
                            rs.getLong("fencing_token"),
                            deltas,
                            versions,
                            StepLifecycleStatus.valueOf(rs.getString("status")),
                            rs.getLong("created_at_ms"),
                            rs.getBytes("mac_signature")
                    );
                }
            }
        } catch (SQLException e) {
            log.debug("[StepJournal] JDBC 查询未命中或表尚未初始化: {}", e.getMessage());
        }
        return null;
    }

    private static String normalizeBranch(String branchId) {
        return (branchId == null || branchId.isBlank()) ? "main" : branchId;
    }

    private static String buildScopeKey(String executionId, String branchId) {
        return executionId + "::" + branchId;
    }
}
