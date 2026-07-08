-- RAG retrieval indexes for hybrid keyword/vector/graph candidate selection.
-- Non-destructive: only CREATE IF NOT EXISTS and additive table/index changes.
-- For online production-sized tables, prefer 13-rag-retrieval-indexes-online.sql
-- because regular CREATE INDEX can block writes while the index is built.
-- Run 14-backfill-kg-node-segment-rel.sql separately for node-segment relation
-- backfill; this script intentionally does not update data rows.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kg_node_segment_rel (
    node_id BIGINT NOT NULL,
    segment_id BIGINT NOT NULL,
    document_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (node_id, segment_id)
);

CREATE INDEX IF NOT EXISTS idx_kg_node_segment_rel_segment
    ON kg_node_segment_rel(segment_id, node_id);

CREATE INDEX IF NOT EXISTS idx_kg_node_segment_rel_document
    ON kg_node_segment_rel(document_id);

CREATE INDEX IF NOT EXISTS idx_kg_node_label_lower_active
    ON kg_node (lower(label))
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kmc_doc_kb_active
    ON kmc_document(knowledge_base_id, id)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kmc_seg_doc_active_order
    ON kmc_document_segment(document_id, position, id)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kmc_seg_content_tsv_active
    ON kmc_document_segment USING GIN (content_tsv)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kmc_seg_content_trgm_active
    ON kmc_document_segment USING GIN (content gin_trgm_ops)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kmc_document_name_trgm
    ON kmc_document USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_kmc_document_name_trgm_active
    ON kmc_document USING GIN (name gin_trgm_ops)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

CREATE INDEX IF NOT EXISTS idx_vector_store_kb_id_expr
    ON vector_store ((metadata->>'kmc_knowledgeBase_id'));

CREATE INDEX IF NOT EXISTS idx_vector_store_segment_id_expr
    ON vector_store ((metadata->>'kmc_segment_id'));

CREATE INDEX IF NOT EXISTS idx_vector_store_kb_segment_expr
    ON vector_store ((metadata->>'kmc_knowledgeBase_id'), (metadata->>'kmc_segment_id'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_path
    ON vector_store USING GIN (metadata jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_lookup
    ON semantic_cache_store(workspace_id, bot_id, knowledge_ids_hash, model_name, expires_at)
    INCLUDE (id);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding_hnsw_1536
    ON semantic_cache_store USING hnsw ((query_embedding::vector(1536)) vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

CREATE INDEX IF NOT EXISTS idx_kg_node_label_trgm_active
    ON kg_node USING GIN (label gin_trgm_ops)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kg_edge_active_source_target
    ON kg_edge(source_id, target_id)
    WHERE del_flag = 0;

CREATE INDEX IF NOT EXISTS idx_kg_edge_active_target
    ON kg_edge(target_id)
    WHERE del_flag = 0;
