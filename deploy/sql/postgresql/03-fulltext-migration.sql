-- Phase 3.4: 全文检索从 Lucene 迁移到 PostgreSQL
-- 执行方式: psql -U postgres -d ai_agent -f 03-fulltext-migration.sql

-- 1. 新增 tsvector 列
ALTER TABLE kmc_document_segment ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- 2. 创建 GIN 索引
CREATE INDEX IF NOT EXISTS idx_segment_content_tsv ON kmc_document_segment USING GIN(content_tsv);

-- 3. 初始化已有数据的 tsvector
UPDATE kmc_document_segment SET content_tsv = to_tsvector('simple', COALESCE(content, '')) WHERE content_tsv IS NULL;

-- 4. 创建触发器：INSERT/UPDATE 时自动更新 tsvector
CREATE OR REPLACE FUNCTION update_content_tsv() RETURNS trigger AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', COALESCE(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_segment_content_tsv ON kmc_document_segment;
CREATE TRIGGER trg_segment_content_tsv
    BEFORE INSERT OR UPDATE ON kmc_document_segment
    FOR EACH ROW EXECUTE FUNCTION update_content_tsv();

-- 5. 验证
-- SELECT id, ts_rank(content_tsv, plainto_tsquery('simple', '测试查询')) AS rank
-- FROM kmc_document_segment
-- WHERE content_tsv @@ plainto_tsquery('simple', '测试查询')
-- ORDER BY rank DESC LIMIT 10;
