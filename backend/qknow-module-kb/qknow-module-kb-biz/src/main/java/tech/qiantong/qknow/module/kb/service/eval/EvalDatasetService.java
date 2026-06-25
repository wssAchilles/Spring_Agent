package tech.qiantong.qknow.module.kb.service.eval;

import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;

import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class EvalDatasetService {

    private final RagasEvaluator ragasEvaluator;
    private final JdbcTemplate jdbcTemplate;
    private final Executor evalTaskExecutor;

    public EvalDatasetService(RagasEvaluator ragasEvaluator) {
        this(ragasEvaluator, null, Runnable::run);
    }

    public EvalDatasetService(RagasEvaluator ragasEvaluator, JdbcTemplate jdbcTemplate) {
        this(ragasEvaluator, jdbcTemplate, Runnable::run);
    }

    @Autowired
    public EvalDatasetService(RagasEvaluator ragasEvaluator, JdbcTemplate jdbcTemplate,
                              @Qualifier("evalTaskExecutor") Executor evalTaskExecutor) {
        this.ragasEvaluator = ragasEvaluator;
        this.jdbcTemplate = jdbcTemplate;
        this.evalTaskExecutor = evalTaskExecutor != null ? evalTaskExecutor : Runnable::run;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createDataset(EvalDatasetDTO dto) {
        ensurePersistence();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO eval_dataset(name, knowledge_base_id)
                    VALUES (?, ?)
                    """, new String[]{"id"});
            ps.setString(1, dto.getName());
            ps.setObject(2, dto.getKnowledgeBaseId());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        replaceItems(id, dto.getItems());
        log.info("Created eval dataset: id={}, name={}, kbId={}", id, dto.getName(), dto.getKnowledgeBaseId());
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDataset(EvalDatasetDTO dto) {
        ensurePersistence();
        int updated = jdbcTemplate.update("""
                UPDATE eval_dataset
                SET name = ?, knowledge_base_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, dto.getName(), dto.getKnowledgeBaseId(), dto.getId());
        if (updated == 0) {
            throw new IllegalArgumentException("Dataset not found: " + dto.getId());
        }
        replaceItems(dto.getId(), dto.getItems());
        log.info("Updated eval dataset: id={}", dto.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        ensurePersistence();
        jdbcTemplate.update("DELETE FROM eval_run_item WHERE run_id IN (SELECT run_id FROM eval_run WHERE dataset_id = ?)", id);
        jdbcTemplate.update("DELETE FROM eval_run WHERE dataset_id = ?", id);
        jdbcTemplate.update("DELETE FROM eval_dataset_item WHERE dataset_id = ?", id);
        int deleted = jdbcTemplate.update("DELETE FROM eval_dataset WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("Dataset not found: " + id);
        }
        log.info("Deleted eval dataset: id={}", id);
    }

    public EvalDatasetDTO getDataset(Long id) {
        ensurePersistence();
        EvalDatasetDTO dto = jdbcTemplate.query("""
                        SELECT id, name, knowledge_base_id
                        FROM eval_dataset
                        WHERE id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    EvalDatasetDTO item = new EvalDatasetDTO();
                    item.setId(rs.getLong("id"));
                    item.setName(rs.getString("name"));
                    item.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
                    return item;
                }, id);
        if (dto == null) {
            throw new IllegalArgumentException("Dataset not found: " + id);
        }
        dto.setItems(loadItems(id));
        return dto;
    }

    private EvaluationDataset getEvaluationDataset(Long id) {
        EvalDatasetDTO dto = new EvalDatasetDTO();
        EvalDatasetDTO stored = getDataset(id);
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName(stored.getName());
        dataset.setKnowledgeBaseId(stored.getKnowledgeBaseId());
        dataset.setItems(stored.getItems());
        return dataset;
    }

    public List<EvalDatasetDTO> listByKnowledgeBase(Long kbId) {
        ensurePersistence();
        List<EvalDatasetDTO> result = jdbcTemplate.query("""
                SELECT id, name, knowledge_base_id
                FROM eval_dataset
                WHERE knowledge_base_id = ?
                ORDER BY id DESC
                """, (rs, rowNum) -> {
                EvalDatasetDTO dto = new EvalDatasetDTO();
                dto.setId(rs.getLong("id"));
                dto.setName(rs.getString("name"));
                dto.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
                return dto;
        }, kbId);
        Map<Long, List<EvaluationDataset.EvalItem>> itemsByDataset = loadItemsByDatasetIds(
                result.stream().map(EvalDatasetDTO::getId).toList());
        result.forEach(dto -> dto.setItems(itemsByDataset.getOrDefault(dto.getId(), List.of())));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void importFromJson(Long datasetId, String json) {
        ensurePersistence();
        getDataset(datasetId);
        EvaluationDataset imported = EvaluationDataset.loadFromJson(json);
        if (imported.getItems() != null) {
            insertItems(datasetId, imported.getItems());
        }
        log.info("Imported {} items into dataset {}", imported.getItems() != null ? imported.getItems().size() : 0, datasetId);
    }

    public String exportToJson(Long datasetId) {
        return getEvaluationDataset(datasetId).toJson();
    }

    public String runEvaluation(Long datasetId) {
        ensurePersistence();
        EvaluationDataset dataset = getEvaluationDataset(datasetId);
        String runId = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO eval_run(run_id, dataset_id, status)
                VALUES (?, ?, ?)
                """, runId, datasetId, "RUNNING");
        log.info("Starting evaluation run: runId={}, datasetId={}", runId, datasetId);

        evalTaskExecutor.execute(() -> evaluateAndPersist(runId, dataset));
        return runId;
    }

    private void evaluateAndPersist(String runId, EvaluationDataset dataset) {
        try {
            EvaluationReport report = ragasEvaluator.evaluate(dataset);
            jdbcTemplate.update("""
                    UPDATE eval_run
                    SET status = ?, report_json = %s, completed_at = CURRENT_TIMESTAMP
                    WHERE run_id = ?
                    """.formatted(jsonParam()), "COMPLETED", report.toJson(), runId);
            persistRunItems(runId, report);
            log.info("Evaluation run completed: runId={}", runId);
        } catch (Exception e) {
            jdbcTemplate.update("""
                    UPDATE eval_run
                    SET status = ?, error_message = ?, completed_at = CURRENT_TIMESTAMP
                    WHERE run_id = ?
                    """, "FAILED", e.getMessage(), runId);
            log.error("Evaluation run failed: runId={}", runId, e);
        }
    }

    public EvaluationReport getReport(String runId) {
        ensurePersistence();
        return jdbcTemplate.query("""
                        SELECT status, report_json
                        FROM eval_run
                        WHERE run_id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Run not found: " + runId);
                    }
                    if ("RUNNING".equals(rs.getString("status"))) {
                        return null;
                    }
                    String json = rs.getString("report_json");
                    return json == null ? null : JSON.parseObject(json, EvaluationReport.class);
                }, runId);
    }

    private void ensurePersistence() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("EvalDatasetService requires JdbcTemplate persistence");
        }
    }

    private List<EvaluationDataset.EvalItem> loadItems(Long datasetId) {
        return jdbcTemplate.query("""
                SELECT query, expected_answer, ground_truth_contexts
                FROM eval_dataset_item
                WHERE dataset_id = ?
                ORDER BY position ASC, id ASC
                """, (rs, rowNum) -> new EvaluationDataset.EvalItem(
                rs.getString("query"),
                rs.getString("expected_answer"),
                parseStringList(rs.getString("ground_truth_contexts"))
        ), datasetId);
    }

    private Map<Long, List<EvaluationDataset.EvalItem>> loadItemsByDatasetIds(List<Long> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = datasetIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        Map<Long, List<EvaluationDataset.EvalItem>> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT dataset_id, query, expected_answer, ground_truth_contexts
                FROM eval_dataset_item
                WHERE dataset_id IN (%s)
                ORDER BY dataset_id ASC, position ASC, id ASC
                """.formatted(placeholders), rs -> {
            Long datasetId = rs.getLong("dataset_id");
            result.computeIfAbsent(datasetId, ignored -> new ArrayList<>()).add(new EvaluationDataset.EvalItem(
                    rs.getString("query"),
                    rs.getString("expected_answer"),
                    parseStringList(rs.getString("ground_truth_contexts"))
            ));
        }, datasetIds.toArray());
        return result;
    }

    private void replaceItems(Long datasetId, List<EvaluationDataset.EvalItem> items) {
        jdbcTemplate.update("DELETE FROM eval_dataset_item WHERE dataset_id = ?", datasetId);
        insertItems(datasetId, items);
    }

    private void insertItems(Long datasetId, List<EvaluationDataset.EvalItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            EvaluationDataset.EvalItem item = items.get(i);
            jdbcTemplate.update("""
                    INSERT INTO eval_dataset_item(dataset_id, position, query, expected_answer, ground_truth_contexts)
                    VALUES (?, ?, ?, ?, %s)
                    """.formatted(jsonParam()), datasetId, i, item.getQuery(), item.getExpectedAnswer(),
                    JSON.toJSONString(item.getGroundTruthContexts() != null ? item.getGroundTruthContexts() : List.of()));
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.parseArray(json, String.class);
    }

    private void persistRunItems(String runId, EvaluationReport report) {
        if (report == null || report.getItemResults() == null) {
            return;
        }
        for (EvaluationReport.ItemResult item : report.getItemResults()) {
            jdbcTemplate.update("""
                    INSERT INTO eval_run_item(run_id, query, answer, scores_json, feedback)
                    VALUES (?, ?, ?, %s, ?)
                    """.formatted(jsonParam()), runId, item.getQuery(), item.getAnswer(),
                    JSON.toJSONString(item.getScores() != null ? item.getScores() : Map.of()), item.getFeedback());
        }
    }

    private String jsonParam() {
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2")
                    ? "?"
                    : "?::jsonb";
        } catch (Exception e) {
            return "?::jsonb";
        }
    }
}
