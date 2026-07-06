-- Migration: add HNSW 向量索引
-- 参考：PgVector v0.8 — HNSW 在高维度(1536)场景下召回率和延迟均优于 IVFFlat
-- 参考：pgvector/pgvector (22k⭐) 官方文档

-- 1. 保留旧 IVFFlat 索引，先新增 HNSW，待 EXPLAIN/压测确认后再人工清理旧索引。

-- 2. 创建 HNSW 索引（cosine 距离）
-- 注意：CREATE INDEX CONCURRENTLY 不能运行在显式事务块中。
-- 生产已有数据表建议使用并发建索引，避免长时间阻塞写入。
-- HNSW 参数说明：
--   m = 16: 每个节点的最大连接数（默认 16，平衡召回率和内存）
--   ef_construction = 200: 构建时的搜索宽度（越大越精确，构建越慢）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vector_store_embedding_hnsw
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 3. 设置运行时参数
-- ef_search: 查询时的搜索宽度（越大召回率越高，延迟越大）
-- 默认 40，建议生产环境在数据库或连接池初始化中设为 100；这里仅影响当前迁移会话。
SET hnsw.ef_search = 100;

-- 4. 验证索引
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'vector_store';
