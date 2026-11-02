package tech.qiantong.qknow.hermes.agent.harness.ppc.controlled;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.Category;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.HistoryTurn;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.RetrievalHit;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.Rubric;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.Split;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.ToolFixture;

/** Reads only the isolated PostgreSQL fixture; gold IDs are checked after real retrieval. */
public final class JdbcControlledFixtureSource {
    private static final Map<String, String> FROZEN_SNAPSHOTS = Map.of(
            "tasks", "94f79eb0d943d637373299675a3185c759c96ba032872d58972fef32b174a1b4",
            "rubrics", "9d4dffcf685acda497064e2d4547582b49a8649e1dd941b5d4911860503a67cb",
            "documents", "8ea599fff26e8fe24581c0f72cb647e96526ea7684034ce5f896eb7d94fe80af",
            "segments", "fb5feed799ab9c54f5cd7189353aba54ce08a19d0bf35f5cedca10549e3a1562",
            "tools", "2b1a57c3a5356c66a7a57757388d8788c48292d75a20a7a36d50576daac5e438",
            "tool_results", "1c51fc76f8aa4d628a61b57cf96490ab2314083627823c341f46628e28f2b202",
            "history", "f0f142e803a64ccac0db9182d8cb3a34f25051c85c896dc5ef6151cbee12236c");
    private final Connection connection;

