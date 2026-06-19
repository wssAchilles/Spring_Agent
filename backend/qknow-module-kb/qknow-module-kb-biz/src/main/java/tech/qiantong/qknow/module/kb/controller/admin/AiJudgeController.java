package tech.qiantong.qknow.module.kb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.dal.dataobject.judge.KbAiJudgeScoreDO;
import tech.qiantong.qknow.module.kb.service.judge.AiJudgeService;
import tech.qiantong.qknow.module.kb.service.judge.KbAiJudgeScoreService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "AI Judge 质量分析")
@RestController
@RequestMapping("/kb/judge")
public class AiJudgeController extends BaseController {

    @Resource
    private KbAiJudgeScoreService judgeScoreService;

    @Resource
    private AiJudgeService aiJudgeService;

    @Operation(summary = "获取评分统计")
    @GetMapping("/statistics")
    public CommonResult<Map<String, Object>> getStatistics(@RequestParam Long workspaceId) {
        KbAiJudgeScoreService.JudgeStatistics stats = judgeScoreService.getStatistics(workspaceId);

        Map<String, Object> result = new HashMap<>();
        result.put("total", stats.total());
        result.put("passed", stats.passed());
        result.put("passRate", stats.total() > 0 ? (double) stats.passed() / stats.total() : 0);
        result.put("avgFactuality", stats.avgFactuality());
        result.put("avgRelevance", stats.avgRelevance());
        result.put("avgInstruction", stats.avgInstruction());

        return CommonResult.success(result);
    }

    @Operation(summary = "获取对话的评分历史")
    @GetMapping("/history")
    public CommonResult<List<KbAiJudgeScoreDO>> getHistory(@RequestParam Long conversationId) {
        return CommonResult.success(judgeScoreService.getScoresByConversationId(conversationId));
    }

    @Operation(summary = "获取当前评分阈值")
    @GetMapping("/threshold")
    public CommonResult<Map<String, Object>> getThreshold() {
        Map<String, Object> result = new HashMap<>();
        result.put("threshold", aiJudgeService.getThreshold());
        return CommonResult.success(result);
    }

    @Operation(summary = "设置评分阈值")
    @PostMapping("/threshold")
    public CommonResult<Boolean> setThreshold(@RequestParam double threshold) {
        aiJudgeService.setThreshold(threshold);
        return CommonResult.success(true);
    }
}
