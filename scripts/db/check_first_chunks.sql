SELECT s.position, SUBSTRING(s.content, 1, 50) as preview
FROM kmc_document_segment s
JOIN kmc_document d ON s.document_id = d.id
WHERE d.name = '分布式.pdf'
ORDER BY s.position
LIMIT 20;
