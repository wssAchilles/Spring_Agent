-- Phase 7.1: Agent 执行追踪表
CREATE TABLE IF NOT EXISTS agent_trace (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) UNIQUE NOT NULL,
    request_id VARCHAR(64),
    bot_id BIGINT,
    user_id BIGINT,
    total_duration_ms BIGINT,
    total_tokens INT,
    total_cost DECIMAL(10,6),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_trace_span (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64) UNIQUE NOT NULL,
    parent_span_id VARCHAR(64),
    name VARCHAR(128),
    input TEXT,
    output TEXT,
    status VARCHAR(20),
    duration_ms BIGINT,
    token_count INT,
    cost DECIMAL(10,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trace_span_trace_id ON agent_trace_span(trace_id);

-- Phase 7.3: RAGAS 评估表
CREATE TABLE IF NOT EXISTS eval_dataset (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    knowledge_base_id BIGINT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_dataset_item (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_dataset(id),
    query TEXT NOT NULL,
    expected_answer TEXT,
    ground_truth_contexts JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eval_result (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) UNIQUE NOT NULL,
    dataset_id BIGINT NOT NULL REFERENCES eval_dataset(id),
    faithfulness DECIMAL(5,4),
    answer_relevance DECIMAL(5,4),
    context_precision DECIMAL(5,4),
    context_recall DECIMAL(5,4),
    report_json JSONB,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
