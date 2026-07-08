-- Backfill day_no and position in vector_store metadata.
--
-- Default mode is dry-run and writes nothing:
--   psql -f deploy/sql/postgresql/05-backfill-vector-metadata.sql
--
-- To update one online batch, run:
--   psql -v dry_run=false -v batch_size=500 \
--     -f deploy/sql/postgresql/05-backfill-vector-metadata.sql
--
-- Re-run write mode until both updated counts are 0. Dirty non-numeric metadata ids
-- are reported in dry-run and skipped in write mode.

\if :{?dry_run}
\else
\set dry_run true
\endif

\if :{?batch_size}
\else
\set batch_size 500
\endif

\if :dry_run

SELECT 'day_no_candidates' AS check_name, count(*) AS row_count
FROM vector_store v
JOIN kmc_document d
  ON d.id = CASE
      WHEN v.metadata->>'kmc_document_id' ~ '^[0-9]+$'
      THEN (v.metadata->>'kmc_document_id')::bigint
  END
CROSS JOIN LATERAL regexp_matches(d.name, 'Day([0-9]+)', 'i') AS m(day_no_parts)
WHERE v.metadata->>'day_no' IS NULL
  AND v.metadata->>'kmc_document_id' ~ '^[0-9]+$';

SELECT 'position_candidates' AS check_name, count(*) AS row_count
FROM vector_store v
JOIN kmc_document_segment s
  ON s.id = CASE
      WHEN v.metadata->>'kmc_segment_id' ~ '^[0-9]+$'
      THEN (v.metadata->>'kmc_segment_id')::bigint
  END
WHERE v.metadata->>'position' IS NULL
  AND v.metadata->>'kmc_segment_id' ~ '^[0-9]+$'
  AND s.position IS NOT NULL;

SELECT 'dirty_kmc_document_id' AS check_name, count(*) AS row_count
FROM vector_store v
WHERE v.metadata->>'day_no' IS NULL
  AND v.metadata->>'kmc_document_id' IS NOT NULL
  AND v.metadata->>'kmc_document_id' !~ '^[0-9]+$';

SELECT 'dirty_kmc_segment_id' AS check_name, count(*) AS row_count
FROM vector_store v
WHERE v.metadata->>'position' IS NULL
  AND v.metadata->>'kmc_segment_id' IS NOT NULL
  AND v.metadata->>'kmc_segment_id' !~ '^[0-9]+$';

\else

BEGIN;
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '30s';

WITH target AS (
    SELECT v.id, (m.day_no_parts)[1]::integer AS day_no
    FROM vector_store v
    JOIN kmc_document d
      ON d.id = CASE
          WHEN v.metadata->>'kmc_document_id' ~ '^[0-9]+$'
          THEN (v.metadata->>'kmc_document_id')::bigint
      END
    CROSS JOIN LATERAL regexp_matches(d.name, 'Day([0-9]+)', 'i') AS m(day_no_parts)
    WHERE v.metadata->>'day_no' IS NULL
      AND v.metadata->>'kmc_document_id' ~ '^[0-9]+$'
    ORDER BY v.id
    LIMIT :batch_size
    FOR UPDATE OF v SKIP LOCKED
),
updated AS (
    UPDATE vector_store v
    SET metadata = jsonb_set(COALESCE(v.metadata, '{}'::jsonb), '{day_no}', to_jsonb(target.day_no), true)
    FROM target
    WHERE v.id = target.id
    RETURNING v.id
)
SELECT 'day_no_updated' AS action, count(*) AS row_count FROM updated;

WITH target AS (
    SELECT v.id, s.position
    FROM vector_store v
    JOIN kmc_document_segment s
      ON s.id = CASE
          WHEN v.metadata->>'kmc_segment_id' ~ '^[0-9]+$'
          THEN (v.metadata->>'kmc_segment_id')::bigint
      END
    WHERE v.metadata->>'position' IS NULL
      AND v.metadata->>'kmc_segment_id' ~ '^[0-9]+$'
      AND s.position IS NOT NULL
    ORDER BY v.id
    LIMIT :batch_size
    FOR UPDATE OF v SKIP LOCKED
),
updated AS (
    UPDATE vector_store v
    SET metadata = jsonb_set(COALESCE(v.metadata, '{}'::jsonb), '{position}', to_jsonb(target.position), true)
    FROM target
    WHERE v.id = target.id
    RETURNING v.id
)
SELECT 'position_updated' AS action, count(*) AS row_count FROM updated;

ANALYZE vector_store;

COMMIT;

\endif
