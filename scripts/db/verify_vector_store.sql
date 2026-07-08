SELECT 
  d.name as "Document",
  COUNT(s.id) as "Total Chunks (Semantic)",
  COALESCE(v.vec_chunks, 0) as "Vector Embedded Chunks"
FROM kmc_document d
LEFT JOIN kmc_document_segment s ON d.id = s.document_id
LEFT JOIN (
  SELECT (metadata->>'kmc_document_id')::bigint as doc_id, COUNT(*) as vec_chunks 
  FROM vector_store 
  WHERE metadata->>'kmc_document_id' IS NOT NULL
  GROUP BY metadata->>'kmc_document_id'
) v ON d.id = v.doc_id
WHERE d.sync_status = 2
GROUP BY d.id, d.name, v.vec_chunks
ORDER BY "Document";
