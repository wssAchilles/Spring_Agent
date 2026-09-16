package tech.qiantong.qknow.hermes.cognitive.engine;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.cognitive.dto.ReflexionAction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 层次化工具自省反思与自愈执行器 (HierarchicalToolReflexionGovernor)
 * 遵循 Phase 84 定理 1.2 HMDP 因果自愈：
 * 微观单步参数因果自省纠偏 (<= 2次) + 键序规范化 SHA-256 指纹拦截 + 宏观任务图级状态回退
 */
@Slf4j
public class HierarchicalToolReflexionGovernor {

    public static final int MAX_MICRO_RETRIES = 2;
    private final Map<String, Integer> fingerprintHistory = new ConcurrentHashMap<>();

    /**
     * 计算参数键序规范化 SHA-256 指纹
     */
    public String calculateCanonicalFingerprint(String toolName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            argumentsJson = "{}";
        }
        try {
            Object parsed = JSON.parse(argumentsJson);
            String canonical = JSON.toJSONString(parsed, JSONWriter.Feature.MapSortField);
            return DigestUtil.sha256Hex(toolName + "::" + canonical);
        } catch (Exception e) {
            return DigestUtil.sha256Hex(toolName + "::" + argumentsJson.trim());
        }
    }

    /**
     * 工具执行异常因果自省决策
     */
    public ReflexionAction governToolFailure(
            String toolName,
            String argumentsJson,
            String rawErrorMessage,
            int currentRetryCount,
            String candidateFallbackTool
    ) {
        String fp = calculateCanonicalFingerprint(toolName, argumentsJson);
        int seenCount = fingerprintHistory.getOrDefault(fp, 0) + 1;
        fingerprintHistory.put(fp, seenCount);

        // 1. 同构参数连续调用直接阻断，强制升级为宏观图级回退
        if (seenCount > 1) {
            log.warn("[ToolGovernor] 检测到同构参数重复调用，同构死循环概率恒为0强制阻断: tool={}, fp={}", toolName, fp);
            return handleMacroFallback(toolName, candidateFallbackTool, "检测到完全相同参数同构调用，微观反思无法收敛");
        }

        // 2. 微观单步因果特征提取与参数自适应自愈
        if (currentRetryCount < MAX_MICRO_RETRIES) {
            String causalFeature = extractCausalFeature(rawErrorMessage);
            String revisedJson = attemptHeuristicCorrection(argumentsJson, causalFeature);
            log.info("[ToolGovernor] 微观单步自省纠偏 (第 {} 次): tool={}, cause={}", currentRetryCount + 1, toolName, causalFeature);
            return new ReflexionAction(
                    ReflexionAction.ReflexionType.MICRO_RETRY_ADAPTIVE,
                    currentRetryCount + 1,
                    causalFeature,
                    fp,
                    revisedJson,
                    null,
                    null
            );
        }

        // 3. 达到微观重试上限，升级为宏观任务图级回退
        return handleMacroFallback(toolName, candidateFallbackTool, "微观重试达到上限 (" + MAX_MICRO_RETRIES + "次)");
    }

    private ReflexionAction handleMacroFallback(String toolName, String fallbackTool, String reason) {
        if (fallbackTool != null && !fallbackTool.trim().isEmpty()) {
            log.warn("[ToolGovernor] 触发宏观工具切换: 原工具 {} -> 备选工具 {}, 原因: {}", toolName, fallbackTool, reason);
            return new ReflexionAction(
                    ReflexionAction.ReflexionType.MACRO_TOOL_FALLBACK,
                    MAX_MICRO_RETRIES,
                    reason,
                    "",
                    null,
                    fallbackTool,
                    null
            );
        } else {
            log.warn("[ToolGovernor] 无备选工具，触发 HITL 人机协同询问: tool={}, 原因: {}", toolName, reason);
            String hitlPrompt = String.format("工具 '%s' 执行失败 (%s)，当前无自动备选工具，请向用户澄清补充必要执行上下文。", toolName, reason);
            return new ReflexionAction(
                    ReflexionAction.ReflexionType.HITL_ASK_USER,
                    MAX_MICRO_RETRIES,
                    reason,
                    "",
                    null,
                    null,
                    hitlPrompt
            );
        }
    }

    /**
     * 提取因果错误特征
     */
    public String extractCausalFeature(String rawErrorMessage) {
        if (rawErrorMessage == null) return "UNKNOWN_ERROR: 未知错误";
        String msg = rawErrorMessage.toLowerCase();
        if (msg.contains("syntax error") || msg.contains("sql")) {
            return "SQL_SYNTAX_ERROR: 语法存在非法保留字或标点符号";
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "DOWNSTREAM_TIMEOUT: 下游工具服务调用超时";
        }
        if (msg.contains("missing") && msg.contains("parameter")) {
            return "MISSING_PARAMETER: 缺失必填字段参数";
        }
        if (msg.contains("json") || msg.contains("parse")) {
            return "JSON_FORMAT_ERROR: 参数或返回结果不是合法JSON";
        }
        return "RUNTIME_EXCEPTION: " + rawErrorMessage.substring(0, Math.min(60, rawErrorMessage.length()));
    }

    /**
     * 启发式参数微调
     */
    private String attemptHeuristicCorrection(String originalJson, String causalFeature) {
        if (causalFeature.contains("MISSING_PARAMETER")) {
            return originalJson != null && !originalJson.isBlank() ? originalJson : "{}";
        }
        return originalJson;
    }

    public void clearHistory() {
        fingerprintHistory.clear();
    }
}
