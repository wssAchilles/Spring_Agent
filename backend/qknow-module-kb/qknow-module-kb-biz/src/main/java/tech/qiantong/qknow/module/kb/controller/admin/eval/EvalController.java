package tech.qiantong.qknow.module.kb.controller.admin.eval;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.module.kb.service.eval.EvalDatasetDTO;
import tech.qiantong.qknow.module.kb.service.eval.EvalDatasetService;

import java.util.List;
import java.util.Map;

@Tag(name = "Evaluation 管理")
@RestController
@RequestMapping("/api/eval")
public class EvalController extends BaseController {

    @Resource
    private EvalDatasetService evalDatasetService;

    @Operation(summary = "创建评估数据集")
    @PostMapping("/datasets")
    public CommonResult<Long> createDataset(@RequestBody EvalDatasetDTO dto) {
        return CommonResult.success(evalDatasetService.createDataset(dto));
    }

    @Operation(summary = "获取评估数据集")
    @GetMapping("/datasets/{id}")
    public CommonResult<EvalDatasetDTO> getDataset(@PathVariable Long id) {
        return CommonResult.success(evalDatasetService.getDataset(id));
    }

    @Operation(summary = "按知识库列出评估数据集")
    @GetMapping("/datasets")
    public CommonResult<List<EvalDatasetDTO>> listByKnowledgeBase(@RequestParam Long kbId) {
        return CommonResult.success(evalDatasetService.listByKnowledgeBase(kbId));
    }

    @Operation(summary = "删除评估数据集")
    @DeleteMapping("/datasets/{id}")
    public CommonResult<Boolean> deleteDataset(@PathVariable Long id) {
        evalDatasetService.deleteDataset(id);
        return CommonResult.success(true);
    }

    @Operation(summary = "导入评估数据")
    @PostMapping("/datasets/{id}/import")
    public CommonResult<Boolean> importJson(@PathVariable Long id, @RequestBody String json) {
        evalDatasetService.importFromJson(id, json);
        return CommonResult.success(true);
    }

    @Operation(summary = "导出评估数据")
    @GetMapping("/datasets/{id}/export")
    public CommonResult<String> exportJson(@PathVariable Long id) {
        return CommonResult.success(evalDatasetService.exportToJson(id));
    }

    @Operation(summary = "触发评估运行")
    @PostMapping("/run")
    public CommonResult<Map<String, String>> runEvaluation(@RequestParam Long datasetId) {
        String runId = evalDatasetService.runEvaluation(datasetId);
        return CommonResult.success(Map.of("runId", runId));
    }

    @Operation(summary = "获取评估报告")
    @GetMapping("/report/{runId}")
    public CommonResult<EvaluationReport> getReport(@PathVariable String runId) {
        EvaluationReport report = evalDatasetService.getReport(runId);
        if (report == null) {
            return CommonResult.success(null);
        }
        return CommonResult.success(report);
    }
}
