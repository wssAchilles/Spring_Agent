package tech.qiantong.qknow.module.kb.controller.admin;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 内部 REST API（供 Hermes 微服务回调，强制执行共享密钥常量时间鉴权或回环隔离）
 */
@Slf4j
@RestController
@RequestMapping("/internal/api")
public class KbInternalApiController {

    private static final String INTERNAL_TOKEN_HEADER = "x-hermes-auth-token";

    @Resource
    private IKmcApiService kmcApiService;

    @Resource
    private IAiModelApiService aiModelService;

    @Value("${hermes.grpc.auth.token:${HERMES_GRPC_AUTH_TOKEN:}}")
    private String hermesAuthToken;

    /**
     * 校验内部回调请求身份：已配置共享密钥时执行常量时间比对，未配置时仅允许本机回环访问
     */
    private boolean isAuthorizedInternalCall() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }
        HttpServletRequest req = attrs.getRequest();
        if (hermesAuthToken != null && !hermesAuthToken.isBlank()) {
            String provided = req.getHeader(INTERNAL_TOKEN_HEADER);
            return provided != null && MessageDigest.isEqual(
                    provided.getBytes(StandardCharsets.UTF_8),
                    hermesAuthToken.getBytes(StandardCharsets.UTF_8));
        }
        String remoteAddr = req.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr);
    }

    /**
     * RAG 知识检索
     * POST /internal/api/rag/recall
     */
    @PostMapping("/rag/recall")
    public CommonResult<String> recall(@RequestBody Map<String, Object> body) {
        if (!isAuthorizedInternalCall()) {
            return CommonResult.error(401, "未授权的内部接口访问");
        }
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
     * GET /internal/api/model/config?modelId=1&modelName=deepseek-flash
     */
    @GetMapping("/model/config")
    public CommonResult<Map<String, String>> getModelConfig(
            @RequestParam Long modelId, @RequestParam String modelName) {
        if (!isAuthorizedInternalCall()) {
            return CommonResult.error(401, "未授权的内部接口访问");
        }
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
