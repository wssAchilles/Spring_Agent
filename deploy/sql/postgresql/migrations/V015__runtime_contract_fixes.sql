DO $$
DECLARE
    target RECORD;
    flag RECORD;
    valid_type TEXT;
    del_type TEXT;
    type_clauses TEXT[];
BEGIN
    FOR target IN
        SELECT *
        FROM (VALUES
            ('conversation', 'ck_conversation_valid_flag_01', 'ck_conversation_del_flag_01'),
            ('chat_message', 'ck_chat_message_valid_flag_01', 'ck_chat_message_del_flag_01')
        ) AS flags(table_name, valid_constraint_name, del_constraint_name)
    LOOP
        CONTINUE WHEN to_regclass(format('%I.%I', current_schema(), target.table_name)) IS NULL;

        SELECT data_type
        INTO valid_type
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = target.table_name
          AND column_name = 'valid_flag';

        SELECT data_type
        INTO del_type
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = target.table_name
          AND column_name = 'del_flag';

        type_clauses := ARRAY[]::text[];
        IF valid_type = 'boolean' THEN
            type_clauses := array_append(type_clauses,
                    'ALTER COLUMN valid_flag TYPE SMALLINT USING CASE WHEN valid_flag IS NULL THEN NULL WHEN valid_flag THEN 1 ELSE 0 END');
        END IF;
        IF del_type = 'boolean' THEN
            type_clauses := array_append(type_clauses,
                    'ALTER COLUMN del_flag TYPE SMALLINT USING CASE WHEN del_flag IS NULL THEN NULL WHEN del_flag THEN 1 ELSE 0 END');
        END IF;

        IF cardinality(type_clauses) > 0 THEN
            EXECUTE format(
                    'ALTER TABLE %I.%I ALTER COLUMN valid_flag DROP DEFAULT, ALTER COLUMN del_flag DROP DEFAULT',
                    current_schema(), target.table_name);
            EXECUTE format('ALTER TABLE %I.%I %s',
                    current_schema(), target.table_name, array_to_string(type_clauses, ', '));
        END IF;

        EXECUTE format(
                'UPDATE %I.%I SET valid_flag = COALESCE(valid_flag, 1), del_flag = COALESCE(del_flag, 0) WHERE valid_flag IS NULL OR del_flag IS NULL',
                current_schema(), target.table_name);
        EXECUTE format(
                'ALTER TABLE %I.%I ALTER COLUMN valid_flag SET DEFAULT 1, ALTER COLUMN valid_flag SET NOT NULL, ALTER COLUMN del_flag SET DEFAULT 0, ALTER COLUMN del_flag SET NOT NULL',
                current_schema(), target.table_name);

        FOR flag IN
            SELECT *
            FROM (VALUES
                ('valid_flag', target.valid_constraint_name),
                ('del_flag', target.del_constraint_name)
            ) AS constraints(column_name, constraint_name)
        LOOP
            IF NOT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conname = flag.constraint_name
                  AND conrelid = to_regclass(format('%I.%I', current_schema(), target.table_name))
            ) THEN
                EXECUTE format(
                        'ALTER TABLE %I.%I ADD CONSTRAINT %I CHECK (%I IN (0, 1)) NOT VALID',
                        current_schema(), target.table_name, flag.constraint_name, flag.column_name);
            END IF;
            EXECUTE format('ALTER TABLE %I.%I VALIDATE CONSTRAINT %I',
                    current_schema(), target.table_name, flag.constraint_name);
        END LOOP;
    END LOOP;

    FOR target IN
        SELECT * FROM (VALUES ('created_at'), ('updated_at')) AS columns(column_name)
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'dag_checkpoints'
              AND column_name = target.column_name
              AND data_type = 'bigint'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dag_checkpoints ALTER COLUMN %I DROP DEFAULT',
                    current_schema(), target.column_name);
            EXECUTE format(
                    'ALTER TABLE %I.dag_checkpoints ALTER COLUMN %I TYPE TIMESTAMP USING to_timestamp(%I / 1000.0)',
                    current_schema(), target.column_name, target.column_name);
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'dag_checkpoints'
          AND column_name = 'created_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I.dag_checkpoints ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP',
                current_schema());
    END IF;

    EXECUTE format('ALTER TABLE IF EXISTS %I.kmc_document ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0',
            current_schema());
END $$;
