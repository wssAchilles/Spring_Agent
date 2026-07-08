SELECT s.document_id, d.name, s.position, SUBSTRING(s.content, 1, 100) 
FROM kmc_document_segment s
JOIN kmc_document d ON s.document_id = d.id
WHERE d.name = 'Day07-AI对话功能开发.md' AND (s.content LIKE '%Day 07%' OR s.content LIKE '%第七天%')
LIMIT 5;
