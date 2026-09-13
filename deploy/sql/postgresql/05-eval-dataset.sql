-- Create Golden Dataset table for RAG evaluation
CREATE TABLE IF NOT EXISTS eval_golden_dataset (
    id VARCHAR(50) PRIMARY KEY,
    query TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    expected_contexts TEXT[] NOT NULL,
    hard_negatives TEXT[] NOT NULL,
    expected_output_claims TEXT[] NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert mock data for benchmarking
INSERT INTO eval_golden_dataset (
    id, 
    query, 
    category, 
    expected_contexts, 
    hard_negatives, 
    expected_output_claims
)
VALUES
(
    'q_001',
    '如何配置Agent的内存上限？',
    'single-doc',
    ARRAY['doc_uuid_1'],
    ARRAY['doc_uuid_3', 'doc_uuid_4'],
    ARRAY['在配置文件中修改memory_limit参数', '需要重启服务后生效']
),
(
    'q_002',
    'RAG系统如何处理跨文档的复杂查询？',
    'cross-doc',
    ARRAY['doc_uuid_5', 'doc_uuid_6'],
    ARRAY['doc_uuid_7', 'doc_uuid_8', 'doc_uuid_9'],
    ARRAY['使用多路召回技术', '结合重排算法提升准确率', '利用LLM进行文档间信息的整合与推理']
)
ON CONFLICT (id) DO NOTHING;
