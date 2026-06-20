package tech.qiantong.qknow.module.kb.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;

import java.util.List;
import java.util.Map;

/**
 * 内部 REST API（供 Hermes 微服务回调）
 * 仅在 Docker 内网可访问，不经过 Nginx 路由
 */
@Slf4j
@RestController
@RequestMapping("/internal/api")
public class KbInternalApiController {

    @Resource
    private IKmcApiService kmcApiService;

    @Resource
    private IAiModelApiService aiModelService;

    /**
     * RAG 知识检索
     * POST /internal/api/rag/recall
     */
    @PostMapping("/rag/recall")
    public CommonResult<String> recall(@RequestBody Map<String, Object> body) {
        Long knowledgeId = Long.parseLong(body.get("knowledgeId").toString());
        String query = body.get("query").toString();
        log.info("内部 RAG 检索: knowledgeId={}, query={}", knowledgeId, query);

        try {
            var results = kmcApiService.recallTest(knowledgeId, query);
            return CommonResult.success(JSONObject.toJSONString(results));
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return CommonResult.error(500, "检索失败: " + e.getMessage());
        }
    }

    /**
     * 获取模型配置
     * GET /internal/api/model/config?modelId=1&modelName=deepseek-chat
     */
    @GetMapping("/model/config")
    public CommonResult<Map<String, String>> getModelConfig(
            @RequestParam Long modelId, @RequestParam String modelName) {
        log.info("内部模型配置查询: modelId={}, modelName={}", modelId, modelName);

        try {
            var chatModel = aiModelService.getChatModel(modelId, modelName);
            return CommonResult.success(Map.of(
                    "status", "ok",
                    "modelId", String.valueOf(modelId),
                    "modelName", modelName
            ));
        } catch (Exception e) {
            log.error("模型配置查询失败", e);
            return CommonResult.error(500, "查询失败: " + e.getMessage());
        }
    }
}