    public JdbcControlledFixtureSource(Connection connection) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        if (!connection.getCatalog().matches("h11b2a_fixture_[a-z0-9_]+")) {
            throw new IllegalArgumentException("an isolated h11b2a_fixture_* database is required");
        }
    }

    public List<ControlledFixture> load() throws SQLException {
        verifyFrozenSnapshots();
        List<ControlledFixture> tasks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, split, category, question, retrieval_query, knowledge_base_id, rubric::text "
                        + "FROM h11b2a.task ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString(1);
                Split split = Split.valueOf(rows.getString(2));
                Category category = Category.valueOf(rows.getString(3));
                if (rows.getLong(6) != ControlledPpcRunner.DOMAIN.knowledgeBaseId()) {
                    throw new IllegalStateException("controlled task has an unexpected KB: " + id);
                }
                JSONObject rubricJson = JSON.parseObject(rows.getString(7));
                Rubric rubric = new Rubric(strings(rubricJson, "answerAllOf"),
                        strings(rubricJson, "forbid"), rubricJson.getString("goldSegmentId"),
                        rubricJson.getString("requiredToolResultId"),
                        strings(rubricJson, "requiredHistorySources"));
                RetrievalHit retrieval = retrieval(id, split);
                ToolFixture tool = tool(id, split);
                List<HistoryTurn> history = history(id);
                if (retrieval != null && !retrieval.segmentId().equals(rubric.goldSegmentId())
                        || tool != null && !tool.resultId().equals(rubric.requiredToolResultId())
                        || !history.stream().map(HistoryTurn::sourceId).toList()
                                .equals(rubric.requiredHistorySources())) {
                    throw new IllegalStateException("actual fixture provenance disagrees with frozen rubric: " + id);
                }
                tasks.add(new ControlledFixture(id, split, category, rows.getString(4),
                        rows.getString(5), retrieval, tool, history, rubric));
            }
        }
        if (tasks.size() != 10) {
            throw new IllegalStateException("controlled fixture must contain exactly ten tasks");
        }
        for (Split split : Split.values()) {
            EnumSet<Category> categories = EnumSet.noneOf(Category.class);
            for (ControlledFixture task : tasks) {
                if (task.split() == split && !categories.add(task.category())) {
                    throw new IllegalStateException("duplicate category in " + split);
                }
            }
            if (categories.size() != Category.values().length) {
                throw new IllegalStateException("incomplete controlled split " + split);
            }
        }
        return List.copyOf(tasks);
    }

    private void verifyFrozenSnapshots() throws SQLException {
        String query = """
                SELECT artifact, sha256 FROM (
                    SELECT 'tasks' AS artifact,
                           encode(sha256(convert_to(string_agg((to_jsonb(t) - 'rubric')::text, E'\\n' ORDER BY id), 'UTF8')), 'hex') AS sha256
                    FROM h11b2a.task t
                    UNION ALL
                    SELECT 'rubrics', encode(sha256(convert_to(string_agg(jsonb_build_object('id', id, 'rubric', rubric)::text, E'\\n' ORDER BY id), 'UTF8')), 'hex')
                    FROM h11b2a.task
                    UNION ALL
                    SELECT 'documents', encode(sha256(convert_to(string_agg(to_jsonb(d)::text, E'\\n' ORDER BY id), 'UTF8')), 'hex')
                    FROM h11b2a.document d
                    UNION ALL
                    SELECT 'segments', encode(sha256(convert_to(string_agg((to_jsonb(s) - 'search_index')::text, E'\\n' ORDER BY id), 'UTF8')), 'hex')
                    FROM h11b2a.segment s
                    UNION ALL
                    SELECT 'tools', encode(sha256(convert_to(string_agg(to_jsonb(tool)::text, E'\\n' ORDER BY name), 'UTF8')), 'hex')
                    FROM h11b2a.tool tool
                    UNION ALL
                    SELECT 'tool_results', encode(sha256(convert_to(string_agg(to_jsonb(tr)::text, E'\\n' ORDER BY id), 'UTF8')), 'hex')
                    FROM h11b2a.tool_result tr
                    UNION ALL
                    SELECT 'history', encode(sha256(convert_to(string_agg(to_jsonb(h)::text, E'\\n' ORDER BY source_id), 'UTF8')), 'hex')
                    FROM h11b2a.history_message h
                ) hashes
                """;
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rows = statement.executeQuery()) {
            int verified = 0;
            while (rows.next()) {
                if (!Objects.equals(FROZEN_SNAPSHOTS.get(rows.getString(1)), rows.getString(2))) {
                    throw new IllegalStateException("controlled database differs from frozen snapshot: " + rows.getString(1));
                }
                verified++;
            }
            if (verified != FROZEN_SNAPSHOTS.size()) {
                throw new IllegalStateException("controlled database snapshot is incomplete");
            }
        }
    }

    private RetrievalHit retrieval(String taskId, Split split) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT split, document_id, segment_id, rank, retrieved_body "
                        + "FROM h11b2a.retrieval_hit WHERE task_id=? "
                        + "ORDER BY rank DESC, document_id, segment_id")) {
            statement.setString(1, taskId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                if (!split.name().equals(rows.getString(1))) {
                    throw new IllegalStateException("cross-split retrieval hit: " + taskId);
                }
                RetrievalHit hit = new RetrievalHit(rows.getString(2), rows.getString(3),
                        rows.getString(5), rows.getDouble(4));
                if (rows.next()) throw new IllegalStateException("ambiguous retrieval hit: " + taskId);
                return hit;
            }
        }
    }

    private ToolFixture tool(String taskId, Split split) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT tr.split, tr.id, tr.tool_name, tool.description, tool.input_schema::text, "
                        + "tr.input_args::text, tr.output_value::text FROM h11b2a.task_tool tt "
                        + "JOIN h11b2a.tool_result tr ON tr.id=tt.tool_result_id "
                        + "JOIN h11b2a.tool tool ON tool.name=tr.tool_name WHERE tt.task_id=?")) {
            statement.setString(1, taskId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                if (!split.name().equals(rows.getString(1))) {
                    throw new IllegalStateException("cross-split tool: " + taskId);
                }
                ToolFixture fixture = new ToolFixture(rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5), rows.getString(6), rows.getString(7));
                if (rows.next()) throw new IllegalStateException("ambiguous tool binding: " + taskId);
                return fixture;
            }
        }
    }

    private List<HistoryTurn> history(String taskId) throws SQLException {
        List<HistoryTurn> messages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT source_id, role, content FROM h11b2a.history_message "
                        + "WHERE task_id=? ORDER BY turn_no")) {
            statement.setString(1, taskId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    messages.add(new HistoryTurn(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
        }
        return List.copyOf(messages);
    }

    private static List<String> strings(JSONObject object, String key) {
        return object.getJSONArray(key) == null ? List.of() : object.getJSONArray(key).toJavaList(String.class);
    }
}
