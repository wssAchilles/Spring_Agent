package tech.qiantong.qknow.kb.eval;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;
import tech.qiantong.qknow.module.kb.service.eval.EvalDatasetDTO;
import tech.qiantong.qknow.module.kb.service.eval.EvalDatasetService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalDatasetServiceTest {

    @Mock
    private RagasEvaluator ragasEvaluator;

    private JdbcTemplate jdbcTemplate;
    private EvalDatasetService evalDatasetService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:eval_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        evalDatasetService = new EvalDatasetService(ragasEvaluator, jdbcTemplate);
    }

    @Test
    @DisplayName("CRUD lifecycle persists dataset and items")
    void crudLifecyclePersistsData() {
        Long id = createDataset("test-dataset", 100L, List.of());

        EvalDatasetDTO fetched = evalDatasetService.getDataset(id);
        assertEquals("test-dataset", fetched.getName());
        assertEquals(100L, fetched.getKnowledgeBaseId());

        EvalDatasetDTO updateDto = new EvalDatasetDTO();
        updateDto.setId(id);
        updateDto.setName("updated-dataset");
        updateDto.setKnowledgeBaseId(200L);
        updateDto.setItems(List.of(new EvaluationDataset.EvalItem("q", "a", List.of("ctx"))));
        evalDatasetService.updateDataset(updateDto);

        EvalDatasetService rebuilt = new EvalDatasetService(ragasEvaluator, jdbcTemplate);
        EvalDatasetDTO updated = rebuilt.getDataset(id);
        assertEquals("updated-dataset", updated.getName());
        assertEquals(1, updated.getItems().size());
        assertEquals("q", updated.getItems().get(0).getQuery());

        assertEquals(1, rebuilt.listByKnowledgeBase(200L).size());
        rebuilt.deleteDataset(id);
        assertThrows(IllegalArgumentException.class, () -> rebuilt.getDataset(id));
    }

    @Test
    @DisplayName("Import/export roundtrip")
    void importExportRoundtrip() {
        Long id = createDataset("roundtrip-test", 1L, List.of());

        String importJson = """
                {
                  "name": "imported",
                  "knowledgeBaseId": 1,
                  "items": [
                    {"query": "What is X?", "expectedAnswer": "X is Y", "groundTruthContexts": ["ctx1"]},
                    {"query": "What is Z?", "expectedAnswer": "Z is W", "groundTruthContexts": ["ctx2"]}
                  ]
                }
                """;
        evalDatasetService.importFromJson(id, importJson);

        EvalDatasetDTO afterImport = evalDatasetService.getDataset(id);
        assertEquals(2, afterImport.getItems().size());
        assertEquals("What is X?", afterImport.getItems().get(0).getQuery());

        String exported = evalDatasetService.exportToJson(id);
        assertTrue(exported.contains("What is X?"));
        assertTrue(exported.contains("What is Z?"));
    }

    @Test
    @DisplayName("Evaluation run persists report")
    void runEvaluationPersistsReport() {
        Long id = createDataset("eval-test", 1L,
                List.of(new EvaluationDataset.EvalItem("test query", "test answer", List.of("context"))));

        EvaluationReport report = new EvaluationReport();
        report.setRunId("test-run-id");
        report.setDatasetName("eval-test");
        report.setMetricScores(Map.of("faithfulness", 0.9));
        when(ragasEvaluator.evaluate(any(EvaluationDataset.class))).thenReturn(report);

        String runId = evalDatasetService.runEvaluation(id);
        EvaluationReport persisted = evalDatasetService.getReport(runId);

        assertNotNull(persisted);
        assertEquals("eval-test", persisted.getDatasetName());
        assertEquals(0.9, persisted.getMetricScores().get("faithfulness"));
    }

    private Long createDataset(String name, Long kbId, List<EvaluationDataset.EvalItem> items) {
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setName(name);
        dto.setKnowledgeBaseId(kbId);
        dto.setItems(items);
        return evalDatasetService.createDataset(dto);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE eval_dataset (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    knowledge_base_id BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE eval_dataset_item (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    dataset_id BIGINT NOT NULL,
                    position INTEGER DEFAULT 0,
                    query TEXT NOT NULL,
                    expected_answer TEXT,
                    ground_truth_contexts TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE eval_run (
                    run_id VARCHAR(64) PRIMARY KEY,
                    dataset_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    report_json TEXT,
                    error_message TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE eval_run_item (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    query TEXT NOT NULL,
                    answer TEXT,
                    scores_json TEXT,
                    feedback TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
