SELECT 
  d.name as "Document",
  COUNT(s.id) as "Total Chunks",
  SUM(CASE WHEN m.id IS NOT NULL THEN 1 ELSE 0 END) as "Graph Extracted Chunks"
FROM kmc_document d
LEFT JOIN kmc_document_segment s ON d.id = s.document_id
LEFT JOIN kmc_segment_entity_metadata m ON s.id::varchar = m.qm_segment_id
WHERE d.sync_status = 2
GROUP BY d.id, d.name
ORDER BY "Document";
