package tech.qiantong.qknow.kb.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;
import tech.qiantong.qknow.module.kb.service.eval.EvalDatasetDTO;
import tech.qiantong.qknow.module.kb.service.eval.EvalDatasetService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalDatasetServiceTest {

    @Mock
    private RagasEvaluator ragasEvaluator;

    private EvalDatasetService evalDatasetService;

    @BeforeEach
    void setUp() {
        evalDatasetService = new EvalDatasetService(ragasEvaluator);
    }

    @Test
    @DisplayName("CRUD lifecycle - create, get, update, delete")
    void crudLifecycle() {
        EvalDatasetDTO createDto = new EvalDatasetDTO();
        createDto.setName("test-dataset");
        createDto.setKnowledgeBaseId(100L);

        Long id = evalDatasetService.createDataset(createDto);
        assertNotNull(id);

        EvalDatasetDTO fetched = evalDatasetService.getDataset(id);
        assertEquals("test-dataset", fetched.getName());
        assertEquals(100L, fetched.getKnowledgeBaseId());

        EvalDatasetDTO updateDto = new EvalDatasetDTO();
        updateDto.setId(id);
        updateDto.setName("updated-dataset");
        updateDto.setKnowledgeBaseId(200L);
        evalDatasetService.updateDataset(updateDto);

        EvalDatasetDTO updated = evalDatasetService.getDataset(id);
        assertEquals("updated-dataset", updated.getName());
        assertEquals(200L, updated.getKnowledgeBaseId());

        List<EvalDatasetDTO> list = evalDatasetService.listByKnowledgeBase(200L);
        assertEquals(1, list.size());

        evalDatasetService.deleteDataset(id);
        assertThrows(IllegalArgumentException.class, () -> evalDatasetService.getDataset(id));
    }

    @Test
    @DisplayName("Import/export roundtrip")
    void importExportRoundtrip() {
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setName("roundtrip-test");
        dto.setKnowledgeBaseId(1L);
        Long id = evalDatasetService.createDataset(dto);

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
        assertNotNull(exported);
        assertTrue(exported.contains("What is X?"));
        assertTrue(exported.contains("What is Z?"));
    }

    @Test
    @DisplayName("Async evaluation trigger returns runId")
    void asyncEvaluationTrigger() {
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setName("eval-test");
        dto.setKnowledgeBaseId(1L);

        EvaluationDataset.EvalItem item = new EvaluationDataset.EvalItem();
        item.setQuery("test query");
        item.setExpectedAnswer("test answer");
        item.setGroundTruthContexts(List.of("context"));
        dto.setItems(List.of(item));

        Long id = evalDatasetService.createDataset(dto);

        EvaluationReport report = new EvaluationReport();
        report.setRunId("test-run-id");
        report.setDatasetName("eval-test");
        when(ragasEvaluator.evaluate(any(EvaluationDataset.class))).thenReturn(report);

        String runId = evalDatasetService.runEvaluation(id);
        assertNotNull(runId);
        assertFalse(runId.isEmpty());
    }
}
