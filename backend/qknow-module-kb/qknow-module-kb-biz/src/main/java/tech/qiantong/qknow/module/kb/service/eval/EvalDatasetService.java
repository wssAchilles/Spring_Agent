package tech.qiantong.qknow.module.kb.service.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class EvalDatasetService {

    private final RagasEvaluator ragasEvaluator;
    private final Map<Long, EvaluationDataset> datasets = new ConcurrentHashMap<>();
    private final Map<String, EvaluationReport> reports = new ConcurrentHashMap<>();
    private final Map<String, String> runStatus = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public EvalDatasetService(RagasEvaluator ragasEvaluator) {
        this.ragasEvaluator = ragasEvaluator;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createDataset(EvalDatasetDTO dto) {
        Long id = idGenerator.getAndIncrement();
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName(dto.getName());
        dataset.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        dataset.setItems(dto.getItems() != null ? dto.getItems() : new ArrayList<>());
        datasets.put(id, dataset);
        log.info("Created eval dataset: id={}, name={}, kbId={}", id, dto.getName(), dto.getKnowledgeBaseId());
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDataset(EvalDatasetDTO dto) {
        if (!datasets.containsKey(dto.getId())) {
            throw new IllegalArgumentException("Dataset not found: " + dto.getId());
        }
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName(dto.getName());
        dataset.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        dataset.setItems(dto.getItems() != null ? dto.getItems() : new ArrayList<>());
        datasets.put(dto.getId(), dataset);
        log.info("Updated eval dataset: id={}", dto.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        if (datasets.remove(id) == null) {
            throw new IllegalArgumentException("Dataset not found: " + id);
        }
        log.info("Deleted eval dataset: id={}", id);
    }

    public EvalDatasetDTO getDataset(Long id) {
        EvaluationDataset dataset = datasets.get(id);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found: " + id);
        }
        EvalDatasetDTO dto = new EvalDatasetDTO();
        dto.setId(id);
        dto.setName(dataset.getName());
        dto.setKnowledgeBaseId(dataset.getKnowledgeBaseId());
        dto.setItems(dataset.getItems());
        return dto;
    }

    public List<EvalDatasetDTO> listByKnowledgeBase(Long kbId) {
        List<EvalDatasetDTO> result = new ArrayList<>();
        datasets.forEach((id, dataset) -> {
            if (kbId.equals(dataset.getKnowledgeBaseId())) {
                EvalDatasetDTO dto = new EvalDatasetDTO();
                dto.setId(id);
                dto.setName(dataset.getName());
                dto.setKnowledgeBaseId(dataset.getKnowledgeBaseId());
                dto.setItems(dataset.getItems());
                result.add(dto);
            }
        });
        return result;
    }

    public void importFromJson(Long datasetId, String json) {
        EvaluationDataset dataset = datasets.get(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found: " + datasetId);
        }
        EvaluationDataset imported = EvaluationDataset.loadFromJson(json);
        if (imported.getItems() != null) {
            dataset.getItems().addAll(imported.getItems());
        }
        log.info("Imported {} items into dataset {}", imported.getItems() != null ? imported.getItems().size() : 0, datasetId);
    }

    public String exportToJson(Long datasetId) {
        EvaluationDataset dataset = datasets.get(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found: " + datasetId);
        }
        return dataset.toJson();
    }

    @Async
    public String runEvaluation(Long datasetId) {
        EvaluationDataset dataset = datasets.get(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found: " + datasetId);
        }
        String runId = UUID.randomUUID().toString().replace("-", "");
        runStatus.put(runId, "RUNNING");
        log.info("Starting evaluation run: runId={}, datasetId={}", runId, datasetId);

        try {
            EvaluationReport report = ragasEvaluator.evaluate(dataset);
            reports.put(runId, report);
            runStatus.put(runId, "COMPLETED");
            log.info("Evaluation run completed: runId={}", runId);
        } catch (Exception e) {
            runStatus.put(runId, "FAILED");
            log.error("Evaluation run failed: runId={}", runId, e);
        }
        return runId;
    }

    public EvaluationReport getReport(String runId) {
        String status = runStatus.get(runId);
        if (status == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        if ("RUNNING".equals(status)) {
            return null;
        }
        return reports.get(runId);
    }
}
