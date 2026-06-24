-- Test queries for RAG v2 verification

-- 1. Check vector_store has data
SELECT count(*) as total_vectors FROM vector_store;

-- 2. Check metadata has day_no
SELECT
    metadata->>'kmc_document_name' as doc_name,
    metadata->>'day_no' as day_no,
    count(*) as vectors
FROM vector_store
WHERE metadata->>'kmc_knowledgeBase_id' = '1'
GROUP BY metadata->>'kmc_document_name', metadata->>'day_no'
ORDER BY metadata->>'kmc_document_name';

-- 3. Test ILIKE search
SELECT s.id, s.content, d.name as doc_name
FROM kmc_document_segment s
JOIN kmc_document d ON d.id = s.document_id
WHERE d.knowledge_base_id = 1
AND s.content ILIKE '%项目架构%'
LIMIT 5;

-- 4. Test day-based filtering
SELECT s.id, s.content, d.name as doc_name
FROM kmc_document_segment s
JOIN kmc_document d ON d.id = s.document_id
WHERE d.knowledge_base_id = 1
AND d.name LIKE 'Day01%'
LIMIT 5;
