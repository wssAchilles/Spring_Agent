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
    sources_json JSONB NOT NULL DEFAULT '[]'::jsonb,
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

ALTER TABLE semantic_cache_store
    ADD COLUMN IF NOT EXISTS sources_json JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE IF NOT EXISTS semantic_cache_knowledge_rel (
    cache_id BIGINT NOT NULL REFERENCES semantic_cache_store(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT NOT NULL,
    PRIMARY KEY (cache_id, knowledge_base_id)
);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_rel_knowledge
    ON semantic_cache_knowledge_rel(knowledge_base_id);

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

CREATE TABLE IF NOT EXISTS eval_dataset (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    knowledge_base_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_dataset_item (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_dataset(id) ON DELETE CASCADE,
    position INTEGER DEFAULT 0,
    query TEXT NOT NULL,
    expected_answer TEXT,
    ground_truth_contexts JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_run (
    run_id VARCHAR(64) PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_dataset(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    report_json JSONB,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_run_item (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES eval_run(run_id) ON DELETE CASCADE,
    query TEXT NOT NULL,
    answer TEXT,
    scores_json JSONB DEFAULT '{}'::jsonb,
    feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_eval_dataset_kb
    ON eval_dataset(knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_eval_dataset_item_dataset
    ON eval_dataset_item(dataset_id);

CREATE INDEX IF NOT EXISTS idx_eval_run_dataset
    ON eval_run(dataset_id);
