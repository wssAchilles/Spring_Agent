SELECT 
  document_name,
  COUNT(id) as total_segments,
  SUM(CASE WHEN sync_status = 2 THEN 1 ELSE 0 END) as vector_synced_segments
FROM kmc_document_segment
GROUP BY document_name
ORDER BY document_name;
