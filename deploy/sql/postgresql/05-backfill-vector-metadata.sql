-- Backfill day_no in vector_store metadata
UPDATE vector_store
SET metadata = metadata || jsonb_build_object(
    'day_no',
    (SELECT CAST((regexp_match(d.name, 'Day(\d+)', 'i'))[1] AS INTEGER)
     FROM kmc_document d
     WHERE d.id = (metadata->>'kmc_document_id')::bigint)
)
WHERE metadata->>'kmc_document_id' IS NOT NULL
AND metadata->>'day_no' IS NULL;

-- Backfill position from segment
UPDATE vector_store v
SET metadata = metadata || jsonb_build_object(
    'position',
    (SELECT s.position FROM kmc_document_segment s WHERE s.id = (v.metadata->>'kmc_segment_id')::bigint)
)
WHERE metadata->>'kmc_segment_id' IS NOT NULL
AND metadata->>'position' IS NULL;
