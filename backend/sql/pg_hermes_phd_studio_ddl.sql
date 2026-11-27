-- =============================================================================
-- H-NSCDS 博士论文认知内核 PostgreSQL 16 幂等迁移 DDL
-- 覆盖：
--   1. hermes_step_journal      : PG Step Journal 增量超步预写日志（DBOS + DeltaChannel）
--   2. hermes_state_cold_ring   : 三级冷备可逆环形隔离归档区（严禁物理硬删除）
-- =============================================================================

CREATE TABLE IF NOT EXISTS hermes_step_journal (
    id                  BIGSERIAL PRIMARY KEY,
    execution_id        VARCHAR(128) NOT NULL,
    branch_id           VARCHAR(64)  NOT NULL DEFAULT 'main',
    superstep           INTEGER      NOT NULL,
    node_id             VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(256) NOT NULL,
    fencing_token       BIGINT       NOT NULL,
    channel_deltas_json TEXT         NOT NULL,
    versions_seen_json  TEXT         NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    created_at_ms       BIGINT       NOT NULL,
    mac_signature       BYTEA        NOT NULL,
    CONSTRAINT uq_hermes_step_journal_exec_branch_idem
        UNIQUE (execution_id, branch_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_hermes_step_journal_exec_branch_step
    ON hermes_step_journal (execution_id, branch_id, superstep, created_at_ms);

CREATE INDEX IF NOT EXISTS idx_hermes_step_journal_status
    ON hermes_step_journal (status);

CREATE TABLE IF NOT EXISTS hermes_state_cold_ring (
    id                  BIGSERIAL PRIMARY KEY,
    execution_id        VARCHAR(128) NOT NULL,
    branch_id           VARCHAR(64)  NOT NULL,
    superstep           INTEGER      NOT NULL,
    node_id             VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(256) NOT NULL,
    fencing_token       BIGINT       NOT NULL,
    channel_deltas_json TEXT         NOT NULL,
    versions_seen_json  TEXT         NOT NULL,
    archived_status     VARCHAR(32)  NOT NULL,
    created_at_ms       BIGINT       NOT NULL,
    archived_at_ms      BIGINT       NOT NULL,
    mac_signature       BYTEA        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hermes_cold_ring_exec
    ON hermes_state_cold_ring (execution_id, archived_at_ms);

-- =============================================================================
-- Phase 6.2 扩展：双时态海马体顶点表与超边账本表（含冷备可逆层级与 HMAC 验签）
-- =============================================================================
CREATE TABLE IF NOT EXISTS hermes_hippocampal_vertex (
    node_id             VARCHAR(128) PRIMARY KEY,
    tier                VARCHAR(48)  NOT NULL,
    content             TEXT         NOT NULL,
    unit_embedding_json TEXT         NOT NULL,
    created_at_ms       BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hermes_hippo_vertex_tier
    ON hermes_hippocampal_vertex (tier);

CREATE TABLE IF NOT EXISTS hermes_hippocampal_edge (
    edge_id                 VARCHAR(128) PRIMARY KEY,
    source_node_id          VARCHAR(128) NOT NULL,
    target_node_id          VARCHAR(128) NOT NULL,
    relation_predicate      VARCHAR(128) NOT NULL,
    functional_exclusive    BOOLEAN      NOT NULL DEFAULT FALSE,
    unit_embedding_json     TEXT         NOT NULL,
    valid_from_ms           BIGINT       NOT NULL,
    valid_to_ms             BIGINT,
    tx_from_ms              BIGINT       NOT NULL,
    tx_to_ms                BIGINT,
    source_reference_count  INTEGER      NOT NULL DEFAULT 1,
    cold_standby_tier       VARCHAR(48)  NOT NULL,
    source_docs_json        TEXT         NOT NULL,
    hmac_signature          VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hermes_hippo_edge_src_rel
    ON hermes_hippocampal_edge (source_node_id, relation_predicate);

CREATE INDEX IF NOT EXISTS idx_hermes_hippo_edge_tier_tx
    ON hermes_hippocampal_edge (cold_standby_tier, tx_to_ms);
