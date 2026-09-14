-- V016: 扩展 dag_checkpoints 表支持 SAGA 逆拓扑补偿、非阻塞挂起状态与乐观锁版本号
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'dag_checkpoints'
    ) THEN
        -- 增加乐观锁版本号
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'dag_checkpoints'
              AND column_name = 'version'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dag_checkpoints ADD COLUMN version INT NOT NULL DEFAULT 1', current_schema());
        END IF;

        -- 增加全量上下文变量 JSON 快照字段
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'dag_checkpoints'
              AND column_name = 'variables_json'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dag_checkpoints ADD COLUMN variables_json TEXT', current_schema());
        END IF;

        -- 增加运行时状态字段
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'dag_checkpoints'
              AND column_name = 'status'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dag_checkpoints ADD COLUMN status VARCHAR(64) NOT NULL DEFAULT ''SUSPENDED''', current_schema());
        END IF;

        -- 增加 SAGA 补偿审计日志字段
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'dag_checkpoints'
              AND column_name = 'compensation_log'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dag_checkpoints ADD COLUMN compensation_log TEXT', current_schema());
        END IF;
    END IF;
END $$;
