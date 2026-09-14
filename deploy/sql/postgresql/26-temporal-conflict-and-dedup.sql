-- Phase 26: 知识库切片时序版本控制、语义冲突消歧与去重字段升级
-- 包含：双时态生效区间、版本标签、四态冲突状态机、取代指针链、64位SimHash指纹与香农熵

-- 1. 为 kmc_document_segment 添加时效、版本、状态机与去重字段
ALTER TABLE kmc_document_segment
    ADD COLUMN IF NOT EXISTS effective_start TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS effective_end TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN IF NOT EXISTS version_tag VARCHAR(64) DEFAULT 'v1.0',
    ADD COLUMN IF NOT EXISTS conflict_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS superseded_by_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS simhash_64 BIGINT NULL,
    ADD COLUMN IF NOT EXISTS shannon_entropy REAL NULL;

-- 2. 状态机与时效区间组合索引 (非阻塞在线检索过滤加速)
CREATE INDEX IF NOT EXISTS idx_kmc_seg_active_temporal
    ON kmc_document_segment (conflict_status, effective_start, effective_end)
    WHERE del_flag = 0;

-- 3. 64位 SimHash 指纹去重索引
CREATE INDEX IF NOT EXISTS idx_kmc_seg_simhash
    ON kmc_document_segment (simhash_64)
    WHERE del_flag = 0;

-- 4. 取代链条外键与版本演进追踪索引
CREATE INDEX IF NOT EXISTS idx_kmc_seg_superseded_by
    ON kmc_document_segment (superseded_by_id)
    WHERE superseded_by_id IS NOT NULL;

-- 5. 字段业务注释说明
COMMENT ON COLUMN kmc_document_segment.effective_start IS '切片生效起始时间戳';
COMMENT ON COLUMN kmc_document_segment.effective_end IS '切片失效截止时间戳(NULL表示永久有效)';
COMMENT ON COLUMN kmc_document_segment.version_tag IS '业务版本标识(如 v1.0, 2026-Q1)';
COMMENT ON COLUMN kmc_document_segment.conflict_status IS '切片冲突状态: ACTIVE(有效最新), DEPRECATED(已废弃), SUPERSEDED(被取代), CONFLICTED(待仲裁)';
COMMENT ON COLUMN kmc_document_segment.superseded_by_id IS '取代当前切片的新切片主键ID';
COMMENT ON COLUMN kmc_document_segment.simhash_64 IS '64位SimHash指纹数值';
COMMENT ON COLUMN kmc_document_segment.shannon_entropy IS '切片文本字符级香农信息熵';
