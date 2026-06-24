-- Enterprise RAG upgrade: semantic cache, parent-child retrieval indexes, entity metadata.

CREATE TABLE IF NOT EXISTS semantic_cache_store (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    bot_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    knowledge_ids_hash VARCHAR(128) NOT NULL,
    query TEXT NOT NULL,
    query_embedding vector NOT NULL,
    answer TEXT NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    hit_count BIGINT NOT NULL DEFAULT 0,
    last_hit_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_scope
    ON semantic_cache_store(workspace_id, bot_id, knowledge_base_id, knowledge_ids_hash, model_name);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_expires_at
    ON semantic_cache_store(expires_at);

CREATE TABLE IF NOT EXISTS kmc_segment_entity_metadata (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    segment_id BIGINT NOT NULL UNIQUE,
    qm_segment_id VARCHAR(128),
    entities JSONB NOT NULL DEFAULT '[]'::jsonb,
    relations JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_segment_entity_document_id
    ON kmc_segment_entity_metadata(document_id);

CREATE INDEX IF NOT EXISTS idx_segment_entity_qm_segment_id
    ON kmc_segment_entity_metadata(qm_segment_id);

CREATE INDEX IF NOT EXISTS idx_segment_entity_entities_gin
    ON kmc_segment_entity_metadata USING GIN(entities);

CREATE INDEX IF NOT EXISTS idx_kmc_document_segment_parent_id
    ON kmc_document_segment(parent_id);

CREATE INDEX IF NOT EXISTS idx_kmc_document_segment_qm_segment_id
    ON kmc_document_segment(qm_segment_id);

CREATE INDEX IF NOT EXISTS idx_kmc_document_segment_document_id
    ON kmc_document_segment(document_id);
