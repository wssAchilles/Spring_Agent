\set ON_ERROR_STOP on

DO $$
DECLARE
    actual_count integer;
BEGIN
    IF current_database() !~ '^h11b2a_fixture_[a-z0-9_]+$' THEN
        RAISE EXCEPTION '11B-2A verification requires an isolated h11b2a_fixture_* database';
    END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.task;
    IF actual_count <> 10 THEN RAISE EXCEPTION 'expected 10 tasks, got %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM (
        SELECT split, category FROM h11b2a.task GROUP BY split, category HAVING count(*) = 1
    ) coverage;
    IF actual_count <> 10 THEN RAISE EXCEPTION 'expected one task per category per split'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.task t
    WHERE t.category IN ('RAG', 'COMPOSITE')
      AND (SELECT count(*) FROM h11b2a.retrieval_hit h WHERE h.task_id = t.id) = 1;
    IF actual_count <> 4 THEN RAISE EXCEPTION 'expected four unique actual retrieval hits'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.retrieval_hit h
    JOIN h11b2a.task t ON t.id = h.task_id
    WHERE h.segment_id = t.rubric ->> 'goldSegmentId';
    IF actual_count <> 4 THEN RAISE EXCEPTION 'actual retrieval disagrees with frozen gold labels'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.task t
    JOIN h11b2a.task_tool tt ON tt.task_id = t.id
    JOIN h11b2a.tool_result tr ON tr.id = tt.tool_result_id
    JOIN h11b2a.tool tool ON tool.name = tr.tool_name
    WHERE t.category IN ('TOOL', 'COMPOSITE')
      AND tr.split = t.split
      AND tr.id = t.rubric ->> 'requiredToolResultId'
      AND tr.input_args ?& ARRAY(SELECT jsonb_array_elements_text(tool.input_schema -> 'required'))
      AND NOT EXISTS (SELECT 1 FROM jsonb_object_keys(tr.input_args) k
                      WHERE NOT (tool.input_schema -> 'properties') ? k)
      AND NOT EXISTS (SELECT 1 FROM jsonb_each_text(tr.input_args) arg
                      WHERE position(arg.value IN t.question) = 0);
    IF actual_count <> 4 THEN RAISE EXCEPTION 'expected four bound controlled tool results'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.task_tool;
    IF actual_count <> 4 THEN RAISE EXCEPTION 'unexpected tool binding'; END IF;
    SELECT count(*) INTO actual_count FROM (
        SELECT h.task_id FROM h11b2a.history_message h
        JOIN h11b2a.task t ON t.id = h.task_id
        WHERE t.category = 'HISTORY'
        GROUP BY h.task_id HAVING count(*) = 4
    ) histories;
    IF actual_count <> 2 THEN RAISE EXCEPTION 'expected two four-turn history sources'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.history_message;
    IF actual_count <> 8 THEN RAISE EXCEPTION 'unexpected history message'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.history_message h
    WHERE (h.turn_no IN (1, 3) AND h.role = 'user')
       OR (h.turn_no IN (2, 4) AND h.role = 'assistant');
    IF actual_count <> 8 THEN RAISE EXCEPTION 'history roles or order differ from contract'; END IF;
    SELECT count(*) INTO actual_count FROM (
        SELECT t.id FROM h11b2a.task t
        JOIN h11b2a.history_message h ON h.task_id = t.id
        WHERE t.category = 'HISTORY'
        GROUP BY t.id, t.rubric
        HAVING to_jsonb(array_agg(h.source_id ORDER BY h.turn_no)) = t.rubric -> 'requiredHistorySources'
    ) mapped_histories;
    IF actual_count <> 2 THEN RAISE EXCEPTION 'history provenance disagrees with rubric'; END IF;
    SELECT count(*) INTO actual_count FROM h11b2a.task t
    WHERE jsonb_array_length(t.rubric -> 'answerAllOf') > 0;
    IF actual_count <> 10 THEN RAISE EXCEPTION 'every task needs a nonempty rubric'; END IF;
END $$;

SELECT h.*, encode(sha256(convert_to(h.retrieved_body, 'UTF8')), 'hex') AS body_sha256
FROM h11b2a.retrieval_hit h
ORDER BY h.task_id, h.rank DESC, h.document_id, h.segment_id;

SELECT t.id AS task_id, tr.id AS tool_result_id, tr.tool_name, tr.input_args
FROM h11b2a.task t
JOIN h11b2a.task_tool tt ON tt.task_id = t.id
JOIN h11b2a.tool_result tr ON tr.id = tt.tool_result_id
ORDER BY t.id;

SELECT task_id, array_agg(source_id ORDER BY turn_no) AS history_source_ids
FROM h11b2a.history_message
GROUP BY task_id ORDER BY task_id;

SELECT artifact, sha256 FROM (
    SELECT 'tasks' AS artifact,
           encode(sha256(convert_to(string_agg((to_jsonb(t) - 'rubric')::text, E'\n' ORDER BY id), 'UTF8')), 'hex') AS sha256
    FROM h11b2a.task t
    UNION ALL
    SELECT 'rubrics',
           encode(sha256(convert_to(string_agg(jsonb_build_object('id', id, 'rubric', rubric)::text, E'\n' ORDER BY id), 'UTF8')), 'hex')
    FROM h11b2a.task
    UNION ALL
    SELECT 'documents',
           encode(sha256(convert_to(string_agg(to_jsonb(d)::text, E'\n' ORDER BY id), 'UTF8')), 'hex')
    FROM h11b2a.document d
    UNION ALL
    SELECT 'segments',
           encode(sha256(convert_to(string_agg((to_jsonb(s) - 'search_index')::text, E'\n' ORDER BY id), 'UTF8')), 'hex')
    FROM h11b2a.segment s
    UNION ALL
    SELECT 'tools',
           encode(sha256(convert_to(string_agg(to_jsonb(tool)::text, E'\n' ORDER BY name), 'UTF8')), 'hex')
    FROM h11b2a.tool tool
    UNION ALL
    SELECT 'tool_results',
           encode(sha256(convert_to(string_agg(to_jsonb(tr)::text, E'\n' ORDER BY id), 'UTF8')), 'hex')
    FROM h11b2a.tool_result tr
    UNION ALL
    SELECT 'history',
           encode(sha256(convert_to(string_agg(to_jsonb(h)::text, E'\n' ORDER BY source_id), 'UTF8')), 'hex')
    FROM h11b2a.history_message h
) hashes ORDER BY artifact;
