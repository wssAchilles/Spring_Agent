-- Online RAG retrieval indexes for existing production-sized PostgreSQL databases.
-- Run this script outside any explicit transaction block: CREATE INDEX CONCURRENTLY
-- is rejected inside BEGIN/COMMIT and is intended to reduce write blocking.
-- This script creates the GraphRAG relation table and large indexes only. Run
-- 14-backfill-kg-node-segment-rel.sql separately for the online batched backfill.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kg_node_segment_rel (
    node_id BIGINT NOT NULL,
    segment_id BIGINT NOT NULL,
    document_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Preflight after interrupted concurrent builds. If this returns rows, drop the
-- named invalid index with DROP INDEX CONCURRENTLY IF EXISTS index_name before
-- rerunning this script; IF NOT EXISTS can otherwise skip an unusable index name.
WITH target_tables(table_name) AS (
    VALUES
        ('kg_node_segment_rel'),
        ('kg_node'),
        ('kg_edge'),
        ('kmc_document'),
        ('kmc_document_segment'),
        ('vector_store'),
        ('semantic_cache_store')
)
SELECT t.relname AS table_name, i.relname AS invalid_index, ix.indisvalid, ix.indisready
FROM pg_class t
JOIN pg_index ix ON ix.indrelid = t.oid
JOIN pg_class i ON i.oid = ix.indexrelid
JOIN target_tables target ON target.table_name = t.relname
WHERE NOT ix.indisvalid OR NOT ix.indisready
ORDER BY t.relname, i.relname;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS kg_node_segment_rel_pkey
    ON kg_node_segment_rel(node_id, segment_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kg_node_label_lower_active
    ON kg_node (lower(label))
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kg_node_segment_rel_segment
    ON kg_node_segment_rel(segment_id, node_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kg_node_segment_rel_document
    ON kg_node_segment_rel(document_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kmc_doc_kb_active
    ON kmc_document(knowledge_base_id, id)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kmc_seg_doc_active_order
    ON kmc_document_segment(document_id, position, id)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kmc_seg_content_tsv_active
    ON kmc_document_segment USING GIN (content_tsv)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kmc_seg_content_trgm_active
    ON kmc_document_segment USING GIN (content gin_trgm_ops)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kmc_document_name_trgm_active
    ON kmc_document USING GIN (name gin_trgm_ops)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vector_store_embedding_hnsw
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- HNSW ef_search is a per-session query-time setting, not a persistent index
-- property. Configure it in the application connection/session path or with a
-- reviewed ALTER ROLE/DATABASE operation, not by assuming this migration sets it.
-- Check support first:
-- SELECT current_setting('hnsw.ef_search', true);
-- Example for one retrieval session:
-- SET hnsw.ef_search = 100;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vector_store_kb_id_expr
    ON vector_store ((metadata->>'kmc_knowledgeBase_id'));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vector_store_segment_id_expr
    ON vector_store ((metadata->>'kmc_segment_id'));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vector_store_kb_segment_expr
    ON vector_store ((metadata->>'kmc_knowledgeBase_id'), (metadata->>'kmc_segment_id'));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vector_store_metadata_path
    ON vector_store USING GIN (metadata jsonb_path_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_semantic_cache_lookup
    ON semantic_cache_store(workspace_id, bot_id, knowledge_ids_hash, model_name, expires_at)
    INCLUDE (id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_semantic_cache_embedding_hnsw_1536
    ON semantic_cache_store USING hnsw ((query_embedding::vector(1536)) vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kg_node_label_trgm_active
    ON kg_node USING GIN (label gin_trgm_ops)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kg_edge_active_source_target
    ON kg_edge(source_id, target_id)
    WHERE del_flag = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kg_edge_active_target
    ON kg_edge(target_id)
    WHERE del_flag = 0;
