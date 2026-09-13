-- ====================================================================
-- Phase 16: kmc_knowledge_recall_log 按月范围声明式分区与冷热归档 DDL
-- ====================================================================

-- 1. 检查并创建按月范围分区的召回日志主表
-- 注意：PostgreSQL 要求分区表的主键必须包含分区键 create_time
CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_partitioned (
    id                BIGSERIAL,
    workspace_id      BIGINT NOT NULL,
    knowledge_base_id BIGINT DEFAULT NULL,
    query             TEXT DEFAULT NULL,
    result            TEXT DEFAULT NULL,
    recall_time       TIMESTAMP DEFAULT NULL,
    valid_flag        BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag          BOOLEAN NOT NULL DEFAULT FALSE,
    create_by         VARCHAR(32) DEFAULT NULL,
    creator_id        BIGINT DEFAULT NULL,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by         VARCHAR(32) DEFAULT NULL,
    updater_id        BIGINT DEFAULT NULL,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark            VARCHAR(512) DEFAULT NULL,
    CONSTRAINT pk_recall_log_part PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

COMMENT ON TABLE kmc_knowledge_recall_log_partitioned IS '召回记录按月范围分区主表 (Phase 16)';

-- 2. 预创建 2026 年各月度分区
CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m01 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-02-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m02 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-02-01 00:00:00') TO ('2026-03-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m03 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-03-01 00:00:00') TO ('2026-04-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m04 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-04-01 00:00:00') TO ('2026-05-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m05 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m06 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m07 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m08 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m09 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m10 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2026-11-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m11 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-11-01 00:00:00') TO ('2026-12-01 00:00:00');

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_y2026m12 PARTITION OF kmc_knowledge_recall_log_partitioned
    FOR VALUES FROM ('2026-12-01 00:00:00') TO ('2027-01-01 00:00:00');

-- 3. 兜底默认分区 (防止未知超出时间段的数据写入报错)
CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log_default PARTITION OF kmc_knowledge_recall_log_partitioned DEFAULT;

-- 4. 创建常用局部复合索引
CREATE INDEX IF NOT EXISTS idx_part_recall_kb_time ON kmc_knowledge_recall_log_partitioned (knowledge_base_id, create_time);
CREATE INDEX IF NOT EXISTS idx_part_recall_ws_time ON kmc_knowledge_recall_log_partitioned (workspace_id, create_time);

-- 5. 冷热数据卸载与分离标准运维操作模板 (参考示例):
-- 步骤 A: 导出历史分区数据为压缩文件或推送到 MinIO
-- COPY kmc_knowledge_recall_log_y2026m01 TO PROGRAM 'gzip > /backup/archive/recall_log_202601.csv.gz' WITH CSV HEADER;
-- 步骤 B: 解除分区挂载并彻底释放主表查询负担
-- ALTER TABLE kmc_knowledge_recall_log_partitioned DETACH PARTITION kmc_knowledge_recall_log_y2026m01;
-- 步骤 C: 清理或归档该子表
-- DROP TABLE kmc_knowledge_recall_log_y2026m01;
