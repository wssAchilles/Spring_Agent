-- Phase 25: 面向百万级超长知识库的高吞吐异步分块流与双活平滑索引热切换体系
-- 包含：断点游标持久化表、双活索引版本元数据表与只读视图别名

-- 1. 断点游标进度表 (用于大文件秒级精准断点续切续传)
CREATE TABLE IF NOT EXISTS kmc_document_checkpoint (
    document_id BIGINT PRIMARY KEY,
    total_bytes BIGINT NOT NULL DEFAULT 0,
    last_line_offset BIGINT NOT NULL DEFAULT 0,
    embedded_segments INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'INIT', -- INIT, RUNNING, COMPLETED, SUSPENDED, ERROR
    error_msg TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_checkpoint_status ON kmc_document_checkpoint(status);

-- 2. 双活索引版本控制与元数据表
CREATE TABLE IF NOT EXISTS kmc_index_metadata (
    id INT PRIMARY KEY DEFAULT 1,
    active_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    swapped_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

INSERT INTO kmc_index_metadata (id, active_version, status)
VALUES (1, 'v1', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 3. 初始只读活动视图 (默认指向已有的 vector_store)
CREATE OR REPLACE VIEW vector_store_active AS
SELECT id, content, metadata, embedding
FROM vector_store;
