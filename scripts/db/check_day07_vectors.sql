SELECT s.id, s.position, s.content, 
       EXISTS(SELECT 1 FROM vector_store v WHERE v.metadata->>'original_segment_id' = s.qm_segment_id) as is_vectorized
FROM kmc_document_segment s
JOIN kmc_document d ON s.document_id = d.id
WHERE d.name LIKE '%Day07%'
ORDER BY s.position
LIMIT 10;
