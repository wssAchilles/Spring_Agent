package tech.qiantong.qknow.ai.htn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动作异常错误分类与结构化自愈补丁引擎（Causal Error Classifier & Meta-Patch Engine）。
 * <p>
 * 区分可恢复瞬态/语法参数异常与不可恢复权限/资源缺失异常；当注入真实 DeepSeek {@link ChatModel} 时生成针对性修复建议，
 * 无网络单测环境下执行确定性分类与退避策略（形式化约束级极小冲突核自愈由第 5 章 {@code DbmLtlFormalMicroSolver} 承接）。
 * </p>
 */
@Component
public class CausalMetaReasoningEngine {

    private static final Logger log = LoggerFactory.getLogger(CausalMetaReasoningEngine.class);

    public static final int MAX_HEALING_ROUNDS = 3;

    private final ChatModel chatModel;

    public CausalMetaReasoningEngine() {
        this(null);
    }

    @Autowired
    public CausalMetaReasoningEngine(@Autowired(required = false) ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 错误分类枚举 */
    public enum ErrorCategory {
        RECOVERABLE_TRANSIENT,       // 网络抖动、限流、瞬态异常 (可自愈)
        RECOVERABLE_SYNTAX_PARAMS,   // 参数错误、SQL 语法错误、缺少可选字段 (可自愈)
        NON_RECOVERABLE_PERMISSION,  // 权限缺失、身份认证被拒 (不可恢复，快速失败)
        NON_RECOVERABLE_NOT_FOUND    // 物理资源/表/记录永久不存在 (不可恢复，快速失败)
    }

    /**
     * 故障执行上下文数据模型
     */
    public record FailureContext(
            String taskId,
            String actionName,
            String rawErrorMessage,
            int currentRound,
            Map<String, Object> executionContext
    ) {}

    /**
     * 元反思自愈裁决模型
     */
    public record HealingVerdict(
            boolean canHeal,
            ErrorCategory category,
            int roundCompleted,
            Map<String, Object> metaPatch, // 修正后的补丁参数
            String diagnosticExplanation
    ) {}

    /**
     * 执行因果溯因元反思
     */
    public HealingVerdict diagnoseAndHeal(FailureContext context) {
        if (context == null) {
            return new HealingVerdict(false, ErrorCategory.NON_RECOVERABLE_NOT_FOUND, 0, Map.of(), "上下文为空，拒绝自愈");
        }

        String msg = context.rawErrorMessage() != null ? context.rawErrorMessage().toLowerCase() : "";

        // 1. 结构因果模式匹配与分类
        ErrorCategory category = classifyError(msg);

        // 2. 检查是否为不可恢复错误 (直接快速失败，拒绝暴力重试)
        if (category == ErrorCategory.NON_RECOVERABLE_PERMISSION || category == ErrorCategory.NON_RECOVERABLE_NOT_FOUND) {
            log.warn("因果元反思判定为致命不可恢复异常: taskId={}, category={}, reason={}",
                    context.taskId(), category, context.rawErrorMessage());
            return new HealingVerdict(false, category, context.currentRound(), Map.of(), "因果诊断确定为不可恢复错误，快速失败并触发 SAGA 补偿");
        }

        // 3. 检查轮次上限
        if (context.currentRound() >= MAX_HEALING_ROUNDS) {
            log.warn("达到因果元反思最大轮次上限 {}: taskId={}", MAX_HEALING_ROUNDS, context.taskId());
            return new HealingVerdict(false, category, context.currentRound(), Map.of(), "达到最大自愈轮次上限，自愈收敛终止");
        }

        // 4. 构建针对性元补丁 (Meta-Patch)
        Map<String, Object> patch = new LinkedHashMap<>();
        if (category == ErrorCategory.RECOVERABLE_SYNTAX_PARAMS) {
            patch.put("injected_sanitization", true);
            patch.put("fallback_param_cast", "STRING_TO_LONG");
            patch.put("retry_delay_ms", 100L);
        } else {
            patch.put("exponential_backoff_ms", (long) Math.pow(2, context.currentRound()) * 150L);
            patch.put("jitter_ratio", 0.15);
        }

        if (chatModel != null) {
            try {
                String llmAdvice = chatModel.call("请用一句话给出工具动作 [" + context.actionName()
                        + "] 报错 [" + context.rawErrorMessage() + "] 的参数修正建议");
                if (llmAdvice != null && !llmAdvice.isBlank()) {
                    patch.put("llm_remediation_hint", llmAdvice.trim());
                }
            } catch (Exception ex) {
                log.debug("DeepSeek 辅助诊断不可用，沿用确定性修复策略: {}", ex.getMessage());
            }
        }

        int nextRound = context.currentRound() + 1;
        log.info("因果元反思生成精准自愈补丁: taskId={}, round={}, patch={}", context.taskId(), nextRound, patch);

        return new HealingVerdict(
                true,
                category,
                nextRound,
                Collections.unmodifiableMap(patch),
                "因果元反思成功定位根因并生成补丁参数，自愈收敛推进"
        );
    }

    private ErrorCategory classifyError(String msg) {
        if (msg.contains("permission denied") || msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("403")) {
            return ErrorCategory.NON_RECOVERABLE_PERMISSION;
        }
        if (msg.contains("not found") || msg.contains("table does not exist") || msg.contains("404")) {
            return ErrorCategory.NON_RECOVERABLE_NOT_FOUND;
        }
        if (msg.contains("syntax error") || msg.contains("invalid parameter") || msg.contains("type mismatch")) {
            return ErrorCategory.RECOVERABLE_SYNTAX_PARAMS;
        }
        return ErrorCategory.RECOVERABLE_TRANSIENT;
    }
}
