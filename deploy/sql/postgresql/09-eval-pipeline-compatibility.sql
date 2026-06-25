-- Evaluation pipeline compatibility migration.
-- 04-trace-and-eval-tables.sql created an earlier eval schema. Keep legacy data and add
-- the columns/tables required by the persisted LLM-as-a-Judge runner.

ALTER TABLE eval_dataset
    ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT;

ALTER TABLE eval_dataset
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE eval_dataset_item
    ADD COLUMN IF NOT EXISTS position INTEGER DEFAULT 0;

ALTER TABLE eval_dataset_item
    ADD COLUMN IF NOT EXISTS ground_truth_contexts JSONB DEFAULT '[]'::jsonb;

ALTER TABLE eval_dataset_item
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

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
