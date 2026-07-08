-- Backfill kg_node_segment_rel from kmc_segment_entity_metadata.
--
-- Default mode is dry-run and writes nothing:
--   psql -f deploy/sql/postgresql/14-backfill-kg-node-segment-rel.sql
--
-- To update one online batch, run:
--   psql -v dry_run=false -v batch_size=500 \
--     -f deploy/sql/postgresql/14-backfill-kg-node-segment-rel.sql
--
-- Re-run write mode until kg_node_segment_rel_upserted returns 0. The script
-- skips non-array entities and only locks metadata rows that still have missing
-- or stale node-segment relations.

\if :{?dry_run}
\else
\set dry_run true
\endif

\if :{?batch_size}
\else
\set batch_size 500
\endif

\if :dry_run

WITH candidates AS (
    SELECT n.id AS node_id, em.segment_id, max(em.document_id) AS document_id
    FROM kmc_segment_entity_metadata em
    JOIN LATERAL jsonb_array_elements_text(
        CASE WHEN jsonb_typeof(em.entities) = 'array' THEN em.entities ELSE '[]'::jsonb END
    ) AS entity(label) ON TRUE
    JOIN kg_node n ON n.del_flag = 0 AND lower(n.label) = lower(entity.label)
    WHERE em.segment_id IS NOT NULL
      AND entity.label <> ''
    GROUP BY n.id, em.segment_id
)
SELECT 'kg_node_segment_rel_candidates' AS check_name, count(*) AS row_count
FROM candidates c
WHERE NOT EXISTS (
    SELECT 1
    FROM kg_node_segment_rel rel
    WHERE rel.node_id = c.node_id
      AND rel.segment_id = c.segment_id
      AND rel.document_id IS NOT DISTINCT FROM c.document_id
);

SELECT 'non_array_entities_skipped' AS check_name, count(*) AS row_count
FROM kmc_segment_entity_metadata em
WHERE em.entities IS NOT NULL
  AND jsonb_typeof(em.entities) <> 'array';

\else

BEGIN;
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '30s';

WITH em_batch AS (
    SELECT em.segment_id, em.document_id, em.entities
    FROM kmc_segment_entity_metadata em
    WHERE em.segment_id IS NOT NULL
      AND jsonb_typeof(em.entities) = 'array'
      AND EXISTS (
          SELECT 1
          FROM jsonb_array_elements_text(em.entities) AS entity(label)
          JOIN kg_node n ON n.del_flag = 0 AND lower(n.label) = lower(entity.label)
          WHERE entity.label <> ''
            AND NOT EXISTS (
                SELECT 1
                FROM kg_node_segment_rel rel
                WHERE rel.node_id = n.id
                  AND rel.segment_id = em.segment_id
                  AND rel.document_id IS NOT DISTINCT FROM em.document_id
            )
      )
    ORDER BY em.segment_id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
),
source_rows AS (
    SELECT n.id AS node_id, em.segment_id, max(em.document_id) AS document_id
    FROM em_batch em
    JOIN LATERAL jsonb_array_elements_text(em.entities) AS entity(label) ON TRUE
    JOIN kg_node n ON n.del_flag = 0 AND lower(n.label) = lower(entity.label)
    WHERE entity.label <> ''
    GROUP BY n.id, em.segment_id
),
upserted AS (
    INSERT INTO kg_node_segment_rel(node_id, segment_id, document_id)
    SELECT node_id, segment_id, document_id
    FROM source_rows
    ON CONFLICT (node_id, segment_id) DO UPDATE SET
        document_id = EXCLUDED.document_id
    WHERE kg_node_segment_rel.document_id IS DISTINCT FROM EXCLUDED.document_id
    RETURNING 1
)
SELECT 'kg_node_segment_rel_upserted' AS action, count(*) AS row_count FROM upserted;

ANALYZE kg_node_segment_rel;

COMMIT;

\endif
